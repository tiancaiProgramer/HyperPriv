package io.github.hyperpriv;
import android.content.SharedPreferences;
import dalvik.system.PathClassLoader;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
public final class ExtraHooksIntegrationCheck {
 interface Next {Object run(Object[] args)throws Throwable;}
 static final Map<String,Object> settings=new HashMap<>();
 static final Map<String,XposedInterface.Hooker> hooks=new HashMap<>();
 static final Map<String,Executable> methods=new HashMap<>();
 static final class Flags {int mInstallFlags=128;}
 static final class Request {Object mInstallArgs=new Flags();}
 static final class Op {int mOpMode=2;}
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static <T>T proxy(Class<T> cls,InvocationHandler h){return cls.cast(Proxy.newProxyInstance(cls.getClassLoader(),new Class[]{cls},h));}
 static String id(Executable m){return m.getDeclaringClass().getName()+"#"+m.getName()+"/"+m.getParameterTypes().length;}
 static XposedInterface api(){return proxy(XposedInterface.class,(p,m,a)->{
  if(m.getName().equals("hook")){Executable target=(Executable)a[0];String id=id(target);methods.put(id,target);
   return proxy(XposedInterface.HookBuilder.class,(b,bm,ba)->{
    if(bm.getName().equals("intercept")){hooks.put(id,(XposedInterface.Hooker)ba[0]);return proxy(XposedInterface.HookHandle.class,(hh,hm,ha)->hm.getName().equals("getExecutable")?target:null);}return b;
   });
  }
  if(m.getName().equals("deoptimize"))return true;
  return null;
 });}
 static SharedPreferences preferences(){return proxy(SharedPreferences.class,(p,m,a)->{
  if(m.getName().startsWith("get")&&a!=null&&a.length==2)return settings.containsKey(a[0])?settings.get(a[0]):a[1];
  if(m.getName().equals("contains"))return settings.containsKey(a[0]);return null;
 });}
 static Object invoke(String key,Object target,Object[] args,Next next)throws Throwable{
  XposedInterface.Chain chain=proxy(XposedInterface.Chain.class,(p,m,a)->{
   switch(m.getName()){
    case "getExecutable":return methods.get(key);
    case "getThisObject":return target;
    case "getArgs":return Arrays.asList(args);
    case "getArg":return args[(Integer)a[0]];
    case "proceed":return next.run(a==null||a.length==0?args:(Object[])a[0]);
    default:throw new UnsupportedOperationException(m.getName());
   }
  });return hooks.get(key).intercept(chain);
 }
 static Object persistent()throws Throwable{return invoke("com.android.server.pm.PackageSetting#isPersistent/0",null,new Object[0],args->true);}
 public static void main(String[] argv)throws Throwable{
  try{Class<?> c=Class.forName("dalvik.system.VMRuntime");Method get=c.getDeclaredMethod("getRuntime"),ex=c.getDeclaredMethod("setHiddenApiExemptions",String[].class);get.setAccessible(true);ex.setAccessible(true);ex.invoke(get.invoke(null),(Object)new String[]{"L"});}catch(Throwable ignored){}
  settings.put("master",true);settings.put("core_enabled",true);
  ClassLoader loader=new PathClassLoader("/system/framework/services.jar:/system_ext/framework/miui-services.jar",ExtraHooksIntegrationCheck.class.getClassLoader());
  new ExtraHooks(api(),loader,preferences()).install("android",36L);
  String prepare="com.android.server.pm.InstallPackageHelper#preparePackage/1";
  Request request=new Request();Flags flags=(Flags)request.mInstallArgs;
  settings.put("core_low_sdk",true);settings.put("core_persistent",true);
  invoke(prepare,null,new Object[]{request},args->{
   require((flags.mInstallFlags&ExtraPolicy.LOW_SDK_FLAG)!=0,"low SDK flag missing during prepare");
   require(Boolean.FALSE.equals(persistent()),"persistent check not limited to installation thread");
   AtomicReference<Object> other=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();
   Thread thread=new Thread(()->{try{other.set(persistent());}catch(Throwable e){error.set(e);}});thread.start();thread.join();
   require(error.get()==null&&Boolean.TRUE.equals(other.get()),"installation context leaked into another thread");flags.mInstallFlags|=512;return null;
  });
  require(flags.mInstallFlags==(128|512),"prepare failed to restore flag while preserving other changes");
  require(Boolean.TRUE.equals(persistent()),"prepare leaked context after completion");
  try{invoke(prepare,null,new Object[]{request},args->{throw new IllegalStateException("expected");});throw new AssertionError("exception lost");}catch(IllegalStateException expected){}
  require(flags.mInstallFlags==(128|512)&&Boolean.TRUE.equals(persistent()),"exception path leaked flag/context");
  settings.put("framework_unknown_sources",true);
  String op="com.android.server.appop.AppOpsService#noteOperation/7";
  for(int number:new int[]{65,66,67}){Op result=new Op();Object[] args={number,10001,"com.example.app",null,false,null,false};invoke(op,null,args,a->result);require(result.mOpMode==(number==66?0:2),"unrelated operation modified");}
  settings.put("framework_clipboard",true);settings.put("clipboard_packages","com.example.allowed");String clip="com.android.server.clipboard.ClipboardService#clipboardAccessAllowed/8";
  require(Boolean.TRUE.equals(invoke(clip,null,new Object[]{29,"com.example.allowed",null,0,10001,0,false,false},a->false)),"whitelist read denied");
  require(Boolean.FALSE.equals(invoke(clip,null,new Object[]{30,"com.example.allowed",null,0,10001,0,false,false},a->false)),"whitelist bypassed a write operation");
  require(Boolean.FALSE.equals(invoke(clip,null,new Object[]{29,"com.example.allowed.other",null,0,10001,0,false,false},a->false)),"whitelist prefix accepted");
  if(argv.length>0){
   ClassLoader app=new PathClassLoader(argv[0],loader);new ExtraHooks(api(),app,preferences()).install("com.miui.securitycenter",40001275L);
   String pay="com.miui.antivirus.service.GuardService#Q/0";require(hooks.containsKey(pay),"payment entry not registered");
   java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
   settings.put("safepay_scan",true);invoke(pay,null,new Object[0],a->{calls.incrementAndGet();return null;});require(calls.get()==0,"payment auto scan still invoked");
   settings.put("safepay_scan",false);invoke(pay,null,new Object[0],a->{calls.incrementAndGet();return null;});require(calls.get()==1,"payment scan off did not restore original");
   settings.put("safepay_scan",true);settings.put("master",false);invoke(pay,null,new Object[0],a->{calls.incrementAndGet();return null;});require(calls.get()==2,"master off did not restore payment scan");settings.put("master",true);
   System.out.println("Payment callback passed: enabled suppresses automatic entry; option/master off restore original");
  }
  settings.put("master",false);require(Boolean.FALSE.equals(invoke(clip,null,new Object[]{29,"com.example.allowed",null,0,10001,0,false,false},a->false)),"master off did not restore original call");
  System.out.println("Hook callback integration passed: scoped installation context, concurrent thread isolation, exception restoration, AppOp boundaries, whitelist and master off");
 }
}
