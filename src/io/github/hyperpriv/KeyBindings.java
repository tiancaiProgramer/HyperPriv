package io.github.hyperpriv;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.os.PowerManager;
import org.json.JSONException;
import org.json.JSONObject;

final class KeyBindings {
    static final int[] CODES = {26, 24, 25};
    static final String[] KEYS = {"电源键", "音量上", "音量下"};
    static final String[] STATES = {"unlocked", "locked", "off"};
    static final String[] STATE_LABELS = {"亮屏且已解锁", "亮屏且锁屏", "息屏"};
    static final String[] GESTURES = {"single", "double", "triple", "long"};
    static final String[] GESTURE_LABELS = {"单击", "双击", "三击", "长按"};
    private final JSONObject data;

    KeyBindings(String text) {
        JSONObject parsed;
        try {
            parsed = new JSONObject(text.isEmpty() ? "{}" : text);
            if (parsed.optInt("version", 1) != 1) parsed = new JSONObject();
        } catch (JSONException error) {
            parsed = new JSONObject();
        }
        data = parsed;
    }

    static String slot(int code, String state, String gesture) {
        return code + ":" + state + ":" + gesture;
    }

    JSONObject action(int code, String state, String gesture) {
        return data.optJSONObject(slot(code, state, gesture));
    }

    boolean active(int code, String state) {
        for (String gesture : GESTURES) if (action(code, state, gesture) != null) return true;
        return false;
    }

    int maxCount(int code, String state) {
        if (action(code, state, "triple") != null) return 3;
        if (action(code, state, "double") != null) return 2;
        return 1;
    }

    void put(int code, String state, String gesture, JSONObject action) {
        String id = slot(code, state, gesture);
        try {
            data.put("version", 1);
            if (action == null) data.remove(id);
            else data.put(id, action);
        } catch (JSONException error) {
            throw new IllegalArgumentException("动作无法保存", error);
        }
    }

    String encode() { return data.toString(); }

    static KeyBindings read(SharedPreferences prefs) {
        return new KeyBindings(prefs.getString("key_bindings", ""));
    }

    static String state(Context context) {
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (power != null && !power.isInteractive()) return "off";
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return keyguard != null && keyguard.isKeyguardLocked() ? "locked" : "unlocked";
    }

    static JSONObject make(String type, String title, Intent intent) {
        JSONObject result = new JSONObject();
        try {
            result.put("type", type);
            result.put("title", title);
            if (intent != null) result.put("uri", intent.toUri(Intent.URI_INTENT_SCHEME));
        } catch (JSONException error) {
            throw new IllegalArgumentException("动作无法保存", error);
        }
        return result;
    }

    static Intent checkedIntent(Context context, Intent source, String pkg) {
        Intent intent = new Intent(source);
        intent.setSelector(null);
        intent.setClipData(null);
        intent.setPackage(pkg);
        ResolveInfo resolved = context.getPackageManager().resolveActivity(intent, 0);
        if (resolved == null || resolved.activityInfo == null) throw new IllegalArgumentException("应用入口已不存在");
        ActivityInfo activity = resolved.activityInfo;
        if (!activity.exported || !activity.enabled || !activity.applicationInfo.enabled
                || !pkg.equals(activity.packageName)) throw new IllegalArgumentException("此入口未向其他应用开放");
        intent.setComponent(new android.content.ComponentName(activity.packageName, activity.name));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return intent;
    }

    static String original(Context context, int code, String state, String gesture) {
        try {
            if (code == 26) {
                if (gesture.equals("single")) return state.equals("off") ? "唤醒屏幕" : "锁屏／熄屏";
                String key = gesture.equals("double") ? "double_click_power_key"
                        : gesture.equals("long") ? "long_press_power_key" : "three_click_power_key";
                return function(android.provider.Settings.System.getString(context.getContentResolver(), key));
            }
            if (gesture.equals("single")) return code == 24 ? "增大音量" : "减小音量";
            if (code == 25 && gesture.equals("double") && !state.equals("unlocked")) {
                String value = android.provider.Settings.System.getString(context.getContentResolver(), "volumekey_launch_camera");
                if ("1".equals(value)) return "打开相机";
                if ("2".equals(value)) return "打开相机并拍照";
                if (value != null) return "未启用相机快捷操作（系统值 " + value + "）";
                return "未读取到相机快捷操作设置";
            }
            if (code == 25 && gesture.equals("long")) {
                String value = android.provider.Settings.Secure.getString(context.getContentResolver(), "key_long_press_volume_down");
                return function(value);
            }
            return "未读取到系统快捷绑定";
        } catch (RuntimeException error) {
            return "系统绑定读取失败";
        }
    }

    private static String function(String value) {
        if (value == null || value.isEmpty() || value.equals("none") || value.equals("null")) return "无自定义快捷操作";
        switch (value) {
            case "mi_pay": return "卡包";
            case "launch_voice_assistant": return "小爱同学";
            case "launch_camera": return "打开相机";
            case "launch_camera_and_take_photo": return "打开相机并拍照";
            case "launch_sound_recorder": return "直接录音";
            case "screen_shot": return "截图";
            case "turn_on_torch": return "手电筒";
            default: return value;
        }
    }

}
