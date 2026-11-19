package io.github.hyperpriv;

import java.util.Arrays;
import java.util.List;

final class HdmiCompatibility {
    static final String PACKAGE = "com.android.systemui";
    static final String KEY = "hdmi_auto_confirm";
    static final String SHEET = PACKAGE + ".statusbar.phone.SystemUIBottomSheetDialog";
    static final String DELEGATE = PACKAGE + ".statusbar.phone.DialogDelegate";
    static final String CONFIRMATION = PACKAGE + ".display.ui.view.MirroringConfirmationDialogDelegate";
    static final String DELEGATE_FIELD = "delegate";
    static final String BUTTON_FIELD = "mirrorButton";
    private static final String VERIFIED_APK =
            "0ac0c967145dd5cda61b682525ed78d6d25c8abc31e8e32789138bfb2813ebac";
    private static final List<Rule> METHODS = Arrays.asList(
            entrance(SHEET, "onCreate", "Landroid/os/Bundle;"),
            entrance(CONFIRMATION, "onCreate", "Landroid/app/Dialog;", "Landroid/os/Bundle;"));
    private static final List<String> FIELDS = Arrays.asList(
            DexEntrances.fieldSignature(SHEET, DELEGATE_FIELD, "L" + DELEGATE.replace('.', '/') + ";"),
            DexEntrances.fieldSignature(CONFIRMATION, BUTTON_FIELD, "Landroid/widget/TextView;"));

    enum Match {
        EXACT, EXPERIMENTAL, NONE;

        boolean allowed(boolean compatibility) {
            return this == EXACT || (this == EXPERIMENTAL && compatibility);
        }
    }

    static Match resolve(int sdk, long version, String apkHash, String basePath,
                         String[] splits, boolean compatibility) {
        if (sdk == 36 && version == 202501210L && VERIFIED_APK.equals(apkHash)
                && (splits == null || splits.length == 0)) return Match.EXACT;
        if (!compatibility || basePath == null) return Match.NONE;

        String[] paths = new String[1 + (splits == null ? 0 : splits.length)];
        paths[0] = basePath;
        if (splits != null) System.arraycopy(splits, 0, paths, 1, splits.length);
        DexEntrances.Result result = DexEntrances.inspect(paths, METHODS, FIELDS);
        if (!result.complete || !result.found.containsAll(FIELDS)) return Match.NONE;
        for (Rule method : METHODS) {
            if (!result.found.contains(DexEntrances.signature(method))) return Match.NONE;
        }
        return Match.EXPERIMENTAL;
    }

    private static Rule entrance(String owner, String method, String... params) {
        return new Rule(PACKAGE, 202501210L, owner, method, "V", KEY, "", params);
    }

    private HdmiCompatibility() {}
}
