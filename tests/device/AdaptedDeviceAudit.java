package io.github.hyperpriv;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import dalvik.system.PathClassLoader;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.*;
import java.util.*;
/** Real vendor return types with a fake hook framework; no original target invocation or external I/O. */
public final class AdaptedDeviceAudit {
 static final Object ORIGINAL=new Object();static XposedInterface.Hooker callback;static int originalCalls,contextReads;
 static final Map<String,Object> values=new HashMap<>();
 static class Trap extends ContextWrapper {
  Trap(){super(null);}public Object getSystemService(String name){contextReads++;throw new AssertionError("No device reads");}
  public ContentResolver getContentResolver(){contextReads++;throw new AssertionError("No device reads");}
  public PackageManager getPackageManager(){contextReads++;throw new AssertionError("No device reads");}
  public String getPackageName(){contextReads++;throw new AssertionError("No device reads");}
 }
 static <T>T proxy(Class<T> type,InvocationHandler h){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},h));}
 static XposedInterface api(){return proxy(XposedInterface.class,(p,m,a)->{
  if(m.getName().equals("hook"))return proxy(XposedInterface.HookBuilder.class,(b,bm,ba)->{if(bm.getName().equals("intercept")){callback=(XposedInterface.Hooker)ba[0];return proxy(XposedInterface.HookHandle.class,(h,hm,ha)->null);}return b;});return null;
 });}
 static SharedPreferences prefs(){return proxy(SharedPreferences.class,(p,m,a)->m.getName().equals("getBoolean")?(values.containsKey(a[0])?values.get(a[0]):a[1]):null);}
 static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
 static Object invoke(Object...args)throws Throwable{return callback.intercept(proxy(XposedInterface.Chain.class,(p,m,a)->{if(m.getName().equals("getArg"))return args[(Integer)a[0]];if(m.getName().equals("getArgs"))return Arrays.asList(args);if(m.getName().equals("proceed")){originalCalls++;return ORIGINAL;}if(m.getName().equals("getThisObject"))return null;throw new UnsupportedOperationException(m.getName());}));}
 static void field(Object obj,String name,Object v)throws Exception{Field f=obj.getClass().getDeclaredField(name);f.setAccessible(true);f.set(obj,v);}
 static void install(Rule r,ClassLoader loader)throws Exception{
  HookEntry entry=new HookEntry();entry.attachFramework(api(),()->{});field(entry,"prefs",prefs());field(entry,"loader",loader);
  Method m=HookEntry.class.getDeclaredMethod("installRule",Rule.class);m.setAccessible(true);m.invoke(entry,r);
 }
 public static void main(String[] args)throws Throwable{
  System.out.println("PROXY_ONLY_TYPED_RETURNS pid="+android.os.Process.myPid());
  Class<?> vm=Class.forName("dalvik.system.VMRuntime");Method get=vm.getDeclaredMethod("getRuntime"),ex=vm.getDeclaredMethod("setHiddenApiExemptions",String[].class);get.setAccessible(true);ex.setAccessible(true);ex.invoke(get.invoke(null),(Object)new String[]{"L"});
  String installer=args[0],helper=args[1];ClassLoader pi=new PathClassLoader(installer,AdaptedDeviceAudit.class.getClassLoader()),h=new PathClassLoader(helper,AdaptedDeviceAudit.class.getClassLoader());
  RuntimeProfile.initializePackage("com.miui.packageinstaller",EuEnvironment.hash(installer));
  values.put("master",true);values.put("cloud",true);values.put("guards",false);values.put("upload",false);values.put("ads",false);
  Rule cloud=null;for(Rule r:Profiles.ALL)if(r.pkg.equals("com.miui.packageinstaller")&&r.version==55500&&r.action.equals("cloud_result"))cloud=r;
  require(cloud!=null,"cloud profile missing");install(cloud,pi);Object result=invoke(new Trap(),null,0,null,null,null);
  require(result!=null&&result.getClass().getName().equals("com.miui.packageInstaller.model.CloudResult$Success")&&originalCalls==0,"real new installer return type invalid");
  Object cp=result.getClass().getMethod("getData").invoke(result);require(cp!=null&&Boolean.TRUE.equals(cp.getClass().getField("useSystemAppRules").get(cp)),"local data missing");
  values.put("cloud",false);require(invoke(new Trap(),null,0,null,null,null)==ORIGINAL&&originalCalls==1,"cloud-off original path lost");
  RuntimeProfile.initializePackage("com.mobiletools.systemhelper",EuEnvironment.hash(helper));values.clear();values.put("master",true);values.put("systemhelper_report",true);
  int checked=0;for(Rule r:Profiles.ALL)if(r.pkg.equals("com.mobiletools.systemhelper")&&r.version==34&&r.action.equals("empty_device")){
   install(r,h);originalCalls=0;Object device=invoke(new Trap(),"X");require(device!=null&&device.getClass().getName().equals("e1.b")&&originalCalls==0,"helper empty record invalid");
   values.put("master",false);require(invoke(new Trap(),"X")==ORIGINAL&&originalCalls==1,"helper-off original path lost");values.put("master",true);checked++;
  }
  require(checked==2&&contextReads==0,"unexpected device data access");
  System.out.println("Typed return contracts passed: CloudResult.Success + "+checked+" actual helper factories; original called only by fake chain when off; Context reads="+contextReads+"; external requests=0");
 }
}
