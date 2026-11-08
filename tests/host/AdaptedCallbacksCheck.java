package io.github.hyperpriv;
import android.content.Context;
import android.content.SharedPreferences;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.*;
import java.util.*;
public final class AdaptedCallbacksCheck {
 static final Map<String,Object> values=new HashMap<>();static XposedInterface.Hooker callback;static int originalCalls;static final Object ORIGINAL=new Object();
 public static final class Device {public Device(){}}
 public static final class Fixture {public void environment(Object request){}public List<?> revoke(List<?> values){return values;}public Object cloud(){return null;}public Device device(Context context,String value){return null;}}
 static <T>T proxy(Class<T> type,InvocationHandler h){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},h));}
 static SharedPreferences prefs(){return proxy(SharedPreferences.class,(p,m,a)->{if(m.getName().equals("getBoolean"))return values.containsKey(a[0])?values.get(a[0]):a[1];return null;});}
 static XposedInterface api(){return proxy(XposedInterface.class,(p,m,a)->{
  if(m.getName().equals("hook"))return proxy(XposedInterface.HookBuilder.class,(b,bm,ba)->{if(bm.getName().equals("intercept")){callback=(XposedInterface.Hooker)ba[0];return proxy(XposedInterface.HookHandle.class,(h,hm,ha)->null);}return b;});return null;
 });}
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static Object invoke(Object...args)throws Throwable{return callback.intercept(proxy(XposedInterface.Chain.class,(p,m,a)->{
  if(m.getName().equals("getArg"))return args[(Integer)a[0]];if(m.getName().equals("getArgs"))return Arrays.asList(args);if(m.getName().equals("proceed")){originalCalls++;return ORIGINAL;}if(m.getName().equals("getThisObject"))return new Fixture();throw new UnsupportedOperationException(m.getName());
 }));}
 static void flags(String key){values.clear();values.put("master",true);values.put(key,true);values.put("ads",false);values.put("upload",false);values.put("guards",false);originalCalls=0;RuntimeProfile.apply(RomPolicy.normal());}
 static void extra(Rule r)throws Exception{ExtraHooks entry=new ExtraHooks(api(),Fixture.class.getClassLoader(),prefs());Method m=ExtraHooks.class.getDeclaredMethod("install",Rule.class);m.setAccessible(true);m.invoke(entry,r);}
 static void base(Rule r)throws Exception{HookEntry entry=new HookEntry();entry.attachFramework(api(),()->{});for(String name:new String[]{"loader","prefs"}){Field f=HookEntry.class.getDeclaredField(name);f.setAccessible(true);f.set(entry,name.equals("loader")?Fixture.class.getClassLoader():prefs());}Method m=HookEntry.class.getDeclaredMethod("installRule",Rule.class);m.setAccessible(true);m.invoke(entry,r);}
 public static void main(String[] args)throws Throwable{
  flags("environment_checks");extra(new Rule("fixture",40001283,Fixture.class.getName(),"environment","V","environment_checks","environment",new String[]{"Ljava/lang/Object;"}));
  for(int id:new int[]{10,13,14,15,16}){La.a request=new La.a(id);require(invoke(request)==null&&request.calls==1&&request.code==1001&&request.value==null,"new environment request was not completed unavailable");}
  require(originalCalls==0,"blocked environment task reached original handler");
  for(int id:new int[]{11,12,99}){La.a request=new La.a(id);int before=originalCalls;require(invoke(request)==ORIGINAL&&originalCalls==before+1&&request.calls==0,"unrelated task was intercepted");}
  values.put("master",false);La.a off=new La.a(10);require(invoke(off)==ORIGINAL&&off.calls==0,"master-off environment altered");
  flags("accessibility_keep");extra(new Rule("fixture",40001283,Fixture.class.getName(),"revoke","Ljava/util/List;","accessibility_keep","accessibility_revoke",new String[]{"Ljava/util/List;"}));
  List<String> input=Arrays.asList("fixture.service");require(invoke(input)==ORIGINAL&&originalCalls==1,"manual/unrelated revoke path blocked");
  Object blocked=com.miui.antivirus.service.VirusScanJobService.invoke(()->invoke(input));require(blocked instanceof List&&((List<?>)blocked).isEmpty()&&originalCalls==1,"automatic revoke not blocked");
  values.put("accessibility_keep",false);require(com.miui.antivirus.service.VirusScanJobService.invoke(()->invoke(input))==ORIGINAL&&originalCalls==2,"feature-off revoke not restored");
  flags("cloud");base(new Rule("fixture",55500,Fixture.class.getName(),"cloud","Ljava/lang/Object;","cloud","cloud_result",new String[0]));
  Object cloud=invoke();require(cloud instanceof com.miui.packageInstaller.model.CloudResult.Success,"new coroutine received raw CloudParams instead of CloudResult");
  com.miui.packageInstaller.model.CloudParams data=((com.miui.packageInstaller.model.CloudResult.Success)cloud).getData();require(Boolean.TRUE.equals(data.useSystemAppRules)&&"miui".equals(data.categoryAbbreviation)&&originalCalls==0,"cloud fallback contract invalid");
  values.put("cloud",false);require(invoke()==ORIGINAL&&originalCalls==1,"cloud-off original path not restored");
  flags("systemhelper_report");base(new Rule("fixture",34,Fixture.class.getName(),"device","Lio/github/hyperpriv/AdaptedCallbacksCheck$Device;","systemhelper_report","empty_device",new String[]{"Landroid/content/Context;","Ljava/lang/String;"}));
  require(invoke(null,"X") instanceof Device&&originalCalls==0,"new helper factory did not return safe empty type");
  values.put("master",false);require(invoke(null,"X")==ORIGINAL&&originalCalls==1,"helper master-off not restored");
  System.out.println("Adapted callbacks: new request IDs/completion, manual accessibility boundaries, CloudResult return contract and non-null empty helper type passed");
 }
}
