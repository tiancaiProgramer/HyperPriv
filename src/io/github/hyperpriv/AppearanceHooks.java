package io.github.hyperpriv;

import static io.github.hyperpriv.Appearance.Value.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.view.SurfaceControl;
import android.view.View;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

final class AppearanceHooks {
    private final XposedModule module;
    private final ClassLoader loader;
    private final SharedPreferences prefs;
    private final Context context;
    private final String key;
    private final boolean experimental;
    private final AppearanceSupport.StatusMatch status;
    private volatile int[] config;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    private final WeakHashMap<View, Boolean> views = new WeakHashMap<>();
    private final List<Runnable> refreshActions = new ArrayList<>();
    private int appliedRequest;
    private boolean taskHandleInstalled;

    AppearanceHooks(XposedModule module, ClassLoader loader, SharedPreferences prefs, Context context,
            String key, AppearanceSupport.Match match, AppearanceSupport.StatusMatch status) {
        this.module = module;
        this.loader = loader;
        this.prefs = prefs;
        this.context = context;
        this.key = key;
        experimental = match == AppearanceSupport.Match.COMPATIBLE;
        this.status = status;
        config = readConfig();
        appliedRequest = prefs.getInt(Appearance.REFRESH_REQUEST, 0);
        preferenceListener = (store, changed) -> {
            if (changed == null || Appearance.REFRESH_REQUEST.equals(changed))
                context.getMainExecutor().execute(this::refresh);
        };
    }

    private int[] readConfig() {
        int[] values = new int[Appearance.Value.ALL.length];
        for (Appearance.Value value : Appearance.Value.ALL)
            values[value.ordinal()] = supports(value) ? Settings.appearance(prefs, value, context) : value.initial;
        if (status != null && !status.supportsBatteryStyle(values[BATTERY.ordinal()]))
            values[BATTERY.ordinal()] = BATTERY.initial;
        return values;
    }

    private void refresh() {
        int request = prefs.getInt(Appearance.REFRESH_REQUEST, 0);
        if (appliedRequest == request) return;
        appliedRequest = request;
        int[] next = readConfig();
        boolean needsRestart = false;
        boolean batteryOverride = custom(BATTERY) || custom(CHARGE_MARK);
        boolean changesDuo = (next[SIGNAL.ordinal()] == Appearance.DUO_SIGNAL_STYLE)
                != (value(SIGNAL) == Appearance.DUO_SIGNAL_STYLE);
        for (Appearance.Value value : Appearance.Value.ALL) {
            int index = value.ordinal();
            if (next[index] == config[index] || value == HOME_SCALE
                    || Appearance.GESTURE.equals(key) != value.name().startsWith("PILL_")) continue;
            if (Appearance.requiresUiRestart(value)
                    || (value == SIGNAL && changesDuo && batteryOverride)) {
                next[index] = config[index];
                needsRestart = true;
            }
        }
        config = next;
        for (Runnable action : refreshActions) runRefresh(action);
        List<View> attached;
        synchronized (views) {
            attached = new ArrayList<>(views.keySet());
        }
        for (View view : attached) {
            refreshView(view, () -> {
                view.requestLayout();
                view.invalidate();
            });
        }
        log("reloaded " + key + " request=" + request + (needsRestart ? " · restart pending" : ""));
    }

    boolean supports(Appearance.Value value) { return status == null || status.supports(value); }
    boolean hasNativeBatteryStyles() { return status != null && NativeBatteryStyle.available(status.match); }
    void watch(View view) {
        synchronized (views) {
            views.put(view, Boolean.TRUE);
        }
    }
    void onRefresh(Runnable action) { refreshActions.add(action); }

    void refreshView(View view, Runnable action) {
        Handler handler = view == null ? null : view.getHandler();
        if (handler != null) handler.post(() -> {
            if (view.isAttachedToWindow() && view.getHandler() == handler) runRefresh(action);
        });
    }

    private void runRefresh(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError error) {
            log("refresh failed " + key + " " + error.getClass().getSimpleName());
        }
    }

    void install(CameraCompatibility.Profile camera) {
        boolean live = Appearance.SYSTEM_UI.equals(Appearance.target(key));
        if (!live && !active()) return;
        switch (key) {
            case Appearance.STATUS: new SystemBarAppearance(this).install(); break;
            case Appearance.GESTURE: installHandle(); break;
            case Appearance.ICONS: installHome(); break;
            case Appearance.WATERMARK: new CameraWatermarks(this, context).install(camera); break;
        }
        if (live) prefs.registerOnSharedPreferenceChangeListener(preferenceListener);
    }

    interface Installer { void install() throws Exception; }
    void register(String feature, Installer installer) {
        try {
            installer.install();
            log("ready " + feature + (experimental ? " · compatible" : ""));
        } catch (Exception | LinkageError error) {
            log("unmatched " + feature + " " + error.getClass().getSimpleName());
        }
    }

    boolean active() {
        try {
            return prefs.getBoolean("master", true) && prefs.getBoolean(key, false)
                    && (!experimental || prefs.getBoolean(Compatibility.KEY, Options.defaultValue(Compatibility.KEY)));
        } catch (RuntimeException error) { return false; }
    }
    int value(Appearance.Value value) { return config[value.ordinal()]; }
    boolean custom(Appearance.Value value) { return value(value) != value.initial; }
    Context context() { return context; }
    int systemUiId(String id) { return context.getResources().getIdentifier(id, "id", Appearance.SYSTEM_UI); }
    Executable method(String name, String method, String result, String... args) throws Exception {
        return Reflect.executable(AppearanceSupport.method(key, name, method, result, args), loader);
    }
    Field field(String owner, String name, Class<?> type) throws ReflectiveOperationException {
        Field field = Reflect.field(Class.forName(owner, false, loader), name);
        if (field.getType() != type) throw new NoSuchFieldException(name);
        return field;
    }
    void hook(Executable method, io.github.libxposed.api.XposedInterface.Hooker hooker) {
        module.hook(method).intercept(hooker);
    }
    Runnable firstHit(String feature) {
        AtomicBoolean first = new AtomicBoolean(true);
        return () -> { if (first.getAndSet(false)) log("hit " + feature); };
    }
    private void log(String message) {
        android.util.Log.i("HyperPriv", "appearance " + message);
    }
    static float dp(View view, float value) { return value * view.getResources().getDisplayMetrics().density; }

    private void installHome() {
        register("home_icons", () -> {
            String owner = AppearanceSupport.HOME_CONFIG;
            Executable iconSize;
            try { iconSize = method(owner, "getIconSize", "I"); }
            catch (ReflectiveOperationException error) {
                owner = AppearanceSupport.LEGACY_HOME_CONFIG;
                iconSize = method(owner, "getIconSize", "I");
            }
            Field cellWidth = field(owner, "mCellWidth", int.class);
            Runnable hit = firstHit("home_icons");
            Map<Long, String> screens = new ConcurrentHashMap<>();
            Map<String, Integer> measuredLimits = new ConcurrentHashMap<>();
            java.util.concurrent.Executor reports = Executors.newSingleThreadExecutor();
            hook(iconSize, chain -> {
                int original = (Integer) chain.proceed();
                if (!active() || original <= 0) return original;
                int cell = cellWidth.getInt(chain.getThisObject());
                if (cell <= 0) return original;
                int cap = Appearance.homeScaleLimit(original, cell);
                Configuration configuration = context.getResources().getConfiguration();
                long profile = ((long) configuration.densityDpi << 32)
                        | ((long) configuration.screenWidthDp << 16) | configuration.screenHeightDp;
                String screen = screens.computeIfAbsent(profile, ignored -> HomeScaleProvider.profile(context));
                Integer earlier = screen == null ? null : measuredLimits.get(screen);
                if (screen != null && (earlier == null || cap < earlier)) {
                    measuredLimits.merge(screen, cap, Math::min);
                    reports.execute(() -> {
                        if (!Integer.valueOf(cap).equals(measuredLimits.get(screen))) return;
                        try { HomeScaleProvider.report(context, screen, cap); }
                        catch (RuntimeException error) { log("home grid report unavailable " + error.getClass().getSimpleName()); }
                    });
                }
                hit.run();
                return Math.max(1, Math.round(original * Math.min(value(HOME_SCALE), cap) / 100f));
            });
        });
    }

    private boolean customHandle() {
        return active() && (custom(PILL_WIDTH) || custom(PILL_HEIGHT) || custom(PILL_RADIUS)
                || custom(PILL_Y) || custom(PILL_COLOR) || custom(PILL_OPACITY));
    }

    private void installHandle() {
        register("gesture_task_handle", this::installTaskHandle);
        register("gesture_handle", () -> {
            String owner = AppearanceSupport.GESTURE;
            Executable draw = method(owner, "onDraw", "V", "Landroid/graphics/Canvas;");
            Field radius = field(owner, "mRadius", float.class);
            Field bottom = field(owner, "mBottom", float.class);
            Field paintField = field(owner, "mPaint", Paint.class);
            Field pulse = field(owner, "mPulseAnimationProgress", float.class);
            Field extraHeight = field(owner, "mAdditionalHeightForAnimation", float.class);
            Field extraWidth = field(owner, "mAdditionalWidthForAnimation", float.class);
            Field shrink = field(owner, "mShrink", boolean.class);
            Field shrinkWidth = field(owner, "mShrinkWidthForAnimation", float.class);
            int handleId = context.getResources().getIdentifier("home_handle", "id", Appearance.SYSTEM_UI);
            Runnable hit = firstHit("gesture_handle");
            hook(draw, chain -> {
                View handle = (View) chain.getThisObject();
                if (handleId == 0 || handle.getId() != handleId || !handle.getClass().getName().equals(owner))
                    return chain.proceed();
                watch(handle);
                if (!customHandle() || handle.getWidth() == 0 || handle.getHeight() == 0) return chain.proceed();
                Canvas canvas = (Canvas) chain.getArg(0);
                Paint paint = (Paint) paintField.get(handle);
                if (taskHandleInstalled && paint.getAlpha() == 0) return chain.proceed();
                float progress = pulse.getFloat(handle);
                boolean contracting = shrink.getBoolean(handle);
                float h = contracting ? 0 : extraHeight.getFloat(handle) * progress;
                float w = (contracting ? -shrinkWidth.getFloat(handle) : extraWidth.getFloat(handle)) * progress;
                float thickness = Math.min(handle.getHeight(), Math.max(1,
                        (custom(PILL_HEIGHT) ? dp(handle, value(PILL_HEIGHT)) : radius.getFloat(handle) * 2) + h));
                float x = handle.getWidth() / 2f;
                float halfWidth = Math.max(1, x * value(PILL_WIDTH) / 100f + w);
                float baseline = handle.getHeight() - bottom.getFloat(handle) - thickness + h / 2;
                float y = Appearance.pillTop(baseline, thickness, handle.getHeight(), value(PILL_Y), dp(handle, 1));
                float r = custom(PILL_RADIUS) ? Math.min(thickness / 2, dp(handle, value(PILL_RADIUS))) : thickness / 2;
                int color = paint.getColor();
                paint.setColor(pillColor(color));
                paint.setAlpha(pillAlpha(paint.getAlpha()));
                try { canvas.drawRoundRect(x - halfWidth, y, x + halfWidth, y + thickness, r, r, paint); }
                finally { paint.setColor(color); }
                hit.run();
                return null;
            });
        });
    }

    private int pillColor(int current) {
        switch (value(PILL_COLOR)) {
            case 1: return Color.WHITE;
            case 2: return Color.BLACK;
            case 3: return 0xff5c91ff;
            case 4: return 0xffff8c45;
            default: return current;
        }
    }

    private int pillAlpha(int original) {
        return Math.round(Math.max(0, Math.min(255, original)) * value(PILL_OPACITY) / 100f);
    }

    private void installTaskHandle() throws Exception {
        String prefix = AppearanceSupport.MIUI_DECORATION;
        String owner = prefix + "MiuiDecorationBottomView";
        Executable draw = method(owner, "onDraw", "V", "Landroid/graphics/Canvas;");
        Field paintField = field(owner, "mPaint", Paint.class);
        Field radiusX = field(owner, "mRadiusX", float.class);
        Field radiusY = field(owner, "mRadiusY", float.class);
        Class<?> folmeType = Class.forName(prefix + "MiuiDecorationFolmeControl", false, loader);
        Field folme = field(owner, "mFolmeControl", folmeType);
        Method focusAlpha = (Method) method(folmeType.getName(), "getAlphaFocus", "F");
        Runnable hit = firstHit("gesture_task_handle");
        hook(draw, chain -> {
            View view = (View) chain.getThisObject();
            watch(view);
            if (!customHandle() || view.getWidth() == 0 || view.getHeight() == 0) return chain.proceed();
            Canvas canvas = (Canvas) chain.getArg(0);
            float width = view.getWidth() * value(PILL_WIDTH) / 100f;
            float height = view.getHeight();
            float x = (view.getWidth() - width) / 2;
            float rx = custom(PILL_RADIUS) ? dp(view, value(PILL_RADIUS)) : radiusX.getFloat(view);
            float ry = custom(PILL_RADIUS) ? dp(view, value(PILL_RADIUS)) : radiusY.getFloat(view);
            Paint paint = (Paint) paintField.get(view);
            int original = paint.getColor();
            int alpha = Math.round((Float) focusAlpha.invoke(folme.get(view)));
            paint.setColor(pillColor(original));
            paint.setAlpha(pillAlpha(alpha));
            try {
                canvas.drawRoundRect(x, 0, x + width, height,
                        Math.min(width / 2, rx), Math.min(height / 2, ry), paint);
            } finally { paint.setColor(original); }
            hit.run();
            return null;
        });
        taskHandleInstalled = true;
        if (custom(PILL_HEIGHT)) register("gesture_task_thickness", () -> {
            Executable measure = method(prefix + "MiuiDecorationBottomViewHost", "getSurfaceHeight", "F");
            Field hostContext = field(prefix + "MiuiDecorationBottomViewHost", "mContext", Context.class);
            hook(measure, chain -> {
                if (!active()) return chain.proceed();
                Context viewContext = (Context) hostContext.get(chain.getThisObject());
                return viewContext.getResources().getDisplayMetrics().density * value(PILL_HEIGHT);
            });
        });
        if (custom(PILL_Y)) register("gesture_task_offset", () -> {
            try {
                installTaskPillOffset(prefix);
                module.log(android.util.Log.INFO, "HyperPriv", "gesture task position ready, offset=" + value(PILL_Y));
            } catch (ReflectiveOperationException error) {
                module.log(android.util.Log.WARN, "HyperPriv", "gesture task position entry unavailable", error);
                throw error;
            }
        });
    }

    private void installTaskPillOffset(String prefix) throws Exception {
        String decor = prefix + "MiuiDecorationBottom";
        String resultClass = prefix + "MiuiDecorationBase$RelayoutResult";
        Executable layout = method(decor, "updateViews", "V",
                "L" + resultClass.replace('.', '/') + ";", "Landroid/view/SurfaceControl$Transaction;");
        Field surface = field(decor, "mCaptionContainerSurface", SurfaceControl.class);
        Field mode = field(decor, "mWindowingMode", int.class);
        Field x = field(resultClass, "mCaptionX", int.class);
        Field y = field(resultClass, "mCaptionY", int.class);
        Field root = Reflect.field(Class.forName(resultClass, false, loader), "mRoot");
        Field taskBounds = field(decor, "mTaskBounds", Rect.class);
        Field scale = field(decor, "mScale", float.class);
        Field viewContext = field(decor, "mContext", Context.class);
        String hostClass = prefix + "MiuiDecorationRootViewHost";
        Field viewHost = field(decor, "mMiuiDecorationRootViewHost", Class.forName(hostClass, false, loader));
        Method surfaceHeight = (Method) method(hostClass, "getSurfaceHeight", "F");
        Runnable layoutHit = firstHit("gesture_task_offset");
        hook(layout, chain -> {
            Object result = chain.proceed();
            Object decoration = chain.getThisObject(), layoutResult = chain.getArg(0);
            if (!active() || mode.getInt(decoration) != 1 || root.get(layoutResult) == null) return result;
            Object host = viewHost.get(decoration);
            float surfaceScale = scale.getFloat(decoration);
            if (host == null || !(surfaceScale > 0) || !Float.isFinite(surfaceScale)) return result;
            float height = ((Float) surfaceHeight.invoke(host)).intValue() / surfaceScale;
            if (positionTaskPill((SurfaceControl.Transaction) chain.getArg(1),
                    (SurfaceControl) surface.get(decoration), (Rect) taskBounds.get(decoration),
                    x.getInt(layoutResult), y.getInt(layoutResult), height, (Context) viewContext.get(decoration)))
                layoutHit.run();
            return result;
        });
        register("gesture_task_resize_offset", () -> {
            Executable resize = method(decor, "updateBottomCaptionSurface", "V",
                    "Landroid/view/SurfaceControl$Transaction;", "Landroid/graphics/Rect;", "F", "F");
            Method captionHeight = (Method) method(decor, "getBottomCaptionHeight", "I");
            Method surfaceWidth = (Method) method(hostClass, "getSurfaceWidth", "F");
            Field currentLayout = field(decor, "mResult", Class.forName(resultClass, false, loader));
            Runnable resizeHit = firstHit("gesture_task_resize_offset");
            hook(resize, chain -> {
                Object decoration = chain.getThisObject();
                float previousScale = scale.getFloat(decoration);
                Object result = chain.proceed();
                float requestedScale = (Float) chain.getArg(2);
                float surfaceScale = (Float) chain.getArg(3);
                if (!active() || mode.getInt(decoration) != 1 || Math.abs(previousScale - requestedScale) < 1e-6f
                        || !(surfaceScale > 0) || !Float.isFinite(surfaceScale)) return result;
                Object host = viewHost.get(decoration), layoutResult = currentLayout.get(decoration);
                Rect bounds = (Rect) chain.getArg(1);
                if (host == null || layoutResult == null || root.get(layoutResult) == null || bounds == null) return result;
                float rawHeight = (Float) surfaceHeight.invoke(host);
                float originalX = (bounds.width() - (Float) surfaceWidth.invoke(host)) / 2;
                float originalY = bounds.height() - ((Integer) captionHeight.invoke(decoration) + rawHeight / surfaceScale) / 2;
                if (positionTaskPill((SurfaceControl.Transaction) chain.getArg(0),
                        (SurfaceControl) surface.get(decoration), bounds, originalX, originalY,
                        (int) rawHeight / surfaceScale, (Context) viewContext.get(decoration))) resizeHit.run();
                return result;
            });
        });
        register("gesture_home_offset", () -> {
            String home = prefix + "MiuiDecorationHomeBottom";
            Executable homeLayout = method(home, "updateViews", "V",
                    "L" + resultClass.replace('.', '/') + ";", "Landroid/view/SurfaceControl$Transaction;");
            Executable homeAnimation = method(prefix + "MiuiDecorationHomeBottomAnim", "startHomeBottomBarAnim",
                    "V", "Landroid/view/SurfaceControl;", "F", "F", "F", "F", "Z");
            ThreadLocal<Object> homeDecoration = new ThreadLocal<>();
            Runnable homeHit = firstHit("gesture_home_offset");
            hook(homeAnimation, chain -> {
                Object decoration = homeDecoration.get();
                if (decoration == null || !active()) return chain.proceed();
                Rect bounds = (Rect) taskBounds.get(decoration);
                Context context = (Context) viewContext.get(decoration);
                float height = ((Float) chain.getArg(4)).intValue();
                if (bounds == null || context == null || !(height > 0 && height <= bounds.height()))
                    return chain.proceed();
                float density = context.getResources().getDisplayMetrics().density;
                float targetY = Appearance.pillTop((Float) chain.getArg(2), height, bounds.height(), value(PILL_Y), density);
                homeHit.run();
                return chain.proceed(new Object[]{chain.getArg(0), chain.getArg(1), targetY,
                        chain.getArg(3), chain.getArg(4), chain.getArg(5)});
            });
            hook(homeLayout, chain -> {
                Object previous = homeDecoration.get();
                homeDecoration.set(chain.getThisObject());
                try {
                    return chain.proceed();
                } finally {
                    if (previous == null) homeDecoration.remove(); else homeDecoration.set(previous);
                }
            });
        });
    }

    private boolean positionTaskPill(SurfaceControl.Transaction transaction, SurfaceControl surface, Rect bounds,
            float x, float origin, float height, Context viewContext) {
        if (surface == null || !surface.isValid() || bounds == null || !(height > 0 && height <= bounds.height())
                || viewContext == null) return false;
        float density = viewContext.getResources().getDisplayMetrics().density;
        transaction.setPosition(surface, x, Appearance.pillTop(origin, height, bounds.height(), value(PILL_Y), density));
        return true;
    }
}
