package io.github.hyperpriv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputEvent;
import android.view.KeyEvent;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/** Isolated Android process. Replayed events are captured, never sent to the phone. */
public final class KeyActionsCheck {
    private static final Map<String,Object> values = new HashMap<>();
    private static Method handle;
    private static Context context;
    private static long clock;
    private static final Map<java.lang.reflect.Executable,XposedInterface.Hooker> hooks=new HashMap<>();

    public static final class CapturedInput {
        final List<KeyEvent> events = new ArrayList<>();
        public boolean inject(InputEvent event, int mode) {
            events.add(new KeyEvent((KeyEvent) event));
            return true;
        }
    }

    private static void require(boolean condition,String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void set(Object target,String name,Object value) throws Exception {
        Field field=target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target,value);
    }

    private static Object get(Object target,String name) throws Exception {
        Field field=target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object engine(CapturedInput input,String gesture,JSONObject action) throws Exception {
        KeyBindings bindings=new KeyBindings("");
        for (String state:KeyBindings.STATES) bindings.put(25,state,gesture,action);
        hooks.clear();
        values.clear();
        values.put("master",true);
        values.put("key_actions",true);
        values.put(Compatibility.KEY,true);
        values.put("key_bindings",bindings.encode());
        SharedPreferences preferences=(SharedPreferences)Proxy.newProxyInstance(
                KeyActionsCheck.class.getClassLoader(),new Class[]{SharedPreferences.class},(proxy,method,args)->{
                    if (method.getName().equals("getBoolean") || method.getName().equals("getString"))
                        return values.getOrDefault(args[0],args[1]);
                    if (method.getName().equals("getAll")) return new HashMap<>(values);
                    if (method.getName().equals("registerOnSharedPreferenceChangeListener")) return null;
                    throw new UnsupportedOperationException(method.getName());
                });
        XposedInterface api=(XposedInterface)Proxy.newProxyInstance(
                KeyActionsCheck.class.getClassLoader(),new Class[]{XposedInterface.class},(proxy,method,args)->{
                    if (method.getName().equals("log")) return null;
                    if (method.getName().equals("deoptimize")) return true;
                    if (method.getName().equals("hook")) {
                        java.lang.reflect.Executable executable=(java.lang.reflect.Executable)args[0];
                        return Proxy.newProxyInstance(KeyActionsCheck.class.getClassLoader(),
                                new Class[]{XposedInterface.HookBuilder.class},(builder,bm,ba)->{
                                    if (bm.getName().equals("intercept")) {
                                        hooks.put(executable,(XposedInterface.Hooker)ba[0]);
                                        return Proxy.newProxyInstance(KeyActionsCheck.class.getClassLoader(),
                                                new Class[]{XposedInterface.HookHandle.class},(h,hm,ha)->null);
                                    }
                                    return builder;
                                });
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        Constructor<KeyHooks> constructor=KeyHooks.class.getDeclaredConstructor(
                XposedInterface.class,ClassLoader.class,SharedPreferences.class);
        constructor.setAccessible(true);
        Object target=constructor.newInstance(api,KeyActionsCheck.class.getClassLoader(),preferences);
        set(target,"input",input);
        set(target,"inject",CapturedInput.class.getMethod("inject",InputEvent.class,int.class));
        set(target,"ready",true);
        return target;
    }

    private static boolean event(Object engine,int action,int code,int repeat,int flags) throws Exception {
        clock+=20;
        KeyEvent event=new KeyEvent(clock,clock,action,code,repeat,0,-1,0,flags);
        return (Boolean)handle.invoke(engine,context,event);
    }

    private static void tap(Object engine) throws Exception {
        require(event(engine,KeyEvent.ACTION_DOWN,25,0,0),"configured key down was not captured");
        require(event(engine,KeyEvent.ACTION_UP,25,0,0),"configured key up was not captured");
    }

    private static void runTimer(Object engine,String name) throws Exception {
        Runnable timer=(Runnable)get(engine,name);
        require(timer!=null,"timer missing "+name);
        timer.run();
    }

    private static void nativeCallbackCheck() throws Throwable {
        CapturedInput input=new CapturedInput();
        Object engine=engine(input,"single",KeyBindings.make("none","不执行动作",null));
        Method install=KeyHooks.class.getDeclaredMethod("install",ClassLoader.class);
        install.setAccessible(true);
        install.invoke(engine,KeyActionsCheck.class.getClassLoader());
        Class<?> service=Class.forName("com.android.server.input.InputManagerService");
        Method queue=service.getDeclaredMethod("interceptKeyBeforeQueueing",KeyEvent.class,int.class);
        XposedInterface.Hooker callback=hooks.get(queue);
        require(callback!=null,"native input callback was not hooked");
        Object unsafe=Class.forName("sun.misc.Unsafe").getMethod("getUnsafe").invoke(null);
        Object receiver=unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,service);
        Field contextField=service.getDeclaredField("mContext");
        contextField.setAccessible(true);
        contextField.set(receiver,context);
        int[] originals={0};
        for (boolean enabled:new boolean[]{false,true}) {
            values.put("key_actions",enabled);
            for (int action:new int[]{KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP}) {
                clock+=30;
                KeyEvent key=new KeyEvent(clock,clock,action,25,0);
                Object result=callback.intercept((XposedInterface.Chain)Proxy.newProxyInstance(
                        KeyActionsCheck.class.getClassLoader(),new Class[]{XposedInterface.Chain.class},
                        (proxy,method,args)->{
                            if (method.getName().equals("getArg")) return (Integer)args[0]==0?key:0;
                            if (method.getName().equals("getThisObject")) return receiver;
                            if (method.getName().equals("getExecutable")) return queue;
                            if (method.getName().equals("proceed")) {
                                require(!Thread.holdsLock(engine),"original input ran under the gesture monitor");
                                originals[0]++;return 1;
                            }
                            throw new UnsupportedOperationException(method.getName());
                        }));
                require(Integer.valueOf(enabled?0:1).equals(result),"native callback gate/consume failed");
            }
        }
        require(originals[0]==2,"disabled native callback did not preserve both original events");
        require(input.events.isEmpty(),"configured native callback replayed consumed keys");
    }

    public static void main(String[] args) throws Throwable {
        if (Looper.getMainLooper()==null) Looper.prepareMainLooper();
        Class<?> thread=Class.forName("android.app.ActivityThread");
        Object instance=thread.getMethod("systemMain").invoke(null);
        context=(Context)thread.getMethod("getSystemContext").invoke(instance);
        RuntimeProfile.initializeSystem();
        clock=SystemClock.uptimeMillis();
        handle=KeyHooks.class.getDeclaredMethod("handle",Context.class,KeyEvent.class);
        handle.setAccessible(true);
        require(KeyCompatibility.matched(),"device method/field profile did not match");
        KeyBindings saved=new KeyBindings("");
        JSONObject none=KeyBindings.make("none","不执行动作",null);
        saved.put(26,"off","triple",none);
        KeyBindings restored=new KeyBindings(saved.encode());
        require(restored.maxCount(26,"off")==3,"triple mapping was lost");
        require(restored.action(26,"locked","triple")==null,"state leaked");
        restored.put(26,"off","triple",null);
        require(!restored.active(26,"off"),"follow-system did not remove replacement");
        require(!new KeyBindings("invalid").active(25,"off"),"invalid configuration became active");

        CapturedInput input=new CapturedInput();
        Object engine=engine(input,"double",none);
        tap(engine);
        runTimer(engine,"multiTask");
        require(input.events.size()==2,"unbound single did not replay exactly one pair");
        require((input.events.get(0).getFlags()&KeyEvent.FLAG_FALLBACK)!=0,"replay marker missing");

        input=new CapturedInput();engine=engine(input,"double",none);
        tap(engine);tap(engine);
        require(input.events.isEmpty(),"custom double replayed native action");
        require((Integer)get(engine,"volumeCode")==-1,"double gesture did not reset");

        input=new CapturedInput();engine=engine(input,"triple",none);
        tap(engine);tap(engine);
        require(input.events.isEmpty(),"triple fired early");
        tap(engine);
        require(input.events.isEmpty() && (Integer)get(engine,"volumeCode")==-1,"triple did not finish");

        input=new CapturedInput();engine=engine(input,"long",none);
        event(engine,KeyEvent.ACTION_DOWN,25,0,0);
        runTimer(engine,"longTask");
        event(engine,KeyEvent.ACTION_DOWN,25,1,0);
        event(engine,KeyEvent.ACTION_UP,25,0,0);
        require(input.events.isEmpty(),"long press emitted another action");

        input=new CapturedInput();engine=engine(input,"double",none);
        event(engine,KeyEvent.ACTION_DOWN,25,0,0);
        runTimer(engine,"longTask");
        event(engine,KeyEvent.ACTION_UP,25,0,0);
        require(input.events.size()==2,"unbound held key did not replay down/up");

        input=new CapturedInput();engine=engine(input,"single",none);
        values.put("master",false);
        require(!event(engine,KeyEvent.ACTION_DOWN,25,0,0),"master off captured key");
        require(!event(engine,KeyEvent.ACTION_UP,25,0,0),"master off captured release");

        input=new CapturedInput();engine=engine(input,"double",none);
        event(engine,KeyEvent.ACTION_DOWN,25,0,0);
        values.put("key_actions",false);
        event(engine,KeyEvent.ACTION_UP,25,0,0);
        require(input.events.size()==2,"disable mid-gesture lost original events");

        input=new CapturedInput();engine=engine(input,"single",none);
        event(engine,KeyEvent.ACTION_DOWN,26,0,0);
        require(!event(engine,KeyEvent.ACTION_DOWN,25,0,0),"existing power chord intercepted");

        input=new CapturedInput();engine=engine(input,"double",none);
        event(engine,KeyEvent.ACTION_DOWN,25,0,0);
        require(event(engine,KeyEvent.ACTION_DOWN,26,0,0),"pending combination was not replayed");
        event(engine,KeyEvent.ACTION_UP,25,0,0);
        event(engine,KeyEvent.ACTION_UP,26,0,0);
        require(input.events.size()==4,"combination lost or duplicated events");
        require(input.events.get(0).getKeyCode()==25 && input.events.get(1).getKeyCode()==26,
                "combination order changed");

        input=new CapturedInput();engine=engine(input,"single",none);
        require(!event(engine,KeyEvent.ACTION_DOWN,25,0,KeyEvent.FLAG_FALLBACK),"replay entered gesture detector");

        input=new CapturedInput();engine=engine(input,"double",none);
        event(engine,KeyEvent.ACTION_DOWN,25,0,0);
        event(engine,KeyEvent.ACTION_UP,25,0,KeyEvent.FLAG_CANCELED);
        require(input.events.size()==2 && (Integer)get(engine,"volumeCode")==-1,"cancel left a stuck gesture");

        input=new CapturedInput();engine=engine(input,"single",KeyBindings.make("unknown","invalid",null));
        tap(engine);
        require(input.events.size()==2,"failed action did not restore original input");

        nativeCallbackCheck();
        System.out.println("PASS native input callback route and live gate; key mappings, device DEX profile, single/double/triple/long, original replay,"
                +" master/feature off, mid-gesture disable, both chord orders, cancellation and replay isolation");
        System.exit(0);
    }
}
