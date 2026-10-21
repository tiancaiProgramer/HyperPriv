package io.github.hyperpriv;

import android.content.Context;
import android.content.SharedPreferences;
import io.github.libxposed.service.XposedService;

final class Settings {
    static volatile XposedService service;
    static volatile SharedPreferences remote;
    static volatile String status="等待 LSPosed 连接";
    static volatile Runnable changed;
    static SharedPreferences local(Context c){return c.getSharedPreferences("options",0);}
    static synchronized void bind(Context c,XposedService s){
        try {
            SharedPreferences own=local(c), received=s.getRemotePreferences(Options.PREFS);
            SharedPreferences.Editor init=own.edit();
            if(!own.getBoolean("initialized",false)){
                for(String k:Options.KEYS)init.putBoolean(k,received.getBoolean(k,true));
                init.putBoolean("initialized",true).commit();
            }
            service=s;remote=received;sync(c);
            status=s.getFrameworkName()+" "+s.getFrameworkVersion()+" · API "+s.getApiVersion();
        }catch(Throwable e){service=null;remote=null;status="框架连接失败："+e.getClass().getSimpleName();}
        notifyChanged();
    }
    static synchronized boolean sync(Context c){
        if(remote==null)return false;
        try {
            SharedPreferences.Editor e=remote.edit();
            for(String k:Options.KEYS)e.putBoolean(k,local(c).getBoolean(k,true));
            return e.commit();
        }catch(Throwable e){status="设置已本地保存，等待框架重连";return false;}
    }
    static void set(Context c,String k,boolean v){
        if(!Options.accepts(k))throw new IllegalArgumentException(k);
        local(c).edit().putBoolean(k,v).putBoolean("initialized",true).commit();sync(c);
    }
    static void notifyChanged(){Runnable r=changed;if(r!=null)r.run();}
}
