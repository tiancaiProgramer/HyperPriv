package io.github.hyperpriv;

import static io.github.hyperpriv.Appearance.Value.*;
import static io.github.hyperpriv.AppearanceHooks.dp;

import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

final class BatteryRings {
    private final AppearanceHooks hooks;
    private final SystemBarAppearance statusBar;
    private final Runnable meterHit, keyguardMeterHit, cutoutHit, topPercentHit;
    private final Map<View, Boolean> phoneBars = new WeakHashMap<>();
    private boolean nativeTopSelected;
    private Field levelField, tintField, chargingField;

    BatteryRings(AppearanceHooks hooks, SystemBarAppearance statusBar) {
        this.hooks = hooks;
        this.statusBar = statusBar;
        meterHit = hooks.firstHit("battery_circle");
        keyguardMeterHit = hooks.firstHit("keyguard_battery_circle");
        cutoutHit = hooks.firstHit("battery_cutout_ring");
        topPercentHit = hooks.firstHit("top_battery_percent");
    }

    void install() throws Exception {
        String owner = AppearanceSupport.BATTERY;
        levelField = hooks.field(owner, "mLevel", int.class);
        tintField = hooks.field(owner, "mTintColor", int.class);
        chargingField = hooks.field(owner, "mCharging", boolean.class);
        installSurface(AppearanceSupport.PHONE_BAR, false);
        int style = hooks.value(BATTERY);
        if (style == Appearance.BATTERY_RING_STYLE || style == Appearance.BATTERY_CUTOUT_STYLE)
            hooks.register("keyguard_battery_ring", () -> installSurface(AppearanceSupport.KEYGUARD_BAR, true));
        if (style == 0 && hooks.hasNativeBatteryStyles()) observeNativeStyle();
    }

    private void observeNativeStyle() {
        Context context = hooks.context();
        nativeTopSelected = NativeBatteryStyle.current(context) == NativeBatteryStyle.TOP_BAR.systemValue;
        context.getContentResolver().registerContentObserver(NativeBatteryStyle.settingUri(), false,
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override public void onChange(boolean selfChange) {
                        nativeTopSelected = NativeBatteryStyle.current(context) == NativeBatteryStyle.TOP_BAR.systemValue;
                        for (View bar : new java.util.ArrayList<>(phoneBars.keySet()))
                            hooks.refreshView(bar, bar::invalidate);
                    }
                });
    }

    private void installSurface(String owner, boolean keyguard) throws Exception {
        Executable inflate = hooks.method(owner, "onFinishInflate", "V");
        hooks.hook(inflate, chain -> {
            Object result = chain.proceed();
            View bar = (View) chain.getThisObject();
            Drawings drawings = new Drawings(bar, keyguard);
            bar.addOnAttachStateChangeListener(drawings);
            if (bar.isAttachedToWindow()) drawings.onViewAttachedToWindow(bar);
            return result;
        });
    }

    private final class Drawings implements View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
        final View bar;
        final boolean keyguard;
        final Ring meterRing = new Ring(), cutoutRing = new Ring();
        final ColorDrawable topStrip = new ColorDrawable();
        final Map<View, Float> replaced = new WeakHashMap<>();
        final int[] origin = new int[2];
        final RectF holeBounds = new RectF();
        ViewGroup meter;
        View meterWithChangedVisibility;
        int originalVisibility;
        int originalMeterWidth, appliedMeterWidth;
        boolean compactMeter;

        Drawings(View bar, boolean keyguard) { this.bar = bar; this.keyguard = keyguard; }

        @Override public void onViewAttachedToWindow(View view) {
            hooks.watch(view);
            if (!keyguard) phoneBars.put(view, true);
            view.getViewTreeObserver().addOnPreDrawListener(this);
        }

        @Override public void onViewDetachedFromWindow(View view) {
            if (view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnPreDrawListener(this);
            phoneBars.remove(view);
            restoreDrawings();
        }

        @Override public boolean onPreDraw() {
            int style = hooks.value(BATTERY);
            boolean top = !keyguard && (style == Appearance.BATTERY_TOP_STYLE
                    || (style == 0 && nativeTopSelected));
            if (!hooks.active() || hooks.value(SIGNAL) == Appearance.DUO_SIGNAL_STYLE
                    || (!top && style != Appearance.BATTERY_RING_STYLE && style != Appearance.BATTERY_CUTOUT_STYLE)) {
                restoreDrawings();
                return true;
            }
            View candidate = statusBar.child(bar, "battery");
            if (!levelField.getDeclaringClass().isInstance(candidate)) candidate = statusBar.child(bar, "battery_meter_view");
            if (!(candidate instanceof LinearLayout) || !levelField.getDeclaringClass().isInstance(candidate)) {
                restoreDrawings();
                return true;
            }
            try {
                int level = levelField.getInt(candidate);
                int tint = tintField.getInt(candidate);
                boolean charging = chargingField.getBoolean(candidate);
                if (level < 0 || level > 100) {
                    restoreDrawings();
                    return true;
                }
                int textColor = Color.alpha(tint) > 0 ? tint : Color.WHITE;
                int color = charging ? 0xff43c322 : level <= 15 ? 0xffeb3b35 : textColor;
                if (top)
                    updateTopBar((ViewGroup) candidate, level, color, textColor);
                else if (style == Appearance.BATTERY_RING_STYLE)
                    updateMeter((ViewGroup) candidate, level, color, textColor);
                else
                    updateCutout((ViewGroup) candidate, level, color, textColor);
            } catch (IllegalAccessException | RuntimeException error) {
                restoreDrawings();
            }
            return true;
        }

        private void updateTopBar(ViewGroup candidate, int level, int color, int textColor) {
            View nativeIndicator = statusBar.child(bar, "battery_indicator");
            if (nativeIndicator == null || !nativeIndicator.isShown() || nativeIndicator.getHeight() == 0) {
                if (topStrip.getCallback() == null) bar.getOverlay().add(topStrip);
                topStrip.setColor(color);
                topStrip.setBounds(0, 0, Math.round(bar.getWidth() * level / 100f), Math.round(dp(bar, 2)));
            } else if (topStrip.getCallback() != null) {
                bar.getOverlay().remove(topStrip);
            }
            if (statusBar.islandShown(bar)) {
                restoreMeter();
                restoreVisibility();
            } else if (hooks.custom(TOP_BAR_PERCENT)) {
                showMeterPercentage(candidate, level, textColor);
                topPercentHit.run();
            } else {
                restoreMeter();
                setMeterVisibility(candidate, View.GONE);
            }
        }

        private void updateMeter(ViewGroup candidate, int level, int color, int textColor) {
            int width = candidate.getWidth(), height = candidate.getHeight();
            float stroke = dp(candidate, 1.5f);
            float radius = Math.min(dp(candidate, 8f), Math.min(width, height) / 2f - stroke);
            if (hooks.value(BATTERY) != Appearance.BATTERY_RING_STYLE || !candidate.isShown() || radius < stroke * 2) {
                restoreMeter();
                return;
            }
            boolean installed = meter != candidate;
            showMeter(candidate);
            if (installed) {
                if (keyguard) keyguardMeterHit.run(); else meterHit.run();
            }
            float labelSize = hooks.custom(RING_HIDE_PERCENT) ? 0 : radius;
            if (labelSize > 0) labelSize = meterRing.fitLabelSize(level, labelSize, radius * 1.64f);
            meterRing.update(width, height, width / 2f, height / 2f, radius, stroke,
                    level, color, textColor, width / 2f, labelSize);
        }

        private void showMeter(ViewGroup candidate) {
            if (meter != candidate) {
                restoreMeter();
                meter = candidate;
                candidate.getOverlay().add(meterRing);
            }
            for (int i = 0; i < candidate.getChildCount(); i++) {
                View view = candidate.getChildAt(i);
                float alpha = view.getAlpha();
                if (alpha == 0) continue;
                replaced.put(view, alpha);
                view.setAlpha(0);
            }
        }

        private void restoreMeter() {
            if (meter == null) return;
            meter.getOverlay().remove(meterRing);
            ViewGroup.LayoutParams params = meter.getLayoutParams();
            if (compactMeter && params != null && params.width == appliedMeterWidth) {
                params.width = originalMeterWidth;
                meter.setLayoutParams(params);
            }
            compactMeter = false;
            for (Map.Entry<View, Float> entry : replaced.entrySet()) {
                View child = entry.getKey();
                if (child != null && child.getAlpha() == 0) child.setAlpha(entry.getValue());
            }
            replaced.clear();
            meter = null;
        }

        private void updateCutout(ViewGroup candidate, int level, int color, int textColor) {
            Display display = bar.getDisplay();
            DisplayCutout cutout = display == null ? null : display.getCutout();
            if (cutout == null || statusBar.islandShown(bar)
                    || cutout.getBoundingRects().size() != 1
                    || bar.getResources().getConfiguration().orientation != Configuration.ORIENTATION_PORTRAIT) {
                restoreCutout();
                return;
            }
            Rect hole = cutout.getBoundingRectTop();
            if (hole.isEmpty()) {
                restoreCutout();
                return;
            }
            holeBounds.set(hole);
            if (Build.VERSION.SDK_INT >= 31) {
                Path shape = cutout.getCutoutPath();
                if (shape != null && !shape.isEmpty()) shape.computeBounds(holeBounds, true);
            }
            bar.getLocationOnScreen(origin);
            float x = holeBounds.centerX() - origin[0], y = holeBounds.centerY() - origin[1];
            float holeRadius = Math.max(holeBounds.width(), holeBounds.height()) / 2f;
            // The Fold 3 outer panel describes a centered reserved strip through the top edge.
            if ("babylon".equals(Build.DEVICE) && holeBounds.top == 0
                    && holeBounds.height() > holeBounds.width()
                    && holeBounds.width() <= dp(bar, 32)
                    && Math.abs(x - bar.getWidth() / 2f) <= dp(bar, 2)) {
                // Photo calibration against the ring pixels on the Fold 3 cover display.
                holeRadius = holeBounds.width() * .48f;
                y += holeBounds.height() * .02f;
            } else if (holeBounds.width() > holeBounds.height() * 1.5f
                    || holeBounds.height() > holeBounds.width() * 1.5f || holeBounds.width() > dp(bar, 45)) {
                restoreCutout();
                return;
            }
            float stroke = dp(bar, hooks.value(CUTOUT_RING_STROKE));
            float gap = dp(bar, hooks.value(CUTOUT_RING_GAP));
            float available = Math.min(Math.min(x, bar.getWidth() - x), Math.min(y, bar.getHeight() - y));
            float radius = Math.min(holeRadius + gap + stroke / 2f, available - stroke / 2f);
            if (radius < holeRadius + stroke / 2f) {
                restoreCutout();
                return;
            }
            if (meterWithChangedVisibility != null && meterWithChangedVisibility != candidate) restoreCutout();
            if (cutoutRing.getCallback() == null) bar.getOverlay().add(cutoutRing);
            cutoutRing.update(bar.getWidth(), bar.getHeight(), x, y, radius, stroke, level, color, textColor, x, 0);
            boolean showPercentage = hooks.custom(CUTOUT_RING_PERCENT);
            if (showPercentage) showMeterPercentage(candidate, level, textColor);
            else {
                restoreMeter();
                setMeterVisibility(candidate, View.GONE);
            }
            cutoutHit.run();
        }

        private void showMeterPercentage(ViewGroup candidate, int level, int textColor) {
            setMeterVisibility(candidate, View.VISIBLE);
            float size = dp(candidate, 11);
            ViewGroup.LayoutParams params = candidate.getLayoutParams();
            if (params == null) return;
            showMeter(candidate);
            int desired = (int) Math.ceil(meterRing.labelWidth(100, size) + dp(candidate, 2));
            if (!compactMeter || params.width != appliedMeterWidth) originalMeterWidth = params.width;
            appliedMeterWidth = desired;
            compactMeter = true;
            if (params.width != desired) {
                params.width = desired;
                candidate.setLayoutParams(params);
            }

            int width = candidate.getWidth(), height = candidate.getHeight();
            float available = width - dp(candidate, 2);
            float labelSize = available > 0 ? meterRing.fitLabelSize(level, size, available) : 0;
            meterRing.update(width, height, width / 2f, height / 2f, 0, 0,
                    level, textColor, textColor, width / 2f, labelSize);
        }

        private void setMeterVisibility(View candidate, int visibility) {
            if (meterWithChangedVisibility != candidate) restoreVisibility();
            if (candidate.getVisibility() == visibility) return;
            if (meterWithChangedVisibility == null) {
                meterWithChangedVisibility = candidate;
                originalVisibility = candidate.getVisibility();
            }
            candidate.setVisibility(visibility);
        }

        private void restoreCutout() {
            if (cutoutRing.getCallback() != null) bar.getOverlay().remove(cutoutRing);
            if (compactMeter) restoreMeter();
            restoreVisibility();
        }

        private void restoreVisibility() {
            if (meterWithChangedVisibility != null) meterWithChangedVisibility.setVisibility(originalVisibility);
            meterWithChangedVisibility = null;
        }

        private void restoreDrawings() {
            if (topStrip.getCallback() != null) bar.getOverlay().remove(topStrip);
            restoreMeter();
            restoreCutout();
        }
    }

    private static final class Ring extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arc = new RectF();
        private final Paint.FontMetrics font = new Paint.FontMetrics();
        private float x, y, radius, stroke, labelX, labelSize;
        private int level = -1, color, textColor;
        private String label = "";

        Ring() {
            paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        }

        float labelWidth(int level, float size) {
            paint.setTextSize(size);
            return paint.measureText(this.level == level && !label.isEmpty() ? label : level + "%");
        }

        float fitLabelSize(int level, float size, float width) {
            float measured = labelWidth(level, size);
            return measured > width ? size * width / measured : size;
        }

        void update(int width, int height, float x, float y, float radius, float stroke,
                int level, int color, int textColor, float labelX, float labelSize) {
            if (this.x == x && this.y == y && this.radius == radius && this.stroke == stroke && this.level == level
                    && this.color == color && this.textColor == textColor && this.labelX == labelX && this.labelSize == labelSize
                    && getBounds().width() == width && getBounds().height() == height) return;
            this.x = x; this.y = y; this.radius = radius; this.stroke = stroke;
            this.level = level;
            this.color = color;
            this.textColor = textColor;
            this.labelX = labelX;
            this.labelSize = labelSize;
            label = labelSize > 0 ? level + "%" : "";
            arc.set(x - radius, y - radius, x + radius, y + radius);
            setBounds(0, 0, width, height);
            invalidateSelf();
        }

        @Override public void draw(Canvas canvas) {
            if (radius > 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(stroke);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setColor((color & 0x00ffffff) | 0x40000000);
                canvas.drawOval(arc, paint);
                paint.setColor(color);
                if (level > 0) canvas.drawArc(arc, -90, level * 3.6f, false, paint);
            }
            if (label.isEmpty()) return;
            paint.setColor(textColor);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(labelSize);
            paint.getFontMetrics(font);
            canvas.drawText(label, labelX, y - (font.ascent + font.descent) / 2f, paint);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter colorFilter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
