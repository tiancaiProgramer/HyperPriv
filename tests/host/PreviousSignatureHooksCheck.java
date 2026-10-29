package io.github.hyperpriv;

import android.content.SharedPreferences;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.*;
import java.util.*;

/** Exercises the real commit callback with disposable request objects. */
public final class PreviousSignatureHooksCheck {
 public static final class Details {final String id;Details(String id){this.id=id;}Details(Details old){id=old.id;}public boolean hasSignatures(){return true;}}
 public static class Parsed {Object details=new Details("new");boolean fail;public String getPackageName(){return "fixture.app";}public Object getSigningDetails(){return details;}public void setSigningDetails(Details d){if(fail&&d.id.equals("old"))throw new IllegalStateException("fixture setter failure");details=d;}}
 public static final class Old {public String getPackageName(){return "fixture.app";}public Object getSigningDetails(){return new Details("old");}}
 public static final class Args {Object mSigningDetails=new Details("new");}
 public static final class Request {final Parsed parsed=new Parsed();final Args mInstallArgs=new Args();Object old=new Old();public Object getParsedPackage(){return parsed;}public Object getScanRequestOldPackageSetting(){return old;}}
 public static final class Reconciled {final Request mInstallRequest=new Request();Object mSigningDetails=new Details("new");}
 public static final class Fixture {public void commitPackagesLocked(List<?> entries,int[] users){}}
 static final Map<String,Object> options=new HashMap<>();static XposedInterface.Hooker hook;
 static <T>T proxy(Class<T> type,InvocationHandler h){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},h));}
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
 static String id(Object d){return ((Details)d).id;}
 static void state(Reconciled r,String expected){require(id(r.mSigningDetails).equals(expected)&&id(r.mInstallRequest.parsed.details).equals(expected)&&id(r.mInstallRequest.mInstallArgs.mSigningDetails).equals(expected),"partial signature metadata update");}
 static Object call(Reconciled r,boolean throwing)throws Throwable{return hook.intercept(proxy(XposedInterface.Chain.class,(p,m,a)->{
  if(m.getName().equals("getArg"))return Collections.singletonList(r);
  if(m.getName().equals("proceed")){if(throwing)throw new IllegalStateException("fixture commit failure");return null;}
  throw new UnsupportedOperationException(m.getName());
 }));}
 public static void main(String[] args)throws Throwable{
  SharedPreferences prefs=proxy(SharedPreferences.class,(p,m,a)->m.getName().equals("getBoolean")?(options.containsKey(a[0])?options.get(a[0]):a[1]):null);
  XposedInterface api=proxy(XposedInterface.class,(p,m,a)->{
   if(m.getName().equals("hook"))return proxy(XposedInterface.HookBuilder.class,(b,bm,ba)->{if(bm.getName().equals("intercept")){hook=(XposedInterface.Hooker)ba[0];return proxy(XposedInterface.HookHandle.class,(h,hm,ha)->null);}return b;});return null;
  });
  ExtraHooks extra=new ExtraHooks(api,Fixture.class.getClassLoader(),prefs);
  Rule rule=new Rule("fixture",1,Fixture.class.getName(),"commitPackagesLocked","V","core_previous_signature","previous_signature",new String[]{"Ljava/util/List;","[I"});
  Method install=ExtraHooks.class.getDeclaredMethod("install",Rule.class);install.setAccessible(true);install.invoke(extra,rule);
  options.put("master",true);options.put("core_enabled",true);options.put("core_update_signature",true);options.put("core_previous_signature",true);
  Reconciled r=new Reconciled();call(r,false);state(r,"old");
  r=new Reconciled();r.mInstallRequest.old=null;call(r,false);state(r,"new");
  r=new Reconciled();r.mInstallRequest.parsed.fail=true;call(r,false);state(r,"new");
  r=new Reconciled();try{call(r,true);throw new AssertionError("commit exception swallowed");}catch(IllegalStateException expected){}state(r,"new");
  for(String key:new String[]{"master","core_enabled","core_update_signature","core_previous_signature"}){
   options.put(key,false);r=new Reconciled();call(r,false);state(r,"new");options.put(key,true);
  }
  System.out.println("Previous signature callback: installed-package scope, full metadata update, option/master gates and failure rollback passed");
 }
}
