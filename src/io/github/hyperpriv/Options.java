package io.github.hyperpriv;

final class Options {
    static final String PREFS="hyperpriv", MODULE="io.github.hyperpriv";
    static final String[] TARGETS={"com.miui.packageinstaller","com.miui.securitycenter","com.miui.guardprovider","com.miui.analytics","com.android.settings","com.miui.powerkeeper","com.xiaomi.joyose","com.miui.systemAdSolution"};
    static final String[] KEYS={"master","ads","upload","cloud","scan","guards","rate","completed_tip","permission_timer","usb_timer","usb_local","never_sleep","app_list","risk_notice","analytics","msa","power_cloud","joyose_cloud","battery_report","thermal_fps"};
    static final String[] LABELS={"启用模块","安装器广告与应用推荐","安装资料与 APK 片段上传","安装器云端检测请求","安装时病毒扫描","安装器纯净模式与风险拦截","安装频次风控","安装完成后推荐开启安全守护","无障碍 / 未知来源授权读秒","USB 调试安全设置读秒","USB 安装与安全设置本地开启","解锁锁屏休眠“永不”选项","反诈应用列表上传与检测","风险应用通知","小米统计采集与上报","MSA 广告与预装列表上报","电量与性能云控","Joyose 性能云控","电池和耗电统计上报","高温限制屏幕刷新率"};
    static final String[] DETAILS={"关闭后恢复系统原有行为。","关闭安装前后推荐卡片和个性化广告入口。","阻止已识别的安装报告上传入口。","关闭这项联网检测，保留正常安装确认。","跳过安装器向安全组件发起的病毒扫描。","关闭已识别的纯净模式、备案、风险和账号校验分支。","取消按安装次数触发的这项风控限制。","隐藏安装完成页面底部的安全守护推荐项。","移除等待时间，保留用户勾选和确认。","移除三步提示的等待时间。","点击开启后跳过已识别的账号和联网验证流程，无需 SIM 卡。普通 USB 调试本身没有该联网分支。","显示原生永不选项，仍需手动选择，不绕过设备管理员限制。","阻止安全组件向指定反诈接口提交已安装应用列表。","不再推送已识别风险应用的这一类通知。","屏蔽已识别的统计记录采集、事件和上报入口。","屏蔽 MSA 已识别的广告联网、预装列表及统计联网入口。","屏蔽 PowerKeeper 指定云控读取及更新入口，部分已应用配置可能保留。","屏蔽已识别的云控同步和更新入口，不清除本地缓存。","屏蔽 PowerKeeper 统计、耗电数据和温度日志上传，以及安全中心电池健康自动上传开关。","屏蔽 PowerKeeper 指定的温度锁刷新率分支；不解除 CPU/GPU 温控、过热关机和充电保护。"};
    static final String[] SECTIONS={"总开关","安装器","安装器","安装器","安装器","安装器","安装器","安装器","开发者与显示","开发者与显示","开发者与显示","开发者与显示","隐私与上报","隐私与上报","隐私与上报","隐私与上报","电量与性能","电量与性能","电量与性能","电量与性能"};
    static boolean accepts(String key){for(String k:KEYS)if(k.equals(key))return true;return false;}
}
