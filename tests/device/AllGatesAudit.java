package io.github.hyperpriv;

import android.content.SharedPreferences;
import dalvik.system.PathClassLoader;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.*;
import java.util.*;

/** Real callbacks, proxy framework, disposable process. Never labels this as live injection. */
public final class AllGatesAudit {
 static final Map<String,Object> values=new HashMap<>();
 static XposedInterface.Hooker callback;static int proceeds;static final Object ORIGINAL=new Object();
 static <T>T proxy(Class<T> c,InvocationHandler h){return c.cast(Proxy.newProxyInstance(c.getClassLoader(),new Class[]{c},h));}
 static SharedPreferences prefs(){return proxy(SharedPreferences.class,(p,m,a)->{
  if(a!=null&&a.length==2&&m.getName().startsWith("get"))return values.containsKey(a[0])?values.get(a[0]):a[1];return null;
 });}
 static XposedInterface api(){return proxy(XposedInterface.class,(p,m,a)->{
  if(m.getName().equals("hook"))return proxy(XposedInterface.HookBuilder.class,(b,bm,ba)->{
   if(bm.getName().equals("intercept")){callback=(XposedInterface.Hooker)ba[0];return proxy(XposedInterface.HookHandle.class,(h,hm,ha)->null);}return b;
  });
  if(m.getName().equals("deoptimize"))return true;return null;
 });}
 static void field(Object owner,String name,Object value)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
 static Object invoke(Object[] args)throws Throwable{return callback.intercept(proxy(XposedInterface.Chain.class,(p,m,a)->{
  if(m.getName().equals("proceed")){proceeds++;return ORIGINAL;}
  if(m.getName().equals("getArg"))return args[(Integer)a[0]];
  if(m.getName().equals("getArgs"))return Arrays.asList(args);
  if(m.getName().equals("getThisObject"))return null;
  throw new UnsupportedOperationException(m.getName());
 }));}
 static void original(Object[] args,String id)throws Throwable{proceeds=0;Object r=invoke(args);if(proceeds!=1||r!=ORIGINAL)throw new AssertionError("Original path not preserved: "+id);}
 static void gates(String group,Object[] args,String id)throws Throwable{
  values.clear();values.put("master",false);values.put("core_enabled",true);values.put(group,true);values.put("master",false);original(args,id+" master-off");
  if(!group.equals("master")){values.put("master",true);values.put(group,false);original(args,id+" option-off");}
 }
 static boolean simple(String action){return Arrays.asList("null","false","true","zero","none","empty_list").contains(action);}
 static void simpleOn(String key,String action,Object[] args,String id)throws Throwable{
  values.clear();values.put("master",true);values.put("core_enabled",true);values.put(key,true);
  proceeds=0;Object r=invoke(args);boolean good=action.equals("null")?r==null:action.equals("false")?Boolean.FALSE.equals(r):action.equals("true")?Boolean.TRUE.equals(r):action.equals("zero")?Integer.valueOf(0).equals(r):action.equals("none")?"NONE".equals(r):r instanceof List&&((List<?>)r).isEmpty();
  if(proceeds!=0||!good)throw new AssertionError("Enabled callback failed: "+id);
 }
 public static void main(String[] argv)throws Throwable{
  Class<?> vm=Class.forName("dalvik.system.VMRuntime");Method get=vm.getDeclaredMethod("getRuntime"),ex=vm.getDeclaredMethod("setHiddenApiExemptions",String[].class);get.setAccessible(true);ex.setAccessible(true);ex.invoke(get.invoke(null),(Object)new String[]{"L"});
  ClassLoader system=new PathClassLoader("/system/framework/services.jar:/system_ext/framework/miui-services.jar",AllGatesAudit.class.getClassLoader());
  Map<String,ClassLoader> loaders=new HashMap<>();loaders.put("android",system);Map<String,Long> versions=new HashMap<>();versions.put("android",36L);
  for(String arg:argv){String[] s=arg.split("=",3);loaders.put(s[0],new PathClassLoader(s[2],system));versions.put(s[0],Long.parseLong(s[1]));}
  int checked=0,on=0;
  for(String pkg:loaders.keySet()){
   ClassLoader loader=loaders.get(pkg);
   for(Rule r:Compatibility.select(Profiles.ALL,pkg,versions.get(pkg),false)){
    HookEntry entry=new HookEntry();entry.attachFramework(api(),()->{});field(entry,"loader",loader);field(entry,"prefs",prefs());
    Method install=HookEntry.class.getDeclaredMethod("installRule",Rule.class);install.setAccessible(true);install.invoke(entry,r);
    Object[] args=new Object[r.params.length];gates(r.group,args,r.id());if(simple(r.action)){simpleOn(r.group,r.action,args,r.id());on++;}checked++;
    System.out.println("GATES "+r.group+" "+r.id()+" enabled="+(simple(r.action)?"checked":"requires_scenario"));
   }
   for(Rule r:Compatibility.select(ExtraTargets.ALL,pkg,versions.get(pkg),false)){
    ExtraHooks entry=new ExtraHooks(api(),loader,prefs());Method install=ExtraHooks.class.getDeclaredMethod("install",Rule.class);install.setAccessible(true);install.invoke(entry,r);
    Object[] args=new Object[r.params.length];gates(r.group,args,r.id());if(simple(r.action)&&!r.group.equals("core_shared_uid")){simpleOn(r.group,r.action,args,r.id());on++;}checked++;
    System.out.println("GATES "+r.group+" "+r.id()+" enabled="+(simple(r.action)&&!r.group.equals("core_shared_uid")?"checked":"requires_scenario"));
   }
  }
  for(CoreTargets.Target t:CoreTargets.ALL){
   CoreHooks entry=new CoreHooks(api(),system);field(entry,"prefs",prefs());Method install=CoreHooks.class.getDeclaredMethod("install",CoreTargets.Target.class);install.setAccessible(true);install.invoke(entry,t);
   Object[] args=new Object[t.params.length];gates(t.key,args,t.id());checked++;System.out.println("GATES "+t.key+" "+t.id()+" enabled=requires_install_scenario");
  }
  System.out.println("All callback gates checked="+checked+" enabled_simple="+on+" failures=0; proxy framework, not live injection");
 }
}
