package io.github.hyperpriv;

import android.content.Context;
import android.content.SharedPreferences;
import io.github.libxposed.service.XposedService;

final class Settings {
    static volatile XposedService service;
    static volatile SharedPreferences remote;
    static volatile String status="等待 LSPosed 连接";
    static volatile Runnable changed;
    static volatile java.util.List<String> scopes;
    static SharedPreferences local(Context c){return c.getSharedPreferences("options",0);}
    static boolean storedKey(String key,boolean local){
        return Options.accepts(key)||java.util.Arrays.asList(ExtraFeatures.STRING_KEYS).contains(key)
            ||key.equals("clipboard_opacity")||(local&&key.equals("initialized"));
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
        return removeObsolete(prefs,editor,local)==0||editor.commit();
    }
    static synchronized void bind(Context c,XposedService s){
        try {
            SharedPreferences own=local(c), received=s.getRemotePreferences(Options.PREFS);
            SharedPreferences.Editor init=own.edit();
            if(!own.getBoolean("initialized",false)){
                for(String k:Options.KEYS)init.putBoolean(k,received.getBoolean(k,Options.defaultValue(k)));
                for(String k:ExtraFeatures.STRING_KEYS)init.putString(k,received.getString(k,""));
                init.putInt("clipboard_opacity",received.getInt("clipboard_opacity",100));
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
            for(String k:Options.KEYS)e.putBoolean(k,local(c).getBoolean(k,Options.defaultValue(k)));
            for(String k:ExtraFeatures.STRING_KEYS)e.putString(k,local(c).getString(k,""));
            e.putInt("clipboard_opacity",Math.max(0,Math.min(100,local(c).getInt("clipboard_opacity",100))));
            return e.commit();
        }catch(Throwable e){status="设置已本地保存，等待框架重连";return false;}
    }
    static void set(Context c,String k,boolean v){
        if(!Options.accepts(k))throw new IllegalArgumentException(k);
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
    static void refreshScopes(){
        XposedService current=service;if(current!=null)try{scopes=current.getScope();}catch(Throwable ignored){scopes=null;}
    }
    static String scopeStatus(){
        java.util.List<String> current=scopes;if(current==null)return "作用域状态待框架确认";
        java.util.ArrayList<String> missing=new java.util.ArrayList<>();
        for(UiCatalog.Component component:UiCatalog.COMPONENTS){
            boolean present=current.contains(component.pkg)||(component.pkg.equals("android")&&current.contains("system"));
            if(!present)missing.add(component.title);
        }
        return missing.isEmpty()?"推荐作用域已勾选；系统选项改动后需重启手机":"待勾选作用域："+String.join("、",missing);
    }
    static void notifyChanged(){Runnable r=changed;if(r!=null)r.run();}
}

