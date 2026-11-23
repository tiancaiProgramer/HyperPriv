package io.github.hyperpriv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class AppearanceSupport {
    static final String NOTIFICATIONS = "com.android.systemui.statusbar.phone.NotificationIconContainer";
    static final String PHONE_BAR = "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView";
    static final String GESTURE = "com.android.systemui.navigationbar.gestural.NavigationHandle";
    static final String MIUI_DECORATION = "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.decoration.";
    static final String BATTERY = "com.android.systemui.statusbar.views.MiuiBatteryMeterView";
    static final String CLOCK = "com.android.systemui.statusbar.views.MiuiClock";
    static final String SPEED = "com.android.systemui.statusbar.views.NetworkSpeedView";
    static final String STATUS_ICONS = "com.android.systemui.statusbar.views.MiuiStatusIconContainer";
    static final String HOME_CONFIG = "com.miui.home.common.gridconfig.GridConfig$IconConfig";
    static final String LEGACY_HOME_CONFIG = "com.miui.home.launcher.GridConfig$IconConfig";

    enum Match { NONE, EXACT, COMPATIBLE }

    static Match match(String key, int sdk, long version, String hash, String path, String[] splits,
            boolean compatibility) {
        if (Appearance.WATERMARK.equals(key))
            return CameraCompatibility.match(version, hash, path, splits, compatibility).match;
        boolean home = Appearance.ICONS.equals(key);
        if (home && version == 601052258L
                && "a41a1e57cc74320c7cd9a722588c8b712da33fc704c68c6997f654f7461f4111".equals(hash))
            return Match.EXACT;
        if (!home && sdk == 36 && version == 202501210L
                && "0ac0c967145dd5cda61b682525ed78d6d25c8abc31e8e32789138bfb2813ebac".equals(hash))
            return Match.EXACT;
        if (!compatibility) return Match.NONE;
        List<String> paths = new ArrayList<>();
        paths.add(path);
        if (splits != null) paths.addAll(Arrays.asList(splits));
        String[] apkPaths = paths.toArray(new String[0]);
        if (home) {
            for (String config : new String[]{HOME_CONFIG, LEGACY_HOME_CONFIG}) {
                Rule iconSize = method(key, config, "getIconSize", "I");
                if (has(apkPaths, Arrays.asList(iconSize),
                        Arrays.asList(field(config, "mCellWidth", "I")))) return Match.COMPATIBLE;
            }
        } else if (Appearance.GESTURE.equals(key)) {
            String bottom = MIUI_DECORATION + "MiuiDecorationBottomView";
            String root = MIUI_DECORATION + "MiuiDecorationRootView";
            if (has(apkPaths, Arrays.asList(method(key, bottom, "onDraw", "V", "Landroid/graphics/Canvas;"),
                            method(key, MIUI_DECORATION + "MiuiDecorationFolmeControl", "getAlphaFocus", "F")),
                    Arrays.asList(field(bottom, "mRadiusX", "F"), field(bottom, "mRadiusY", "F"),
                            field(root, "mPaint", "Landroid/graphics/Paint;"),
                            field(root, "mFolmeControl", "L" + MIUI_DECORATION.replace('.', '/')
                                    + "MiuiDecorationFolmeControl;")))) return Match.COMPATIBLE;
            if (has(apkPaths, Arrays.asList(method(key, GESTURE, "onDraw", "V", "Landroid/graphics/Canvas;")),
                    Arrays.asList(field(GESTURE, "mRadius", "F"), field(GESTURE, "mBottom", "F"),
                            field(GESTURE, "mPaint", "Landroid/graphics/Paint;")))) return Match.COMPATIBLE;
        } else if (has(apkPaths, Arrays.asList(
                method(key, PHONE_BAR, "onFinishInflate", "V"),
                method(key, NOTIFICATIONS, "onMeasure", "V", "I", "I"),
                method(key, NOTIFICATIONS, "calculateIconXTranslations", "V"),
                method(key, BATTERY, "updateChargeAndText", "V")),
                Arrays.asList(field(NOTIFICATIONS, "mMaxIcons", "I")))) return Match.COMPATIBLE;
        return Match.NONE;
    }

    private static boolean has(String[] paths, List<Rule> methods, List<String> fields) {
        DexEntrances.Result code = DexEntrances.inspect(paths, methods, fields);
        for (Rule method : methods) if (!code.found.contains(DexEntrances.signature(method))) return false;
        return code.found.containsAll(fields);
    }

    static Rule method(String key, String owner, String name, String result, String... params) {
        return new Rule(Appearance.target(key), 0, owner, name, result, key, "", params);
    }
    private static String field(String owner, String name, String type) {
        return DexEntrances.fieldSignature(owner, name, type);
    }
    private AppearanceSupport() {}
}
