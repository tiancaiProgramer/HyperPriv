> 发布整理：0.4.4-preview2 / code22。本文保留本地实测标签；发布构建仅移除版本名-local后缀，DEX/资源及编译类文件另作一致性核对。未因更名重新执行全部实机测试。

# SystemHelper采集与上报分析

日期：2026-11-04。目标来自当前已连接MIX Fold3的系统分区APK。

## 识别结果

- com.mobiletools.systemhelper，应用名SystemHelper，versionCode31 / 3.0.1-20250311，位于/system/priv-app/SystemHelper。APK SHA256：5267008ede808c467988df588915791d21a6f1f3fbe219c57b125a235000df4f。
- com.qualcomm.qti.services.systemhelper是另一个System Helper Service，版本1.1。本次不将同名高通组件纳入目标。
- 静态代码可确认设备注册/采集与上传能力，但不能仅凭该代码认定服务端反诈用途，亦不等于已证明当前手机自然周期已实际上传。

## 代码证据

1. com.mobiletools.systemhelper.control.f.b()获取getInstalledApplications(0)、queryAndAggregateUsageStats，按前台时长排序；逐应用汇总NetworkStatsManager流量。c.d.a.b.b的JSON包含应用信息；经DeviceManager回调进入注册线程。
2. c.d.a.a.a及control.d使用getImei/getImeiForSlot、getSubscriberId、getSimSerialNumber，并读取运营商、网络制式和基站信号字段。control.b.a()组装IMEI1/2、IMSI1/2、ICCID1/2、TAC/CID/PCI、AppInfo等字段。
3. control.GetSignalInfoReceiver使用内置百度定位客户端采集经纬度及信号信息；代码存在缓存和历史补报。
4. control.g.a(DeviceInfo)和control.e.a(JSONObject)构造加密注册/历史负载，交给c.d.a.b.f.a(String,byte[],byte[])。该方法实际创建HttpURLConnection/HttpsURLConnection并POST请求，默认地址为http://dm.wo.com.cn:18080/register，可由配置改变。
5. BOOT、SIM、网络变化和周期接收器可以触发对应任务；具体读取/发送仍受代码内状态、网络、SIM及配置等条件影响。声明权限本身未作为实际上传证明。

## 本地新增

0.4.4-preview1-local / versionCode21。新增“隐私与上报 → 统计与上报 → 阻止 SystemHelper 采集与上报”，键systemhelper_report，默认关闭；本次用户授权在当前手机开启。新增com.mobiletools.systemhelper作用域，总计11项作用域、63项布尔设置，3个参数及原菜单结构保留。

22条规则覆盖接收器、采集总入口、电话监听注册、应用列表采集、IP/设备/信号线程、注册和历史线程、实际POST发送及本包内置定位服务初始化与绑定。使用已存在的null/false/empty_list动作，模块总控及独立开关关闭后恢复原调用。严格核对31版及APK哈希，实验兼容模式也不放宽该目标的版本和哈希。

本地默认值读取修正为Options.defaultValue，使缺少远程偏好时新开关仍关闭；原已有默认开启功能保持原默认值。主机检查包含缺省/开启/独立关闭/总控关闭边界。

## 2026-11-04最终验证结果

当前正常本地候选：0.4.4-preview2-local / versionCode22。原正常21版与22版的DEX和资源一致，仅递增版本元数据，使框架不再沿用同版本号诊断代码缓存。

| 层级 | 结果 | 实际范围 |
| --- | --- | --- |
| 作用域/开关 | 通过 | 官方服务及只读集合核对11项，原10项保留，未加入高通同名组件；systemhelper_report开启 |
| 方法与配置边界 | 通过 | 真实APK22方法签名核对；主机缺省/单项/总控边界、构建与签名检查通过 |
| 隔离回调对照 | 通过 | 执行正常候选原始回调共121项断言；人工POST关闭/开启/恢复关闭/总控关闭为1/0/1/1，假Context阻止读取 |
| 真实ART Hook受控检查 | 通过 | 临时诊断构建在真实SystemHelper进程注册22条、跳过0条；22个目标均记录命中，12项检查通过，假Context读取尝试0、合成回环POST0，内置定位未初始化 |
| 正常22版加载 | 通过 | 恢复正常代码并递增构建号后，真实进程22条加载、0跳过；实际启动的电话监听注册和OnBootReceiver入口命中；无任何诊断执行日志或诊断结果文件 |

## 外网隔离、授权与缓存核对

外部instrumentation最初因测试包与目标签名不同被拒绝，随后真实服务启动又遭自动审批拒绝，因此早期记录没有将其写成通过。用户后来明确同意：先阻断该UID的IPv4/IPv6外网后验证真实进程，并知悉本机信息读取的可能性。

执行前核对10098仅属于com.mobiletools.systemhelper，验证目标APK哈希与版本。只给该UID增加临时OUTPUT链，双栈REJECT；诊断阶段IPv4仅允许127.0.0.1:28485人工接收端，正常22版复核阶段双栈全部阻断。规则逐项确认后才启动服务。Android后台启动限制导致第一次服务未运行；使用最长60秒且结束时显式撤销的临时执行白名单完成后续启动。各次临时防火墙及后台豁免均已撤销，未覆盖系统或其他应用的网络规则。

诊断构建真实进程检查通过后，进一步审阅恢复21版的日志发现框架仍缓存诊断代码，故未将该次恢复计为正常候选通过。正常候选递增为22版，重新在外网阻断下启动并核对：只出现正常加载及真实启动命中，诊断入口未执行、回执未生成。手机安装包哈希与最终正常22版一致。

## 收尾与剩余边界

原62项远程开关、输入法、休眠时长及无障碍开关/服务列表核对一致。新作用域和新开关保留开启。SystemHelper已恢复原来的未停用、非强制停止状态；本轮手动启动的临时服务已停止。临时probe、APK副本、诊断回执、ADB回环映射、本机接收端、后台执行豁免、IPv4/IPv6防火墙链均清理，未留下诊断APK作为当前模块。

以上证明当前APK及系统版本的受控入口拦截与正常启动加载，不代表每个自然周期或长期场景都完成了验证。未在开放外网下放行真实隐私数据作对照，也未覆盖其他版本、更新后的APK、重启/长周期上报、全部网络内容、通话及移动数据的完整业务回归。

## 本地记录与分发边界

模块源码：work/hyperpriv-systemhelper-20261104。最终APK：outputs/HyperPriv-0.4.4-preview2-local.apk。静态分析、隔离回调、真实ART日志与正常22版日志位于work/systemhelper-audit-20261104，并提供清理后的验证包。

原系统APK、反编译源码、手机私有偏好、完整防火墙快照和签名密钥不随源码或验证包分发。本地实测结束时尚未发布；用户于2026-11-04确认整理为0.4.4-preview2预览版发布。
