package io.github.hyperpriv;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import java.util.Map;

/** Accepts the grid limit measured by the scoped Xiaomi launcher. */
public final class HomeScaleProvider extends ContentProvider {
    private static final Uri URI = Uri.parse("content://io.github.hyperpriv.homeScale");
    private static final String REPORT = "reportLimit";
    private static final String LIMIT = "limit";

    @SuppressWarnings("deprecation")
    static String profile(Context context) {
        DisplayManager displays = context.getSystemService(DisplayManager.class);
        Display display = displays == null ? null : displays.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) return null;
        DisplayMetrics snapshot = new DisplayMetrics();
        display.getRealMetrics(snapshot);
        if (snapshot.widthPixels <= 0 || snapshot.heightPixels <= 0 || snapshot.densityDpi <= 0) return null;
        return snapshot.widthPixels + "x" + snapshot.heightPixels + "@" + snapshot.densityDpi;
    }

    private static SharedPreferences store(Context context) {
        return context.getSharedPreferences("hyperpriv_home_limits", Context.MODE_PRIVATE);
    }

    static int current(Context context) {
        String screen = profile(context);
        if (screen == null) return Appearance.Value.HOME_SCALE.initial;
        SharedPreferences preferences = store(context);
        if (preferences.contains(screen)) return preferences.getInt(screen, Appearance.Value.HOME_SCALE.initial);
        // Older reports used the launcher's app-specific density, which can differ on the inner screen.
        String resolution = screen.substring(0, screen.indexOf('@') + 1);
        int fallback = Appearance.Value.HOME_SCALE.max + 1;
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (entry.getKey().startsWith(resolution) && entry.getValue() instanceof Integer)
                fallback = Math.min(fallback, (Integer) entry.getValue());
        }
        return fallback <= Appearance.Value.HOME_SCALE.max ? fallback : Appearance.Value.HOME_SCALE.initial;
    }

    static void report(Context context, String profile, int limit) {
        Bundle extras = new Bundle();
        extras.putInt(LIMIT, limit);
        context.getContentResolver().call(URI, REPORT, profile, extras);
    }

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String profile, Bundle extras) {
        if (!Appearance.HOME.equals(getCallingPackage())) throw new SecurityException("Launcher required");
        if (!REPORT.equals(method) || profile == null || !profile.matches("[0-9]{1,5}x[0-9]{1,5}@[0-9]{1,4}")
                || extras == null) throw new IllegalArgumentException("Invalid grid profile");
        int limit = extras.getInt(LIMIT);
        Appearance.Value scale = Appearance.Value.HOME_SCALE;
        if (limit < scale.initial || limit > scale.max) throw new IllegalArgumentException("Invalid grid limit");
        SharedPreferences preferences = store(getContext());
        if (preferences.getInt(profile, -1) != limit) {
            preferences.edit().putInt(profile, limit).apply();
            Settings.notifyChanged();
        }
        return Bundle.EMPTY;
    }

    @Override public Cursor query(Uri uri, String[] columns, String where, String[] args, String sort) {
        throw new UnsupportedOperationException();
    }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String where, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String where, String[] args) { throw new UnsupportedOperationException(); }
}
