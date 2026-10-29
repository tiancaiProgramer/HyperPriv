package io.github.hyperpriv;

import android.app.Application;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.FileInputStream;
import java.security.MessageDigest;

/** AGPL-3.0-only. Hook targets researched from HyperCeiler and the device APKs.
 * No executable replacement, permanent settings edits or network permission.
 */
public final class HookEntry extends XposedModule {
    private static final String TAG="HyperPriv";
    private final AtomicBoolean installed=new AtomicBoolean();
    private SharedPreferences prefs;
    private ClassLoader loader;
    private boolean experimental;
    private final Set<String> hits=Collections.synchronizedSet(new HashSet<>());

    @Override public void onModuleLoaded(ModuleLoadedParam param){
        report("module loaded process="+param.getProcessName()+" system="+param.isSystemServer());
    }

    @Override public void onSystemServerStarting(SystemServerStartingParam param){
        new CoreHooks(this,param.getClassLoader()).install();
        try{new ExtraHooks(this,param.getClassLoader(),getRemotePreferences(Options.PREFS)).install("android",android.os.Build.VERSION.SDK_INT);}
        catch(Throwable e){report("extra system setup failed: "+e.getClass().getSimpleName());}
    }

    @Override public void onPackageReady(PackageReadyParam param){
        String pkg=param.getPackageName();
        if(!param.isFirstPackage()||!Arrays.asList(Options.TARGETS).contains(pkg))return;
        loader=param.getClassLoader();
        try {
            Method attach=Application.class.getDeclaredMethod("attach",Context.class);
            hook(attach).intercept(chain->{
                Object result=chain.proceed();
                if(installed.compareAndSet(false,true))install((Context)chain.getArg(0),pkg);
                return result;
            });
        }catch(Throwable e){report("initialization failed: "+e.getClass().getSimpleName());}
    }
    private void install(Context context,String pkg){
        try {
            long version=context.getPackageManager().getPackageInfo(pkg,0).getLongVersionCode();
            prefs=getRemotePreferences(Options.PREFS);
            boolean compatibility=prefs.getBoolean(Compatibility.KEY,false);
            String expected=Profiles.apkHash(pkg,version);
            boolean hashMismatch=expected!=null&&!expected.equals(apkHash(context.getApplicationInfo().sourceDir));
            if(hashMismatch&&!compatibility){
                report("unsupported APK hash: "+pkg);return;
            }
            experimental=hashMismatch||Compatibility.profileVersion(Profiles.ALL,pkg,version,compatibility)!=version;
            int count=0,skipped=0;
            for(Rule r:Compatibility.select(Profiles.ALL,pkg,version,compatibility)){
                try{installRule(r);count++;}catch(Throwable e){skipped++;report("skip "+r.id()+": "+e.getClass().getSimpleName());}
            }
            new ExtraHooks(this,loader,prefs).install(pkg,version,hashMismatch);
            if(experimental&&count>0)report("experimental compatibility "+pkg+" version="+version+"; definitions matched, behavior unverified");
            report("ready "+pkg+" version="+version+" installed="+count+" skipped="+skipped);
        }catch(Throwable e){report("setup failed: "+e.getClass().getSimpleName());}
    }
    private boolean enabled(String key){
        try{return prefs!=null&&(!experimental||prefs.getBoolean(Compatibility.KEY,false))&&prefs.getBoolean("master",true)&&prefs.getBoolean(key,true);}
        catch(Throwable e){return false;}
    }
    private void hit(Rule r){if(hits.add(r.id()))report("hit "+r.group+" "+r.id());}
    private void report(String s){Log.i(TAG,s);log(Log.INFO,TAG,s);}
    private Class<?> type(String d)throws ClassNotFoundException{
        switch(d){case "V":return void.class;case "Z":return boolean.class;case "I":return int.class;case "J":return long.class;case "B":return byte.class;case "S":return short.class;case "C":return char.class;case "F":return float.class;case "D":return double.class;}
        return Class.forName(d.charAt(0)=='L'?d.substring(1,d.length()-1).replace('/','.'):d.replace('/','.'),false,loader);
    }
    private void installRule(final Rule r)throws Exception{
        Class<?> c=Class.forName(r.cls,false,loader);Class<?>[] p=new Class<?>[r.params.length];
        for(int i=0;i<p.length;i++)p[i]=type(r.params[i]);
        Executable executable;
        if(r.method.equals("<init>"))executable=c.getDeclaredConstructor(p);
        else {Method m=c.getDeclaredMethod(r.method,p);if(m.getReturnType()!=type(r.returns))throw new NoSuchMethodException("return type");executable=m;}
        executable.setAccessible(true);
        hook(executable).intercept(chain->{
            if(!enabled(r.group))return chain.proceed();
            if(r.action.equals("sanitize_return")){
                Object result=chain.proceed();sanitize(result);hit(r);return result;
            }
            if(r.action.equals("sanitize_this")){
                Object result=chain.proceed();sanitize(chain.getThisObject());hit(r);return result;
            }
            if(r.action.equals("sanitize_arg")){
                sanitize(chain.getArg(0));hit(r);return chain.proceed();
            }
            if(r.action.equals("permission_state")){
                Object[] args=chain.getArgs().toArray();Bundle state=args[0] instanceof Bundle?new Bundle((Bundle)args[0]):new Bundle();
                state.putInt("KET_STEP_COUNT",0);args[0]=state;hit(r);return chain.proceed(args);
            }
            if(r.action.equals("usb_after")){
                Object result=chain.proceed();
                try{if(finishUsbTimer(chain.getThisObject()))hit(r);}catch(Throwable e){report("USB timer unavailable: "+e.getClass().getSimpleName());}
                return result;
            }
            if(r.action.equals("usb_install")){
                boolean applied=false;
                try{applied=enableLocalUsbInstall(chain.getThisObject());}
                catch(Throwable e){report("local USB install unavailable: "+e.getClass().getSimpleName());}
                if(applied){hit(r);return null;}
                return chain.proceed();
            }
            if(r.action.equals("usb_verify")){
                boolean applied=false;
                try{applied=completeLocalUsbVerify(chain.getThisObject());}
                catch(Throwable e){report("local USB verification unavailable: "+e.getClass().getSimpleName());}
                if(applied){hit(r);return null;}
                return chain.proceed();
            }
            if(r.action.equals("never_sleep")){
                Object result=chain.proceed();
                try{
                    Field oled=ownField(chain.getThisObject(),"mIsOled");
                    if(oled.getType()==boolean.class){oled.setBoolean(chain.getThisObject(),false);hit(r);}
                }catch(Throwable e){report("never sleep unavailable: "+e.getClass().getSimpleName());}
                return result;
            }
            if(r.action.equals("thermal_fps")){
                if(Policy.isThermalDisplayCookie((Integer)chain.getArg(1))){hit(r);return null;}
                return chain.proceed();
            }
            Object result;
            switch(r.action){
                case "false":result=Boolean.FALSE;break;
                case "zero":result=Integer.valueOf(0);break;
                case "none":result="NONE";break;
                case "empty_list":result=Collections.emptyList();break;
                case "cloud":result=localCloud();break;
                default:result=null;
            }
            hit(r);return result;
        });
    }
    private void field(Object o,String n,Object value)throws Exception{
        Field f=o.getClass().getField(n);f.set(o,value);
    }
    private void sanitize(Object o){
        if(o==null||!o.getClass().getName().equals("com.miui.packageInstaller.model.CloudParams"))return;
        try {
            if(enabled("ads")){field(o,"showAdsBefore",false);field(o,"showAdsAfter",false);}
            if(enabled("upload")){field(o,"cInfo",null);field(o,"trackingStrategy",null);}
            if(enabled("guards")){
                for(String n:new String[]{"installNotAllow","showSafeModeTip","useRegistrationPop","bundleApp","underAgeShield","useMiRiskyApp"})field(o,n,false);
                for(String n:new String[]{"registrationRestrictStatus","riskType","sourceRiskType","verifyAccount"})field(o,n,0);
                field(o,"registrationStatus",3);field(o,"inTargetSdkWhiteList",true);
                for(String n:new String[]{"registrationRestrictTip","registrationTip","riskWarningTip","riskDetailTip","secureWarningTip","enhanceModeWarningTip","guideOpenEnhanceModeTip","authFunction","rc"})field(o,n,null);
            }
        }catch(Throwable e){report("normalization unavailable: "+e.getClass().getSimpleName());}
    }
    private Object localCloud()throws Exception{
        Object cp=Class.forName("com.miui.packageInstaller.model.CloudParams",true,loader).getConstructor().newInstance();
        field(cp,"categoryAbbreviation","miui");field(cp,"useSystemAppRules",true);sanitize(cp);return cp;
    }
    private static Field ownField(Object o,String name)throws Exception{
        Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static String apkHash(String path)throws Exception{
        MessageDigest d=MessageDigest.getInstance("SHA-256");
        try(FileInputStream in=new FileInputStream(path)){
            byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);
        }
        StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();
    }
    private boolean enableLocalUsbInstall(Object controller)throws Exception{
        Context context=(Context)ownField(controller,"mActivity").get(controller);
        Class<?> utils=Class.forName("com.android.security.AdbUtils",false,loader);
        Method isEnabled=utils.getDeclaredMethod("isInstallEnabled",Context.class);
        Method setEnabled=utils.getDeclaredMethod("setInstallEnabled",Context.class,boolean.class);
        isEnabled.setAccessible(true);setEnabled.setAccessible(true);
        // This private controller method runs only after the user taps the USB install switch.
        // Preserve the original disable path and Android's host RSA authorization.
        if(Boolean.TRUE.equals(isEnabled.invoke(null,context)))return false;
        setEnabled.invoke(null,context,true);return true;
    }
    private boolean completeLocalUsbVerify(Object target)throws Exception{
        Activity activity=(Activity)target;
        Method accepted=target.getClass().getDeclaredMethod("L0");accepted.setAccessible(true);
        // The activity was entered through an explicit USB enable flow. No request is
        // sent, no server response or Xiaomi account identity is fabricated.
        accepted.invoke(target);activity.finish();return true;
    }
    private boolean finishUsbTimer(Object activity)throws Exception{
        Field timer=ownField(activity,"g"),handler=ownField(activity,"h");
        if(timer.getType()!=int.class||!Handler.class.isAssignableFrom(handler.getType()))throw new NoSuchFieldException("timer signature");
        Handler h=(Handler)handler.get(activity);
        Object button=ownField(activity,"e").get(activity);
        if(h==null||button==null)return false;
        h.removeMessages(100);timer.setInt(activity,1);Message msg=Message.obtain();msg.what=100;h.handleMessage(msg);msg.recycle();
        return true;
    }
}

