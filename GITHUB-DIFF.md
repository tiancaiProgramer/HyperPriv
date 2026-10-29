# 相对GitHub发布版的本地改动

基线：v0.4.2-preview，提交786aef72be2efbf6542a8da9cca9c4862d386bbb。
本次预览版：0.4.3-preview1 / versionCode20。用户于2026-10-29确认发布，实机证据保留原local标签。

- Profiles.java / profiles.json：仅新增安装器53601的NewInstallerPrepareActivity.A1()V展示规则，归属现有scan组，使用现有null动作。原规则全部保留。
- UiCatalog.java：补充原开关说明，说明扫描加载页与动画也会隐藏。
- AndroidManifest.xml：版本号20，原包名与签名保留。
- 文档：补充本次修复与实测证据。未新增权限、作用域或偏好键。

不修改APK解析、来源授权和最终确认流程。Core/Extra编译类文件与19版一致，本次无需手机重启；应用更新后强制停止再启动安装器即可加载。

新增UNVERIFIED.md及VERIFICATION-0.4.3-preview1.csv，区分场景、入口、部分、未适配和控制项，未将旧证据改写为全部新测。
