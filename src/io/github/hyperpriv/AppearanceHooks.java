package io.github.hyperpriv;

import static io.github.hyperpriv.Appearance.Value.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.SurfaceControl;
import android.view.View;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

final class AppearanceHooks {
    private final XposedModule module;
    private final ClassLoader loader;
    private final SharedPreferences prefs;
    private final Context context;
    private final String key;
    private final boolean experimental;
    private final int[] config = new int[Appearance.Value.ALL.length];
    private boolean taskHandleInstalled;

    AppearanceHooks(XposedModule module, ClassLoader loader, SharedPreferences prefs, Context context,
            String key, AppearanceSupport.Match match) {
        this.module = module;
        this.loader = loader;
        this.prefs = prefs;
        this.context = context;
        this.key = key;
        experimental = match == AppearanceSupport.Match.COMPATIBLE;
        for (Appearance.Value value : Appearance.Value.ALL) config[value.ordinal()] = Settings.appearance(prefs, value);
    }

    void install(CameraCompatibility.Profile camera) {
        if (!active()) return;
        switch (key) {
            case Appearance.STATUS: new SystemBarAppearance(this).install(); break;
            case Appearance.GESTURE: installHandle(); break;
            case Appearance.ICONS: installHome(); break;
            case Appearance.WATERMARK: new CameraWatermarks(this, context).install(camera); break;
        }
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
        if (!custom(HOME_SCALE)) return;
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
            hook(iconSize, chain -> {
                int original = (Integer) chain.proceed();
                if (!active() || original <= 0) return original;
                int cell = cellWidth.getInt(chain.getThisObject());
                if (cell <= 0) return original;
                hit.run();
                return Math.max(1, Math.min(Math.max(original, cell * 9 / 10),
                        Math.round(original * value(HOME_SCALE) / 100f)));
            });
        });
    }

    private void installHandle() {
        if (!(custom(PILL_WIDTH) || custom(PILL_HEIGHT) || custom(PILL_RADIUS) || custom(PILL_Y)
                || custom(PILL_COLOR) || custom(PILL_OPACITY))) return;
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
                if (!active() || handleId == 0 || handle.getId() != handleId
                        || !handle.getClass().getName().equals(owner) || handle.getWidth() == 0
                        || handle.getHeight() == 0) return chain.proceed();
                Canvas canvas = (Canvas) chain.getArg(0);
                Paint paint = (Paint) paintField.get(handle);
                if (taskHandleInstalled && paint.getAlpha() == 0) return chain.proceed();
                float progress = pulse.getFloat(handle);
                boolean contracting = shrink.getBoolean(handle);
                float h = contracting ? 0 : extraHeight.getFloat(handle) * progress;
                float w = (contracting ? -shrinkWidth.getFloat(handle) : extraWidth.getFloat(handle)) * progress;
                float thickness = Math.max(1, (custom(PILL_HEIGHT) ? dp(handle, value(PILL_HEIGHT))
                        : radius.getFloat(handle) * 2) + h);
                float x = handle.getWidth() / 2f;
                float halfWidth = Math.max(1, x * value(PILL_WIDTH) / 100f + w);
                float y = Math.max(0, handle.getHeight() - bottom.getFloat(handle) - thickness + h / 2
                        - dp(handle, value(PILL_Y)));
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
            if (!active() || view.getWidth() == 0 || view.getHeight() == 0) return chain.proceed();
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
            String decor = prefix + "MiuiDecorationBottom";
            String resultClass = prefix + "MiuiDecorationBase$RelayoutResult";
            Executable layout = method(decor, "updateViews", "V",
                    "L" + resultClass.replace('.', '/') + ";", "Landroid/view/SurfaceControl$Transaction;");
            Field surface = field(decor, "mCaptionContainerSurface", SurfaceControl.class);
            Field mode = field(decor, "mWindowingMode", int.class);
            Field x = field(resultClass, "mCaptionX", int.class);
            Field y = field(resultClass, "mCaptionY", int.class);
            Field root = Reflect.field(Class.forName(resultClass, false, loader), "mRoot");
            hook(layout, chain -> {
                Object result = chain.proceed();
                Object decoration = chain.getThisObject(), layoutResult = chain.getArg(0);
                Object view = root.get(layoutResult);
                if (!active() || mode.getInt(decoration) != 1 || !(view instanceof View)) return result;
                SurfaceControl layer = (SurfaceControl) surface.get(decoration);
                if (layer != null && layer.isValid()) {
                    SurfaceControl.Transaction transaction = (SurfaceControl.Transaction) chain.getArg(1);
                    transaction.setPosition(layer, x.getInt(layoutResult),
                            y.getInt(layoutResult) - dp((View) view, value(PILL_Y)));
                }
                return result;
            });
        });
    }
}
