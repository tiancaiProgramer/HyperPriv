# HyperPriv

HyperOS 3 / Android 16 的现代LSPosed API102模块。当前预览版 **0.4.4-preview2 / versionCode22**，[下载本版本](https://github.com/tiancaiProgramer/HyperPriv/releases/tag/v0.4.4-preview2)。仍有未完成场景验证或尚未适配项目。

本版本新增SystemHelper指定版本/APK哈希的采集与上报阻止，沿用统计与上报菜单，独立开关默认关闭。当前63个布尔设置、3个参数、11项作用域，保留3张卡片及11个原地展开菜单。既有安装器扫描加载页修复保留。

[更新说明](RELEASE-NOTES.md) · [新增实测](SYSTEMHELPER-20261104.md) · [63项状态](VERIFICATION-0.4.4-preview2.csv) · [未完成验证及方案](UNVERIFIED.md) · [相对基线改动](GITHUB-DIFF.md)

## SystemHelper

目标`com.mobiletools.systemhelper`，3.0.1-20250311 / versionCode31，仅已核对哈希5267008ede808c467988df588915791d21a6f1f3fbe219c57b125a235000df4f。严格检查版本/哈希，实验兼容不放宽此目标。不会因名称相似而处理高通同名组件。

代码中存在应用列表、使用时长及流量、设备/SIM/信号和定位采集，以及注册/历史数据加密POST链路；这证明能力，不证明每个自然周期都会运行或服务端用于反诈。

在LSPosed为HyperPriv勾选SystemHelper作用域，再开启“隐私与上报 → 统计与上报 → 阻止 SystemHelper 采集与上报”，重启目标进程生效。只勾选作用域、只保存开关或显示版本适配均不能单独证明实际命中。

当前设备已记录22条真实加载、受控12项检查及121项隔离回调对照；自然长期、重启、其他版本和完整联网内容仍待验证。实机测试在该UID临时双栈外网隔离下进行，临时规则/工具已清理。

## 配置、适配与恢复

菜单包含安装与授权、隐私与上报、电量与显示三张卡片，子菜单原地展开；关于提供桌面图标、兼容及组件说明。63项布尔设置包含模块总控、核心总控和兼容模式，另有剪贴板白名单、提示文字及不透明度3参数。原62项偏好键及默认值保留，新功能默认关闭。

参考设备MIX Fold3，Android16，HyperOS OS3.0.304.0.WMVCNXM，LSPosed2.2.1(7912)。其他精确组件定义见profiles.json、extra-profiles.json及Java目标定义。共11项推荐作用域：系统框架、安装器、安全中心、安全守护、设置、权限管理、电量与性能、Joyose、小米统计、MSA、SystemHelper。

实验兼容模式默认关闭。对原适配规则可放宽构建及版本限制以尝试现有定义；仍检查方法签名，缺失跳过，未知设备不保证效果。SystemHelper保持严格版本/哈希要求。应用层修改后重启对应进程；系统层修改或兼容模式变化需重启手机。

原签名覆盖升级可保留配置，ADB建议普通安装：

```sh
adb install --no-incremental -r HyperPriv-0.4.4-preview2.apk
```

关闭单项/总开关后重启对应进程可恢复原调用；停用或卸载模块后重启目标也可恢复。手动选择的USB设置、休眠时长及已有缓存云控不保证自动回退。

## 验证边界

本次主要测试新增SystemHelper，其他功能沿用有日期的历史记录，未重新全量验证。当前63项中20项有记录内场景对照、28项待场景、8项入口命中但完整效果未验证、3项部分验证、1项业务未适配、3项控制设置。数量不代表覆盖所有版本或条件。

安全守护Root开关仍只保存选择，不执行该业务拦截。温度选项仅针对指定屏幕刷新率分支。未完成清单与逐项方案见UNVERIFIED.md；实际样本、限制及构建标签见相应报告。

## 构建与检查

需要Python3、JDK17、Android SDK Platform35 / Build Tools36.0.0，以及libxposed API/service/interface102的classes.jar，分别放入API_ROOT的api、service、interface子目录。API只用于编译，service/interface为运行时依赖。

```sh
python3 build.py --jdk /path/to/jdk17 --sdk /path/to/android-sdk --libxposed /path/to/API_ROOT --keystore /path/to/signing.p12 --out HyperPriv.apk
python3 tests/run_checks.py --jdk /path/to/jdk17 --android-jar /path/to/android-sdk/platforms/android-35/android.jar --libxposed /path/to/API_ROOT
```

密钥别名hyperpriv，密码使用HYPERPRIV_SIGN_PASSWORD环境变量。自签包不能直接覆盖原签名包；私钥与密码不随源码分发。

## 来源与许可

AGPL-3.0-only。既有Hook参考HyperCeiler并按实机适配现代API；SystemHelper规则按当前系统APK独立研究，未分发原APK或反编译实现。libxposed依Apache2.0使用。详见THIRD_PARTY_NOTICES.txt、REFERENCE_BLOBS.json及许可证。
