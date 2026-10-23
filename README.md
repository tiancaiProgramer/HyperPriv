# HyperPriv

HyperOS 3 / Android 16 的 LSPosed 模块，针对下列组件版本调整安装器广告、部分资料上传入口、风险拦截及权限授权读秒。当前版本为 **0.2.0，实验性设备适配版**。

[下载安装包](https://github.com/tiancaiProgramer/HyperPriv/releases/tag/v0.2.0) · [验证记录](VALIDATION.txt) · [参考项目与许可证](THIRD_PARTY_NOTICES.txt)

## 功能

- 安装器广告、应用推荐与个性化广告入口。
- 安装来源、包信息、权限与 APK 片段上传入口。
- 安装器云检、安装病毒扫描、纯净模式、风险/备案/账号校验和安装频次风控。
- 无障碍、安装未知应用及 USB 调试（安全设置）的提示读秒；保留手动确认。
- 安全组件指定反诈接口的应用列表提交、风险应用通知。
- 小米统计中已识别的采集、事件和联网入口。
- MSA 的指定广告联网、预装列表及统计联网入口。
- USB 安装本地开启、USB 安全设置的已识别联网验证跳过。
- 解锁锁屏休眠“永不”选项，仍需手动选择，保留设备管理员限制。
- PowerKeeper / Joyose 指定云控入口，耗电统计与温度日志上报。
- PowerKeeper 的温度锁屏幕刷新率分支。CPU/GPU 温控、过热关机与充电保护保持原状。
- 安装完成后底部推荐开启安全守护的卡片。

配置页提供总开关和 19 个功能开关。模块无网络权限，日志不包含安装包信息或应用列表。它只处理列出的入口，不代表关闭整机全部数据收集或所有反诈功能。

## 适配范围

新增功能的边界与未实现项目见 [FEATURES.md](FEATURES.md)。

需要可用的 Root / Zygisk / LSPosed 环境，以及现代 libxposed API 102。已在 LSPosed 2.2.1（7912）、HyperOS 3 / Android 16 上验证加载。

| 组件 | 包名 | 版本 | versionCode |
| --- | --- | --- | --- |
| 应用包管理组件 | `com.miui.packageinstaller` | 5.3.6.0.1-20250825F | 53601 |
| 安全中心 | `com.miui.securitycenter` | 12.6.8-260609.0.1 / 12.7.5-260709.0.1 | 40001268 / 40001275 |
| 安全组件 | `com.miui.guardprovider` | 2.5.6-20251106.0 / 3.0.7-20260708.0 | 256 / 307 |
| 小米统计 | `com.miui.analytics` | 6.31.0 | 2026082100 |
| 设置 | `com.android.settings` | 16（另校验 APK SHA-256） | 36 |
| 电量与性能 | `com.miui.powerkeeper` | 4.2.00 | 40200 |
| Joyose | `com.xiaomi.joyose` | 2.4.92 | 492 |
| MSA | `com.miui.systemAdSolution` | 2025.07.19.06-release | 2025071906 |

**不是通用 HyperOS 模块。** 每个 Hook 校验目标组件的 versionCode、参数和返回类型；版本不匹配时跳过对应规则。系统组件更新后需重新适配，不应强行修改版本判断套用。

## 安装与恢复

1. 从 Releases 安装 `HyperPriv-0.2.0.apk`。
2. 在 LSPosed 启用 HyperPriv，并使用推荐的八个作用域。
3. 打开 HyperPriv 配置页，确认显示“LSPosed 已连接”。首次使用各功能默认开启，按需调整。
4. 重新启动对应目标应用进程；必要时重启手机。

关闭总开关后重新启动目标应用，可恢复原方法；也可在 LSPosed 停用或卸载模块后重启。模块不在启动时自动授予权限；USB 本地开启仅在用户进入开启流程时执行。无障碍、普通 USB 调试和未知来源安装仍需确认，不修改系统分区、签名校验或 SELinux。

关闭扫描和风险提示后，相应入口不会再提示有害安装包，相关开关可单独关闭。

## 已验证与限制

配置共 72 条规则（包含旧组件版本），当前八个组件对应 63 条，均核对过目标 APK 的 DEX 签名。八个组件的运行日志已确认规则加载，新增组件均为 0 跳过。具体运行时与场景验证以 [VALIDATION.txt](VALIDATION.txt) 为准。

- 已观察到安装器广告/扫描开关、纯净模式、云检替代及统计规则实际命中。
- 0.1.0 已验证未知来源和无障碍警示页无读秒且确认框未勾选；0.2.0 在新安全中心再次观察到读秒规则命中。
- USB 三步提示已适配；无账号/无 SIM/离线 USB 开启未完成场景实测。
- MSA、Joyose、PowerKeeper 云控及安全中心电池健康上传开关已实际命中；尚未抓包。
- “永不”构造条件调整已命中，未改变原有锁屏设置。
- 安装资料上传与反诈应用列表规则已加载，尚未抓包验证。
- 安装流程已到来源确认，尚未完成图形安装界面至成功页的端到端验证。

详见 [VALIDATION.txt](VALIDATION.txt)。适配结果不保证其他设备、其他同号构建或系统更新后的兼容性。

## AVB 与真实电池容量

本版**没有移除 AVB，也没有解除真实电池锁容**。AVB 属于应用启动前的启动链校验，Xposed 无法代替 vbmeta/启动镜像适配；本项目未写入任何启动分区。实际容量受电池状态、驱动、固件和充电策略共同影响；容量低于设计值不足以证明被官方锁容。本版没有定位到可验证的真实容量限制入口，不伪造容量或修改充电电压。

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

显示分支隔离检查：使用 JDK 17 将 `Policy.java` 与 `tests/PolicyCheck.java` 编译到临时目录，运行 `io.github.hyperpriv.PolicyCheck`，检查只匹配温度刷新率 cookie，不匹配其他显示模式。
