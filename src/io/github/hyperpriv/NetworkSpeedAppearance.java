package io.github.hyperpriv;

import static io.github.hyperpriv.Appearance.Value.*;

import android.net.TrafficStats;
import android.os.SystemClock;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;
import java.util.WeakHashMap;

final class NetworkSpeedAppearance {
    private final AppearanceHooks hooks;
    private final SystemBarAppearance bar;
    private final WeakHashMap<View, Original> originals = new WeakHashMap<>();
    private final WeakHashMap<View, Boolean> views = new WeakHashMap<>();
    private long time, tx, rx;
    private double up, down;
    private boolean sampled, hidden;
    private final Paint.FontMetrics topMetrics = new Paint.FontMetrics(), bottomMetrics = new Paint.FontMetrics();

    NetworkSpeedAppearance(AppearanceHooks hooks, SystemBarAppearance bar) {
        this.hooks = hooks;
        this.bar = bar;
    }

    void install() throws Exception {
        String owner = AppearanceSupport.SPEED;
        Executable update = hooks.method(owner, "updateNetworkSpeed", "V");
        Executable set = hooks.method(owner, "setNetworkSpeed", "V", "Ljava/lang/String;", "Ljava/lang/String;");
        Executable visible = hooks.method(owner, "isIconVisible", "Z");
        Runnable hit = hooks.firstHit("status_speed");
        Runnable keyguardHit = hooks.firstHit("keyguard_speed");
        io.github.libxposed.api.XposedInterface.Hooker renderSpeed = chain -> {
            Object result = chain.proceed();
            View view = (View) chain.getThisObject();
            View host = SystemBarAppearance.statusBar(view);
            if (host == null) return result;
            views.put(view, true);
            boolean custom = hooks.custom(SUCCESSION) || hooks.custom(SPEED_UNIT) || hooks.custom(SPEED_THRESHOLD)
                    || hooks.custom(SPEED_SIZE) || hooks.custom(SPEED_GAP);
            if (hooks.active() && custom && view.isShown()) {
                apply(view);
                if (SystemBarAppearance.keyguardBar(host)) keyguardHit.run(); else hit.run();
            } else if (!hooks.active() || !custom) {
                restore(view);
            }
            return result;
        };
        hooks.hook(update, renderSpeed);
        hooks.hook(set, renderSpeed);
        hooks.onRefresh(() -> {
            hidden = false;
            for (View view : new ArrayList<>(views.keySet())) {
                hooks.refreshView(view, () -> {
                    restore(view);
                    try { ((Method) update).invoke(view); }
                    catch (ReflectiveOperationException ignored) {}
                });
            }
        });
        hooks.hook(visible, chain -> {
            Object result = chain.proceed();
            if (hidden && hooks.active() && hooks.custom(SPEED_THRESHOLD) && SystemBarAppearance.statusBar((View) chain.getThisObject()) != null)
                return false;
            return result;
        });
    }

    private void apply(View view) {
        TextView number = bar.child(view, "network_speed_number");
        TextView unit = bar.child(view, "network_speed_unit");
        if (number == null || unit == null) return;
        Original original = originals.get(view);
        if (original == null) {
            original = new Original(view, number, unit);
            originals.put(view, original);
        }
        if (hooks.custom(SPEED_SIZE)) {
            number.setTextSize(TypedValue.COMPLEX_UNIT_SP, hooks.value(SPEED_SIZE));
            unit.setTextSize(TypedValue.COMPLEX_UNIT_SP, hooks.value(SPEED_SIZE));
        }
        if (hooks.custom(SPEED_GAP)) {
            int delta = Math.round(AppearanceHooks.dp(view, hooks.value(SPEED_GAP)));
            view.setPaddingRelative(Math.max(0, original.start + delta), view.getPaddingTop(),
                    Math.max(0, original.end + delta), view.getPaddingBottom());
        }
        if (!hooks.custom(SUCCESSION) && !hooks.custom(SPEED_UNIT) && !hooks.custom(SPEED_THRESHOLD)) return;
        sample();
        if (!sampled) return;
        boolean below = hooks.custom(SPEED_THRESHOLD) && up + down < hooks.value(SPEED_THRESHOLD) * 1024d;
        if (below != hidden) {
            hidden = below;
            view.requestLayout();
        }
        if (hooks.custom(SPEED_THRESHOLD)) {
            number.setVisibility(hidden ? View.INVISIBLE : View.VISIBLE);
            unit.setVisibility(hidden ? View.INVISIBLE : View.VISIBLE);
        }
        int layout = hooks.value(SUCCESSION);
        int preferredUnit = hooks.value(SPEED_UNIT);
        if (layout == 0 && preferredUnit == 0) return;
        boolean bits = preferredUnit == 2;
        if (layout == 0) {
            String[] combined = format(up + down, bits);
            write(number, combined[0]);
            write(unit, combined[1]);
            view.setContentDescription(combined[0] + " " + combined[1]);
        } else {
            String[] transmit = format(up, bits);
            String[] receive = format(down, bits);
            String tx = "↑" + transmit[0] + transmit[1];
            String rx = "↓" + receive[0] + receive[1];
            if (layout == 1) {
                write(number, tx);
                write(unit, rx);
                number.getPaint().getFontMetrics(topMetrics);
                unit.getPaint().getFontMetrics(bottomMetrics);
                float topHeight = topMetrics.descent - topMetrics.ascent;
                float bottomHeight = bottomMetrics.descent - bottomMetrics.ascent;
                number.setTranslationY(-bottomHeight / 2);
                unit.setTranslationY(topHeight / 2);
            } else {
                write(number, tx + "  " + rx);
                number.setTranslationY(0);
                unit.setVisibility(View.GONE);
            }
            view.setContentDescription(tx + "  " + rx);
        }
    }

    private void restore(View view) {
        Original value = originals.remove(view);
        if (value == null) return;
        view.setPaddingRelative(value.start, view.getPaddingTop(), value.end, view.getPaddingBottom());
        TextView number = bar.child(view, "network_speed_number");
        TextView unit = bar.child(view, "network_speed_unit");
        if (number != null) {
            number.setTextSize(TypedValue.COMPLEX_UNIT_PX, value.numberSize);
            number.setTranslationY(value.numberY);
            number.setVisibility(value.numberVisibility);
        }
        if (unit != null) {
            unit.setTextSize(TypedValue.COMPLEX_UNIT_PX, value.unitSize);
            unit.setTranslationY(value.unitY);
            unit.setVisibility(value.unitVisibility);
        }
        view.requestLayout();
    }

    private static final class Original {
        final int start, end, numberVisibility, unitVisibility;
        final float numberSize, unitSize, numberY, unitY;

        Original(View view, TextView number, TextView unit) {
            start = view.getPaddingStart();
            end = view.getPaddingEnd();
            numberSize = number.getTextSize();
            unitSize = unit.getTextSize();
            numberY = number.getTranslationY();
            unitY = unit.getTranslationY();
            numberVisibility = number.getVisibility();
            unitVisibility = unit.getVisibility();
        }
    }

    private void sample() {
        long now = SystemClock.elapsedRealtime();
        if (time != 0 && now - time < 500) return;
        long nextTx = TrafficStats.getTotalTxBytes();
        long nextRx = TrafficStats.getTotalRxBytes();
        if (nextTx < 0 || nextRx < 0) {
            sampled = false;
            time = 0;
            return;
        }
        if (time != 0 && nextTx >= tx && nextRx >= rx && now - time < 20_000) {
            double seconds = (now - time) / 1000d;
            up = (nextTx - tx) / seconds;
            down = (nextRx - rx) / seconds;
            sampled = true;
        } else sampled = false;
        time = now;
        tx = nextTx;
        rx = nextRx;
    }

    private static String[] format(double bytes, boolean bits) {
        double count = bits ? bytes * 8 : bytes;
        int base = bits ? 1000 : 1024;
        int index = 0;
        String[] labels = bits ? new String[]{"b/s", "Kb/s", "Mb/s", "Gb/s"}
                : new String[]{"B/s", "KB/s", "MB/s", "GB/s"};
        while (count >= base && index + 1 < labels.length) {
            count /= base;
            index++;
        }
        return new String[]{String.format(Locale.getDefault(), index == 0 || count >= 10 ? "%.0f" : "%.1f", count),
                labels[index]};
    }

    private static void write(TextView view, String text) {
        if (!text.contentEquals(view.getText())) view.setText(text);
    }
}
