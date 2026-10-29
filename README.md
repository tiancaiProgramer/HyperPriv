# HyperPriv

HyperOS 3 / Android 16 的现代LSPosed API102模块。当前预览版为 **0.4.3-preview1 / versionCode20**，[下载本版本](https://github.com/tiancaiProgramer/HyperPriv/releases/tag/v0.4.3-preview1)。不是全部功能完成实测的稳定版。

本候选在0.4.2-preview基础上补齐“跳过安装时病毒扫描”：开启时同时阻止通用扫描加载页及动画的创建，关闭时恢复原调用。沿用原开关、菜单和作用域。

[版本更新说明](RELEASE-NOTES.md) · [对比GitHub](GITHUB-DIFF.md) · [验证状态](VALIDATION.txt) · [历史逐项实测](VERIFICATION-20261026.md)

## 界面与配置

保留“安装与授权”“隐私与上报”“电量与显示”三张卡片、11个原地展开小菜单；卡片与菜单分别记住展开状态，不跳转新页面。“关于”保留显示桌面图标、组件适配和恢复入口。

62个布尔偏好包含模块总开关、核心总开关和实验兼容模式，另有剪贴板包名白名单、提示按钮文字与不透明度3个参数。保留既有偏好键、默认值和10项推荐作用域，未新增默认开启功能。具体功能见FEATURES.md。

## 实验兼容模式

默认关闭。在“关于”开启后，放宽机型、系统构建、组件版本和设置APK哈希限制，尝试现有规则。已有精确版本优先；未知版本选择该组件最新的一组定义，不混用多个版本。仍精确检查类、方法、参数和返回类型，缺失就跳过。

同名方法含义可能变化，反射匹配不等于行为正确。模式沿用已保存的开关，包含原来默认开启的功能。切换模式后重启目标应用；系统层变化需重启手机并检查日志。未知机型及小米15未实测。

## 适配与安装

参考设备为Xiaomi MIX Fold3、Android16、HyperOS OS3.0.304.0.WMVCNXM、LSPosed2.2.1(7912)，需要支持现代API102的框架。这不是通用设备适配。

已定义组件包括安装器53601、安全中心40001268/40001275、安全守护256/307、设置36及APK哈希、权限管理192、电量与性能40200、Joyose492、MSA2025071906、小米统计2026082100及当前Android16完整系统构建。精确规则见profiles.json、extra-profiles.json和Java目标定义。

候选APK保持原签名。基线19版已有重启加载证据；本次20版仅变更安装器展示，已普通升级并重启安装器进程完成自建样本对照，无需重启手机。ADB可指定：

```sh
adb install --no-incremental -r HyperPriv-0.4.3-preview1.apk
```

此前定位过增量安装导致早期启动读取模块失败；覆盖安装成功不能代替系统进程已加载。在LSPosed启用模块并勾选9个应用和系统框架，共10项作用域。修改应用选项后重启对应应用；修改系统层选项或模式后重启手机。框架服务显示同步成功不证明运行中系统进程采用新值。

关闭单项或总开关并重启目标进程，可恢复对应原调用。已经手动更改的USB、锁屏、应用启停和缓存云控不保证自动回退。

## 验证边界

本地20版Java编译、完整APK构建、签名验证及与原签名一致性、主机策略和真实回调代理检查通过。**本次新APK已升级并通过扫描加载页开启/关闭安装对照；其他功能沿用基线19版证据，未全部重新实测。**

历史2026-10-26的62项逐项记录保留为有日期证据。匹配、回调、入口命中、场景对照、条件不足和未测试分别记录，不能合并为全部通过。

安全守护Root开关可保存，但业务未适配。温度选项仅限指定屏幕刷新率分支，不能等同解除全部游戏锁帧、CPU/GPU温控、过热关机或充电保护。

## 构建与检查

需要Python3、JDK17、Android SDK Platform35 / Build Tools36.0.0，以及libxposed API/service/interface102的classes.jar。分别置于API_ROOT的api、service、interface子目录；API仅编译使用，service/interface为打包运行时。

使用自己的PKCS12密钥，别名hyperpriv，密码通过HYPERPRIV_SIGN_PASSWORD环境变量传递：

```sh
python3 build.py --jdk /path/to/jdk17 --sdk /path/to/android-sdk --libxposed /path/to/API_ROOT --keystore /path/to/signing.p12 --out HyperPriv.apk
python3 tests/run_checks.py --jdk /path/to/jdk17 --android-jar /path/to/android-sdk/platforms/android-35/android.jar --libxposed /path/to/API_ROOT
```

私钥和密码不随源码分发。自签构建不能直接覆盖原签名安装包，不应未经用户同意卸载或清空配置。

## 来源与许可

参考HyperCeiler对应Hook并按实际设备适配现代API102，保留AGPL-3.0-only及来源声明；libxposed按Apache2.0使用。具体来源见THIRD_PARTY_NOTICES.txt、REFERENCE_BLOBS.json、LICENSE和LICENSE-APACHE-2.0。源码包不含原版系统APK、第三方修改APK、私钥或用户设备数据。

本轮清理、加载和实机验证见[2026-10-29记录](VERIFICATION-20261029.md)。

本地扫描加载页修复及本次验证见[SCAN-UI-FIX-20261029.md](SCAN-UI-FIX-20261029.md)。上一版重启与其他功能证据保留为历史范围，本次仅改安装器展示规则。

本次发布只将已测20版版本名移除-local后缀；实机证据保留原构建标签。未完成项目和方案见[UNVERIFIED.md](UNVERIFIED.md)，当前62项状态见[验证表](VERIFICATION-0.4.3-preview1.csv)。
