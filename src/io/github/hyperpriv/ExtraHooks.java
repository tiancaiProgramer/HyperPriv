package io.github.hyperpriv;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityManager;
import android.widget.*;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.*;
import java.util.*;

/** AGPL-3.0-only. Copyright (C) 2023–2026 HyperCeiler Contributions.
 * Device-specific API 102 adaptation of the referenced HyperCeiler features.
 * All added controls default to off. Failed optional actions retain original behavior.
 */
final class ExtraHooks {
    private final XposedInterface api;
    private final ClassLoader loader;
    private final SharedPreferences prefs;
    private final Set<String> hits=Collections.synchronizedSet(new HashSet<>());
    private final ThreadLocal<Integer> preparing=new ThreadLocal<>();
    private Field sharedAllow;
    private boolean originalSharedAllow;
    private volatile boolean sharedConfigured;
    private static final int MENU_STATE=0x485950;
    ExtraHooks(XposedInterface api,ClassLoader loader,SharedPreferences prefs){this.api=api;this.loader=loader;this.prefs=prefs;}
    void install(String pkg,long version){
        if(pkg.equals("android")&&(Build.VERSION.SDK_INT!=36||!CoreTargets.FINGERPRINT.equals(Build.FINGERPRINT)))return;
        int count=0,skipped=0;
        if(pkg.equals("android"))initializeSharedFlag();
        for(Rule r:ExtraTargets.ALL)if(r.pkg.equals(pkg)&&r.version==version){
            try{install(r);count++;}catch(Throwable e){skipped++;report("extra skip "+r.id()+": "+e.getClass().getSimpleName());}
        }
        if(pkg.equals("android")){
            deopt("com.android.server.pm.InstallPackageHelper","preparePackage");
            deopt("com.android.server.pm.PackageManagerServiceUtils","verifySignatures","canJoinSharedUserId");
            deopt("com.android.server.pm.ReconcilePackageUtils","reconcilePackages");
            deopt("com.android.internal.pm.pkg.parsing.ParsingPackageUtils","getSigningDetails");
            deopt("com.android.server.wm.WindowStateAnimator","createSurfaceLocked");
            deopt("com.android.server.wm.WindowManagerService","relayoutWindow");
            deopt("com.android.server.wm.DisplayContent","applySurfaceChangesTransaction");
        }
        if(pkg.equals("com.miui.securitycenter")&&version==40001275L)
            deopt("com.miui.antivirus.service.GuardService","J");
        report("extra ready "+pkg+" version="+version+" installed="+count+" skipped="+skipped);
    }
    private boolean enabled(String key){
        try{
            if(!prefs.getBoolean("master",true)||!prefs.getBoolean(key,false))return false;
            if(key.startsWith("core_")&&!prefs.getBoolean("core_enabled",false))return false;
            if(key.equals("core_previous_signature")&&!prefs.getBoolean("core_update_signature",false))return false;
            if(key.equals("clipboard_close_once")&&!prefs.getBoolean("clipboard_toast",false))return false;
            return true;
        }catch(Throwable e){return false;}
    }
    private void install(Rule r)throws Exception{
        Executable e=Reflect.executable(r,loader);
        api.hook(e).intercept(chain->{
            // Keep installation-thread context even if the low-SDK option is off.
            if(r.action.equals("low_sdk"))return prepare(chain,r);
            if(r.action.equals("shared_reconcile")){
                configureSharedFlag(enabled("core_shared_uid"));return chain.proceed();
            }
            if(!enabled(r.group)||(r.group.equals("core_shared_uid")&&!sharedConfigured))return chain.proceed();
            Object result;
            switch(r.action){
                case "null":hit(r);return null;
                case "true":hit(r);return true;
                case "false":hit(r);return false;
                case "privacy_tip":
                    if(!ExtraPolicy.containsFrame(Thread.currentThread().getStackTrace(),"com.miui.permcenter.o",null))return chain.proceed();
                    hit(r);return true;
                case "protected":
                    if(!ExtraPolicy.containsFrame(Thread.currentThread().getStackTrace(),"com.android.server.pm.PackageManagerService","setEnabledSettings"))return chain.proceed();
                    hit(r);return false;
                case "persistent":
                    if(preparing.get()==null||preparing.get()==0)return chain.proceed();hit(r);return false;
                case "split_digest":
                    if(!ExtraPolicy.parsingSplit(Thread.currentThread().getStackTrace()))return chain.proceed();hit(r);return true;
                case "shared_capability":
                    if(!sharedConfigured||!ExtraPolicy.sharedCapability((Integer)chain.getArg(1))||!CorePolicy.installation(Thread.currentThread().getStackTrace()))return chain.proceed();
                    hit(r);return true;
                case "shared_ancestor":
                    if(!sharedConfigured||!CorePolicy.installation(Thread.currentThread().getStackTrace()))return chain.proceed();hit(r);return true;
                case "shared_lineage":
                    if(sharedConfigured)try{updateSharedLineage(chain.getThisObject(),chain.getArg(0),r.method.equals("removePackage"));hit(r);}catch(Throwable error){failure(r,error);}
                    return chain.proceed();
                case "environment":
                    try{Object request=chain.getArg(0);
                        if(request!=null&&request.getClass().getName().equals("Ma.a")&&ExtraPolicy.environmentTask((Integer)Reflect.get(request,"a"))){
                            Reflect.call(request,"c",null,1001);hit(r);return null;
                        }
                    }catch(Throwable error){failure(r,error);}return chain.proceed();
                case "unknown_op":
                    result=chain.proceed();
                    if(result!=null&&ExtraPolicy.unknownInstallOp((Integer)chain.getArg(0)))try{Reflect.set(result,"mOpMode",0);hit(r);}catch(Throwable error){failure(r,error);}return result;
                case "clipboard":
                    if(ExtraPolicy.clipboardReadOp((Integer)chain.getArg(0))&&ExtraPolicy.clipboardAllowed(prefs.getString("clipboard_packages",""),(String)chain.getArg(1))){hit(r);return true;}return chain.proceed();
                case "log_access":
                    try{Reflect.call(chain.getThisObject(),"onAccessApprovedForClient",chain.getArg(0));hit(r);return null;}catch(Throwable error){failure(r,error);return chain.proceed();}
                case "capture_secure":
                    if(ExtraPolicy.containsFrame(Thread.currentThread().getStackTrace(),"com.android.server.wm.WindowStateAnimator","createSurfaceLocked")
                        ||ExtraPolicy.containsFrame(Thread.currentThread().getStackTrace(),"com.android.server.wm.WindowStateAnimator","setInitialSurfaceControlProperties"))return chain.proceed();
                    hit(r);return false;
                case "capture_args":
                    try{Reflect.set(chain.getArg(0),"mCaptureSecureLayers",true);hit(r);}catch(Throwable error){failure(r,error);}return chain.proceed();
                case "capture_display":
                    Object[] displayArgs=chain.getArgs().toArray();displayArgs[1]=true;hit(r);return chain.proceed(displayArgs);
                case "capture_virtual":
                    if((Integer)chain.getArg(2)>=10000&&chain.getArg(1)==null)return chain.proceed();
                    Object[] virtualArgs=chain.getArgs().toArray();virtualArgs[6]=((Integer)virtualArgs[6])|4;hit(r);return chain.proceed(virtualArgs);
                case "installer_system":
                    ApplicationInfo info=(ApplicationInfo)chain.getArg(0);if(info==null)return chain.proceed();
                    int oldFlags=info.flags;info.flags|=ApplicationInfo.FLAG_SYSTEM;
                    try{hit(r);return chain.proceed();}finally{info.flags=ExtraPolicy.restoreFlag(info.flags,oldFlags,ApplicationInfo.FLAG_SYSTEM);}
                case "previous_signature":
                    try{Object previous=previousSigningDetails((String)Reflect.call(chain.getArg(1),"getPackageName"));
                        if(previous!=null){Object success=Reflect.call(chain.getArg(0),"success",previous);hit(r);return success;}
                    }catch(Throwable error){failure(r,error);}return chain.proceed();
                case "overlay_direct":
                    try{Activity activity=(Activity)chain.getThisObject();Intent intent=activity.getIntent();
                        if(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION.equals(intent.getAction())&&intent.getData()!=null&&"package".equals(intent.getData().getScheme())){
                            Reflect.set(activity,"initialFragmentName","com.android.settings.applications.appinfo.DrawOverlayDetails");hit(r);
                        }
                    }catch(Throwable error){failure(r,error);}return chain.proceed();
                case "offline_ctor":
                    Object[] args=chain.getArgs().toArray();if(args.length>1&&args[1] instanceof Boolean){args[1]=false;hit(r);}return chain.proceed(args);
                case "app_menu":
                    if(r.method.equals("onOptionsItemSelected")&&chain.getArg(0) instanceof MenuItem&&((MenuItem)chain.getArg(0)).getItemId()==MENU_STATE){
                        try{changeAppState((Activity)chain.getThisObject());hit(r);return true;}catch(Throwable error){failure(r,error);return chain.proceed();}
                    }
                    result=chain.proceed();try{configureAppMenu((Activity)chain.getThisObject(),(Menu)chain.getArg(0));hit(r);}catch(Throwable error){failure(r,error);}return result;
                default:
                    result=chain.proceed();
                    try{
                        switch(r.action){
                            case "finish":((Activity)chain.getThisObject()).finish();break;
                            case "pin_shuffle":if(!shufflePin((ViewGroup)chain.getThisObject()))return result;break;
                            case "permission_direct":if(!postPermissionAcceptance(chain.getThisObject(),r))return result;break;
                            case "wake_popup":if(!acceptWakeDialog(chain.getThisObject(),chain.getArg(0),r))return result;break;
                            case "paste_toast":if((Integer)chain.getArg(1)!=1||!(result instanceof View))return result;customizePaste((View)result);break;
                            case "accessibility_direct":if(!openAccessibility((Activity)chain.getThisObject()))return result;break;
                        }
                        hit(r);
                    }catch(Throwable error){failure(r,error);}return result;
            }
        });
    }
    private Object prepare(XposedInterface.Chain chain,Rule rule)throws Throwable{
        int depth=preparing.get()==null?0:preparing.get();preparing.set(depth+1);
        Object args=null;Integer flags=null;
        if(enabled("core_low_sdk"))try{args=Reflect.get(chain.getArg(0),"mInstallArgs");if(args!=null){flags=(Integer)Reflect.get(args,"mInstallFlags");Reflect.set(args,"mInstallFlags",flags|ExtraPolicy.LOW_SDK_FLAG);hit(rule);}}catch(Throwable error){failure(rule,error);}
        try{return chain.proceed();}finally{
            if(args!=null&&flags!=null)try{int current=(Integer)Reflect.get(args,"mInstallFlags");Reflect.set(args,"mInstallFlags",ExtraPolicy.restoreFlag(current,flags,ExtraPolicy.LOW_SDK_FLAG));}catch(Throwable error){failure(rule,error);}
            if(depth==0)preparing.remove();else preparing.set(depth);
        }
    }
    private Object previousSigningDetails(String pkg)throws Exception{
        Application app=(Application)Reflect.call(Class.forName("android.app.ActivityThread",false,loader),"currentApplication");
        if(app==null)return null;
        PackageInfo installed;
        try{installed=app.getPackageManager().getPackageInfo(pkg,PackageManager.GET_SIGNING_CERTIFICATES);}catch(PackageManager.NameNotFoundException e){return null;}
        if(installed.signingInfo==null)return null;
        Object details=Reflect.get(installed.signingInfo,"mSigningDetails");
        Constructor<?> copy=details.getClass().getDeclaredConstructor(details.getClass());copy.setAccessible(true);return copy.newInstance(details);
    }
    private void initializeSharedFlag(){
        try{sharedAllow=Reflect.field(Class.forName("com.android.server.pm.ReconcilePackageUtils",false,loader),"ALLOW_NON_PRELOADS_SYSTEM_SHAREDUIDS");originalSharedAllow=sharedAllow.getBoolean(null);configureSharedFlag(enabled("core_shared_uid"));}
        catch(Throwable error){report("extra shared UID configuration unavailable: "+error.getClass().getSimpleName());}
    }
    private synchronized void configureSharedFlag(boolean enabled){
        if(sharedAllow==null){sharedConfigured=false;return;}
        try{
            boolean wanted=enabled||originalSharedAllow;
            if(sharedAllow.getBoolean(null)!=wanted){
                Field access=Reflect.field(Field.class,"accessFlags");int flags=access.getInt(sharedAllow);access.setInt(sharedAllow,flags&~Modifier.FINAL);
                try{sharedAllow.setBoolean(null,wanted);}finally{access.setInt(sharedAllow,flags);}
            }
            sharedConfigured=enabled;
        }catch(Throwable error){sharedConfigured=false;report("extra shared UID flag unavailable: "+error.getClass().getSimpleName());}
    }
    private void updateSharedLineage(Object shared,Object target,boolean removing)throws Exception{
        if(target==null||(((Integer)Reflect.get(shared,"uidFlags"))&ApplicationInfo.FLAG_SYSTEM)!=0)return;
        Object old=Reflect.call(shared,"getSigningDetails"),packages=Reflect.get(shared,"mPackages"),replacement=null;boolean contained=false;
        int size=(Integer)Reflect.call(packages,"size");
        for(int i=0;i<size;i++){
            Object p=Reflect.call(packages,"valueAt",i);if(p.equals(target)){contained=true;if(removing)continue;p=target;}
            Object details=Reflect.call(p,"getSigningDetails");
            if(Boolean.TRUE.equals(Reflect.call(details,"checkCapability",old,0))||Boolean.TRUE.equals(Reflect.call(old,"checkCapability",details,0)))return;
            replacement=replacement==null?details:Reflect.call(replacement,"mergeLineageWith",details,2);
        }
        if(contained&&replacement!=null)Reflect.set(Reflect.get(shared,"signatures"),"mSigningDetails",replacement);
    }
    private boolean postPermissionAcceptance(Object fragment,Rule r)throws Exception{
        Activity activity=(Activity)Reflect.call(fragment,"getActivity");
        if(activity==null)return false;
        new Handler(activity.getMainLooper()).post(()->{if(enabled(r.group)&&!activity.isFinishing())try{Reflect.call(fragment,"h0",true);}catch(Throwable e){failure(r,e);}});return true;
    }
    private boolean acceptWakeDialog(Object owner,Object dialog,Rule r)throws Exception{
        Activity activity=(Activity)owner;if(activity.isFinishing()||activity.getIntent().getIntExtra("dialogType",-1)==10034)return false;
        // Only ConfirmStartActivity's own allow-once button (id 3). Retains its
        // input validation and original click handler, without accepting other dialogs.
        View allow=(View)Reflect.call(dialog,"getButton",3);
        if(allow==null||allow.getId()!=3)allow=activity.findViewById(3);
        if(allow==null)throw new NoSuchFieldException("allow once button");
        View button=allow;return button.post(()->{if(enabled(r.group)&&!activity.isFinishing())button.performClick();});
    }
    private boolean shufflePin(ViewGroup parent){
        if(parent.getChildCount()!=4)return false;
        List<ViewGroup> rows=new ArrayList<>();List<List<View>> original=new ArrayList<>();List<View> numbers=new ArrayList<>();
        for(int row=0;row<4;row++){
            if(!(parent.getChildAt(row) instanceof ViewGroup))return false;ViewGroup group=(ViewGroup)parent.getChildAt(row);if(group.getChildCount()!=3)return false;
            rows.add(group);List<View> views=new ArrayList<>();for(int col=0;col<3;col++){View view=group.getChildAt(col);views.add(view);if(row!=3||col==1)numbers.add(view);}original.add(views);
        }
        if(numbers.size()!=10)return false;Collections.shuffle(numbers,new java.security.SecureRandom());
        try{for(ViewGroup row:rows)row.removeAllViews();int n=0;for(int row=0;row<4;row++)for(int col=0;col<3;col++)rows.get(row).addView(row==3&&col!=1?original.get(row).get(col):numbers.get(n++));}
        catch(RuntimeException error){for(ViewGroup row:rows)row.removeAllViews();for(int row=0;row<4;row++)for(View view:original.get(row))rows.get(row).addView(view);return false;}return true;
    }
    private void customizePaste(View view){
        view.setAlpha(Math.max(0,Math.min(100,prefs.getInt("clipboard_opacity",100)))/100f);
        int id=view.getResources().getIdentifier("closeButton","id","android");View close=id==0?null:view.findViewById(id);if(!(close instanceof TextView))return;
        String text=prefs.getString("clipboard_close_text","").trim();if(!text.isEmpty())((TextView)close).setText(text);
        if(enabled("clipboard_close_once"))close.setOnClickListener(v->{view.setVisibility(View.GONE);try{((WindowManager)view.getContext().getSystemService(Context.WINDOW_SERVICE)).removeView(view);}catch(RuntimeException ignored){}});
    }
    private boolean openAccessibility(Activity activity)throws Exception{
        Intent from=activity.getIntent();if(!android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS.equals(from.getAction()))return false;
        String pkg=(String)Reflect.get(from,"mSenderPackageName");if(pkg==null||pkg.equals(activity.getPackageName()))return false;
        AccessibilityManager manager=(AccessibilityManager)activity.getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> matches=new ArrayList<>();for(AccessibilityServiceInfo service:manager.getInstalledAccessibilityServiceList())if(pkg.equals(service.getResolveInfo().serviceInfo.packageName))matches.add(service);
        if(matches.size()!=1)return false;
        AccessibilityServiceInfo service=matches.get(0);String name=service.getResolveInfo().serviceInfo.name;
        Bundle args=new Bundle();args.putParcelable("component_name",new ComponentName(pkg,name));args.putString("package",pkg);args.putString("preference_key",pkg+"/"+name);args.putString("summary",service.loadDescription(activity.getPackageManager()));
        Intent target=new Intent().setClassName(activity.getPackageName(),"com.android.settings.SubSettings");target.putExtra(":settings:show_fragment","com.android.settings.accessibility.VolumeShortcutToggleAccessibilityServicePreferenceFragment");target.putExtra(":settings:show_fragment_args",args);target.putExtra(":settings:show_fragment_title",activity.getPackageManager().getApplicationLabel(activity.getPackageManager().getApplicationInfo(pkg,0)));target.putExtra(":settings:source_metrics",0);target.putExtra(":settings:show_fragment_title_resid",-1);activity.startActivity(target);return true;
    }
    private PackageInfo appInfo(Activity activity)throws Exception{
        Class<?> fragment=Class.forName("com.miui.appmanager.fragment.ApplicationsDetailsFragment",false,loader);
        for(Class<?> c=activity.getClass();c!=null;c=c.getSuperclass())for(Field f:c.getDeclaredFields())if(fragment.isAssignableFrom(f.getType())){
            f.setAccessible(true);Object object=f.get(activity);if(object!=null){Object value=Reflect.get(object,"D");if(value instanceof PackageInfo)return (PackageInfo)value;}
        }
        return null;
    }
    private void configureAppMenu(Activity activity,Menu menu)throws Exception{
        PackageInfo info=appInfo(activity);if(info==null)return;
        MenuItem item=menu.findItem(MENU_STATE);if(item==null)item=menu.add(0,MENU_STATE,10,"停用应用");
        item.setTitle(isAppEnabled(activity,info.packageName)?"停用应用":"启用应用");item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
    }
    private boolean isAppEnabled(Activity a,String pkg)throws PackageManager.NameNotFoundException{
        PackageManager pm=a.getPackageManager();int state=pm.getApplicationEnabledSetting(pkg);
        return state==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT?pm.getApplicationInfo(pkg,0).enabled:state==PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }
    private void changeAppState(Activity activity)throws Exception{
        PackageInfo info=appInfo(activity);if(info==null)throw new IllegalStateException("missing app details");
        boolean disabling=isAppEnabled(activity,info.packageName);String title=disabling?"停用应用":"启用应用";
        String name=activity.getPackageManager().getApplicationLabel(info.applicationInfo).toString();
        new AlertDialog.Builder(activity).setTitle(title).setMessage((disabling?"停用 ":"启用 ")+name+"？")
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton(android.R.string.ok,(d,w)->{
                try{activity.getPackageManager().setApplicationEnabledSetting(info.packageName,disabling?PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,0);activity.invalidateOptionsMenu();Toast.makeText(activity,"已保存应用状态",Toast.LENGTH_SHORT).show();}
                catch(RuntimeException e){Toast.makeText(activity,"系统未允许修改此应用状态",Toast.LENGTH_LONG).show();}
            }).show();
    }
    private void deopt(String cls,String...names){try{Set<String> wanted=new HashSet<>(Arrays.asList(names));for(Method m:Class.forName(cls,false,loader).getDeclaredMethods())if(wanted.contains(m.getName()))api.deoptimize(m);}catch(Throwable error){report("extra deoptimization unavailable: "+cls);}}
    private void hit(Rule r){if(hits.add(r.pkg+"#"+r.group+"#"+r.id()))report("extra hit "+r.group+" "+r.id());}
    private void failure(Rule r,Throwable e){String id="fail:"+r.id();if(hits.add(id))report("extra action unavailable "+r.id()+": "+e.getClass().getSimpleName());}
    private void report(String text){api.log(android.util.Log.INFO,"HyperPriv",text);}
}
