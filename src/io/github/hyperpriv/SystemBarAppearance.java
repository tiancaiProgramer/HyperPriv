package io.github.hyperpriv;

import static io.github.hyperpriv.Appearance.Value.*;
import static io.github.hyperpriv.AppearanceHooks.dp;
import static io.github.hyperpriv.AppearanceSupport.*;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.TypedValue;
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
    private final WeakHashMap<TextView, Float> originalClockSizes = new WeakHashMap<>();
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
        if (hooks.supports(Appearance.Value.NOTIFICATIONS)) hooks.register("notification_limit", this::notifications);
        installBattery();
        if (hooks.supports(CLOCK_POSITION)) hooks.register("status_clock", this::clock);
        if (hooks.supports(START_MARGIN)) hooks.register("status_padding", this::padding);
        if (hooks.supports(SUCCESSION))
            hooks.register("status_speed", () -> new NetworkSpeedAppearance(hooks, this).install());
        if (hooks.supports(SIGNAL) || hooks.custom(NETWORK_ORDER) || hooks.supports(ICON_GAP))
            hooks.register("status_signals", () -> new SignalAppearance(hooks, this).install());
    }

    private void installBattery() {
        int style = hooks.value(Appearance.Value.BATTERY);
        boolean standalone = hooks.value(SIGNAL) != Appearance.DUO_SIGNAL_STYLE;
        if (standalone && style < Appearance.BATTERY_RING_STYLE
                && (style != 0 || hooks.custom(CHARGE_MARK)))
            hooks.register("status_battery", this::battery);
        if (standalone && style == Appearance.BATTERY_TOP_STYLE)
            hooks.register("top_battery_bar", this::topBatteryBar);
        boolean ring = standalone && (style == Appearance.BATTERY_RING_STYLE || style == Appearance.BATTERY_CUTOUT_STYLE);
        boolean topBar = hooks.supports(TOP_BAR_PERCENT)
                && (style == Appearance.BATTERY_TOP_STYLE || (style == 0 && hooks.hasNativeBatteryStyles()));
        if (ring || topBar)
            hooks.register("battery_rings", () -> new BatteryRings(hooks, this).install());
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
        return findBar(view, false);
    }

    static View statusBar(View view) {
        return findBar(view, true);
    }

    private static View findBar(View view, boolean includeKeyguard) {
        for (View node = view; node != null; node = node.getParent() instanceof View ? (View) node.getParent() : null) {
            String type = node.getClass().getName();
            if (PHONE_BAR.equals(type) || (includeKeyguard && KEYGUARD_BAR.equals(type))) return node;
        }
        return null;
    }

    static boolean keyguardBar(View bar) { return KEYGUARD_BAR.equals(bar.getClass().getName()); }

    boolean islandShown(View bar) {
        for (String name : new String[]{"focused_notif_view", "focus_status"}) {
            View view = child(bar, name);
            if (view != null && view.isShown() && view.getAlpha() > 0 && view.getWidth() > 0) return true;
        }
        return false;
    }

    private void notifications() throws Exception {
        Executable measure = hooks.method(AppearanceSupport.NOTIFICATIONS, "onMeasure", "V", "I", "I");
        Executable layout = hooks.method(AppearanceSupport.NOTIFICATIONS, "calculateIconXTranslations", "V");
        Field limit = hooks.field(AppearanceSupport.NOTIFICATIONS, "mMaxIcons", int.class);
        Runnable hit = hooks.firstHit("notification_limit");
        io.github.libxposed.api.XposedInterface.Hooker override = chain -> {
            View view = (View) chain.getThisObject();
            if (view.getId() != id(view, NOTIFICATION_CONTAINER) || phoneBar(view) == null)
                return chain.proceed();
            hooks.watch(view);
            if (!hooks.active() || !hooks.custom(Appearance.Value.NOTIFICATIONS)) return chain.proceed();
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
            if (!hooks.active() || statusBar(battery) == null) return result;
            if (mode == Appearance.BATTERY_HIDDEN_STYLE) battery.setVisibility(View.GONE);
            if (mode == Appearance.BATTERY_PERCENT_STYLE) {
                hide(battery, "battery_icon_container", "battery_image", "hollow_battery_image");
                showBatteryPercent(battery);
            }
            if (hooks.custom(CHARGE_MARK) || mode == Appearance.BATTERY_PERCENT_STYLE) hide(battery, "battery_charge_out_image");
            hit.run();
            return result;
        };
        hooks.hook(update, adjust);
        hooks.hook(visibility, adjust);
        hooks.hook(attach, adjust);
        if (mode == Appearance.BATTERY_PERCENT_STYLE) hooks.hook(measure, chain -> {
            View battery = (View) chain.getThisObject();
            if (!hooks.active() || statusBar(battery) == null) return chain.proceed();
            int original = style.getInt(battery);
            showBatteryPercent(battery);
            // Xiaomi only measures the separate percentage container in style 3.
            style.setInt(battery, 3);
            try { return chain.proceed(); }
            finally { style.setInt(battery, original); }
        });
    }

    private void topBatteryBar() throws Exception {
        Executable update = hooks.method(BATTERY_INDICATOR, "onBatteryStyleChanged", "V", "I");
        Runnable hit = hooks.firstHit("top_battery_bar");
        hooks.hook(update, chain -> {
            View indicator = (View) chain.getThisObject();
            if (!hooks.active() || indicator.getId() != id(indicator, "battery_indicator")
                    || phoneBar(indicator) == null) return chain.proceed();
            hit.run();
            // Apply the native bar style only to the bar view. The normal battery view keeps its own style.
            return chain.proceed(new Object[]{2});
        });
    }

    private void showBatteryPercent(View battery) {
        View percent = child(battery, "battery_percentage_container");
        if (percent != null) {
            // The vendor's hidden-state animation can leave the container at zero alpha.
            percent.setAlpha(1f);
            percent.setVisibility(View.VISIBLE);
        }
        View mark = child(battery, "battery_percentage_mark_view");
        if (mark != null) mark.setVisibility(View.VISIBLE);
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
        WeakHashMap<TextView, Boolean> clocks = new WeakHashMap<>();
        hooks.onRefresh(() -> {
            clockFormat = null;
            for (TextView clock : new java.util.ArrayList<>(clocks.keySet())) {
                hooks.refreshView(clock, () -> {
                    try { ((java.lang.reflect.Method) update).invoke(clock); }
                    catch (ReflectiveOperationException ignored) {}
                });
            }
        });
        hooks.hook(update, chain -> {
            Object result = chain.proceed();
            TextView clock = (TextView) chain.getThisObject();
            if (isClock(clock)) {
                clocks.put(clock, true);
                refreshClock(clock);
            }
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
                positionClock(clock);
                refreshClock(clock);
            }
            return result;
        });
    }

    private boolean isClock(View clock) {
        return clock.getId() == id(clock, "clock") && phoneBar(clock) != null;
    }

    private void refreshClock(TextView clock) {
        boolean active = hooks.active();
        if (active && (hooks.custom(CLOCK_SECONDS) || hooks.custom(CLOCK_DATE))) renderTime(clock);
        if (active && hooks.custom(CLOCK_SIZE)) {
            originalClockSizes.putIfAbsent(clock, clock.getTextSize());
            clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, hooks.value(CLOCK_SIZE));
        } else {
            Float original = originalClockSizes.remove(clock);
            if (original != null) clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, original);
        }
        if (active && hooks.custom(CLOCK_SECONDS)) {
            if (!clockTicks.containsKey(clock) && canTick(clock)) tick(clock);
        } else {
            Runnable tick = clockTicks.remove(clock);
            if (tick != null) clock.removeCallbacks(tick);
        }
        if (active && (hooks.custom(CLOCK_SIZE) || hooks.custom(CLOCK_SECONDS) || hooks.custom(CLOCK_DATE)
                || hooks.custom(CLOCK_POSITION))) clockHit.run();
    }

    private void renderTime(TextView clock) {
        Locale locale = clock.getResources().getConfiguration().getLocales().get(0);
        boolean use24 = DateFormat.is24HourFormat(clock.getContext());
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
            if (!hooks.active() || !hooks.custom(CLOCK_SECONDS) || !canTick(clock)) return;
            renderTime(clock);
            tick(clock);
        };
        clockTicks.put(clock, run);
        clock.postDelayed(run, 1000 - SystemClock.uptimeMillis() % 1000);
    }

    private void positionClock(TextView clock) {
        int where = hooks.value(CLOCK_POSITION);
        if (where == 0 || !(clock.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) clock.getParent();
        View notifications = child(parent, "notification_icon_area");
        if (notifications == null || notifications.getParent() != parent) return;
        ViewGroup.LayoutParams params = clock.getLayoutParams();
        parent.removeView(clock);
        parent.addView(clock, parent.indexOfChild(notifications) + where - 1, params);
    }

    private void padding() throws Exception {
        Executable method = hooks.method(PHONE_BAR, "updatePaddings", "V");
        Runnable hit = hooks.firstHit("status_padding");
        WeakHashMap<View, int[]> original = new WeakHashMap<>();
        WeakHashMap<View, Boolean> bars = new WeakHashMap<>();
        hooks.onRefresh(() -> {
            for (View bar : new java.util.ArrayList<>(bars.keySet())) {
                hooks.refreshView(bar, () -> {
                    try { ((java.lang.reflect.Method) method).invoke(bar); }
                    catch (ReflectiveOperationException ignored) {}
                });
            }
        });
        hooks.hook(method, chain -> {
            View bar = (View) chain.getThisObject();
            bars.put(bar, true);
            View contents = child(bar, "status_bar_contents");
            int[] padding = original.remove(contents);
            if (padding != null) {
                contents.setPaddingRelative(padding[0], contents.getPaddingTop(), padding[1], contents.getPaddingBottom());
            }
            Object result = chain.proceed();
            if (contents == null || !hooks.active()
                    || !(hooks.custom(START_MARGIN) || hooks.custom(END_MARGIN))) return result;
            original.put(contents, new int[]{contents.getPaddingStart(), contents.getPaddingEnd()});
            contents.setPaddingRelative(contents.getPaddingStart() + Math.round(dp(contents, hooks.value(START_MARGIN))),
                    contents.getPaddingTop(), contents.getPaddingEnd() + Math.round(dp(contents, hooks.value(END_MARGIN))),
                    contents.getPaddingBottom());
            hit.run();
            return result;
        });
    }
}
