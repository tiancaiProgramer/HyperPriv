package io.github.hyperpriv;

import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/** AGPL-3.0-only. Installation hook design researched from HyperCeiler's corepatch.
 * Optional, default-off system_server hooks using modern libxposed API 102.
 */
final class CoreHooks {
    private final XposedInterface api;
    private final ClassLoader loader;
    private final ThreadLocal<Object> keysetPackage=new ThreadLocal<>();
    private final Set<String> hits=java.util.Collections.synchronizedSet(new HashSet<>());
    private SharedPreferences prefs;
    private boolean experimental;
    CoreHooks(XposedInterface api,ClassLoader loader){this.api=api;this.loader=loader;}
    void install(){
        try{prefs=api.getRemotePreferences(Options.PREFS);}catch(Throwable e){report("core preferences unavailable");return;}
        experimental=!Compatibility.strictSystem(Build.VERSION.SDK_INT,Build.FINGERPRINT);
        if(experimental&&!compatibilityEnabled()){
            report("core unsupported framework; original methods preserved");return;
        }
        int installed=0,skipped=0;
        for(CoreTargets.Target t:CoreTargets.ALL){
            try{install(t);installed++;}catch(Throwable e){skipped++;report("core skip "+t.id()+": "+e.getClass().getSimpleName());}
        }
        // These callers can contain inlined signature/upgrade checks on release ROMs.
        deoptimize("com.android.server.pm.PackageManagerServiceUtils","verifySignatures");
        deoptimize("com.android.server.pm.InstallPackageHelper","preparePackage");
        deoptimize("com.android.server.pm.ReconcilePackageUtils","reconcilePackages");
        deoptimize("android.util.apk.ApkSignatureVerifier","verifySignaturesInternal");
        deoptimize("android.util.jar.StrictJarVerifier","verify");
        deoptimize("android.util.jar.StrictJarVerifier$VerifierEntry","verify");
        deoptimize("com.android.server.pm.PackageInstallerSession","assertApkConsistentLocked");
        deoptimize("com.android.server.pm.PackageInstallerSession","validateApkInstallLocked");
        report("core ready installed="+installed+" skipped="+skipped+"; controls default off"+(experimental?"; experimental compatibility, behavior unverified":""));
    }
    private boolean enabled(String key){
        try{return (!experimental||prefs.getBoolean(Compatibility.KEY,false))&&prefs.getBoolean("master",true)&&prefs.getBoolean("core_enabled",false)&&prefs.getBoolean(key,false);}
        catch(Throwable e){return false;}
    }
    private boolean compatibilityEnabled(){try{return prefs.getBoolean(Compatibility.KEY,false);}catch(Throwable e){return false;}}
    private Class<?> type(String d)throws ClassNotFoundException{
        switch(d){case "V":return void.class;case "Z":return boolean.class;case "I":return int.class;}
        return Class.forName(d.charAt(0)=='L'?d.substring(1,d.length()-1).replace('/','.'):d.replace('/','.'),false,loader);
    }
    private void install(CoreTargets.Target t)throws Exception{
        Class<?> c=Class.forName(t.cls,false,loader);Class<?>[] p=new Class<?>[t.params.length];
        for(int i=0;i<p.length;i++)p[i]=type(t.params[i]);
        Method m=c.getDeclaredMethod(t.method,p);
        if(m.getReturnType()!=type(t.returns))throw new NoSuchMethodException("return type");
        m.setAccessible(true);
        api.hook(m).intercept(chain->{
            // Discard stale per-thread context before any new keyset decision.
            if(t.guard.equals("keyset_start"))keysetPackage.remove();
            if(!enabled(t.key)){if(t.guard.equals("keyset_check"))keysetPackage.remove();return chain.proceed();}
            switch(t.guard){
                case "capability":
                    if(!CorePolicy.updateCapability((Integer)chain.getArg(1))||!CorePolicy.installation(Thread.currentThread().getStackTrace()))return chain.proceed();
                    break;
                case "apk":
                    if(!CorePolicy.apkParsing(Thread.currentThread().getStackTrace()))return chain.proceed();
                    break;
                case "split":
                    if(!CorePolicy.splitVerification(Thread.currentThread().getStackTrace()))return chain.proceed();
                    break;
                case "keyset_start":
                    if(chain.getArg(1)!=null||!CorePolicy.installation(Thread.currentThread().getStackTrace()))return chain.proceed();
                    // Bind the next check to this exact package object, never another package/thread.
                    keysetPackage.set(chain.getArg(0));break;
                case "keyset_check":
                    Object expected=keysetPackage.get();keysetPackage.remove();
                    if(expected==null||expected!=chain.getArg(0)||!CorePolicy.installation(Thread.currentThread().getStackTrace()))return chain.proceed();
                    break;
            }
            if(hits.add(t.id()))report("core hit "+t.key+" "+t.id());
            switch(t.value){case "true":return Boolean.TRUE;case "false":return Boolean.FALSE;case "one":return Integer.valueOf(1);default:return null;}
        });
    }
    private void deoptimize(String cls,String name){
        try{for(Method m:Class.forName(cls,false,loader).getDeclaredMethods())if(m.getName().equals(name)){
            if(!api.deoptimize(m))report("core caller deoptimization unavailable: "+cls+"#"+name);
        }}catch(Throwable e){report("core caller unavailable: "+cls+"#"+name);}
    }
    private void report(String s){api.log(Log.INFO,"HyperPriv",s);}
}
