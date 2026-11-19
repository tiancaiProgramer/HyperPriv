package io.github.hyperpriv;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONObject;

/** Uses native power gestures and buffers only explicitly configured volume-key contexts. */
final class KeyHooks {
    private static final String TAG = "HyperPriv";
    private static final long MULTI_MS = 300;
    private static final long LONG_MS = 600;
    private final XposedInterface api;
    private final SharedPreferences prefs;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    private final Set<String> observations = new HashSet<>();
    private Handler handler;
    private final Method getActions;
    private final Method launchApp;
    private final Method record;
    private Object input;
    private final Method inputInstance;
    private final Method inject;
    private volatile boolean ready;
    private Context context;
    private String powerState = "unlocked";
    private long powerLastUp = -1000;
    private boolean powerReplay;
    private KeyBindings powerBindings = new KeyBindings("");
    private final List<KeyEvent> buffered = new ArrayList<>();
    private final Set<Integer> replayUntilUp = new HashSet<>();
    private final Set<Integer> physicalDown = new HashSet<>();
    private int volumeCode = -1;
    private String volumeState;
    private KeyBindings volumeBindings;
    private int taps;
    private boolean pressed;
    private boolean longHandled;
    private Runnable longTask;
    private Runnable multiTask;

    static void install(XposedInterface api, ClassLoader loader, SharedPreferences prefs) throws Exception {
        new KeyHooks(api, loader, prefs).install(loader);
    }

    private KeyHooks(XposedInterface api, ClassLoader loader, SharedPreferences prefs) throws Exception {
        this.api = api;
        this.prefs = prefs;
        preferenceListener = (store, key) -> {
            if ("key_actions".equals(key) || "key_bindings".equals(key) || "master".equals(key)) {
                api.log(Log.INFO, TAG, "key configuration updated enabled=" + enabled()
                        + " hasActions=" + !store.getString("key_bindings", "").isEmpty());
            }
        };
        Class<?> actions = Class.forName(KeyCompatibility.ACTIONS, false, loader);
        getActions = actions.getDeclaredMethod("getInstance", Context.class);
        launchApp = actions.getDeclaredMethod("launchApp", Intent.class);
        record = actions.getDeclaredMethod("launchSoundRecorder");
        getActions.setAccessible(true);
        launchApp.setAccessible(true);
        record.setAccessible(true);
        Class<?> manager = Class.forName("android.hardware.input.InputManager", false, loader);
        inputInstance = manager.getDeclaredMethod("getInstance");
        inputInstance.setAccessible(true);
        inject = manager.getDeclaredMethod("injectInputEvent", InputEvent.class, int.class);
        inject.setAccessible(true);
    }

    private void install(ClassLoader loader) throws Exception {
        Class<?> rule = Class.forName(KeyCompatibility.RULE, false, loader);
        Reflect.field(rule, "mKeyCode");
        Reflect.field(rule, "mContext");
        Method single = exact(rule, "onPress", void.class, long.class, int.class);
        Method multi = exact(rule, "onMultiPress", void.class, long.class, int.class, int.class);
        Method longer = exact(rule, "onLongPress", void.class, long.class);
        Method max = exact(rule, "getMaxMultiPressCount", int.class);
        Method supportLong = exact(rule, "supportLongPress", boolean.class);
        Class<?> inputService = Class.forName(KeyCompatibility.INPUT, false, loader);
        java.lang.reflect.Field contextField = Reflect.field(inputService, "mContext");
        if (contextField.getType() != Context.class) throw new NoSuchFieldException("input context");
        Method queue = exact(inputService, "interceptKeyBeforeQueueing", int.class, KeyEvent.class, int.class);

        api.hook(single).intercept(chain -> {
            if (powerRule(chain.getThisObject()) && runPower("single")) return null;
            return chain.proceed();
        });
        api.hook(multi).intercept(chain -> {
            int count = (Integer) chain.getArg(1);
            if (count >= 2 && count <= 3 && powerRule(chain.getThisObject())
                    && runPower(count == 2 ? "double" : "triple")) return null;
            return chain.proceed();
        });
        api.hook(longer).intercept(chain -> {
            if (powerRule(chain.getThisObject()) && runPower("long")) {
                try {
                    Object helper = Reflect.get(chain.getThisObject(), "mMiuiShortcutTriggerHelper");
                    if (helper != null) Reflect.call(helper, "notifyLongPressed", 26);
                } catch (Exception error) {
                    failure("long-press completion", error);
                }
                return null;
            }
            return chain.proceed();
        });
        api.hook(max).intercept(chain -> {
            int original = (Integer) chain.proceed();
            if (!powerRule(chain.getThisObject())) return original;
            synchronized (this) { return Math.max(original, powerBindings.maxCount(26, powerState)); }
        });
        api.hook(supportLong).intercept(chain -> {
            boolean original = (Boolean) chain.proceed();
            if (!powerRule(chain.getThisObject())) return original;
            synchronized (this) { return original || powerBindings.action(26, powerState, "long") != null; }
        });
        // This is the native input callback, before gesture handling and vendor policy dispatch.
        api.hook(queue).setPriority(XposedInterface.PRIORITY_HIGHEST).intercept(chain -> {
            KeyEvent event = (KeyEvent) chain.getArg(0);
            boolean bypass;
            synchronized (this) {
                bypass = !enabled() && volumeCode == -1 && replayUntilUp.isEmpty();
                if (bypass) physicalDown.clear();
            }
            // The system policy can cross threads. Never retain our gesture lock across it.
            if (bypass) return chain.proceed();
            observeInput(event);
            try {
                Context policyContext = (Context) contextField.get(chain.getThisObject());
                if (handle(policyContext, event)) return 0;
            } catch (Exception error) {
                failure("input", error);
                synchronized (this) { replayBuffered(); resetVolume(); }
            }
            return chain.proceed();
        });
        for (String cls : new String[]{"com.android.server.policy.SingleKeyGestureDetector",
                "com.android.server.policy.SingleKeyGestureDetector$KeyHandler"}) {
            for (Method method : Class.forName(cls, false, loader).getDeclaredMethods()) {
                if (method.getName().equals("interceptKeyDown") || method.getName().equals("interceptKeyUp")
                        || method.getName().equals("handleMessage")) api.deoptimize(method);
            }
        }
        ready = true;
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener);
        api.log(Log.INFO, TAG, "key actions ready input=" + KeyCompatibility.INPUT
                + " enabled=" + enabled());
    }

    private static Method exact(Class<?> cls, String name, Class<?> result, Class<?>... args)
            throws NoSuchMethodException {
        Method method = cls.getDeclaredMethod(name, args);
        if (method.getReturnType() != result) throw new NoSuchMethodException(name + " return type");
        method.setAccessible(true);
        return method;
    }

    private boolean enabled() {
        return ready && prefs.getBoolean("master", true) && prefs.getBoolean("key_actions", false)
                && (RuntimeProfile.knownSystem()
                    || prefs.getBoolean(Compatibility.KEY, Options.defaultValue(Compatibility.KEY)));
    }

    private synchronized void observeInput(KeyEvent event) {
        int code = event.getKeyCode();
        if ((code != 24 && code != 25 && code != 26)
                || event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) return;
        boolean active = enabled();
        if (observations.add("input:" + code + ":" + active)) {
            api.log(Log.INFO, TAG, "key input code=" + code + " enabled=" + active
                    + " flags=" + Integer.toHexString(event.getFlags()));
        }
    }

    private boolean powerRule(Object rule) {
        try {
            return enabled() && (Integer) Reflect.get(rule, "mKeyCode") == 26;
        } catch (Exception error) {
            failure("power rule", error);
            return false;
        }
    }

    private synchronized boolean runPower(String gesture) {
        return !powerReplay && context != null && enabled()
                && execute(powerBindings.action(26, powerState, gesture));
    }

    private synchronized boolean handle(Context policyContext, KeyEvent event) {
        context = policyContext;
        if (handler == null) handler = new Handler(Looper.getMainLooper());
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean up = event.getAction() == KeyEvent.ACTION_UP;
        boolean replay = (event.getFlags() & KeyEvent.FLAG_FALLBACK) != 0;
        if (code == 26) {
            if (down && event.getRepeatCount() == 0) {
                if (event.getEventTime() - powerLastUp > MULTI_MS) {
                    powerState = KeyBindings.state(context);
                    powerBindings = KeyBindings.read(prefs);
                }
                powerReplay = replay;
            }
            if (up) powerLastUp = event.getEventTime();
        }
        if (!ready || replay || (!down && !up)) return false;
        boolean anotherHeld = !physicalDown.isEmpty() && !physicalDown.contains(code);
        if (down) physicalDown.add(code);
        if (up) physicalDown.remove(code);
        if (replayUntilUp.contains(code)) {
            replayEvent(event);
            if (up) replayUntilUp.remove(code);
            return true;
        }
        if (volumeCode != -1 && code != volumeCode && down) {
            int pending = volumeCode;
            boolean held = pressed;
            replayBuffered();
            resetVolume();
            if (held) replayUntilUp.add(pending);
            replayUntilUp.add(code);
            replayEvent(event);
            return true;
        }
        if (code != 24 && code != 25) return false;
        if (volumeCode != -1 && code != volumeCode) return false;
        if (volumeCode == -1) {
            if (!enabled() || !down || anotherHeld || event.getRepeatCount() != 0) return false;
            String state = KeyBindings.state(context);
            KeyBindings bindings = KeyBindings.read(prefs);
            boolean configured = bindings.active(code, state);
            if (observations.add("context:" + code + ":" + state + ":" + configured)) {
                api.log(Log.INFO, TAG, "key context code=" + code + " state=" + state
                        + " configured=" + configured);
            }
            if (!configured) return false;
            volumeCode = code;
            volumeState = state;
            volumeBindings = bindings;
        }
        if (buffered.size() >= 96 || !enabled()) {
            boolean held = pressed;
            replayBuffered();
            resetVolume();
            if (held && !up) replayUntilUp.add(code);
            replayEvent(event);
            return true;
        }
        if (event.isCanceled()) {
            buffered.add(new KeyEvent(event));
            replayBuffered();
            resetVolume();
            return true;
        }
        if (down && event.getRepeatCount() != 0) {
            if (!longHandled) buffered.add(new KeyEvent(event));
            return true;
        }
        if (down) {
            handler.removeCallbacks(multiTask == null ? NOOP : multiTask);
            buffered.add(new KeyEvent(event));
            pressed = true;
            longTask = () -> {
                synchronized (KeyHooks.this) {
                    if (volumeCode != code || !pressed || longHandled) return;
                    if (enabled() && execute(volumeBindings.action(code, volumeState, "long"))) {
                        longHandled = true;
                        buffered.clear();
                    } else {
                        replayBuffered();
                        resetVolume();
                        replayUntilUp.add(code);
                    }
                }
            };
            handler.postDelayed(longTask, LONG_MS);
        } else if (up) {
            handler.removeCallbacks(longTask == null ? NOOP : longTask);
            pressed = false;
            if (longHandled) { resetVolume(); return true; }
            buffered.add(new KeyEvent(event));
            taps++;
            if (taps >= volumeBindings.maxCount(code, volumeState)) finishVolume();
            else {
                multiTask = () -> {
                    synchronized (KeyHooks.this) {
                        if (volumeCode == code && !pressed) finishVolume();
                    }
                };
                handler.postDelayed(multiTask, MULTI_MS);
            }
        }
        return true;
    }

    private static final Runnable NOOP = () -> {};

    private void finishVolume() {
        String gesture = taps == 1 ? "single" : taps == 2 ? "double" : "triple";
        if (!enabled() || !execute(volumeBindings.action(volumeCode, volumeState, gesture))) replayBuffered();
        resetVolume();
    }

    private void resetVolume() {
        if (longTask != null) handler.removeCallbacks(longTask);
        if (multiTask != null) handler.removeCallbacks(multiTask);
        buffered.clear();
        volumeCode = -1;
        taps = 0;
        pressed = false;
        longHandled = false;
        longTask = null;
        multiTask = null;
        volumeBindings = null;
    }

    private void replayBuffered() {
        for (KeyEvent event : buffered) replayEvent(event);
        buffered.clear();
    }

    private void replayEvent(KeyEvent source) {
        try {
            KeyEvent copy = KeyEvent.changeFlags(source, source.getFlags() | KeyEvent.FLAG_FALLBACK);
            if (input == null) input = inputInstance.invoke(null);
            if (!Boolean.TRUE.equals(inject.invoke(input, copy, 0))) {
                ready = false;
                api.log(Log.WARN, TAG, "key original replay rejected; key overrides suspended code=" + source.getKeyCode());
            }
        } catch (Exception error) { ready = false; failure("original replay", error); }
    }

    private boolean execute(JSONObject action) {
        if (action == null) return false;
        try {
            String type = action.optString("type");
            if (type.equals("none")) return true;
            Object utils = getActions.invoke(null, context);
            boolean success;
            if (type.equals("recorder")) {
                success = Boolean.TRUE.equals(record.invoke(utils));
            } else if (type.equals("intent")) {
                Intent intent = Intent.parseUri(action.getString("uri"), Intent.URI_INTENT_SCHEME);
                if (intent.getComponent() == null) return false;
                intent = KeyBindings.checkedIntent(context, intent, intent.getComponent().getPackageName());
                success = Boolean.TRUE.equals(launchApp.invoke(utils, intent));
            } else return false;
            api.log(Log.INFO, TAG, "key action " + type + " dispatched=" + success);
            return success;
        } catch (Exception error) {
            failure("action", error);
            return false;
        }
    }

    private void failure(String operation, Exception error) {
        api.log(Log.WARN, TAG, "key " + operation + " failed: " + error.getClass().getSimpleName());
    }
}
