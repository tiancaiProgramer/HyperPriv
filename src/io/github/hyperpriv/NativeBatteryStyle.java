package io.github.hyperpriv;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Process;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

enum NativeBatteryStyle {
    GRAPHIC(0, "图形方式"),
    NUMBER_INSIDE(1, "数字在电池内"),
    NUMBER_OUTSIDE(3, "数字在电池外"),
    TOP_BAR(2, "顶部电量条");

    private static final String KEY = "battery_indicator_style";
    final int systemValue;
    final String title;

    NativeBatteryStyle(int systemValue, String title) {
        this.systemValue = systemValue;
        this.title = title + "（系统自带）";
    }

    static boolean available() {
        return available(RuntimeProfile.appearanceMatch(Appearance.STATUS));
    }

    static boolean available(AppearanceSupport.Match match) {
        return "babylon".equals(Build.DEVICE) && match == AppearanceSupport.Match.EXACT;
    }

    static Uri settingUri() {
        return android.provider.Settings.System.getUriFor(KEY);
    }

    static int current(Context context) {
        return android.provider.Settings.System.getInt(context.getContentResolver(), KEY, 1);
    }

    boolean apply(Context context) {
        if (!available()) return false;
        if (current(context) == systemValue) return true;
        java.lang.Process command = null;
        try {
            int user = Process.myUid() / 100_000;
            command = new ProcessBuilder("su", "-c", "/system/bin/settings --user " + user
                    + " put system " + KEY + " " + systemValue)
                    .redirectErrorStream(true).redirectOutput(new File("/dev/null")).start();
            return command.waitFor(10, TimeUnit.SECONDS) && command.exitValue() == 0 && current(context) == systemValue;
        } catch (IOException | SecurityException error) {
            return false;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (command != null && command.isAlive()) command.destroyForcibly();
        }
    }

    static Intent picker() {
        return new Intent().setClassName("com.android.settings",
                "com.android.settings.fuelgauge.BatteryIndicatorStyle");
    }

}
