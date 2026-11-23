package io.github.hyperpriv;

import static io.github.hyperpriv.Appearance.Value.*;
import static io.github.hyperpriv.AppearanceHooks.dp;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.telephony.SubscriptionManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

final class SignalAppearance {
    private static final String MOBILE = "com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView";
    private static final String WIFI = "com.android.systemui.statusbar.pipeline.wifi.ui.view.ModernStatusBarWifiView";
    private static final String SLOTS = "com.android.systemui.statusbar.phone.ui.StatusBarIconList";
    private final AppearanceHooks hooks;
    private final SystemBarAppearance bar;
    private final WeakHashMap<ViewGroup, Icons> groups = new WeakHashMap<>();
    private final Runnable dualHit, visibilityHit, spacingHit, orderHit;

    SignalAppearance(AppearanceHooks hooks, SystemBarAppearance bar) {
        this.hooks = hooks;
        this.bar = bar;
        dualHit = hooks.firstHit("dual_signal");
        visibilityHit = hooks.firstHit("network_indicators");
        spacingHit = hooks.firstHit("status_icon_spacing");
        orderHit = hooks.firstHit("network_order");
    }

    void install() {
        if (hooks.custom(NETWORK_ORDER)) hooks.register("network_order", this::installOrder);
        if (hooks.custom(ICON_GAP)) hooks.register("status_icon_spacing", this::installSpacing);
        if (hooks.custom(SIGNAL) || hooks.custom(MOBILE_TYPE) || hooks.custom(MOBILE_HD) || hooks.custom(MOBILE_ACTIVITY))
            hooks.register("network_indicators", this::installIndicators);
    }

    private void installOrder() throws Exception {
        Executable constructor = hooks.method(SLOTS, "<init>", "V", "[Ljava/lang/String;");
        hooks.hook(constructor, chain -> {
            if (!hooks.active()) return chain.proceed();
            String[] slots = (String[]) chain.getArg(0);
            int wifi = -1, mobile = -1;
            for (int n = 0; n < slots.length; n++) {
                if ("wifi".equals(slots[n])) wifi = n;
                if ("mobile".equals(slots[n])) mobile = n;
            }
            if (wifi < 0 || mobile < 0 || (wifi < mobile) == (hooks.value(NETWORK_ORDER) == 1)) return chain.proceed();
            String[] order = slots.clone();
            order[wifi] = slots[mobile];
            order[mobile] = slots[wifi];
            orderHit.run();
            return chain.proceed(new Object[]{order});
        });
    }

    private void installSpacing() throws Exception {
        String owner = AppearanceSupport.STATUS_ICONS;
        Field spacing = hooks.field(owner, "iconSpacing", int.class);
        Executable measure = hooks.method(owner, "onMeasure", "V", "I", "I");
        Executable layout = hooks.method(owner, "onLayout", "V", "Z", "I", "I", "I", "I");
        io.github.libxposed.api.XposedInterface.Hooker callback = chain -> {
            View view = (View) chain.getThisObject();
            if (!hooks.active() || SystemBarAppearance.phoneBar(view) == null) return chain.proceed();
            int original = spacing.getInt(view);
            spacing.setInt(view, Math.max(0, original + Math.round(dp(view, hooks.value(ICON_GAP)))));
            try {
                spacingHit.run();
                return chain.proceed();
            } finally { spacing.setInt(view, original); }
        };
        hooks.hook(measure, callback);
        hooks.hook(layout, callback);
    }

    private void installIndicators() throws Exception {
        Executable measure = hooks.method(AppearanceSupport.STATUS_ICONS, "onMeasure", "V", "I", "I");
        Executable subId = hooks.method(MOBILE, "getSubId", "I");
        Method visible = subId.getDeclaringClass().getMethod("isIconVisible");
        if (visible.getReturnType() != boolean.class) throw new NoSuchMethodException("isIconVisible");
        hooks.hook(measure, chain -> {
            ViewGroup group = (ViewGroup) chain.getThisObject();
            if (hooks.active() && SystemBarAppearance.phoneBar(group) != null) {
                Icons icons = groups.get(group);
                if (icons == null) {
                    icons = new Icons(group, (Method) subId, visible);
                    groups.put(group, icons);
                    group.addOnAttachStateChangeListener(icons);
                    if (group.isAttachedToWindow()) icons.onViewAttachedToWindow(group);
                }
                icons.update(group);
            }
            return chain.proceed();
        });
    }

    private final class Icons implements View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
        private final WeakReference<ViewGroup> group;
        private final Method subId, visible;
        private final WeakHashMap<View, Integer> hiddenViews = new WeakHashMap<>();
        private WeakReference<View> collapsed = new WeakReference<>(null);
        private WeakReference<ImageView> leading = new WeakReference<>(null);
        private WeakReference<ImageView> trailing = new WeakReference<>(null);
        private Rect oldClip;
        private int oldWidth, oldAlpha;
        private final DualSignal overlay = new DualSignal();

        Icons(ViewGroup group, Method subId, Method visible) {
            this.group = new WeakReference<>(group);
            this.subId = subId;
            this.visible = visible;
        }
        @Override public void onViewAttachedToWindow(View view) {
            view.getViewTreeObserver().addOnPreDrawListener(this);
        }
        @Override public void onViewDetachedFromWindow(View view) {
            if (view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnPreDrawListener(this);
            restorePair();
            restoreHidden();
        }
        @Override public boolean onPreDraw() {
            ViewGroup view = group.get();
            if (view != null) update(view);
            return true;
        }

        void update(ViewGroup view) {
            if (!hooks.active()) {
                restorePair();
                restoreHidden();
                return;
            }
            View primary = null, secondary = null;
            int primaryId = -1, secondaryId = -1, count = 0;
            for (int index = 0; index < view.getChildCount(); index++) {
                View icon = view.getChildAt(index);
                String owner = icon.getClass().getName();
                if (WIFI.equals(owner)) {
                    if (hooks.custom(MOBILE_ACTIVITY)) hide(icon, "wifi_activity", "wifi_in", "wifi_out");
                    continue;
                }
                if (!MOBILE.equals(owner)) continue;
                if (hooks.custom(MOBILE_TYPE)) hide(icon, "mobile_type", "mobile_type_single", "mobile_special_5G");
                int hd = hooks.value(MOBILE_HD);
                if (hd == 1 || hd == 3) hide(icon, "mobile_speech_hd", "mobile_volte", "mobile_volte_no_service");
                if (hd == 2 || hd == 3) hide(icon, "mobile_vowifi");
                if (hooks.custom(MOBILE_ACTIVITY)) hide(icon, "mobile_left_mobile_inout", "mobile_in", "mobile_out");
                if (!hooks.custom(SIGNAL)) continue;
                try {
                    if (!Boolean.TRUE.equals(visible.invoke(icon))) continue;
                    int id = (Integer) subId.invoke(icon);
                    if (!SubscriptionManager.isValidSubscriptionId(id)) continue;
                    if (count++ == 0) { primary = icon; primaryId = id; }
                    else { secondary = icon; secondaryId = id; }
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
            if (count != 2 || primaryId == secondaryId) {
                restorePair();
                return;
            }
            if (secondaryId == SubscriptionManager.getDefaultDataSubscriptionId()) {
                View swap = primary; primary = secondary; secondary = swap;
            }
            ImageView upper = bar.child(primary, "mobile_signal");
            ImageView lower = bar.child(secondary, "mobile_signal");
            if (upper == null || lower == null || upper.getVisibility() != View.VISIBLE || lower.getVisibility() != View.VISIBLE
                    || secondary.getLayoutParams() == null) { restorePair(); return; }
            if (leading.get() != upper || trailing.get() != lower || collapsed.get() != secondary) {
                restorePair();
                if (!overlay.update(upper, lower)) return;
                leading = new WeakReference<>(upper);
                trailing = new WeakReference<>(lower);
                collapsed = new WeakReference<>(secondary);
                oldAlpha = upper.getImageAlpha();
                oldWidth = secondary.getLayoutParams().width;
                oldClip = secondary.getClipBounds();
                upper.getOverlay().add(overlay);
                upper.setImageAlpha(0);
                secondary.setClipBounds(new Rect());
                ViewGroup.LayoutParams params = secondary.getLayoutParams();
                params.width = 0;
                secondary.setLayoutParams(params);
                dualHit.run();
            } else if (!overlay.update(upper, lower)) {
                restorePair();
            }
        }
        private void hide(View root, String... ids) {
            for (String id : ids) {
                View view = bar.child(root, id);
                if (view == null || view.getVisibility() == View.GONE) continue;
                hiddenViews.put(view, view.getVisibility());
                view.setVisibility(View.GONE);
                visibilityHit.run();
            }
        }
        private void restoreHidden() {
            for (java.util.Map.Entry<View, Integer> entry : hiddenViews.entrySet()) {
                View view = entry.getKey();
                if (view != null && view.getVisibility() == View.GONE) view.setVisibility(entry.getValue());
            }
            hiddenViews.clear();
        }
        private void restorePair() {
            ImageView upper = leading.get();
            if (upper != null) {
                upper.getOverlay().remove(overlay);
                upper.setImageAlpha(oldAlpha);
            }
            View secondary = collapsed.get();
            if (secondary != null) {
                secondary.setClipBounds(oldClip);
                ViewGroup.LayoutParams params = secondary.getLayoutParams();
                if (params != null && params.width == 0) {
                    params.width = oldWidth;
                    secondary.setLayoutParams(params);
                }
            }
            leading.clear();
            trailing.clear();
            collapsed.clear();
            overlay.clear();
        }
    }

    private final class DualSignal extends Drawable {
        private final SignalRow top = new SignalRow(), bottom = new SignalRow();

        boolean update(ImageView first, ImageView last) {
            boolean changed = top.update(first) | bottom.update(last);
            if (top.copy == null || bottom.copy == null) return false;
            if (getBounds().width() != first.getWidth() || getBounds().height() != first.getHeight()) {
                setBounds(0, 0, first.getWidth(), first.getHeight());
                changed = true;
            }
            if (changed) invalidateSelf();
            return true;
        }
        void clear() { top.clear(); bottom.clear(); }
        @Override public void draw(Canvas canvas) {
            int width = getBounds().width(), height = getBounds().height();
            int gap = Math.round(dpFromBounds(SIGNAL_GAP)), dy = Math.round(dpFromBounds(SIGNAL_Y));
            int weight = hooks.value(SIGNAL) == 2 ? 48 : 40;
            int size = hooks.value(SIGNAL_SIZE);
            int row = Math.min((height - gap) / 2, Math.max(1, height * weight * size / 10_000));
            int sizeX = Math.min(width, width * size / 100);
            if (row <= 0 || sizeX <= 0) return;
            int x = (width - sizeX) / 2, y = Math.max(0, Math.min(height - row * 2 - gap, (height - row * 2 - gap) / 2 + dy));
            top.draw(canvas, x, y, sizeX, row);
            bottom.draw(canvas, x, y + row + gap, sizeX, row);
        }
        private float dpFromBounds(Appearance.Value value) { return top.density * hooks.value(value); }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static final class SignalRow {
        Drawable original, copy;
        android.content.res.ColorStateList tint;
        ColorFilter filter;
        int level;
        float density;
        boolean update(ImageView view) {
            Drawable image = view.getDrawable();
            if (image == null || image.getConstantState() == null) { clear(); return true; }
            android.content.res.ColorStateList nextTint = view.getImageTintList();
            ColorFilter nextFilter = view.getColorFilter();
            int nextLevel = image.getLevel();
            if (image == original && level == nextLevel && tint == nextTint && filter == nextFilter) return false;
            original = image;
            tint = nextTint;
            filter = nextFilter;
            level = nextLevel;
            density = view.getResources().getDisplayMetrics().density;
            copy = image.getConstantState().newDrawable(view.getResources()).mutate();
            copy.setAlpha(255);
            if (tint != null) copy.setTintList(tint);
            if (filter != null) copy.setColorFilter(filter);
            copy.setLevel(level);
            copy.setState(view.getDrawableState());
            return true;
        }
        void draw(Canvas canvas, int x, int y, int width, int height) {
            if (copy == null) return;
            copy.setBounds(x, y, x + width, y + height);
            copy.draw(canvas);
        }
        void clear() { original = copy = null; tint = null; filter = null; }
    }
}
