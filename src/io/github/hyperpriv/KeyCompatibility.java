package io.github.hyperpriv;

import java.util.Arrays;
import java.util.List;

final class KeyCompatibility {
    static final String INPUT = "com.android.server.input.InputManagerService";
    static final String RULE = "com.android.server.policy.MiuiSingleKeyRule";
    static final String ACTIONS = "com.miui.server.input.util.ShortCutActionsUtils";
    static final List<Rule> REQUIRED = Arrays.asList(
        method(INPUT, "interceptKeyBeforeQueueing", "I", "Landroid/view/KeyEvent;", "I"),
        method(RULE, "onPress", "V", "J", "I"),
        method(RULE, "onMultiPress", "V", "J", "I", "I"),
        method(RULE, "onLongPress", "V", "J"),
        method(RULE, "getMaxMultiPressCount", "I"),
        method(RULE, "supportLongPress", "Z"),
        method(ACTIONS, "getInstance", "Lcom/miui/server/input/util/ShortCutActionsUtils;", "Landroid/content/Context;"),
        method(ACTIONS, "launchApp", "Z", "Landroid/content/Intent;"),
        method(ACTIONS, "launchSoundRecorder", "Z")
    );

    static boolean matched() {
        List<String> fields = Arrays.asList(
            DexEntrances.fieldSignature(RULE, "mKeyCode", "I"),
            DexEntrances.fieldSignature(RULE, "mContext", "Landroid/content/Context;"),
            DexEntrances.fieldSignature(INPUT, "mContext", "Landroid/content/Context;"));
        DexEntrances.Result result = DexEntrances.inspect(
            new String[]{"/system/framework/services.jar", "/system_ext/framework/miui-services.jar"},
            REQUIRED, fields);
        if (!result.complete) return false;
        for (Rule rule : REQUIRED) if (!result.found.contains(DexEntrances.signature(rule))) return false;
        return result.found.containsAll(fields);
    }

    private static Rule method(String cls, String name, String result, String... params) {
        return new Rule("android", 36, cls, name, result, "key_actions", "", params);
    }

    private KeyCompatibility() {}
}
