package io.github.hyperpriv;

import android.content.SharedPreferences;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Executes the real callbacks with framework proxies, without a phone or live hooks. */
public final class CompatibilityHooksCheck {
    public static final class Fixture {public boolean check(){return true;}}
    private static final Map<String,Object> prefs=new HashMap<>();
    private static XposedInterface.Hooker callback;
    private static Executable executable;
    private static final AtomicInteger calls=new AtomicInteger();
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static <T>T proxy(Class<T> type,InvocationHandler fn){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},fn));}
    private static SharedPreferences preferences(){return proxy(SharedPreferences.class,(p,m,a)->{
        if(m.getName().equals("getBoolean"))return prefs.containsKey(a[0])?prefs.get(a[0]):a[1];
        return null;
    });}
    private static XposedInterface api(){return proxy(XposedInterface.class,(p,m,a)->{
        if(!m.getName().equals("hook"))return null;
        executable=(Executable)a[0];
        return proxy(XposedInterface.HookBuilder.class,(b,bm,ba)->{
            if(bm.getName().equals("intercept")){callback=(XposedInterface.Hooker)ba[0];return proxy(XposedInterface.HookHandle.class,(h,hm,ha)->hm.getName().equals("getExecutable")?executable:null);}
            return b;
        });
    });}
    private static Object invoke()throws Throwable{
        return callback.intercept(proxy(XposedInterface.Chain.class,(p,m,a)->{
            if(m.getName().equals("proceed")){calls.incrementAndGet();return true;}
            if(m.getName().equals("getExecutable"))return executable;
            if(m.getName().equals("getThisObject"))return new Fixture();
            if(m.getName().equals("getArgs"))return Collections.emptyList();
            throw new UnsupportedOperationException(m.getName());
        }));
    }
    private static void field(Object target,String name,Object value)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static void checkGates(String feature)throws Throwable{
        prefs.put("master",true);prefs.put("core_enabled",true);prefs.put(feature,true);prefs.put(Compatibility.KEY,true);
        int before=calls.get();require(Boolean.FALSE.equals(invoke())&&calls.get()==before,"trial did not intercept");
        prefs.put(Compatibility.KEY,false);require(Boolean.TRUE.equals(invoke())&&calls.get()==before+1,"mode off kept experimental callback active");
        prefs.put(Compatibility.KEY,true);prefs.put("master",false);require(Boolean.TRUE.equals(invoke()),"master off failed");
        prefs.put("master",true);prefs.put(feature,false);require(Boolean.TRUE.equals(invoke()),"feature off failed");
    }
    public static void main(String[] args)throws Throwable{
        ExtraHooks extra=new ExtraHooks(api(),Fixture.class.getClassLoader(),preferences());field(extra,"experimental",true);
        Rule rule=new Rule("fixture",1,Fixture.class.getName(),"check","Z","safepay_scan","false",new String[0]);
        Method install=ExtraHooks.class.getDeclaredMethod("install",Rule.class);install.setAccessible(true);install.invoke(extra,rule);
        checkGates("safepay_scan");
        field(extra,"experimental",false);prefs.put(Compatibility.KEY,false);prefs.put("safepay_scan",true);require(Boolean.FALSE.equals(invoke()),"strict callbacks incorrectly require compatibility");
        try{Reflect.executable(new Rule("fixture",1,Fixture.class.getName(),"check","I","ads","false",new String[0]),Fixture.class.getClassLoader());throw new AssertionError("return mismatch accepted");}catch(NoSuchMethodException expected){}
        try{Reflect.executable(new Rule("fixture",1,Fixture.class.getName(),"missing","Z","ads","false",new String[0]),Fixture.class.getClassLoader());throw new AssertionError("missing method accepted");}catch(NoSuchMethodException expected){}
        CoreHooks core=new CoreHooks(api(),Fixture.class.getClassLoader());field(core,"prefs",preferences());field(core,"experimental",true);
        CoreTargets.Target target=new CoreTargets.Target(Fixture.class.getName(),"check","Z","core_downgrade","always","false");
        Method coreInstall=CoreHooks.class.getDeclaredMethod("install",CoreTargets.Target.class);coreInstall.setAccessible(true);coreInstall.invoke(core,target);
        checkGates("core_downgrade");
        System.out.println("Real Hook callbacks: experimental/strict gating, master/feature off and exact reflection rejection passed");
    }
}
