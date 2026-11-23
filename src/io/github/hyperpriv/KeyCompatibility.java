package io.github.hyperpriv;

import android.os.Build;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class KeyCompatibility {
    private static final String VERIFIED_OS4_KEYS =
        "Xiaomi/dada/dada:17/CP2A.260605.016/OS4.0.4.0.XOCCNXM:user/release-keys";
    static final String INPUT = "com.android.server.input.InputManagerService";
    static final String RULE = "com.android.server.policy.MiuiSingleKeyRule";
    static final String ACTIONS = "com.miui.server.input.util.ShortCutActionsUtils";
    private static final List<Rule> COMMON = Arrays.asList(
        method(INPUT, "interceptKeyBeforeQueueing", "I", "Landroid/view/KeyEvent;", "I"),
        method(RULE, "getMaxMultiPressCount", "I"),
        method(RULE, "supportLongPress", "Z"),
        method(ACTIONS, "getInstance", "Lcom/miui/server/input/util/ShortCutActionsUtils;", "Landroid/content/Context;"),
        method(ACTIONS, "launchApp", "Z", "Landroid/content/Intent;"),
        method(ACTIONS, "launchSoundRecorder", "Z")
    );

    enum Callbacks {
        LEGACY(
            method(RULE, "onPress", "V", "J", "I"),
            method(RULE, "onMultiPress", "V", "J", "I", "I"),
            method(RULE, "onLongPress", "V", "J"),
            1, false),
        OS4(
            os4("onMiuiPress", "Ljava/lang/Object;", "J", "I"),
            os4("onMiuiMultiPress", "Ljava/lang/Object;", "J", "I", "I"),
            os4("onMiuiLongPress", "Ljava/lang/Object;", "J"),
            2, true);

        final Rule single, multi, longPress;
        final int pressCount;
        final boolean dispatcherNotifiesLongPress;

        Callbacks(Rule single, Rule multi, Rule longPress, int pressCount,
                  boolean dispatcherNotifiesLongPress) {
            this.single = single;
            this.multi = multi;
            this.longPress = longPress;
            this.pressCount = pressCount;
            this.dispatcherNotifiesLongPress = dispatcherNotifiesLongPress;
        }

        private static Rule os4(String name, String... params) {
            return new Rule("android", 37, "com.android.server.input.shortcut.singlekeyrule.PowerKeyRule",
                name, "V", "key_actions", "", params);
        }
    }

    static Callbacks callbacks(int osMajor, int sdk) {
        if (osMajor == 4 && sdk == 37) return Callbacks.OS4;
        return osMajor < 4 && sdk < 37 ? Callbacks.LEGACY : null;
    }

    static Callbacks callbacks() {
        return callbacks(HyperOsVersion.MAJOR, Build.VERSION.SDK_INT);
    }

    static boolean verifiedSystem() {
        return RuntimeProfile.knownSystem()
            || (callbacks() == Callbacks.OS4 && VERIFIED_OS4_KEYS.equals(Build.FINGERPRINT));
    }

    static boolean matched() {
        return matched(callbacks(),
            "/system/framework/services.jar", "/system_ext/framework/miui-services.jar");
    }

    static boolean matched(Callbacks callbacks, String... paths) {
        if (callbacks == null) return false;
        List<Rule> required = new ArrayList<>(COMMON);
        required.add(callbacks.single);
        required.add(callbacks.multi);
        required.add(callbacks.longPress);
        List<String> fields = Arrays.asList(
            DexEntrances.fieldSignature(RULE, "mKeyCode", "I"),
            DexEntrances.fieldSignature(RULE, "mContext", "Landroid/content/Context;"),
            DexEntrances.fieldSignature(INPUT, "mContext", "Landroid/content/Context;"));
        DexEntrances.Result result = DexEntrances.inspect(paths, required, fields);
        if (!result.complete) return false;
        for (Rule rule : required) if (!result.found.contains(DexEntrances.signature(rule))) return false;
        return result.found.containsAll(fields);
    }

    private static Rule method(String cls, String name, String result, String... params) {
        return new Rule("android", 36, cls, name, result, "key_actions", "", params);
    }

    private KeyCompatibility() {}
}
