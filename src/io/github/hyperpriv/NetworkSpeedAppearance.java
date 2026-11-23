package io.github.hyperpriv;

import static io.github.hyperpriv.Appearance.Value.*;

import android.net.TrafficStats;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import java.lang.reflect.Executable;
import java.util.Locale;
import java.util.WeakHashMap;

final class NetworkSpeedAppearance {
    private final AppearanceHooks hooks;
    private final SystemBarAppearance bar;
    private final WeakHashMap<View, int[]> padding = new WeakHashMap<>();
    private long time, tx, rx;
    private double up, down;
    private boolean sampled, hidden;

    NetworkSpeedAppearance(AppearanceHooks hooks, SystemBarAppearance bar) {
        this.hooks = hooks;
        this.bar = bar;
    }

    void install() throws Exception {
        String owner = AppearanceSupport.SPEED;
        Executable update = hooks.method(owner, "updateNetworkSpeed", "V");
        Executable visible = hooks.method(owner, "isIconVisible", "Z");
        Runnable hit = hooks.firstHit("status_speed");
        hooks.hook(update, chain -> {
            Object result = chain.proceed();
            View view = (View) chain.getThisObject();
            if (hooks.active() && SystemBarAppearance.phoneBar(view) != null) {
                apply(view);
                hit.run();
            }
            return result;
        });
        if (hooks.custom(SPEED_THRESHOLD)) hooks.hook(visible, chain -> {
            Object result = chain.proceed();
            if (hidden && hooks.active() && SystemBarAppearance.phoneBar((View) chain.getThisObject()) != null)
                return false;
            return result;
        });
    }

    private void apply(View view) {
        TextView number = bar.child(view, "network_speed_number");
        TextView unit = bar.child(view, "network_speed_unit");
        if (number == null || unit == null) return;
        if (hooks.custom(SPEED_SIZE)) {
            number.setTextSize(TypedValue.COMPLEX_UNIT_SP, hooks.value(SPEED_SIZE));
            unit.setTextSize(TypedValue.COMPLEX_UNIT_SP, hooks.value(SPEED_SIZE));
        }
        if (hooks.custom(SPEED_GAP)) {
            int[] original = padding.get(view);
            if (original == null) {
                original = new int[]{view.getPaddingStart(), view.getPaddingEnd()};
                padding.put(view, original);
            }
            int delta = Math.round(AppearanceHooks.dp(view, hooks.value(SPEED_GAP)));
            view.setPaddingRelative(Math.max(0, original[0] + delta), view.getPaddingTop(),
                    Math.max(0, original[1] + delta), view.getPaddingBottom());
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
                float dy = Math.min(number.getTextSize() * .53f, AppearanceHooks.dp(view, 7));
                number.setTranslationY(-dy);
                unit.setTranslationY(dy);
            } else {
                write(number, tx + "  " + rx);
                number.setTranslationY(0);
                unit.setVisibility(View.GONE);
            }
            view.setContentDescription(tx + "  " + rx);
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
