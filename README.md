# HyperPriv

普通 / xiaomi.eu 统一版：**0.4.4 / versionCode 28**，基于 GitHub [v0.4.4-preview2](https://github.com/tiancaiProgramer/HyperPriv/releases/tag/v0.4.4-preview2)。同一个 APK 自动识别 EU，并隐藏已确认由 ROM 处理、组件缺失或没有对应业务的选项。未知 EU 构建保留尝试入口。

这 12 项标准版均已有实现；按本机实际 APK 方法定义自动识别入口，确认没有的隐藏。组件缺失时，普通版和 EU 均隐藏对应选项。读取失败则保留，隐藏不清除设置。

核心签名的 10 项高级功能收进默认折叠的“更多选项”，展开子菜单增加左侧竖线与逐层缩进；网络助手选项改名“忽略网络助手服务下线标记”。SystemHelper 新增独立 CN34 分支，并支持 CN34/EU34 的相同完整 DEX 变体。

兼容模式默认开启，保留用户已经保存的关闭状态。普通版原有规则与其他默认值保留；EU 新安装其余独立功能默认关闭，升级保留已有选择。此前 14 项 EU 待适配入口中，12 项已完成目标版本映射、2 项确认没有对应业务并隐藏。

- [界面整理、重复按钮排查与 SystemHelper CN/EU 对比](UI-HELPER-20261108.md)
- [适配、构建与实测边界](UNIFIED-ADAPTATION.md)
- [63 项设置的普通 / EU 默认值与本机可见性](UNIFIED-FEATURES-20261108.csv)
- [本次发布说明](RELEASE-NOTES.md)
- [最终28版核验记录](RELEASE-VERIFICATION.json)

本次小米 15 EU 样本的 12 项入口自动识别、139 个保留目标反射及关闭门控检查通过，77 个简单开启回调通过；复杂返回类型与指定请求分支另有隔离检查。这不等于实际 LSPosed 业务全部生效，仍需手动测试。27版已安装到当前小米15并核对展开层级，未改功能开关、未重启或发布 GitHub。

源码、资源和测试位于项目根目录，公开依赖见 `dependencies/libxposed`。不包含主题破解或 eSIM Hook；SIM 安全保护功能保留。源码包不包含签名密钥或设备原 APK。历史验证文档保留各自原始版本范围。AGPL-3.0-only，保留第三方许可声明。
