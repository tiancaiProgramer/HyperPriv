package io.github.hyperpriv;

import android.content.SharedPreferences;
import android.view.MenuItem;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Executes the real callbacks with framework proxies, without a phone or live hooks. */
public final class CompatibilityHooksCheck {
    public static final class Fixture {public boolean check(){return true;}}
    public static final class MenuFixture {public boolean onOptionsItemSelected(MenuItem item){return false;}}
    private static final Map<String,Object> prefs=new HashMap<>();
    private static XposedInterface.Hooker callback;
    private static Executable executable;
    private static final AtomicInteger calls=new AtomicInteger();
    private static final List<String> logs=new ArrayList<>();
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static <T>T proxy(Class<T> type,InvocationHandler fn){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},fn));}
    private static SharedPreferences preferences(){return proxy(SharedPreferences.class,(p,m,a)->{
        if(m.getName().equals("getBoolean"))return prefs.containsKey(a[0])?prefs.get(a[0]):a[1];
        return null;
    });}
    private static XposedInterface api(){return proxy(XposedInterface.class,(p,m,a)->{
        if(m.getName().equals("log")){logs.add(Arrays.toString(a));return null;}
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
    private static void checkMenuDispatch()throws Throwable{
        ExtraHooks extra=new ExtraHooks(api(),MenuFixture.class.getClassLoader(),preferences());
        Rule rule=new Rule("fixture",1,MenuFixture.class.getName(),"onOptionsItemSelected","Z","app_disable","app_menu",new String[]{"Landroid/view/MenuItem;"});
        Method install=ExtraHooks.class.getDeclaredMethod("install",Rule.class);install.setAccessible(true);install.invoke(extra,rule);
        prefs.put("master",true);prefs.put("app_disable",true);logs.clear();
        for(int id:new int[]{android.R.id.home,1,0}){
            MenuItem item=proxy(MenuItem.class,(p,m,a)->m.getName().equals("getItemId")?id:null);
            AtomicInteger original=new AtomicInteger();
            Object result=callback.intercept(proxy(XposedInterface.Chain.class,(p,m,a)->{
                if(m.getName().equals("getArg"))return item;
                if(m.getName().equals("getThisObject"))return new MenuFixture();
                if(m.getName().equals("proceed")){original.incrementAndGet();return false;}
                throw new UnsupportedOperationException(m.getName());
            }));
            require(Boolean.FALSE.equals(result)&&original.get()==1,"unrelated menu item did not retain original handler");
        }
        require(logs.isEmpty(),"unrelated MenuItem was treated as Menu: "+logs);
        System.out.println("Menu callback: back and unrelated items retain original handler without invalid Menu cast");
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
        checkMenuDispatch();
        PreviousSignatureHooksCheck.main(new String[0]);
        System.out.println("Real Hook callbacks: experimental/strict gating, master/feature off and exact reflection rejection passed");
    }
}

