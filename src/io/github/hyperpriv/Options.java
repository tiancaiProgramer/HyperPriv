package io.github.hyperpriv;

final class Options {
    static final String PREFS="hyperpriv", MODULE="io.github.hyperpriv";
    static final String[] TARGETS={"com.miui.packageinstaller","com.miui.securitycenter","com.miui.guardprovider","com.miui.analytics","com.android.settings","com.miui.powerkeeper","com.xiaomi.joyose","com.miui.systemAdSolution","com.lbe.security.miui"};
    private static final String[] APP_KEYS={"master","ads","upload","cloud","scan","guards","rate","completed_tip","permission_timer","usb_timer","usb_local","never_sleep","app_list","risk_notice","analytics","msa","power_cloud","joyose_cloud","battery_report","thermal_fps"};
    private static final String[] ORIGINAL_CORE_KEYS={"core_enabled","core_downgrade","core_update_signature","core_apk_integrity","core_split_signature","core_verification_agent","core_resources"};
    static final String[] CORE_KEYS=join(ORIGINAL_CORE_KEYS,ExtraFeatures.keys(true));
    static final String[] KEYS=join(join(APP_KEYS,CORE_KEYS),ExtraFeatures.keys(false));
    private static String[] join(String[] first,String[] second){
        String[] all=new String[first.length+second.length];
        System.arraycopy(first,0,all,0,first.length);System.arraycopy(second,0,all,first.length,second.length);return all;
    }
    static boolean defaultValue(String key){for(String old:APP_KEYS)if(old.equals(key))return true;return false;}
    static boolean accepts(String key){for(String k:KEYS)if(k.equals(key))return true;return false;}
}
