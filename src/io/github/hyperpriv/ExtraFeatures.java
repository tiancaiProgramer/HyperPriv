package io.github.hyperpriv;

/** New optional features. All default to off; old preference keys remain unchanged. */
final class ExtraFeatures {
    static final class Feature {
        final String key,title,note,pkg;
        Feature(String key,String title,String note,String pkg){this.key=key;this.title=title;this.note=note;this.pkg=pkg;}
    }
    static final Feature[] ALL={
        new Feature("permission_direct","直接跳过授权警告页","开启后直接完成已识别的授权警告页；与只移除读秒分别控制。","com.miui.securitycenter"),
        new Feature("accessibility_keep","阻止静默关闭无障碍","阻止安全中心的已识别自动撤销入口。","com.miui.securitycenter"),
        new Feature("autostart_keep","保留自启动授权","阻止权限管理组件的自动关闭任务。","com.lbe.security.miui"),
        new Feature("usb_intercept","停用 USB 安装拦截","独立于开启 USB 安装时的联网验证。","com.miui.securitycenter"),
        new Feature("overlay_direct","悬浮窗权限直达","从应用发起授权时直接进入该应用的权限页。","com.android.settings"),
        new Feature("accessibility_direct","无障碍权限直达","仅当请求应用有一个明确的无障碍服务时直达其设置。","com.android.settings"),
        new Feature("wake_popup","跳过跨应用启动弹窗","仅处理“某应用想要打开另一应用”的专用页面。","com.miui.securitycenter"),
        new Feature("wake_checker","放宽链式启动检查","调整系统扩展服务的指定跨应用启动检查。","android"),
        new Feature("sim_account","SIM 安全保护免账号开启","保留锁屏密码与 SIM PIN 确认；跳过小米账号存在性检查。","com.miui.securitycenter"),
        new Feature("sim_success","隐藏 SIM 验证成功弹窗",null,"com.miui.securitycenter"),
        new Feature("root_detection","停用安全中心 Root 检测","含支付环境检查中的本地 Root 判断，仅作用于已识别的手机管家入口。","com.miui.securitycenter"),
        new Feature("environment_checks","阻止安全中心环境检测","阻止指定环境任务交给小米信任服务，向调用方返回不可用。","com.miui.securitycenter"),
        new Feature("guard_environment","停用安全守护 Root 检测","参考版本 3.0.7 尚未找到上游对应的 Root 检查入口。支付自动检查与安装扫描已有独立选项。","com.miui.guardprovider"),
        new Feature("safepay_scan","停用支付保护自动检查","覆盖进入支付场景后的自动扫描入口，以及已识别的自动扫描设置入口。","com.miui.securitycenter"),
        new Feature("permission_tips","隐藏权限使用提示","仅调整权限提示管理路径。","com.miui.securitycenter"),
        new Feature("pin_shuffle","应用锁 PIN 键盘乱序","只改变数字按键位置，保留原来的数字和点击行为。","com.miui.securitycenter"),
        new Feature("network_offline","停用网络助手离线配置","调整离线配置标记，不代表阻断全部联网请求。","com.miui.securitycenter"),
        new Feature("app_disable","增加应用停用与启用入口","在手机管家应用详情菜单中手动选择；不会自动停用应用。","com.miui.securitycenter"),
        new Feature("clipboard_toast","自定义剪贴板读取提示",null,"com.lbe.security.miui"),
        new Feature("clipboard_close_once","关闭按钮只关闭本次提示","配合“自定义剪贴板读取提示”使用。","com.lbe.security.miui"),
        new Feature("framework_unknown_sources","默认允许未知来源安装","放宽请求安装应用的检查，仍保留正常安装界面。","android"),
        new Feature("framework_freeze","允许冻结受保护应用","在手动启用或停用应用时放宽保护检查。","android"),
        new Feature("framework_disable_checks","放宽系统应用停用限制","调整本机系统扩展服务的停用检查。","android"),
        new Feature("framework_password_timeout","取消定期锁屏密码验证","需重启后使用；首次解锁和其他强认证场景仍由系统处理。","android"),
        new Feature("framework_log_access","跳过设备日志访问确认","系统已接受的日志访问请求会直接获准。","android"),
        new Feature("framework_capture","允许受保护画面截图与录屏","调整系统画面捕获入口；应用自绘、DRM 内容仍可能受限。","android"),
        new Feature("framework_clipboard","允许白名单后台读取剪贴板","只处理下方白名单中的完整包名，空白名单不放宽任何应用。","android"),
        new Feature("core_shared_uid","允许不同签名共享 UID","放宽安装时共享 UID 的签名检查；需重启后使用。","android"),
        new Feature("core_previous_signature","覆盖安装时沿用原签名","需同时开启不同签名覆盖；仅对已经安装的同包名应用生效。","android"),
        new Feature("core_low_sdk","允许安装低 TargetSDK 应用",null,"android"),
        new Feature("core_persistent","放宽持久性应用安装检查","只在当前安装线程准备应用时生效。","android"),
        new Feature("core_isolation","放宽系统应用隔离检查",null,"android"),
        new Feature("core_system_update","放宽系统应用安装与更新限制","同时需要系统框架与应用包管理组件作用域。","android"),
        new Feature("core_keep_fingerprint","保留现有指纹配置","仅调整系统复制指纹配置的指定分支。","android"),
    };
    static Feature get(String key){for(Feature f:ALL)if(f.key.equals(key))return f;return null;}
    static String[] keys(boolean core){java.util.ArrayList<String> out=new java.util.ArrayList<>();for(Feature f:ALL)if(f.key.startsWith("core_")==core)out.add(f.key);return out.toArray(new String[0]);}
    static final String[] STRING_KEYS={"clipboard_packages","clipboard_close_text"};
    static final String[] INT_KEYS={"clipboard_opacity"};
    private ExtraFeatures(){}
}
