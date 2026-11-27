package io.github.hyperpriv;

import android.content.Context;
import android.content.SharedPreferences;
import io.github.libxposed.service.XposedService;

final class Settings {
    private static final String LEGACY_DUO = "appearance_duo_network";
    private static final String LEGACY_TOP_BATTERY = "appearance_top_battery_bar";
    private static final String LEGACY_CUTOUT_BATTERY = "appearance_cutout_ring";
    static volatile XposedService service;
    static volatile SharedPreferences remote;
    static volatile String status="等待 LSPosed 连接";
    static volatile Runnable changed;
    static volatile java.util.List<String> scopes;
    static SharedPreferences local(Context c){return c.getSharedPreferences("options",0);}
    static boolean storedKey(String key,boolean local){
        return Options.accepts(key)||java.util.Arrays.asList(ExtraFeatures.STRING_KEYS).contains(key)
            ||key.equals("clipboard_opacity")||(!local&&key.equals(Appearance.REFRESH_REQUEST))||Appearance.isParameter(key)||(local&&key.equals("initialized"));
    }
    static int removeObsolete(SharedPreferences prefs,SharedPreferences.Editor editor,boolean local){
        int removed=0;
        for(String key:new java.util.ArrayList<>(prefs.getAll().keySet()))if(!storedKey(key,local)){
            editor.remove(key);removed++;
        }
        return removed;
    }
    static boolean cleanStoredKeys(SharedPreferences prefs,boolean local){
        SharedPreferences.Editor editor=prefs.edit();
        int removed=removeObsolete(prefs,editor,local);
        String clockPosition=Appearance.Value.CLOCK_POSITION.key;
        if(Integer.valueOf(3).equals(prefs.getAll().get(clockPosition))){
            editor.remove(clockPosition);
            removed++;
        }
        if(legacyDuoEnabled(prefs))editor.putInt(Appearance.Value.SIGNAL.key,Appearance.DUO_SIGNAL_STYLE);
        int battery=appearance(prefs,Appearance.Value.BATTERY);
        if(battery!=readAppearance(prefs,Appearance.Value.BATTERY))
            editor.putInt(Appearance.Value.BATTERY.key,battery);
        return removed==0||editor.commit();
    }
    static synchronized void bind(Context c,XposedService s){
        try {
            SharedPreferences own=local(c), received=s.getRemotePreferences(Options.PREFS);
            SharedPreferences.Editor init=own.edit();
            if(!own.getBoolean("initialized",false)){
                for(String k:Options.KEYS)init.putBoolean(k,received.getBoolean(k,RuntimeProfile.defaultValue(k)));
                for(String k:ExtraFeatures.STRING_KEYS)init.putString(k,received.getString(k,""));
                init.putInt("clipboard_opacity",received.getInt("clipboard_opacity",100));
                for(Appearance.Value value:Appearance.Value.ALL)init.putInt(value.key,appearance(received,value,c));
                init.putBoolean("initialized",true).commit();
            }
            service=s;remote=received;sync(c);
            try{scopes=s.getScope();}catch(Throwable ignored){scopes=null;}
            status=s.getFrameworkName()+" "+s.getFrameworkVersion()+" · API "+s.getApiVersion();
        }catch(Throwable e){service=null;remote=null;status="框架连接失败："+e.getClass().getSimpleName();}
        notifyChanged();
    }
    static synchronized boolean sync(Context c){
        if(remote==null)return false;
        try {
            SharedPreferences.Editor e=remote.edit();
            removeObsolete(remote,e,false);
            for(String k:Options.KEYS)e.putBoolean(k,RuntimeProfile.allowed(k)&&local(c).getBoolean(k,RuntimeProfile.defaultValue(k)));
            for(String k:ExtraFeatures.STRING_KEYS)e.putString(k,local(c).getString(k,""));
            e.putInt("clipboard_opacity",Math.max(0,Math.min(100,local(c).getInt("clipboard_opacity",100))));
            SharedPreferences own=local(c);
            for(Appearance.Value value:Appearance.Value.ALL){
                int selected=appearance(own,value,c);
                if(value==Appearance.Value.BATTERY&&usesLegacyBatteryFrame(own))
                    own.edit().putInt(value.key,selected).commit();
                e.putInt(value.key,selected);
            }
            return e.commit();
        }catch(Throwable e){status="设置已本地保存，等待框架重连";return false;}
    }
    static synchronized boolean requestAppearanceRefresh(Context context){
        if(!sync(context))return false;
        try{
            int request=remote.getInt(Appearance.REFRESH_REQUEST,0);
            return remote.edit().putInt(Appearance.REFRESH_REQUEST,request+1).commit();
        }catch(RuntimeException error){return false;}
    }
    static void set(Context c,String k,boolean v){
        if(!Options.accepts(k)||!RuntimeProfile.allowed(k))throw new IllegalArgumentException(k);
        local(c).edit().putBoolean(k,v).putBoolean("initialized",true).commit();sync(c);
    }
    static void setText(Context c,String key,String value){
        if(!java.util.Arrays.asList(ExtraFeatures.STRING_KEYS).contains(key))throw new IllegalArgumentException(key);
        if(key.equals("clipboard_packages"))value=ExtraPolicy.normalizePackages(value);
        if(key.equals("clipboard_close_text")&&value.length()>20)throw new IllegalArgumentException("按钮文字最多 20 个字");
        local(c).edit().putString(key,value).commit();sync(c);
    }
    static void setOpacity(Context c,int opacity){
        local(c).edit().putInt("clipboard_opacity",Math.max(0,Math.min(100,opacity))).commit();sync(c);
    }
    static int appearance(SharedPreferences preferences,Appearance.Value value){
        return appearance(preferences,value,null);
    }
    static int appearance(SharedPreferences preferences,Appearance.Value value,Context context){
        if(value==Appearance.Value.SIGNAL&&legacyDuoEnabled(preferences))return Appearance.DUO_SIGNAL_STYLE;
        int selected=readAppearance(preferences,value);
        if(value==Appearance.Value.BATTERY&&selected<Appearance.BATTERY_TOP_STYLE){
            if(legacyEnabled(preferences,LEGACY_CUTOUT_BATTERY))return Appearance.BATTERY_CUTOUT_STYLE;
            if(legacyEnabled(preferences,LEGACY_TOP_BATTERY))return Appearance.BATTERY_TOP_STYLE;
            if(context!=null&&usesLegacyBatteryFrame(preferences)){
                try{return Appearance.migrateBatteryFrameStyle(NativeBatteryStyle.current(context));}
                catch(SecurityException ignored){return selected;}
            }
        }
        return selected;
    }
    private static boolean usesLegacyBatteryFrame(SharedPreferences prefs){
        try{return prefs.getInt(Appearance.Value.BATTERY.key,0)==Appearance.LEGACY_BATTERY_FRAME_STYLE;}
        catch(ClassCastException error){return false;}
    }
    private static int readAppearance(SharedPreferences prefs,Appearance.Value value){
        try{return value.normalize(prefs.getInt(value.key,value.initial));}
        catch(ClassCastException error){return value.initial;}
    }
    private static boolean legacyDuoEnabled(SharedPreferences prefs){return legacyEnabled(prefs,LEGACY_DUO);}
    private static boolean legacyEnabled(SharedPreferences prefs,String key){
        try{return prefs.getInt(key,0)==1;}
        catch(ClassCastException error){return false;}
    }
    static synchronized void setAppearance(Context context,Appearance.Value value,int selected){
        SharedPreferences.Editor editor=local(context).edit().putInt(value.key,value.normalize(selected));
        if(value==Appearance.Value.SIGNAL)editor.remove(LEGACY_DUO);
        if(value==Appearance.Value.BATTERY)editor.remove(LEGACY_TOP_BATTERY).remove(LEGACY_CUTOUT_BATTERY);
        editor.commit();sync(context);
    }
    static void refreshScopes(){
        XposedService current=service;if(current!=null)try{scopes=current.getScope();}catch(Throwable ignored){scopes=null;}
    }
    static String scopeStatus(){
        java.util.List<String> current=scopes;if(current==null)return "作用域状态待框架确认";
        java.util.ArrayList<String> missing=new java.util.ArrayList<>();
        for(UiCatalog.Component component:UiCatalog.COMPONENTS){
            if((component.pkg.equals(Appearance.HOME)||component.pkg.equals(Appearance.CAMERA))
                    &&(remote==null||!remote.getBoolean(component.pkg.equals(Appearance.HOME)?Appearance.ICONS:Appearance.WATERMARK,false)))continue;
            if(!RuntimeProfile.state().needsScope(component.pkg))continue;
            boolean present=current.contains(component.pkg)||(component.pkg.equals("android")&&current.contains("system"));
            if(!present)missing.add(component.title);
        }
        return missing.isEmpty()?"推荐作用域已勾选；系统选项改动后需重启手机":"待勾选作用域："+String.join("、",missing);
    }
    static void notifyChanged(){Runnable r=changed;if(r!=null)r.run();}
}

