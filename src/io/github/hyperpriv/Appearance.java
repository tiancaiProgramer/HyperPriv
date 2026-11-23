package io.github.hyperpriv;

final class Appearance {
    static final String SYSTEM_UI = "com.android.systemui";
    static final String HOME = "com.miui.home";
    static final String CAMERA = "com.android.camera";
    static final String STATUS = "appearance_status";
    static final String GESTURE = "appearance_gesture";
    static final String ICONS = "appearance_home";
    static final String WATERMARK = "appearance_camera";
    static final String[] KEYS = {STATUS, GESTURE, ICONS, WATERMARK};

    enum Value {
        NOTIFICATIONS("通知图标上限", 0, 15, 0, " 个"),
        SIGNAL("双排信号样式", "跟随系统", "细线双排", "粗线双排"),
        SIGNAL_SIZE("双排信号大小", 75, 125, 100, "%"),
        SIGNAL_GAP("双排信号行距", 0, 4, 1, " dp"),
        SIGNAL_Y("双排信号上下微调", -3, 3, 0, " dp"),
        MOBILE_TYPE("隐藏 4G／5G 标记", "跟随系统", "隐藏"),
        MOBILE_HD("VoLTE／VoWiFi 标记", "跟随系统", "隐藏 VoLTE", "隐藏 VoWiFi", "全部隐藏"),
        MOBILE_ACTIVITY("隐藏网络上／下行箭头", "跟随系统", "隐藏"),
        NETWORK_ORDER("信号图标排序", "跟随系统", "Wi-Fi 在移动信号前", "移动信号在 Wi-Fi 前"),
        BATTERY("电量显示样式", "跟随系统", "隐藏外框，沿用原数字设置", "始终仅显示百分比", "隐藏整个电量区域"),
        BATTERY_MARK("隐藏百分号", "跟随系统", "隐藏"),
        CHARGE_MARK("隐藏充电图标", "跟随系统", "隐藏"),
        CLOCK_POSITION("时钟位置", "跟随系统", "在通知图标左侧", "在通知图标右侧", "居中"),
        CLOCK_TIME("时间格式", "跟随系统", "24 小时", "12 小时"),
        CLOCK_SECONDS("显示秒数", "跟随系统", "显示"),
        CLOCK_DATE("时钟旁显示日期／星期", "关闭", "月日", "星期", "月日和星期"),
        CLOCK_SIZE("时钟字号", 0, 18, 0, " sp"),
        CLOCK_ALIGN("时钟文字对齐", "跟随系统", "靠左", "居中", "靠右"),
        SUCCESSION("网速显示样式", "跟随系统", "上传和下载分两行", "上传和下载在同一行"),
        SPEED_UNIT("网速单位", "跟随系统", "字节／秒（B/s）", "比特／秒（b/s）"),
        SPEED_THRESHOLD("低速隐藏阈值", 0, 128, 0, " KB/s"),
        SPEED_SIZE("网速字号", 0, 14, 0, " sp"),
        SPEED_GAP("网速左右间距", -3, 8, 0, " dp"),
        START_MARGIN("状态栏左侧加宽", 0, 12, 0, " dp"),
        END_MARGIN("状态栏右侧加宽", 0, 12, 0, " dp"),
        ICON_GAP("系统图标间距微调", -3, 6, 0, " dp"),
        STATUS_Y("状态栏内容上下微调", -3, 3, 0, " dp"),
        HOME_SCALE("桌面图标大小", 70, 120, 100, "%"),
        PILL_WIDTH("小白条宽度", 40, 100, 100, "%"),
        PILL_HEIGHT("小白条粗细", 0, 7, 0, " dp"),
        PILL_RADIUS("小白条圆角", -1, 4, -1, " dp"),
        PILL_Y("小白条向上偏移", 0, 6, 0, " dp"),
        PILL_COLOR("小白条颜色", "跟随系统", "白色", "黑色", "蓝色", "橙色"),
        PILL_OPACITY("小白条不透明度", 0, 100, 100, "%");

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
            return value < min || value > max || (this == NOTIFICATIONS && value > 0 && value < 4)
                    || ((this == CLOCK_SIZE || this == SPEED_SIZE) && value > 0 && value < 7)
                    ? initial : value;
        }
        String display(int value) {
            if (choices != null) return choices[value];
            if (this == SPEED_THRESHOLD && value == 0) return "关闭";
            if ((this == NOTIFICATIONS || this == CLOCK_SIZE || this == SPEED_SIZE || this == PILL_HEIGHT)
                    && value == 0) return "跟随系统";
            return this == PILL_RADIUS && value == -1 ? "跟随系统" : value + unit;
        }
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
