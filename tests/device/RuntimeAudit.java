package io.github.hyperpriv;
import dalvik.system.PathClassLoader;
import java.lang.reflect.*;
import java.util.*;
public final class RuntimeAudit {
 public static void main(String[] args)throws Exception{
  try {Class<?> vm=Class.forName("dalvik.system.VMRuntime");Method get=vm.getDeclaredMethod("getRuntime"),exempt=vm.getDeclaredMethod("setHiddenApiExemptions",String[].class);get.setAccessible(true);exempt.setAccessible(true);exempt.invoke(get.invoke(null),(Object)new String[]{"L"});}catch(Throwable ignored){}
  String base="/system/framework/services.jar:/system_ext/framework/miui-services.jar";
  ClassLoader system=new PathClassLoader(base,RuntimeAudit.class.getClassLoader());
  Map<String,ClassLoader> loaders=new HashMap<>();loaders.put("android",system);
  for(String arg:args){int i=arg.indexOf('=');loaders.put(arg.substring(0,i),new PathClassLoader(arg.substring(i+1),system));}
  int matched=0,failed=0;
  for(Rule r:ExtraTargets.ALL){ClassLoader loader=loaders.get(r.pkg);if(loader==null){failed++;System.out.println("MISSING LOADER "+r.pkg);continue;}
   try{Reflect.executable(r,loader);matched++;}catch(Throwable e){failed++;System.out.println("FAIL "+r.group+" "+r.id()+" "+e.getClass().getSimpleName()+":"+e.getMessage());}
  }
  String[][] fields={{"android.content.pm.SigningInfo","mSigningDetails"},{"android.window.ScreenCapture$CaptureArgs","mCaptureSecureLayers"},{"com.android.server.pm.ReconcilePackageUtils","ALLOW_NON_PRELOADS_SYSTEM_SHAREDUIDS"},{"com.android.server.pm.SharedUserSetting","uidFlags"},{"com.android.server.pm.SharedUserSetting","mPackages"},{"com.android.server.pm.InstallArgs","mInstallFlags"},{"com.android.server.pm.InstallRequest","mInstallArgs"},{"com.android.server.pm.PackageSignatures","mSigningDetails"}};
  for(String[] f:fields)try{Reflect.field(Class.forName(f[0],false,system),f[1]);matched++;}catch(Throwable e){failed++;System.out.println("FIELD FAIL "+Arrays.toString(f)+" "+e.getClass().getSimpleName());}
  System.out.println("Runtime reflective audit matched="+matched+" failed="+failed);
  if(failed!=0)System.exit(2);
 }
}
