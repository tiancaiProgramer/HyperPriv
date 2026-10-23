package io.github.hyperpriv;

/** Presentation only. IDs in Options remain the persistent Hook preference keys. */
final class UiCatalog {
    static final class Component {
        final String title,pkg;
        Component(String title,String pkg){this.title=title;this.pkg=pkg;}
    }
    static final class Section {
        final String id,title,detail;
        final String[] keys;
        Section(String id,String title,String detail,String...keys){this.id=id;this.title=title;this.detail=detail;this.keys=keys;}
    }
    static final class Group {
        final String id,title,detail;
        final Section[] sections;
        Group(String id,String title,String detail,Section...sections){this.id=id;this.title=title;this.detail=detail;this.sections=sections;}
    }
    static final Component[] COMPONENTS={
        new Component("系统框架","android"),
        new Component("权限管理","com.lbe.security.miui"),
        new Component("应用包管理组件","com.miui.packageinstaller"),
        new Component("安全中心","com.miui.securitycenter"),
        new Component("设置","com.android.settings"),
        new Component("系统安全组件","com.miui.guardprovider"),
        new Component("电量与性能","com.miui.powerkeeper"),
        new Component("Joyose","com.xiaomi.joyose"),
        new Component("小米统计","com.miui.analytics"),
        new Component("MSA 广告服务","com.miui.systemAdSolution")
    };
    static final Group[] GROUPS={
        new Group("install","安装与授权","安装器、权限与 USB、核心破解、应用管理。",
            new Section("installer","安装器","广告推荐、安装检测与安装限制。",
                "ads","completed_tip","upload","cloud","scan","guards","rate"),
            new Section("permissions","权限与 USB","授权等待、授权保留、USB 与权限入口。",
                "permission_timer","permission_direct","accessibility_keep","autostart_keep","usb_timer","usb_local","usb_intercept","overlay_direct","accessibility_direct","framework_unknown_sources"),
            new Section("core","核心破解",null,Options.CORE_KEYS),
            new Section("apps","应用管理","增加手动停用入口，按需放宽系统保护限制。","app_disable","framework_freeze","framework_disable_checks")),
        new Group("privacy","隐私与上报","应用启动、环境检查、隐私提示与指定上传入口。",
            new Section("app_list","应用启动与通知",null,"app_list","risk_notice","wake_popup","wake_checker"),
            new Section("checks","环境与检查",null,"root_detection","environment_checks","guard_environment","safepay_scan"),
            new Section("privacy_tools","隐私权限与提示",null,"permission_tips","pin_shuffle","clipboard_toast","clipboard_close_once","framework_clipboard","framework_log_access"),
            new Section("sim","SIM 安全保护",null,"sim_account","sim_success"),
            new Section("reports","统计与上报",null,"analytics","msa","battery_report","network_offline")),
        new Group("display","电量与显示","性能云控、屏幕与锁屏选项。",
            new Section("performance","性能云控",null,"power_cloud","joyose_cloud"),
            new Section("screen","屏幕与锁屏",null,"thermal_fps","never_sleep","framework_capture","framework_password_timeout"))
    };
    static String target(String key){
        ExtraFeatures.Feature extra=ExtraFeatures.get(key);if(extra!=null)return extra.pkg;
        switch(key){
            case "ads":case "completed_tip":case "upload":case "cloud":case "scan":case "guards":case "rate":return "com.miui.packageinstaller";
            case "permission_timer":case "usb_timer":case "risk_notice":return "com.miui.securitycenter";
            case "usb_local":case "never_sleep":return "com.android.settings";
            case "app_list":return "com.miui.guardprovider";
            case "power_cloud":case "battery_report":case "thermal_fps":return "com.miui.powerkeeper";
            case "joyose_cloud":return "com.xiaomi.joyose";
            case "analytics":return "com.miui.analytics";
            case "msa":return "com.miui.systemAdSolution";
            default:throw new IllegalArgumentException(key);
        }
    }
    static String label(String key){
        ExtraFeatures.Feature extra=ExtraFeatures.get(key);if(extra!=null)return extra.title;
        switch(key){
            case "ads":return "屏蔽安装器广告与推荐";
            case "completed_tip":return "隐藏安装完成后的安全守护推荐";
            case "upload":return "阻止安装资料与 APK 片段上传";
            case "cloud":return "关闭安装器云端检测";
            case "scan":return "跳过安装时病毒扫描";
            case "guards":return "解除安装器纯净模式与风险拦截";
            case "rate":return "解除安装频次限制";
            case "permission_timer":return "移除无障碍与未知来源授权读秒";
            case "usb_timer":return "移除 USB 安全设置读秒";
            case "usb_local":return "本地开启 USB 安装与安全设置";
            case "never_sleep":return "显示锁屏休眠“永不”选项";
            case "app_list":return "阻止反诈应用列表上传与检测";
            case "risk_notice":return "屏蔽风险应用通知";
            case "power_cloud":return "屏蔽电量与性能云控";
            case "battery_report":return "阻止电池与耗电统计上报";
            case "thermal_fps":return "解除温度限制屏幕刷新率分支";
            case "joyose_cloud":return "屏蔽 Joyose 性能云控";
            case "analytics":return "抑制小米统计采集与上报";
            case "msa":return "抑制 MSA 广告与列表上报";
            default:throw new IllegalArgumentException(key);
        }
    }
    static String note(String key){
        ExtraFeatures.Feature extra=ExtraFeatures.get(key);if(extra!=null)return extra.note;
        switch(key){
            case "usb_local":return "需设置与安全中心作用域；无账号 / 无 SIM / 离线开启仍待场景实测。";
            case "scan":return "关闭后不再通过此入口检查有害安装包。";
            case "guards":return "关闭已识别的纯净模式、备案、风险和账号校验分支。";
            case "power_cloud":case "joyose_cloud":return "已经应用或缓存的云控不保证全部回退。";
            case "battery_report":return "同时需要安全中心和电量与性能作用域。";
            case "thermal_fps":return "仅屏幕刷新率；CPU/GPU 温控、过热关机与充电保护保留。";
            case "never_sleep":return "仍需在系统锁屏设置中手动选择“永不”。";
            default:return null;
        }
    }
    static String versions(String pkg){
        switch(pkg){
            case "android":return "Android 16 · OS3.0.304.0.WMVCNXM 的已适配构建";
            case "com.lbe.security.miui":return "1.9.2";
            case "com.miui.packageinstaller":return "5.3.6.0.1-20250825F";
            case "com.miui.securitycenter":return "12.6.8-260609.0.1 / 12.7.5-260709.0.1";
            case "com.miui.guardprovider":return "2.5.6-20251106.0 / 3.0.7-20260708.0";
            case "com.android.settings":return "16 · 仅已适配的安装包构建";
            case "com.miui.powerkeeper":return "4.2.00";
            case "com.xiaomi.joyose":return "2.4.92";
            case "com.miui.analytics":return "6.31.0";
            case "com.miui.systemAdSolution":return "2025.07.19.06-release";
            default:throw new IllegalArgumentException(pkg);
        }
    }
    private UiCatalog(){}
}
