package io.github.hyperpriv;
import android.content.SharedPreferences;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.*;
import java.util.*;
/** Calls the production registration paths with every stale preference enabled. */
public final class UnifiedHooksCheck {
 static int registrations;
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
 static <T>T proxy(Class<T> type,InvocationHandler h){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},h));}
 public static void main(String[] args)throws Exception{
  SharedPreferences prefs=proxy(SharedPreferences.class,(p,m,a)->m.getName().equals("getBoolean")?true:null);
  XposedInterface api=proxy(XposedInterface.class,(p,m,a)->{if(m.getName().equals("hook"))registrations++;return null;});
  HookEntry base=new HookEntry();Field f=HookEntry.class.getDeclaredField("prefs");f.setAccessible(true);f.set(base,prefs);
  ExtraHooks extra=new ExtraHooks(api,UnifiedHooksCheck.class.getClassLoader(),prefs);
  Method baseRegister=HookEntry.class.getDeclaredMethod("installRule",Rule.class);baseRegister.setAccessible(true);
  Method extraRegister=ExtraHooks.class.getDeclaredMethod("install",Rule.class);extraRegister.setAccessible(true);
  Method baseEnabled=HookEntry.class.getDeclaredMethod("enabled",String.class);baseEnabled.setAccessible(true);
  Method extraEnabled=ExtraHooks.class.getDeclaredMethod("enabled",String.class);extraEnabled.setAccessible(true);
  try{
   Map<String,String> hashes=new HashMap<>();hashes.put("com.miui.packageinstaller",EuBuild.expectedHash("com.miui.packageinstaller",55500));hashes.put("com.miui.securitycenter",EuBuild.expectedHash("com.miui.securitycenter",40001283));hashes.put("com.miui.guardprovider",RomPolicy.GUARD_301);
   RuntimeProfile.apply(new RomPolicy.State(true,true,hashes,new HashSet<>(Arrays.asList("com.miui.analytics","com.miui.systemAdSolution"))));
   for(String key:Arrays.asList("ads","scan","permission_timer","analytics","msa","app_list","guard_environment")){
    Rule fixture=new Rule("unused",1,"missing.ClassMustNotLoad","method","V",key,"null",new String[0]);
    baseRegister.invoke(base,fixture);extraRegister.invoke(extra,fixture);
    require(Boolean.FALSE.equals(baseEnabled.invoke(base,key))&&Boolean.FALSE.equals(extraEnabled.invoke(extra,key)),"stale flag revived "+key);
   }
   require(registrations==0,"EU hidden option attempted a registration");
   require(Boolean.TRUE.equals(baseEnabled.invoke(base,"systemhelper_report")),"adapted helper remained blocked");
   RuntimeProfile.apply(RomPolicy.normal());
   require(Boolean.TRUE.equals(baseEnabled.invoke(base,"ads")),"EU policy leaked into standard callbacks");
   require(!RuntimeProfile.hashMatches("com.mobiletools.systemhelper",34,"unexpected"),"compatibility weakened helper hash checks");
  }finally{RuntimeProfile.apply(RomPolicy.normal());}
  System.out.println("Unified real registration: EU redundant options register zero Hooks even with all flags/compatibility true; standard and adapted functions retained");
 }
}
