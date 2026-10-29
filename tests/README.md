# 检查方式

`python3 tests/run_checks.py --jdk /path/to/jdk17`执行四类纯JVM策略检查，覆盖显示分支、安装上下文、能力边界、额外策略、偏好完整性和兼容选择。

同时传入 `--android-jar /path/to/android.jar --libxposed /path/to/API_ROOT` 会编译完整模块及host/下的主机代理。CompatibilityHooksCheck检查实际菜单分发、总开关/功能门控和精确反射拒绝，并调用PreviousSignatureHooksCheck验证同包名元数据更新、缺少旧包、部分失败及提交异常恢复。没有重复单独执行同一个入口。

device/检查不打包到模块APK。AllTargetsAudit和AllGatesAudit用于独立设备进程的目标/字段与门控审计；RuntimeAudit和ExtraHooksIntegrationCheck保留其他独立检查。运行时按组件补齐系统共享库，不能把缺少classpath导致的ClassNotFound直接归为不适配。

这些检查不能替代LSPosed真实进程加载或业务对照。历史结论按日期与构建记录，不沿用旧总数声称新APK实机通过。

PreferenceCleanupCheck检查真实配置清理编辑：保留支持的布尔/字符串/数值和本地初始化标记，清理无效键，未变化不重复提交，提交失败保持原值。
