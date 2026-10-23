# 检查方式

普通 JVM 检查不依赖 Android 运行时。将 `Policy.java`、`CorePolicy.java`、`ExtraPolicy.java`、`Options.java`、`ExtraFeatures.java`、`UiCatalog.java`、`ExtraTargets.java`、`Rule.java` 与本目录三个 `*Check.java` 编译到临时目录，分别运行 `PolicyCheck`、`CorePolicyCheck`、`ExtraPolicyCheck`。检查策略隔离、原默认值、新增默认关闭、目录覆盖及参数边界。

`device/` 内为单独运行的 Android 检查程序，不打包进模块 APK。先正常构建项目，再将其中的 Java 文件以 `android.jar`、libxposed API 及构建产生的 `code.jar` 为编译依赖。通过 D8 将检查类、`code.jar`、API/service/interface 三个依赖打包为单独的 dex JAR，只用于测试进程。发布模块依旧不包含 API。

- `RuntimeAudit`：在 Root 的 `app_process` 独立进程中运行，参数为 `包名=当前APK绝对路径`，应传入安全中心、权限管理、设置和安装器四个包。只读解析目标和字段；所有包齐全时应为 67 匹配、0 失败。程序只对自身进程临时放宽隐藏 API 反射，不改变系统配置。
- `ExtraHooksIntegrationCheck`：同样在独立 `app_process` 中运行，使用模拟框架接口捕获真实回调，并只在该进程内提供测试偏好和模拟请求。验证线程边界、异常恢复、操作号、剪贴板白名单和总开关关闭。不会向系统发起安装、剪贴板读取或启停应用请求。传入安全中心当前 APK 路径作为唯一参数时，还会在模拟 API 中注册支付自动扫描入口，核对启用拦截、单项关闭和总开关关闭的回调行为。

这两类检查均不证明 LSPosed 已在 system_server 注入，也不替代真实安装、授权、锁屏或截图场景测试。应在实际设备构建匹配时运行；检查结束可删除临时 JAR。
