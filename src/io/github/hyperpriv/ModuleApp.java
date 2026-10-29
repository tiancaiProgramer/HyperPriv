package io.github.hyperpriv;

import android.app.Application;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class ModuleApp extends Application {
    @Override public void onCreate(){
        super.onCreate();
        try{if(!Settings.cleanStoredKeys(Settings.local(this),true))Settings.status="旧配置清理未完成，现有设置保留";}
        catch(Throwable ignored){Settings.status="旧配置清理未完成，现有设置保留";}
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener(){
            public void onServiceBind(XposedService s){new Thread(()->Settings.bind(ModuleApp.this,s),"HyperPriv-settings").start();}
            public void onServiceDied(XposedService s){if(Settings.service==s){Settings.remote=null;Settings.service=null;Settings.status="框架连接已断开";Settings.notifyChanged();}}
        });
    }
}
