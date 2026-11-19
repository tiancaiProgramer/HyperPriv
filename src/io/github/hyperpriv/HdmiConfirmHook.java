package io.github.hyperpriv;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

final class HdmiConfirmHook {
    private static final String TAG = "HyperPriv";
    private static final String MIRROR_CLICK =
            "com.android.systemui.display.ui.viewmodel.ConnectingDisplayViewModel$showDialog$1";

    static void install(XposedInterface api, ClassLoader loader, SharedPreferences prefs,
                        HdmiCompatibility.Match match)
            throws ReflectiveOperationException {
        Class<? extends Dialog> sheetClass =
                Class.forName(HdmiCompatibility.SHEET, false, loader).asSubclass(Dialog.class);
        Class<?> confirmationClass = Class.forName(HdmiCompatibility.CONFIRMATION, false, loader);
        Field delegateField = sheetClass.getDeclaredField(HdmiCompatibility.DELEGATE_FIELD);
        if (!delegateField.getType().getName().equals(HdmiCompatibility.DELEGATE)
                || !delegateField.getType().isAssignableFrom(confirmationClass)
                || Modifier.isStatic(delegateField.getModifiers())) {
            throw new NoSuchFieldException(HdmiCompatibility.DELEGATE_FIELD);
        }
        boolean exact = match == HdmiCompatibility.Match.EXACT;
        Class<?> clickClass = exact ? Class.forName(MIRROR_CLICK, false, loader) : null;
        Field positiveField = confirmationClass.getDeclaredField(
                exact ? "onStartMirroringClickListener" : HdmiCompatibility.BUTTON_FIELD);
        if (positiveField.getType() != (exact ? View.OnClickListener.class : TextView.class)
                || Modifier.isStatic(positiveField.getModifiers())) {
            throw new NoSuchFieldException(positiveField.getName());
        }
        delegateField.setAccessible(true);
        positiveField.setAccessible(true);

        // On the audited SystemUI build the view model invokes Dialog.show() on this bottom sheet.
        api.hook(Dialog.class.getDeclaredMethod("show")).intercept(chain -> {
            Object dialog = chain.getThisObject();
            if (dialog == null || dialog.getClass() != sheetClass) return chain.proceed();
            try {
                Object delegate = delegateField.get(dialog);
                if (delegate != null && delegate.getClass() == confirmationClass
                        && (exact || prefs.getBoolean(Compatibility.KEY, Options.defaultValue(Compatibility.KEY)))
                        && prefs.getBoolean("master", true)
                        && prefs.getBoolean(HdmiCompatibility.KEY, false)) {
                    boolean confirmed = exact
                            ? clickExact(positiveField.get(delegate), clickClass)
                            : clickMirrorButton((Dialog) dialog, delegate, positiveField);
                    if (confirmed) {
                        Log.i(TAG, "hit hdmi_auto_confirm");
                        return null;
                    }
                    api.log(Log.INFO, TAG, "SystemUI HDMI confirmation button not matched");
                }
            } catch (Exception error) {
                api.log(Log.INFO, TAG, "HDMI confirmation unavailable: " + error.getClass().getSimpleName());
            }
            return chain.proceed();
        });
    }

    private static boolean clickExact(Object listener, Class<?> clickClass) {
        if (listener == null || listener.getClass() != clickClass) return false;
        // The audited listener does not read the View argument.
        ((View.OnClickListener) listener).onClick(null);
        return true;
    }

    private static boolean clickMirrorButton(Dialog dialog, Object delegate, Field buttonField)
            throws IllegalAccessException {
        dialog.create();
        TextView button = (TextView) buttonField.get(delegate);
        if (button == null || button.getId() == View.NO_ID || !button.isEnabled()) return false;
        android.content.res.Resources resources = button.getResources();
        int label = resources.getIdentifier("mirror_display", "string", HdmiCompatibility.PACKAGE);
        return label != 0 && "enable_display".equals(resources.getResourceEntryName(button.getId()))
                && resources.getText(label).toString().contentEquals(button.getText())
                && button.callOnClick();
    }

    private HdmiConfirmHook() {}
}
