# HyperPriv

HyperOS 3 / Android 16 的 LSPosed 模块，针对下列组件版本调整安装器广告、部分资料上传入口、风险拦截及权限授权读秒。当前版本为 **0.1.0，实验性设备适配版**。

[下载安装包](https://github.com/tiancaiProgramer/HyperPriv/releases/tag/v0.1.0) · [验证记录](VALIDATION.txt) · [参考项目与许可证](THIRD_PARTY_NOTICES.txt)

## 功能

- 安装器广告、应用推荐与个性化广告入口。
- 安装来源、包信息、权限与 APK 片段上传入口。
- 安装器云检、安装病毒扫描、纯净模式、风险/备案/账号校验和安装频次风控。
- 无障碍、安装未知应用及 USB 调试（安全设置）的提示读秒；保留手动确认。
- 安全组件指定反诈接口的应用列表提交、风险应用通知。
- 小米统计中已识别的采集、事件和联网入口。

配置页提供总开关和 11 个功能开关。模块无网络权限，日志不包含安装包信息或应用列表。它只处理列出的入口，不代表关闭整机全部数据收集或所有反诈功能。

## 适配范围

需要可用的 Root / Zygisk / LSPosed 环境，以及现代 libxposed API 102。已在 LSPosed 2.2.1（7912）、HyperOS 3 / Android 16 上验证加载。

| 组件 | 包名 | 版本 | versionCode |
| --- | --- | --- | --- |
| 应用包管理组件 | `com.miui.packageinstaller` | 5.3.6.0.1-20250825F | 53601 |
| 安全中心 | `com.miui.securitycenter` | 12.6.8-260609.0.1 | 40001268 |
| 安全组件 | `com.miui.guardprovider` | 2.5.6-20251106.0 | 256 |
| 小米统计 | `com.miui.analytics` | 6.31.0 | 2026082100 |

**不是通用 HyperOS 模块。** 每个 Hook 校验目标组件的 versionCode、参数和返回类型；版本不匹配时跳过对应规则。系统组件更新后需重新适配，不应强行修改版本判断套用。

## 安装与恢复

1. 从 Releases 安装 `HyperPriv-0.1.0.apk`。
2. 在 LSPosed 启用 HyperPriv，并使用推荐的四个作用域。
3. 打开 HyperPriv 配置页，确认显示“LSPosed 已连接”。首次使用各功能默认开启，按需调整。
4. 重新启动对应目标应用进程；必要时重启手机。

关闭总开关后重新启动目标应用，可恢复原方法；也可在 LSPosed 停用或卸载模块后重启。模块不自动授予无障碍、USB 调试或未知来源安装权限，不修改系统分区、签名校验或 SELinux。

关闭扫描和风险提示后，相应入口不会再提示有害安装包，相关开关可单独关闭。

## 已验证与限制

36 个目标方法均核对过目标 APK 的 DEX 签名，运行时四个组件加载 22 + 6 + 2 + 6 项规则，无跳过。

- 已观察到安装器广告/扫描开关、纯净模式、云检替代及统计规则实际命中。
- 未知来源和无障碍警示页已验证无读秒，确认框保持未勾选。
- USB 三步提示已按当前实现适配，但未完成三步界面实测。
- 安装资料上传与反诈应用列表规则已加载，尚未抓包验证。
- 安装流程已到来源确认，尚未完成图形安装界面至成功页的端到端验证。

详见 [VALIDATION.txt](VALIDATION.txt)。适配结果不保证其他设备、其他同号构建或系统更新后的兼容性。

## 构建

依赖 Python 3、JDK 17、Android SDK Platform 35 / Build Tools 36.0.0，以及官方 [libxposed API](https://github.com/libxposed/api)、[service / interface](https://github.com/libxposed/service) 102 的 `classes.jar`。

将三个 JAR 分别放在 `API_ROOT/api/classes.jar`、`API_ROOT/service/classes.jar`、`API_ROOT/interface/classes.jar`。API 仅用于编译，service/interface 运行时会打包进 APK。

创建自己的 PKCS12 签名证书（别名 `hyperpriv`），通过环境变量 `HYPERPRIV_SIGN_PASSWORD` 传递签名密码，然后执行：

```sh
python3 build.py \
  --jdk /path/to/jdk17 \
  --sdk /path/to/android-sdk \
  --libxposed /path/to/API_ROOT \
  --keystore /path/to/signing.p12 \
  --out HyperPriv.apk
```

签名私钥不随源码分发，自签构建无法覆盖安装已发布安装包。仓库不包含原版系统 APK、用户应用或第三方 APK 修改版。

## 来源与许可

参考 [HyperCeiler](https://github.com/ReChronoRain/HyperCeiler) 的相关 Hook，基于目标组件实际方法签名独立实现现代 API 102 模块；参考提交为 `861be0473847d5c020102191f2c2891acda5d628`。

HyperPriv 源码按 **AGPL-3.0-only** 发布，保留 HyperCeiler 的来源与许可声明。libxposed 依赖按 Apache-2.0 使用。具体文件与声明见 [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt)、[LICENSE](LICENSE) 和 [LICENSE-APACHE-2.0](LICENSE-APACHE-2.0)。
