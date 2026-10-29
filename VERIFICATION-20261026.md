# HyperPriv 本机逐项验证

日期：2026-10-26；本地最终版本：0.4.1-preview6-local / versionCode 13。未发布 GitHub。

本表覆盖62个布尔偏好，包括两个总控和实验兼容模式。方法匹配、模拟回调、真实入口命中及场景对照分别记录；“通过”只适用于注明的样本范围。

本轮修复前的失败记录保留在 advanced-install-results.json；分包和沿用旧签名的最终结论分别以 split-streamed-results.json、previous-signature-regression.json 为准。

三个文字/数值参数的边界、白名单及同步由策略和回调检查覆盖；未借此读取用户剪贴板。显示桌面图标沿用此前本机隐藏/恢复及MODULE_SETTINGS入口验证，本轮未重新改动。

| 分类 / 小菜单 | 功能 | 结论 | 证据和限制 |
| --- | --- | --- | --- |
| 全局 / 模块总开关 | 模块总开关 (`master`) | 开关边界检查通过 | 136个目标回调的总开关关闭恢复原调用；代理框架及手机独立进程，不代表每个功能的真实场景。 证据：`all-gates.txt` |
| 电量与显示 / 屏幕与锁屏 | 屏蔽安装器广告与推荐 (`ads`) | 仅匹配/回调；场景待验证 | 完成了安装器真实流程；测试时固定关闭广告项以隔离完成推荐开关。无广告投放样本的逐项开关对照。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 电量与显示 / 屏幕与锁屏 | 阻止安装资料与 APK 片段上传 (`upload`) | 真实入口已命中 | 本轮真实临时应用安装中记录对应入口命中；未逐项构造关闭/开启的联网、病毒或风险样本对照。 证据：`installer-runtime.txt` |
| 电量与显示 / 屏幕与锁屏 | 关闭安装器云端检测 (`cloud`) | 真实入口已命中 | 本轮真实临时应用安装中记录对应入口命中；未逐项构造关闭/开启的联网、病毒或风险样本对照。 证据：`installer-runtime.txt` |
| 电量与显示 / 屏幕与锁屏 | 跳过安装时病毒扫描 (`scan`) | 真实入口已命中 | 本轮真实临时应用安装中记录对应入口命中；未逐项构造关闭/开启的联网、病毒或风险样本对照。 证据：`installer-runtime.txt` |
| 电量与显示 / 屏幕与锁屏 | 解除安装器纯净模式与风险拦截 (`guards`) | 真实入口已命中 | 本轮真实临时应用安装中记录对应入口命中；未逐项构造关闭/开启的联网、病毒或风险样本对照。 证据：`installer-runtime.txt` |
| 电量与显示 / 屏幕与锁屏 | 解除安装频次限制 (`rate`) | 仅匹配/回调；场景待验证 | 未通过连续大量安装制造频次封禁；只完成方法及开关回调检查。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 电量与显示 / 屏幕与锁屏 | 隐藏安装完成后的安全守护推荐 (`completed_tip`) | 场景对照通过（见范围） | 完成页推荐显示→隐藏→恢复显示；三轮真实更新自建测试应用。 证据：`installer-results.json` |
| 电量与显示 / 屏幕与锁屏 | 移除无障碍与未知来源授权读秒 (`permission_timer`) | 场景对照通过（见范围） | 未知来源警告按钮读秒关闭→移除→恢复；未实际授予真实应用权限。无障碍授权页未独立完成同样对照。 证据：`permission-timer-results.json` |
| 电量与显示 / 屏幕与锁屏 | 移除 USB 安全设置读秒 (`usb_timer`) | 场景对照通过（见范围） | 关闭有读秒；开启后第一、二步“下一步”及第三步“允许”立即可用；再次关闭恢复读秒。未点最终允许。 证据：`remaining-pages-results.json` |
| 电量与显示 / 屏幕与锁屏 | 本地开启 USB 安装与安全设置 (`usb_local`) | 部分入口通过 | 真实 AdbInstallVerifyActivity 输入安全入口命中本地方法并0→1，已恢复；USB安装控制器及无账号/无SIM/离线组合未完整实测。 证据：`usb-local-results.json` |
| 电量与显示 / 屏幕与锁屏 | 显示锁屏休眠“永不”选项 (`never_sleep`) | 场景对照通过（见范围） | 实际锁屏设置的自动锁屏选项中无永不→有永不→无永不；未选择永不或进行长期待机。 证据：`never-sleep-results.json` |
| 电量与显示 / 屏幕与锁屏 | 阻止反诈应用列表上传与检测 (`app_list`) | 仅匹配/回调；场景待验证 | 未主动触发向云端发送真实应用列表，也未抓包；仅方法与开关回调检查。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 电量与显示 / 屏幕与锁屏 | 屏蔽风险应用通知 (`risk_notice`) | 仅匹配/回调；场景待验证 | 没有制造真实风险应用通知；仅方法与开关回调检查。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 电量与显示 / 屏幕与锁屏 | 抑制小米统计采集与上报 (`analytics`) | 真实入口已命中 | 最终版本开机后真实目标进程记录命中；没有整机抓包，不能证明所有上报/广告/缓存云控都已移除。 证据：`reboot5-module-log.txt` |
| 电量与显示 / 屏幕与锁屏 | 抑制 MSA 广告与列表上报 (`msa`) | 真实入口已命中 | 最终版本开机后真实目标进程记录命中；没有整机抓包，不能证明所有上报/广告/缓存云控都已移除。 证据：`reboot5-module-log.txt` |
| 电量与显示 / 屏幕与锁屏 | 屏蔽电量与性能云控 (`power_cloud`) | 真实入口已命中 | 最终版本开机后真实目标进程记录命中；没有整机抓包，不能证明所有上报/广告/缓存云控都已移除。 证据：`reboot5-module-log.txt` |
| 电量与显示 / 屏幕与锁屏 | 屏蔽 Joyose 性能云控 (`joyose_cloud`) | 真实入口已命中 | 最终版本开机后真实目标进程记录命中；没有整机抓包，不能证明所有上报/广告/缓存云控都已移除。 证据：`reboot5-module-log.txt` |
| 电量与显示 / 屏幕与锁屏 | 阻止电池与耗电统计上报 (`battery_report`) | 真实入口已命中 | 最终版本开机后真实目标进程记录命中；没有整机抓包，不能证明所有上报/广告/缓存云控都已移除。 证据：`reboot5-module-log.txt` |
| 电量与显示 / 屏幕与锁屏 | 解除温度限制屏幕刷新率分支 (`thermal_fps`) | 仅匹配/回调；场景待验证 | 没有人为升温；仅cookie253的分支隔离、方法和回调检查；不等同游戏锁帧解除。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 安装与授权 / 核心破解 | 启用核心破解 (`core_enabled`) | 开关边界检查通过 | 核心目标受核心总开关及模块总开关控制；本轮实际安装用例开启核心总控后进行。 证据：`all-gates.txt; core-install-results.json` |
| 安装与授权 / 核心破解 | 允许降级安装 (`core_downgrade`) | 场景对照通过（见范围） | 自建包版本2覆盖为1：拒绝→成功→拒绝。 证据：`core-install-results.json` |
| 安装与授权 / 核心破解 | 允许不同签名覆盖安装 (`core_update_signature`) | 场景对照通过（见范围） | 自建同包名不同签名覆盖：拒绝→成功→拒绝。 证据：`core-install-results.json` |
| 安装与授权 / 核心破解 | 跳过 APK 完整性校验 (`core_apk_integrity`) | 场景对照通过（见范围） | 自行改坏内容摘要的 V3 签名测试 APK：拒绝→成功→拒绝；未遍历所有签名方案。 证据：`advanced-install-results.json` |
| 安装与授权 / 核心破解 | 允许不同签名的分包 (`core_split_signature`) | 场景对照通过（见范围） | 最终 preview6-local：不同签名 base/split 拒绝→成功→拒绝，修正桥接栈帧判断后通过。 证据：`split-streamed-results.json` |
| 安装与授权 / 核心破解 | 停用系统安装验证代理 (`core_verification_agent`) | 仅匹配/回调；场景待验证 | 只有系统目标匹配和开关回调检查，未构造真实验证代理拦截的差异样本。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 安装与授权 / 核心破解 | 解除 resources.arsc 限制 (`core_resources`) | 场景对照通过（见范围） | 有效签名但压缩 resources.arsc 的自建包：-124拒绝→成功→恢复拒绝。 证据：`advanced-install-results.json` |
| 安装与授权 / 核心破解 | 允许不同签名共享 UID (`core_shared_uid`) | 场景对照通过（见范围） | 两个自建不同签名包：拒绝共享→成功且 UID 相同→恢复拒绝。 证据：`advanced-install-results.json` |
| 安装与授权 / 核心破解 | 覆盖安装时沿用原签名 (`core_previous_signature`) | 场景对照通过（见范围） | 开启不同签名覆盖后：本项关闭旧A变B、开启后保留A、再关闭变B；仅开启本项不会放过损坏 APK。 证据：`previous-signature-regression.json` |
| 安装与授权 / 核心破解 | 允许安装低 TargetSDK 应用 (`core_low_sdk`) | 对照条件不足 | 低TargetSDK22包关闭/开启均成功；开启有preparePackage命中，原系统未阻止样本，不能证明差异。 证据：`core-install-results.json` |
| 安装与授权 / 核心破解 | 放宽持久性应用安装检查 (`core_persistent`) | 对照条件不足 | 普通测试包的persistent标志被系统清除；开启有入口命中，但不能代替真正持久系统应用更新测试。 证据：`advanced-install-results.json` |
| 安装与授权 / 核心破解 | 放宽系统应用隔离检查 (`core_isolation`) | 仅匹配/回调；场景待验证 | 没有系统隔离拒绝样本；只完成方法与开关回调检查。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 安装与授权 / 核心破解 | 放宽系统应用安装与更新限制 (`core_system_update`) | 仅匹配/回调；场景待验证 | 未覆盖更新用户真实系统应用；只完成安装线程、标志恢复及方法/回调检查。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 安装与授权 / 核心破解 | 保留现有指纹配置 (`core_keep_fingerprint`) | 仅匹配/回调；场景待验证 | 未覆盖指纹配置或删除录入数据；只完成方法与开关回调检查。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 安装与授权 / 权限与 USB | 直接跳过授权警告页 (`permission_direct`) | 场景对照通过（见范围） | 受控启动真实已识别警告页，关闭时保留，开启后完成该页并记录命中；不是全部授权类型。 证据：`permission-direct-on.json` |
| 安装与授权 / 权限与 USB | 阻止静默关闭无障碍 (`accessibility_keep`) | 仅匹配/回调；场景待验证 | 未启用用户无障碍服务再等待自动撤销；只完成方法及回调检查。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 安装与授权 / 权限与 USB | 保留自启动授权 (`autostart_keep`) | 仅匹配/回调；场景待验证 | 未改动真实应用自启动授权并触发回收任务；只完成方法及回调检查。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 安装与授权 / 权限与 USB | 停用 USB 安装拦截 (`usb_intercept`) | 仅匹配/回调；场景待验证 | 未构造独立的USB风险安装拦截样本；ADB成功安装不等同其差异验证。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 安装与授权 / 权限与 USB | 悬浮窗权限直达 (`overlay_direct`) | 场景对照通过（见范围） | 由测试应用发起悬浮窗设置，关闭进列表，开启直达应用详情；未授予权限。 证据：`settings-navigation-results.json` |
| 安装与授权 / 权限与 USB | 无障碍权限直达 (`accessibility_direct`) | 场景对照通过（见范围） | 由含单一无障碍服务的测试应用发起设置，关闭进列表，开启直达该服务；未启用服务。 证据：`settings-navigation-results.json` |
| 隐私与上报 / 应用启动与通知 | 跳过跨应用启动弹窗 (`wake_popup`) | 仅匹配/回调；场景待验证 | 未构造真实有效的跨应用唤醒请求；只完成页面/按钮/请求边界及回调检查。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 隐私与上报 / 应用启动与通知 | 放宽链式启动检查 (`wake_checker`) | 仅匹配/回调；场景待验证 | 未复现真实系统链式启动拒绝；只完成方法与开关回调检查。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 隐私与上报 / SIM 安全保护 | SIM 安全保护免账号开启 (`sim_account`) | 仅匹配/回调；场景待验证 | 未退出账号、移除SIM或改变SIM PIN；只完成方法与开关回调检查。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 隐私与上报 / SIM 安全保护 | 隐藏 SIM 验证成功弹窗 (`sim_success`) | 仅匹配/回调；场景待验证 | 未触发真实SIM安全验证成功；只完成方法与开关回调检查。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 隐私与上报 / 环境与检查 | 停用安全中心 Root 检测 (`root_detection`) | 仅匹配/回调；场景待验证 | 安全中心本地Root入口已匹配并完成回调检查；未做Root业务页面开关对照；不是安全守护项。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 隐私与上报 / 环境与检查 | 阻止安全中心环境检测 (`environment_checks`) | 仅匹配/回调；场景待验证 | 已验证指定任务筛选及返回不可用的回调；未复现真实信任任务完整调用。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 隐私与上报 / 环境与检查 | 停用安全守护 Root 检测 (`guard_environment`) | 业务未适配；开关保存通过 | 开/关及重启后保存通过；安全守护3.0.7业务入口未定位，当前不执行Root拦截。 证据：`root-switch-results.json` |
| 隐私与上报 / 环境与检查 | 停用支付保护自动检查 (`safepay_scan`) | 场景对照通过（见范围） | 真实 GuardService 的受控进入/退出事件：关闭扫描、开启拦截、再关闭恢复；未登录、未付款、不是自然支付全链路。 证据：`payment-results.json` |
| 隐私与上报 / 隐私权限与提示 | 隐藏权限使用提示 (`permission_tips`) | 仅匹配/回调；场景待验证 | 未构造真实敏感权限提示差异；只完成指定调用上下文与回调检查。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 隐私与上报 / 隐私权限与提示 | 应用锁 PIN 键盘乱序 (`pin_shuffle`) | 仅匹配/回调；场景待验证 | 未进入或更改用户应用锁PIN；只完成方法与开关回调检查。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 隐私与上报 / 统计与上报 | 停用网络助手离线配置 (`network_offline`) | 仅匹配/回调；场景待验证 | 只核对离线配置构造标记与回调；未验证联网请求变化。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 安装与授权 / 应用管理 | 增加应用停用与启用入口 (`app_disable`) | 场景对照通过（见范围） | 真实应用详情：关闭无入口，开启后可停用临时工具并重新启用，关闭入口消失；其他菜单不再类型转换异常。 证据：`actual-app-menu-results.json` |
| 隐私与上报 / 隐私权限与提示 | 自定义剪贴板读取提示 (`clipboard_toast`) | 仅匹配/回调；场景待验证 | 未读取用户剪贴板或触发其提示，只有方法与开关回调检查。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 隐私与上报 / 隐私权限与提示 | 关闭按钮只关闭本次提示 (`clipboard_close_once`) | 仅匹配/回调；场景待验证 | 未点击真实剪贴板提示，只有功能依赖与回调检查。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 安装与授权 / 权限与 USB | 默认允许未知来源安装 (`framework_unknown_sources`) | 场景对照通过（见范围） | 测试应用真实 PackageManager.canRequestPackageInstalls() 返回 false→true→false；无持久授权。 证据：`remaining-pages-results.json` |
| 安装与授权 / 应用管理 | 允许冻结受保护应用 (`framework_freeze`) | 仅匹配/回调；场景待验证 | 未停用受保护系统应用；自建普通应用启停不能代替保护例外测试。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 安装与授权 / 应用管理 | 放宽系统应用停用限制 (`framework_disable_checks`) | 仅匹配/回调；场景待验证 | 未停用系统应用；自建普通应用启停不能代替系统限制测试。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 电量与显示 / 屏幕与锁屏 | 取消定期锁屏密码验证 (`framework_password_timeout`) | 仅匹配/回调；场景待验证 | 已确认system_server加载及方法匹配；未等待周期超时或修改设备时间；首次解锁已正常完成。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 隐私与上报 / 隐私权限与提示 | 跳过设备日志访问确认 (`framework_log_access`) | 仅匹配/回调；场景待验证 | 未将设备真实日志授予测试应用；只有方法、批准回调与开关检查。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 电量与显示 / 屏幕与锁屏 | 允许受保护画面截图与录屏 (`framework_capture`) | 场景对照通过（见范围） | 无保护蓝色测试画面正常；FLAG_SECURE 画面关闭黑屏、开启可见、关闭再次黑屏，并有真实框架命中；录屏及 DRM 未测。 证据：`capture-results.json` |
| 隐私与上报 / 隐私权限与提示 | 允许白名单后台读取剪贴板 (`framework_clipboard`) | 仅匹配/回调；场景待验证 | 空/非法名单不放宽，精确包名及读取操作29隔离回调通过；未读取用户剪贴板。 证据：`all-targets.txt; all-gates.txt; callbacks.txt` |
| 关于 / 实验兼容模式 | 实验兼容模式 (`experimental_compatibility`) | 严格模式已测；未知机型待测 | 本机使用严格匹配；默认关闭、选择、冲突、缺失项与关闭回调恢复检查通过；未知机型未实测。 证据：`host-check-summary.txt` |

