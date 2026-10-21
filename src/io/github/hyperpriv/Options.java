package io.github.hyperpriv;

final class Options {
    static final String PREFS="hyperpriv", MODULE="io.github.hyperpriv";
    static final String[] TARGETS={"com.miui.packageinstaller","com.miui.securitycenter","com.miui.guardprovider","com.miui.analytics"};
    static final String[] KEYS={"master","ads","upload","cloud","scan","guards","rate","permission_timer","usb_timer","app_list","risk_notice","analytics"};
    static final String[] LABELS={"启用模块","安装器广告与应用推荐","安装资料与 APK 片段上传","安装器云端检测请求","安装时病毒扫描","安装器纯净模式与风险拦截","安装频次风控","无障碍 / 未知来源授权读秒","USB 调试安全设置读秒","反诈应用列表上传与检测","风险应用通知","小米统计采集与上报"};
    static final String[] DETAILS={"关闭后恢复系统原有行为。","关闭安装前后推荐卡片和个性化广告入口。","阻止安装来源、包信息、权限和 APK 片段的安装报告。","关闭这项联网检测，保留正常安装确认。","跳过安装器向安全组件发起的病毒扫描。","关闭安装器的纯净模式、备案、风险和账号校验分支。","取消按安装次数触发的这项风控限制。","移除等待时间，保留用户勾选和确认。","移除三步提示的等待时间，保留各步确认。","阻止安全组件向这项反诈检测接口提交已安装应用列表。","不再推送已识别风险应用的这一类通知。","屏蔽已识别的统计记录采集、事件和上报入口。"};
    static boolean accepts(String key){for(String k:KEYS)if(k.equals(key))return true;return false;}
}
