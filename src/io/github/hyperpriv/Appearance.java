package io.github.hyperpriv;

final class Appearance {
    private static final int PILL_EDGE = 13;
    static final int LEGACY_BATTERY_FRAME_STYLE = 1;
    static final int BATTERY_PERCENT_STYLE = 2;
    static final int BATTERY_HIDDEN_STYLE = 3;
    static final int BATTERY_RING_STYLE = 4;
    static final int BATTERY_TOP_STYLE = 5;
    static final int BATTERY_CUTOUT_STYLE = 6;
    static final int DUO_SIGNAL_STYLE = 5;
    static final String SYSTEM_UI = "com.android.systemui";
    static final String HOME = "com.miui.home";
    static final String CAMERA = "com.android.camera";
    static final String STATUS = "appearance_status";
    static final String REFRESH_REQUEST = "appearance_refresh_request";
    static final String GESTURE = "appearance_gesture";
    static final String ICONS = "appearance_home";
    static final String WATERMARK = "appearance_camera";
    static final String[] KEYS = {STATUS, GESTURE, ICONS, WATERMARK};

    enum Value {
        NOTIFICATIONS("通知图标上限", 0, 9, 0, " 个"),
        SIGNAL("移动信号样式", "跟随系统", "切分双排（细）", "切分双排（粗）",
                null, null, "Duo（合并电量、Wi-Fi 和移动信号）"),
        SIGNAL_SIZE("信号图标大小", 75, 125, 100, "%"),
        SIGNAL_GAP("双排信号行距", 0, 4, 1, " dp"),
        SIGNAL_Y("信号图标上下微调", -3, 3, 0, " dp"),
        MOBILE_TYPE("隐藏 4G／5G 标记", "跟随系统", "隐藏"),
        MOBILE_HD("VoLTE／VoWiFi 标记", "跟随系统", "隐藏 VoLTE", "隐藏 VoWiFi", "全部隐藏"),
        MOBILE_ACTIVITY("隐藏网络上／下行箭头", "跟随系统", "隐藏"),
        NETWORK_ORDER("信号图标排序", "跟随系统", "Wi-Fi 在移动信号前", "移动信号在 Wi-Fi 前"),
        BATTERY("电池样式", "跟随系统", null, "仅显示百分比", "隐藏电量",
                "状态栏圆环", "顶部电量条", "前摄挖孔电量环"),
        TOP_BAR_PERCENT("显示电量百分比", "关闭", "开启"),
        CHARGE_MARK("隐藏充电标记", "跟随系统", "隐藏"),
        CLOCK_POSITION("时钟位置", "跟随系统", "在通知图标左侧", "在通知图标右侧"),
        CLOCK_SECONDS("显示秒数", "跟随系统", "显示"),
        CLOCK_DATE("时钟旁显示日期／星期", "关闭", "月日", "星期", "月日和星期"),
        CLOCK_SIZE("时钟字号", 0, 18, 0, " sp"),
        SUCCESSION("网速显示样式", "跟随系统", "上传和下载分两行", "上传和下载在同一行"),
        SPEED_UNIT("网速单位", "跟随系统", "字节／秒（B/s）", "比特／秒（b/s）"),
        SPEED_THRESHOLD("低速隐藏阈值", 0, 128, 0, " KB/s"),
        SPEED_SIZE("网速字号", 0, 14, 0, " sp"),
        SPEED_GAP("网速左右间距", -3, 8, 0, " dp"),
        START_MARGIN("左侧留白", 0, 12, 0, " dp"),
        END_MARGIN("右侧留白", 0, 12, 0, " dp"),
        ICON_GAP("系统图标间距", -3, 6, 0, " dp"),
        HOME_SCALE("桌面图标大小", 70, 150, 100, "%"),
        PILL_WIDTH("小白条宽度", 40, 100, 100, "%"),
        PILL_HEIGHT("小白条粗细", 0, 7, 0, " dp"),
        PILL_RADIUS("小白条圆角", -1, 4, -1, " dp"),
        PILL_Y("小白条上下偏移", -PILL_EDGE, PILL_EDGE, 0, " dp"),
        PILL_COLOR("小白条颜色", "跟随系统", "白色", "黑色", "蓝色", "橙色"),
        PILL_OPACITY("小白条不透明度", 0, 100, 100, "%"),
        RING_HIDE_PERCENT("显示电量百分比", "显示", "隐藏"),
        CUTOUT_RING_PERCENT("显示电量百分比", "关闭", "开启"),
        CUTOUT_RING_GAP("圆环离挖孔的距离", 0, 5, 0, " dp"),
        CUTOUT_RING_STROKE("圆环粗细", 1, 4, 2, " dp");

        static final Value[] ALL = values();
        final String key, label, unit;
        final String[] choices;
        final int min, max, initial;

        Value(String label, String... choices) {
            this(label, 0, choices.length - 1, 0, "", choices);
        }
        Value(String label, int min, int max, int initial, String unit) {
            this(label, min, max, initial, unit, null);
        }
        Value(String label, int min, int max, int initial, String unit, String[] choices) {
            this.key = "appearance_" + name().toLowerCase(java.util.Locale.ROOT);
            this.label = label;
            this.min = min;
            this.max = max;
            this.initial = initial;
            this.unit = unit;
            this.choices = choices;
        }
        int normalize(int value) {
            // The appearance preview allowed 10–15; preserve those selections at the new cap.
            if (this == NOTIFICATIONS && value > max && value <= 15) return max;
            // Both former dot styles now use the wider two-row bars; keep the Duo ID stable.
            if (this == SIGNAL && (value == 3 || value == 4)) return 2;
            if (this == BATTERY && value == LEGACY_BATTERY_FRAME_STYLE) return BATTERY_PERCENT_STYLE;
            return value < min || value > max || (this == NOTIFICATIONS && value > 0 && value < 4)
                    || ((this == CLOCK_SIZE || this == SPEED_SIZE) && value > 0 && value < 7)
                    ? initial : value;
        }
        String display(int value) {
            if (choices != null) return choices[value];
            if (this == SPEED_THRESHOLD && value == 0) return "关闭";
            if (this == PILL_Y) {
                if (value == -PILL_EDGE) return "贴底";
                if (value == PILL_EDGE) return "上移至上限";
                return value == 0 ? "跟随系统" : (value > 0 ? "上移 " : "下移 ") + Math.abs(value) + unit;
            }
            if ((this == START_MARGIN || this == END_MARGIN || this == ICON_GAP)
                    && value == 0) return "系统默认";
            if ((this == NOTIFICATIONS || this == CLOCK_SIZE || this == SPEED_SIZE || this == PILL_HEIGHT)
                    && value == 0) return "跟随系统";
            return this == PILL_RADIUS && value == -1 ? "跟随系统" : value + unit;
        }
    }

    static boolean requiresUiRestart(Value value) {
        switch (value) {
            case BATTERY: case CHARGE_MARK:
            case CLOCK_POSITION: case NETWORK_ORDER:
            case PILL_HEIGHT: case PILL_Y:
                return true;
            default:
                return false;
        }
    }

    static int homeScaleLimit(int original, int cellWidth) {
        if (original <= 0 || cellWidth <= 0) return Value.HOME_SCALE.initial;
        int cappedPixels = cellWidth * 9 / 10;
        return Math.max(Value.HOME_SCALE.initial, Math.min(Value.HOME_SCALE.max, cappedPixels * 100 / original));
    }

    static float pillTop(float baseline, float height, float frameHeight, int offset, float density) {
        float lastTop = Math.max(0f, frameHeight - height);
        float top = Math.max(0f, Math.min(lastTop, baseline));
        if (offset == -PILL_EDGE) return lastTop;
        float limit = Math.min(top, lastTop - top);
        float requested = offset == PILL_EDGE ? limit : offset * density;
        return top - Math.max(-limit, Math.min(limit, requested));
    }

    static int migrateBatteryFrameStyle(int systemStyle) {
        return systemStyle == 0 || systemStyle == 2 ? BATTERY_HIDDEN_STYLE : BATTERY_PERCENT_STYLE;
    }

    static String target(String key) {
        switch (key) {
            case STATUS: case GESTURE: return SYSTEM_UI;
            case ICONS: return HOME;
            case WATERMARK: return CAMERA;
            default: return null;
        }
    }
    static boolean isParameter(String key) {
        for (Value value : Value.ALL) if (value.key.equals(key)) return true;
        return false;
    }
    private Appearance() {}
}
