package io.github.hyperpriv;

import static io.github.hyperpriv.Appearance.Value.*;
import static io.github.hyperpriv.AppearanceHooks.dp;
import static io.github.hyperpriv.AppearanceSupport.*;

import android.content.Context;
import android.graphics.Rect;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.WeakHashMap;

/** Appearance of the collapsed phone status bar. Each slot registers independently. */
final class SystemBarAppearance {
    private static final String NOTIFICATION_CONTAINER = "notificationIcons";
    private final AppearanceHooks hooks;
    private final WeakHashMap<TextView, Runnable> clockTicks = new WeakHashMap<>();
    private Locale clockLocale;
    private boolean clock24;
    private SimpleDateFormat clockFormat;
    private final java.util.HashMap<String, Integer> ids = new java.util.HashMap<>();
    private final Runnable clockHit;

    SystemBarAppearance(AppearanceHooks hooks) {
        this.hooks = hooks;
        clockHit = hooks.firstHit("status_clock");
    }

    void install() {
        if (hooks.custom(Appearance.Value.NOTIFICATIONS)) hooks.register("notification_limit", this::notifications);
        if (hooks.custom(Appearance.Value.BATTERY) || hooks.custom(BATTERY_MARK) || hooks.custom(CHARGE_MARK))
            hooks.register("status_battery", this::battery);
        if (hooks.custom(CLOCK_POSITION) || hooks.custom(CLOCK_TIME) || hooks.custom(CLOCK_SECONDS)
                || hooks.custom(CLOCK_DATE) || hooks.custom(CLOCK_SIZE) || hooks.custom(CLOCK_ALIGN))
            hooks.register("status_clock", this::clock);
        if (hooks.custom(START_MARGIN) || hooks.custom(END_MARGIN) || hooks.custom(STATUS_Y))
            hooks.register("status_padding", this::padding);
        if (hooks.custom(SUCCESSION) || hooks.custom(SPEED_UNIT) || hooks.custom(SPEED_THRESHOLD)
                || hooks.custom(SPEED_SIZE) || hooks.custom(SPEED_GAP))
            hooks.register("status_speed", () -> new NetworkSpeedAppearance(hooks, this).install());
        if (hooks.custom(SIGNAL) || hooks.custom(MOBILE_TYPE) || hooks.custom(MOBILE_HD)
                || hooks.custom(MOBILE_ACTIVITY) || hooks.custom(NETWORK_ORDER) || hooks.custom(ICON_GAP))
            hooks.register("status_signals", () -> new SignalAppearance(hooks, this).install());
    }

    int id(View view, String name) {
        Integer id = ids.get(name);
        if (id == null) {
            id = view.getResources().getIdentifier(name, "id", Appearance.SYSTEM_UI);
            ids.put(name, id);
        }
        return id;
    }
    <T extends View> T child(View view, String name) {
        int id = id(view, name);
        return id == 0 ? null : view.findViewById(id);
    }
    static View phoneBar(View view) {
        for (View node = view; node != null; node = node.getParent() instanceof View ? (View) node.getParent() : null)
            if (PHONE_BAR.equals(node.getClass().getName())) return node;
        return null;
    }

    private void notifications() throws Exception {
        Executable measure = hooks.method(AppearanceSupport.NOTIFICATIONS, "onMeasure", "V", "I", "I");
        Executable layout = hooks.method(AppearanceSupport.NOTIFICATIONS, "calculateIconXTranslations", "V");
        Field limit = hooks.field(AppearanceSupport.NOTIFICATIONS, "mMaxIcons", int.class);
        Runnable hit = hooks.firstHit("notification_limit");
        io.github.libxposed.api.XposedInterface.Hooker override = chain -> {
            View view = (View) chain.getThisObject();
            if (!hooks.active() || view.getId() != id(view, NOTIFICATION_CONTAINER) || phoneBar(view) == null)
                return chain.proceed();
            int original = limit.getInt(view);
            if (original == 0) return chain.proceed();
            limit.setInt(view, hooks.value(Appearance.Value.NOTIFICATIONS));
            try {
                hit.run();
                return chain.proceed();
            } finally { limit.setInt(view, original); }
        };
        hooks.hook(measure, override);
        hooks.hook(layout, override);
    }

    private void battery() throws Exception {
        int mode = hooks.value(Appearance.Value.BATTERY);
        Executable update = hooks.method(AppearanceSupport.BATTERY, "updateChargeAndText", "V");
        Executable visibility = hooks.method(AppearanceSupport.BATTERY, "updateVisibility$1$1", "V");
        Executable attach = hooks.method(AppearanceSupport.BATTERY, "onAttachedToWindow", "V");
        Executable measure = hooks.method(AppearanceSupport.BATTERY, "onMeasure", "V", "I", "I");
        Field style = hooks.field(AppearanceSupport.BATTERY, "mBatteryStyle", int.class);
        Runnable hit = hooks.firstHit("status_battery");
        io.github.libxposed.api.XposedInterface.Hooker adjust = chain -> {
            Object result = chain.proceed();
            View battery = (View) chain.getThisObject();
            if (!hooks.active() || phoneBar(battery) == null) return result;
            if (mode == 3) battery.setVisibility(View.GONE);
            if (mode == 1 || mode == 2) {
                hide(battery, "battery_icon_container", "battery_image", "hollow_battery_image");
                if (shouldShowBatteryPercent(mode, style.getInt(battery))) showBatteryPercent(battery);
            }
            if (hooks.custom(BATTERY_MARK)) hide(battery, "battery_percentage_mark_view");
            if (hooks.custom(CHARGE_MARK) || mode == 2) hide(battery, "battery_charge_out_image");
            hit.run();
            return result;
        };
        hooks.hook(update, adjust);
        hooks.hook(visibility, adjust);
        hooks.hook(attach, adjust);
        if (mode == 1 || mode == 2) hooks.hook(measure, chain -> {
            View battery = (View) chain.getThisObject();
            if (!hooks.active() || phoneBar(battery) == null) return chain.proceed();
            int original = style.getInt(battery);
            if (!shouldShowBatteryPercent(mode, original)) return chain.proceed();
            showBatteryPercent(battery);
            // Xiaomi only measures the separate percentage container in style 3.
            style.setInt(battery, 3);
            try { return chain.proceed(); }
            finally { style.setInt(battery, original); }
        });
    }

    private static boolean shouldShowBatteryPercent(int mode, int style) {
        return mode == 2 || (mode == 1 && (style == 1 || style == 3));
    }

    private void showBatteryPercent(View battery) {
        View percent = child(battery, "battery_percentage_container");
        if (percent != null) {
            // The vendor's hidden-state animation can leave the container at zero alpha.
            percent.setAlpha(1f);
            percent.setVisibility(View.VISIBLE);
        }
    }

    private void hide(View root, String... names) {
        for (String name : names) {
            View view = child(root, name);
            if (view != null) view.setVisibility(View.GONE);
        }
    }

    private void clock() throws Exception {
        Executable update = hooks.method(CLOCK, "updateTime", "V");
        Executable detach = hooks.method(CLOCK, "onDetachedFromWindow", "V");
        Executable inflate = hooks.method(PHONE_BAR, "onFinishInflate", "V");
        hooks.hook(update, chain -> {
            Object result = chain.proceed();
            TextView clock = (TextView) chain.getThisObject();
            if (hooks.active() && isClock(clock)) refreshClock(clock);
            return result;
        });
        hooks.hook(detach, chain -> {
            TextView view = (TextView) chain.getThisObject();
            Runnable tick = clockTicks.remove(view);
            if (tick != null) view.removeCallbacks(tick);
            return chain.proceed();
        });
        hooks.hook(inflate, chain -> {
            Object result = chain.proceed();
            if (!hooks.active()) return result;
            View bar = (View) chain.getThisObject();
            View view = child(bar, "clock");
            if (view instanceof TextView && view.getClass().getName().equals(CLOCK)) {
                TextView clock = (TextView) view;
                positionClock(bar, clock);
                refreshClock(clock);
            }
            return result;
        });
    }

    private boolean isClock(View clock) {
        return clock.getId() == id(clock, "clock") && phoneBar(clock) != null;
    }

    private void refreshClock(TextView clock) {
        if (hooks.custom(CLOCK_TIME) || hooks.custom(CLOCK_SECONDS) || hooks.custom(CLOCK_DATE)) renderTime(clock);
        if (hooks.custom(CLOCK_SIZE)) clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, hooks.value(CLOCK_SIZE));
        if (hooks.custom(CLOCK_ALIGN)) {
            int[] align = {Gravity.NO_GRAVITY, Gravity.START, Gravity.CENTER_HORIZONTAL, Gravity.END};
            clock.setMinWidth(Math.round(dp(clock, 52)));
            clock.setGravity(Gravity.CENTER_VERTICAL | align[hooks.value(CLOCK_ALIGN)]);
        }
        if (hooks.custom(CLOCK_SECONDS) && !clockTicks.containsKey(clock) && canTick(clock)) tick(clock);
        clockHit.run();
    }

    private void renderTime(TextView clock) {
        Locale locale = clock.getResources().getConfiguration().getLocales().get(0);
        boolean use24 = hooks.value(CLOCK_TIME) == 1
                || (hooks.value(CLOCK_TIME) == 0 && DateFormat.is24HourFormat(clock.getContext()));
        if (clockFormat == null || !locale.equals(clockLocale) || clock24 != use24) {
            clockLocale = locale;
            clock24 = use24;
            String skeleton = (use24 ? "Hm" : "hm") + (hooks.custom(CLOCK_SECONDS) ? "s" : "");
            String date = new String[]{"", "Md", "EEE", "MEd"}[hooks.value(CLOCK_DATE)];
            String pattern = DateFormat.getBestDateTimePattern(locale, skeleton);
            if (!date.isEmpty()) pattern += " " + DateFormat.getBestDateTimePattern(locale, date);
            clockFormat = new SimpleDateFormat(pattern, locale);
        }
        clockFormat.setTimeZone(TimeZone.getDefault());
        String value = clockFormat.format(new Date());
        if (!value.contentEquals(clock.getText())) clock.setText(value);
        clock.setContentDescription(value);
    }

    private static boolean canTick(View clock) {
        PowerManager power = (PowerManager) clock.getContext().getSystemService(Context.POWER_SERVICE);
        return clock.isAttachedToWindow() && clock.isShown() && power != null && power.isInteractive();
    }
    private void tick(TextView clock) {
        Runnable run = () -> {
            clockTicks.remove(clock);
            if (!hooks.active() || !canTick(clock)) return;
            renderTime(clock);
            tick(clock);
        };
        clockTicks.put(clock, run);
        clock.postDelayed(run, 1000 - SystemClock.uptimeMillis() % 1000);
    }

    private void positionClock(View bar, TextView clock) {
        int where = hooks.value(CLOCK_POSITION);
        if (where == 0 || !(clock.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) clock.getParent();
        if (where == 1 || where == 2) {
            View notifications = child(parent, "notification_icon_area");
            if (notifications == null || notifications.getParent() != parent) return;
            ViewGroup.LayoutParams params = clock.getLayoutParams();
            parent.removeView(clock);
            parent.addView(clock, parent.indexOfChild(notifications) + where - 1, params);
            return;
        }
        // Keep the real clock in its container and fall back if the center is occupied.
        int[] anchor = new int[2], point = new int[2];
        Rect desired = new Rect();
        bar.getViewTreeObserver().addOnPreDrawListener(() -> {
            if (!hooks.active() || clock.getWidth() == 0) { clock.setTranslationX(0); return true; }
            bar.getLocationInWindow(anchor);
            clock.getLocationInWindow(point);
            int center = anchor[0] + bar.getWidth() / 2;
            int nextLeft = center - clock.getWidth() / 2;
            float originalOffset = clock.getTranslationX();
            float translation = nextLeft - point[0] + originalOffset;
            desired.set(nextLeft, point[1], nextLeft + clock.getWidth(), point[1] + clock.getHeight());
            DisplayCutout cutout = bar.getRootWindowInsets() == null ? null : bar.getRootWindowInsets().getDisplayCutout();
            if (cutout != null) for (Rect bound : cutout.getBoundingRects()) {
                if (Rect.intersects(desired, bound)) { translation = 0; break; }
            }
            for (String id : new String[]{"statusIcons", "battery_meter_view", "privacy_container"}) {
                View occupied = child(bar, id);
                if (occupied == null || !occupied.isShown() || occupied.getWidth() == 0) continue;
                occupied.getLocationInWindow(point);
                if (point[0] < desired.right && point[0] + occupied.getWidth() > desired.left) { translation = 0; break; }
            }
            clock.setTranslationX(translation);
            return true;
        });
    }

    private void padding() throws Exception {
        Executable method = hooks.method(PHONE_BAR, "updatePaddings", "V");
        Runnable hit = hooks.firstHit("status_padding");
        hooks.hook(method, chain -> {
            Object result = chain.proceed();
            if (!hooks.active()) return result;
            View bar = (View) chain.getThisObject();
            View contents = child(bar, "status_bar_contents");
            if (contents == null) return result;
            contents.setPaddingRelative(contents.getPaddingStart() + Math.round(dp(contents, hooks.value(START_MARGIN))),
                    contents.getPaddingTop(), contents.getPaddingEnd() + Math.round(dp(contents, hooks.value(END_MARGIN))),
                    contents.getPaddingBottom());
            contents.setTranslationY(dp(contents, hooks.value(STATUS_Y)));
            hit.run();
            return result;
        });
    }
}
