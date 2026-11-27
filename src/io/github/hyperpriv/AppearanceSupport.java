package io.github.hyperpriv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

final class AppearanceSupport {
    static final String NOTIFICATIONS = "com.android.systemui.statusbar.phone.NotificationIconContainer";
    static final String PHONE_BAR = "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView";
    static final String KEYGUARD_BAR = "com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView";
    static final String GESTURE = "com.android.systemui.navigationbar.gestural.NavigationHandle";
    static final String MIUI_DECORATION = "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.decoration.";
    static final String BATTERY = "com.android.systemui.statusbar.views.MiuiBatteryMeterView";
    static final String BATTERY_INDICATOR = "com.android.systemui.statusbar.views.BatteryIndicator";
    static final String CLOCK = "com.android.systemui.statusbar.views.MiuiClock";
    static final String SPEED = "com.android.systemui.statusbar.views.NetworkSpeedView";
    static final String STATUS_ICONS = "com.android.systemui.statusbar.views.MiuiStatusIconContainer";
    static final String MOBILE = "com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView";
    static final String SLOTS = "com.android.systemui.statusbar.phone.ui.StatusBarIconList";
    static final String HOME_CONFIG = "com.miui.home.common.gridconfig.GridConfig$IconConfig";
    static final String LEGACY_HOME_CONFIG = "com.miui.home.launcher.GridConfig$IconConfig";

    enum Match { NONE, EXACT, COMPATIBLE }

    enum StatusPart {
        NOTIFICATION_LIMIT(true, Arrays.asList(field(NOTIFICATIONS, "mMaxIcons", "I")),
                statusMethod(NOTIFICATIONS, "onMeasure", "V", "I", "I"),
                statusMethod(NOTIFICATIONS, "calculateIconXTranslations", "V")),
        BATTERY_STYLE(true, Arrays.asList(field(BATTERY, "mBatteryStyle", "I")),
                statusMethod(BATTERY, "updateChargeAndText", "V"),
                statusMethod(BATTERY, "updateVisibility$1$1", "V"),
                statusMethod(BATTERY, "onAttachedToWindow", "V"),
                statusMethod(BATTERY, "onMeasure", "V", "I", "I")),
        TOP_BATTERY_BAR(true, Collections.emptyList(), statusMethod(BATTERY_INDICATOR, "onBatteryStyleChanged", "V", "I")),
        BATTERY_RINGS(true, Arrays.asList(field(BATTERY, "mLevel", "I"), field(BATTERY, "mTintColor", "I"),
                        field(BATTERY, "mCharging", "Z")),
                statusMethod(PHONE_BAR, "onFinishInflate", "V")),
        CLOCK_STYLE(true, Collections.emptyList(),
                statusMethod(CLOCK, "updateTime", "V"),
                statusMethod(CLOCK, "onDetachedFromWindow", "V"),
                statusMethod(PHONE_BAR, "onFinishInflate", "V")),
        PADDING(true, Collections.emptyList(), statusMethod(PHONE_BAR, "updatePaddings", "V")),
        NETWORK_SPEED(true, Collections.emptyList(),
                statusMethod(SPEED, "updateNetworkSpeed", "V"),
                statusMethod(SPEED, "setNetworkSpeed", "V", "Ljava/lang/String;", "Ljava/lang/String;"),
                statusMethod(SPEED, "isIconVisible", "Z")),
        MOBILE_INDICATORS(true, Collections.emptyList(),
                statusMethod(STATUS_ICONS, "onMeasure", "V", "I", "I"),
                statusMethod(MOBILE, "getSubId", "I")),
        NETWORK_ORDER(false, Collections.emptyList(), statusMethod(SLOTS, "<init>", "V", "[Ljava/lang/String;")),
        ICON_SPACING(true, Arrays.asList(field(STATUS_ICONS, "iconSpacing", "I")),
                statusMethod(STATUS_ICONS, "onMeasure", "V", "I", "I"),
                statusMethod(STATUS_ICONS, "onLayout", "V", "Z", "I", "I", "I", "I"));

        final List<Rule> methods;
        final List<String> declarations;

        StatusPart(boolean needsPhoneBar, List<String> fields, Rule... methods) {
            this.methods = Arrays.asList(methods);
            declarations = new ArrayList<>(fields);
            if (needsPhoneBar) declarations.add(DexEntrances.classSignature(PHONE_BAR));
        }

        static StatusPart forValue(Appearance.Value value) {
            switch (value) {
                case NOTIFICATIONS: return NOTIFICATION_LIMIT;
                case SIGNAL: case SIGNAL_SIZE: case SIGNAL_GAP: case SIGNAL_Y:
                case MOBILE_TYPE: case MOBILE_HD: case MOBILE_ACTIVITY: return MOBILE_INDICATORS;
                case NETWORK_ORDER: return NETWORK_ORDER;
                case CHARGE_MARK: return BATTERY_STYLE;
                case RING_HIDE_PERCENT: case CUTOUT_RING_PERCENT: case CUTOUT_RING_GAP: case CUTOUT_RING_STROKE:
                    return BATTERY_RINGS;
                case CLOCK_POSITION: case CLOCK_SECONDS: case CLOCK_DATE: case CLOCK_SIZE: return CLOCK_STYLE;
                case SUCCESSION: case SPEED_UNIT: case SPEED_THRESHOLD: case SPEED_SIZE: case SPEED_GAP:
                    return NETWORK_SPEED;
                case START_MARGIN: case END_MARGIN: return PADDING;
                case ICON_GAP: return ICON_SPACING;
                default: return null;
            }
        }
    }

    static final class StatusMatch {
        static final StatusMatch NONE = new StatusMatch(Match.NONE, EnumSet.noneOf(StatusPart.class));
        final Match match;
        final Set<StatusPart> parts;

        private StatusMatch(Match match, EnumSet<StatusPart> parts) {
            this.match = match;
            this.parts = Collections.unmodifiableSet(parts);
        }
        boolean supports(Appearance.Value value) {
            if (value == Appearance.Value.BATTERY)
                return parts.contains(StatusPart.BATTERY_STYLE) || parts.contains(StatusPart.BATTERY_RINGS)
                        || parts.contains(StatusPart.TOP_BATTERY_BAR);
            if (value == Appearance.Value.TOP_BAR_PERCENT)
                return parts.contains(StatusPart.TOP_BATTERY_BAR) && parts.contains(StatusPart.BATTERY_RINGS);
            StatusPart part = StatusPart.forValue(value);
            return part == null || parts.contains(part);
        }

        boolean supportsBatteryStyle(int mode) {
            switch (mode) {
                case 0: return true;
                case Appearance.BATTERY_RING_STYLE:
                case Appearance.BATTERY_CUTOUT_STYLE: return parts.contains(StatusPart.BATTERY_RINGS);
                case Appearance.BATTERY_TOP_STYLE: return parts.contains(StatusPart.TOP_BATTERY_BAR);
                default: return parts.contains(StatusPart.BATTERY_STYLE);
            }
        }
    }

    static StatusMatch matchStatus(int sdk, long version, String hash, String path, String[] splits,
            boolean compatibility) {
        if (exactSystemUi(sdk, version, hash))
            return new StatusMatch(Match.EXACT, EnumSet.allOf(StatusPart.class));
        if (!compatibility) return StatusMatch.NONE;
        List<Rule> methods = new ArrayList<>();
        List<String> declarations = new ArrayList<>();
        for (StatusPart part : StatusPart.values()) {
            methods.addAll(part.methods);
            declarations.addAll(part.declarations);
        }
        Set<String> found = DexEntrances.inspect(apkPaths(path, splits), methods, declarations).found;
        EnumSet<StatusPart> matched = EnumSet.noneOf(StatusPart.class);
        for (StatusPart part : StatusPart.values())
            if (has(found, part.methods, part.declarations)) matched.add(part);
        return matched.isEmpty() ? StatusMatch.NONE : new StatusMatch(Match.COMPATIBLE, matched);
    }

    static Match match(String key, int sdk, long version, String hash, String path, String[] splits,
            boolean compatibility) {
        if (Appearance.WATERMARK.equals(key))
            return CameraCompatibility.match(version, hash, path, splits, compatibility).match;
        if (Appearance.STATUS.equals(key))
            return matchStatus(sdk, version, hash, path, splits, compatibility).match;
        boolean home = Appearance.ICONS.equals(key);
        if (home && version == 601052258L
                && "a41a1e57cc74320c7cd9a722588c8b712da33fc704c68c6997f654f7461f4111".equals(hash))
            return Match.EXACT;
        if (!home && exactSystemUi(sdk, version, hash)) return Match.EXACT;
        if (!compatibility) return Match.NONE;
        String[] apkPaths = apkPaths(path, splits);
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
        }
        return Match.NONE;
    }

    private static boolean exactSystemUi(int sdk, long version, String hash) {
        return sdk == 36 && version == 202501210L
                && "0ac0c967145dd5cda61b682525ed78d6d25c8abc31e8e32789138bfb2813ebac".equals(hash);
    }
    private static String[] apkPaths(String path, String[] splits) {
        List<String> paths = new ArrayList<>();
        paths.add(path);
        if (splits != null) paths.addAll(Arrays.asList(splits));
        return paths.toArray(new String[0]);
    }
    private static boolean has(String[] paths, List<Rule> methods, List<String> fields) {
        return has(DexEntrances.inspect(paths, methods, fields).found, methods, fields);
    }
    private static boolean has(Set<String> found, List<Rule> methods, List<String> declarations) {
        for (Rule method : methods) if (!found.contains(DexEntrances.signature(method))) return false;
        return found.containsAll(declarations);
    }
    private static Rule statusMethod(String owner, String name, String result, String... params) {
        return method(Appearance.STATUS, owner, name, result, params);
    }

    static Rule method(String key, String owner, String name, String result, String... params) {
        return new Rule(Appearance.target(key), 0, owner, name, result, key, "", params);
    }
    private static String field(String owner, String name, String type) {
        return DexEntrances.fieldSignature(owner, name, type);
    }
    private AppearanceSupport() {}
}
