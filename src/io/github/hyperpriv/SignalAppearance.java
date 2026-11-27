package io.github.hyperpriv;

import static io.github.hyperpriv.Appearance.Value.*;
import static io.github.hyperpriv.AppearanceHooks.dp;
import static io.github.hyperpriv.AppearanceSupport.MOBILE;
import static io.github.hyperpriv.AppearanceSupport.SLOTS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Rect;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.telephony.SubscriptionManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

final class SignalAppearance {
    private static final String[] MOBILE_TYPE_IDS = {"mobile_type", "mobile_type_single", "mobile_special_5G"};
    private static final String[] WIFI_ACTIVITY_IDS = {"wifi_activity", "wifi_in", "wifi_out"};
    private static final String WIFI = "com.android.systemui.statusbar.pipeline.wifi.ui.view.ModernStatusBarWifiView";
    private final AppearanceHooks hooks;
    private final SystemBarAppearance bar;
    private final WeakHashMap<ViewGroup, Icons> groups = new WeakHashMap<>();
    private final Map<Integer, Sample> resourceSamples = new ConcurrentHashMap<>();
    private final Map<ImageView, Sample> imageSamples = Collections.synchronizedMap(new WeakHashMap<>());
    private final Runnable dualHit, duoHit, visibilityHit, spacingHit, orderHit;
    private final Runnable keyguardGlyphHit;
    private long wifiReadAt = -1;
    private int measuredWifiLevel = -1;
    private int batteryLevel = -1;
    private boolean batteryCharging;

    SignalAppearance(AppearanceHooks hooks, SystemBarAppearance bar) {
        this.hooks = hooks;
        this.bar = bar;
        dualHit = hooks.firstHit("dual_signal");
        duoHit = hooks.firstHit("duo_network");
        visibilityHit = hooks.firstHit("network_indicators");
        spacingHit = hooks.firstHit("status_icon_spacing");
        orderHit = hooks.firstHit("network_order");
        keyguardGlyphHit = hooks.firstHit("keyguard_signal");
    }

    void install() {
        if (hooks.custom(NETWORK_ORDER) && !duoSelected()) hooks.register("network_order", this::installOrder);
        if (hooks.supports(ICON_GAP)) hooks.register("status_icon_spacing", this::installSpacing);
        if (hooks.supports(SIGNAL)) hooks.register("network_indicators", this::installIndicators);
    }

    private boolean duoSelected() { return hooks.value(SIGNAL) == Appearance.DUO_SIGNAL_STYLE; }

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
            if (SystemBarAppearance.statusBar(view) == null) return chain.proceed();
            hooks.watch(view);
            if (!hooks.active() || !hooks.custom(ICON_GAP)) return chain.proceed();
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
        hooks.register("signal_resource_levels", this::captureSignalLevels);
        hooks.register("duo_battery", this::observeBattery);
        hooks.onRefresh(() -> {
            for (Icons icons : new java.util.ArrayList<>(groups.values()))
                hooks.refreshView(icons.group.get(), icons::restoreHidden);
        });
        hooks.hook(measure, chain -> {
            ViewGroup group = (ViewGroup) chain.getThisObject();
            if (SystemBarAppearance.statusBar(group) != null) {
                hooks.watch(group);
                Icons icons = groups.get(group);
                if (icons == null) {
                    icons = new Icons(group, (Method) subId, visible);
                    groups.put(group, icons);
                    group.addOnAttachStateChangeListener(icons);
                    if (group.isAttachedToWindow()) icons.onViewAttachedToWindow(group);
                }
            }
            return chain.proceed();
        });
    }

    private void observeBattery() {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (updateBattery(intent))
                    for (ViewGroup group : groups.keySet()) group.postInvalidateOnAnimation();
            }
        };
        updateBattery(hooks.context().registerReceiver(receiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)));
    }

    private boolean updateBattery(Intent intent) {
        if (intent == null || !Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) return false;
        int rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int next = scale > 0 && rawLevel >= 0 && rawLevel <= scale ? Math.round(100f * rawLevel / scale) : -1;
        boolean charging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                == BatteryManager.BATTERY_STATUS_CHARGING;
        if (next == batteryLevel && charging == batteryCharging) return false;
        batteryLevel = next;
        batteryCharging = charging;
        return true;
    }

    private View batteryMeter(View iconGroup) {
        View host = SystemBarAppearance.statusBar(iconGroup);
        if (host == null) return null;
        View battery = bar.child(host, "battery");
        if (battery != null && AppearanceSupport.BATTERY.equals(battery.getClass().getName())) return battery;
        battery = bar.child(host, "battery_meter_view");
        return battery != null && AppearanceSupport.BATTERY.equals(battery.getClass().getName()) ? battery : null;
    }

    private void captureSignalLevels() throws Exception {
        int mobileId = hooks.systemUiId("mobile_signal"), wifiId = hooks.systemUiId("wifi_signal");
        if (mobileId == 0) throw new NoSuchFieldException("mobile_signal");
        Method setImageResource = ImageView.class.getDeclaredMethod("setImageResource", int.class);
        hooks.hook(setImageResource, chain -> {
            ImageView image = (ImageView) chain.getThisObject();
            int id = image.getId();
            if (id != mobileId && (wifiId == 0 || id != wifiId)) return chain.proceed();
            Object result = chain.proceed();
            int resource = (Integer) chain.getArg(0);
            boolean wifi = id == wifiId;
            if (resource == 0) imageSamples.remove(image);
            else imageSamples.put(image, resourceSamples.computeIfAbsent(resource, key -> Sample.read(image, key, wifi)));
            return result;
        });
    }

    private static final class Sample {
        static final Sample UNKNOWN = new Sample(-1, 0);
        static final Sample WIFI_UNAVAILABLE = new Sample(-2, 0);
        final int level, color;

        Sample(int level, int color) { this.level = level; this.color = color; }

        static Sample read(ImageView image, int resource, boolean wifi) {
            try {
                String name = image.getResources().getResourceEntryName(resource);
                if (wifi && (name.startsWith("stat_sys_wifi_signal_unavailable_")
                        || name.startsWith("ic_no_internet_wifi_signal_"))) return WIFI_UNAVAILABLE;
                int level = SignalGlyph.resourceLevel(name, wifi);
                if (level < 0) return UNKNOWN;
                return new Sample(level, name.contains("_darkmode") ? 0xbf000000 : 0xffffffff);
            } catch (Resources.NotFoundException ignored) { return UNKNOWN; }
        }
    }

    @SuppressWarnings("deprecation")
    private int connectedWifiLevel() {
        long now = SystemClock.elapsedRealtime();
        if (wifiReadAt >= 0 && now - wifiReadAt < 1_500) return measuredWifiLevel;
        wifiReadAt = now;
        measuredWifiLevel = -1;
        try {
            WifiManager manager = hooks.context().getSystemService(WifiManager.class);
            WifiInfo info = manager == null ? null : manager.getConnectionInfo();
            if (info != null && info.getNetworkId() >= 0 && info.getRssi() > -127)
                measuredWifiLevel = WifiManager.calculateSignalLevel(info.getRssi(), 4);
        } catch (RuntimeException ignored) { }
        return measuredWifiLevel;
    }

    private final class Icons implements View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
        private final WeakReference<ViewGroup> group;
        private final Method subId, visible;
        private final WeakHashMap<View, Integer> hiddenViews = new WeakHashMap<>();
        private final Map<ImageView, SignalGlyph> glyphs = new WeakHashMap<>();
        private final Map<View, Collapsed> collapsed = new WeakHashMap<>();
        private final Set<ImageView> currentGlyphs = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<View> currentCollapsed = Collections.newSetFromMap(new IdentityHashMap<>());

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
            currentGlyphs.clear();
            currentCollapsed.clear();
            restoreUnused();
            restoreHidden();
        }
        @Override public boolean onPreDraw() {
            ViewGroup view = group.get();
            if (view != null) update(view);
            return true;
        }

        void update(ViewGroup view) {
            currentGlyphs.clear();
            currentCollapsed.clear();
            if (!hooks.active() || !(hooks.custom(SIGNAL) || hooks.custom(MOBILE_TYPE)
                    || hooks.custom(MOBILE_HD) || hooks.custom(MOBILE_ACTIVITY))) {
                restoreUnused();
                restoreHidden();
                return;
            }
            boolean duo = duoSelected();
            View primary = null, secondary = null, wifi = null;
            int primaryId = -1, secondaryId = -1, count = 0;
            for (int index = 0; index < view.getChildCount(); index++) {
                View icon = view.getChildAt(index);
                String owner = icon.getClass().getName();
                if (WIFI.equals(owner)) {
                    if (!duo && hooks.custom(MOBILE_ACTIVITY)) hide(icon, WIFI_ACTIVITY_IDS);
                    if (icon.isShown()) wifi = icon;
                    continue;
                }
                if (!MOBILE.equals(owner)) continue;
                if (!duo) {
                    if (hooks.custom(MOBILE_TYPE)) hide(icon, MOBILE_TYPE_IDS);
                    int hd = hooks.value(MOBILE_HD);
                    if (hd == 1 || hd == 3) hide(icon, "mobile_speech_hd", "mobile_volte", "mobile_volte_no_service");
                    if (hd == 2 || hd == 3) hide(icon, "mobile_vowifi");
                    if (hooks.custom(MOBILE_ACTIVITY)) hide(icon, "mobile_left_mobile_inout", "mobile_in", "mobile_out");
                }
                if (!hooks.custom(SIGNAL)) continue;
                try {
                    if (!Boolean.TRUE.equals(visible.invoke(icon))) continue;
                    int id = (Integer) subId.invoke(icon);
                    if (!SubscriptionManager.isValidSubscriptionId(id)) continue;
                    if (count == 0) { primary = icon; primaryId = id; }
                    else if (id == primaryId || id == secondaryId) continue;
                    else if (count == 1) { secondary = icon; secondaryId = id; }
                    count++;
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
            if (count == 2 && secondaryId == SubscriptionManager.getDefaultDataSubscriptionId()) {
                View swap = primary; primary = secondary; secondary = swap;
            }
            if (count > 0 && count <= 2) {
                ImageView first = image(primary, "mobile_signal");
                ImageView second = image(secondary, "mobile_signal");
                int firstLevel = sample(first).level, secondLevel = sample(second).level;
                if (duo) {
                    applyDuo(view, primary, secondary, wifi, first, firstLevel, secondLevel);
                } else {
                    applyMobileStyle(primary, secondary, first, firstLevel, secondLevel);
                }
            }
            restoreUnused();
            currentGlyphs.clear();
            currentCollapsed.clear();
        }

        private void applyDuo(ViewGroup container, View primary, View secondary, View wifi,
                              ImageView first, int firstLevel, int secondLevel) {
            View battery = batteryMeter(container);
            if (batteryLevel < 0 || !canCollapse(battery) || firstLevel < 0 || !canCollapse(primary)
                    || (secondary != null && (secondLevel < 0 || !canCollapse(secondary)))) return;

            ImageView wifiImage = image(wifi == null ? container : wifi, "wifi_signal");
            int wifiLevel = 0;
            if (wifiImage != null) {
                int level = sample(wifiImage).level;
                if (level == Sample.UNKNOWN.level) level = connectedWifiLevel();
                wifiLevel = Math.max(0, level + 1);
            }
            ImageView target = wifiImage == null ? first : wifiImage;
            if (!showGlyph(target, SignalGlyph.Style.DUO, firstLevel, secondLevel, wifiLevel,
                    batteryLevel, batteryCharging, sample(first).color)) return;

            if (wifiImage == null) {
                // Keep the mobile signal ImageView as the anchor and suppress surrounding decorations.
                for (View child = first; child != primary && child.getParent() instanceof ViewGroup;) {
                    ViewGroup parent = (ViewGroup) child.getParent();
                    for (int i = 0; i < parent.getChildCount(); i++) {
                        View sibling = parent.getChildAt(i);
                        if (sibling != child && canCollapse(sibling)) collapse(sibling);
                    }
                    child = parent;
                }
            } else {
                collapse(primary);
                View wifiRoot = wifi != null ? wifi : (View) wifiImage.getParent();
                for (String id : WIFI_ACTIVITY_IDS) {
                    View arrow = bar.child(wifiRoot, id);
                    if (canCollapse(arrow)) collapse(arrow);
                }
            }
            if (secondary != null) collapse(secondary);
            collapse(battery);
            duoHit.run();
        }

        private void applyMobileStyle(View primary, View secondary, ImageView first,
                                      int firstLevel, int secondLevel) {
            int mode = hooks.value(SIGNAL);
            if (mode == 0) return;
            if (primary == null || !canCollapse(secondary) || firstLevel < 0 || secondLevel < 0) return;
            SignalGlyph.Style style = mode == 1 ? SignalGlyph.Style.SPLIT_THIN : SignalGlyph.Style.SPLIT_THICK;
            if (showGlyph(first, style, firstLevel, secondLevel, -1, -1,
                    false, sample(first).color)) {
                collapse(secondary);
                dualHit.run();
            }
        }

        private ImageView image(View root, String id) {
            if (root == null) return null;
            View child = bar.child(root, id);
            return child instanceof ImageView && child.isShown() ? (ImageView) child : null;
        }

        private Sample sample(ImageView image) {
            if (image == null || image.getDrawable() == null) return Sample.UNKNOWN;
            Sample sample = imageSamples.get(image);
            return sample == null ? Sample.UNKNOWN : sample;
        }

        private boolean showGlyph(ImageView image, SignalGlyph.Style style, int first, int second,
                                  int wifi, int battery, boolean charging, int fallbackColor) {
            if (image == null || first < 0 || image.getWidth() < 8 || image.getHeight() < 8) return false;
            SignalGlyph glyph = glyphs.get(image);
            if (glyph == null) {
                glyph = new SignalGlyph();
                glyph.originalImageAlpha = image.getImageAlpha();
                glyphs.put(image, glyph);
                image.getOverlay().add(glyph);
                View host = SystemBarAppearance.statusBar(image);
                if (host != null && SystemBarAppearance.keyguardBar(host)) keyguardGlyphHit.run();
            }
            int alpha = image.getImageAlpha();
            if (alpha != 0) {
                glyph.originalImageAlpha = alpha;
                image.setImageAlpha(0);
            }
            if (style == SignalGlyph.Style.DUO) setGlyphWidth(image, glyph, Math.round(dp(image, 24)));
            ColorStateList tint = image.getImageTintList();
            int nativeColor = sample(image).color;
            if (nativeColor == 0) nativeColor = fallbackColor;
            int color = tint == null ? nativeColor : tint.getColorForState(image.getDrawableState(), tint.getDefaultColor());
            glyph.update(style, first, second, wifi, battery, charging, color, image.getColorFilter(), image.getWidth(),
                    image.getHeight(), hooks.value(SIGNAL_SIZE), dp(image, hooks.value(SIGNAL_GAP)),
                    dp(image, hooks.value(SIGNAL_Y)));
            currentGlyphs.add(image);
            return true;
        }

        private void setGlyphWidth(ImageView image, SignalGlyph glyph, int desired) {
            ViewGroup.LayoutParams params = image.getLayoutParams();
            if (params == null) return;
            if (desired == 0) {
                if (glyph.originalViewWidth != Integer.MIN_VALUE && params.width == glyph.appliedViewWidth) {
                    params.width = glyph.originalViewWidth;
                    image.setLayoutParams(params);
                }
                glyph.originalViewWidth = Integer.MIN_VALUE;
                return;
            }
            int current = params.width > 0 ? params.width : image.getWidth();
            if (glyph.originalViewWidth == Integer.MIN_VALUE || params.width != glyph.appliedViewWidth) {
                if (current >= desired) return;
                glyph.originalViewWidth = params.width;
            } else {
                desired = Math.max(desired, glyph.originalViewWidth);
                if (current == desired) return;
            }
            glyph.appliedViewWidth = desired;
            params.width = desired;
            image.setLayoutParams(params);
        }

        private boolean canCollapse(View view) { return view != null && view.getLayoutParams() != null; }

        private void collapse(View view) {
            currentCollapsed.add(view);
            if (collapsed.containsKey(view)) return;
            ViewGroup.LayoutParams params = view.getLayoutParams();
            collapsed.put(view, new Collapsed(params.width, view.getClipBounds()));
            view.setClipBounds(new Rect());
            params.width = 0;
            view.setLayoutParams(params);
        }

        private void restoreUnused() {
            glyphs.entrySet().removeIf(entry -> {
                ImageView image = entry.getKey();
                if (currentGlyphs.contains(image)) return false;
                if (image != null) {
                    setGlyphWidth(image, entry.getValue(), 0);
                    image.getOverlay().remove(entry.getValue());
                    if (image.getImageAlpha() == 0) image.setImageAlpha(entry.getValue().originalImageAlpha);
                }
                return true;
            });
            collapsed.entrySet().removeIf(entry -> {
                View view = entry.getKey();
                if (currentCollapsed.contains(view)) return false;
                if (view != null) {
                    Collapsed state = entry.getValue();
                    view.setClipBounds(state.clip);
                    ViewGroup.LayoutParams params = view.getLayoutParams();
                    if (params != null && params.width == 0) {
                        params.width = state.width;
                        view.setLayoutParams(params);
                    }
                }
                return true;
            });
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
    }

    private static final class Collapsed {
        final int width;
        final Rect clip;
        Collapsed(int width, Rect clip) { this.width = width; this.clip = clip; }
    }
}
