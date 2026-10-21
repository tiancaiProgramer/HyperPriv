HyperPriv 0.1.0 — 当前设备适配版

适配环境：HyperOS 3 / Android 16 / LSPosed API 102。
应用包管理组件 5.3.6.0.1-20250825F（53601）
安全中心 12.6.8-260609.0.1（40001268）
安全组件 2.5.6-20251106.0（256）
小米统计 6.31.0（2026082100）

作用域为上述四个应用。LSPosed 中启用 HyperPriv 并勾选推荐作用域，打开模块配置页使设置同步，重新打开目标应用。
关闭总开关可恢复原方法。卸载本模块并重新启动目标进程也可恢复。系统组件更新后版本不匹配的 Hook 自动跳过；需要重新适配。

功能：安装器广告与推荐、安装资料/APK 片段上传、云端检测、安装病毒扫描、纯净/风险/备案限制、安装频次风控、无障碍和未知来源授权读秒、USB 调试安全设置三步读秒、安全组件 detect/app 应用列表上传、风险应用通知、小米统计指定采集和联网入口。

读秒修改保留用户确认，不自动授予无障碍、USB 调试或安装权限。
安装器云检使用本版本的 CloudParams 返回类型；没有套用新版 CloudResult.Success。
此模块只处理列明入口，不保证系统或第三方应用完全不收集数据，不覆盖所有“反诈”功能。
系统扫描和风险提示关闭后，不再通过这些入口提示有害安装包。各项可单独关闭。

源码：src/、profiles.json、AndroidManifest.xml、res/、resources/、build.py。
模块本身无网络权限，不收集用户数据；日志仅输出 Hook 标识与命中，不记录安装包信息或应用列表。

构建：JDK 17；Android SDK platform 35、build-tools 36.0.0；官方 libxposed API、service、interface 102 的 classes.jar。
目录格式为 API_ROOT/api/classes.jar、API_ROOT/service/classes.jar、API_ROOT/interface/classes.jar。
创建自己的 PKCS12 签名证书，别名 hyperpriv；将密码放入 HYPERPRIV_SIGN_PASSWORD 环境变量。
python3 build.py --jdk JDK_PATH --sdk SDK_PATH --libxposed API_ROOT --keystore KEYSTORE_PATH --out HyperPriv.apk
用户的手机安装包及签名私钥不包含在源码包中。

参考项目和许可证见 THIRD_PARTY_NOTICES.txt。HyperPriv 源码以 AGPL-3.0-only 提供。
