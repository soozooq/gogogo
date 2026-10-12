# GoGoGo 蜂窝网络 / 以太网对照实验（2026-10-12）

## 用户发现

用户在 vivo Android 手机上的 GoGoGo SIM 信息页面看到：
- SIM 发卡国家 `US`、运营商名 `T-Mobile USA`，但 `SIM MCC/MNC = 46002`；
- 当前蜂窝网络国家 `CN`，运营商 `中国移动`，`Network MCC/MNC = 46000`，`isNetworkRoaming = false`；
- 用户报告此前在 **以太网联网** 条件下，腾讯地图 App 能跟随 GoGoGo 模拟坐标，但微信发送位置仍不跟随。
- 新一轮发现高德地图 App 能显示模拟美国，抖音“发送位置”蓝点和附近 POI 均仍准确指向国内真实位置。

**要点**：`46002` 和 `46000` 都是中国 MCC 460 下中国移动相关 PLMN，常见 T-Mobile US 则包含 `310260`。此前 SIM 页面混排了 TelephonyManager 默认订阅的 SIM 字段和当前注册蜂窝网络字段，这些数据可能因多 SIM、默认数据卡/通话卡、OEM 属性覆盖和运营商配置而不一致。截图反映字段矛盾，但不能据此证明哪一个实体 SIM、真实地理位置、平台反 mock 检测或账号身份。

## 本次改动

- **`LabCarrierConsistency` 纯 Java 规则**：对确知的 `CN(460/461)`、`US(310-316)` PLMN MCC 判断 ISO/MCC 是否一致，未知 MCC 一律 `UNKNOWN`，不猜真实设备位置。覆盖 `US + 46002 = INCONSISTENT_SIM_ISO_US_MCC_CN` 的用户截图回归用例。
- **`LabCellularEthernetSnapshot.capture` 只读快照**：在已存在的跨应用 BEFORE/AFTER 被动缓存实验中同步采集：
  - GoGoGo 当前默认网络携带的 `VPN / ETHERNET / WIFI / CELLULAR` transport 标志，及 Android 在当前应用可见的各个 transport 的网络数量（**VPN 默认网络的底层真实承载依旧不能从这项确定**，其余 App 是否分流仍未知）。
  - 移动数据状态及开关、Wi-Fi 开关、飞行模式、蜂窝注册网络运营商 MCC/MNC、漫游标志、SIM MCC/MNC 与发卡国家一致性。关闭移动数据**不等于关闭蜂窝网络注册**。
  - 若用户之前单独授权 `READ_PHONE_STATE`，按每张当前活跃卡的 `subId` 关联读取 TelephonyManager SIM/网络值，并标记**是否默认流量卡/通话卡**；不记录原始 subId、IMSI、ICCID、手机号、基站 Cell ID 或 GPS 经纬度。
  - 不增加定位请求、网络测速、SIM 读取权限弹框，不修改第三方 App、模拟服务、Shizuku、VPN 状态。
- **SIM 信息页面**新增 US ISO 与 46002 不一致警告，并在用户授权之后按卡槽关联 SIM/注册网络资料，避免跨卡属性被当成同一个 SIM。无权限时继续显示基础字段，说明其是否来自同一订阅尚未知。

## 研究假设

- H1：**网络路径相关**。以太网、移动数据路由和 VPN 底层承载不同，影响 SDK 的联网请求、缓存刷新、IP 服务端定位。判断须记录 transport 和外部 app 观察。
- H2：**蜂窝基站/Wi-Fi 位置证据相关**。网络定位可以利用基站/Wi-Fi 扫描，即使上网走以太网仍可能收到基站注册信息；只关闭“移动数据”不构成“无线基站已消失”的实验。MCC 只给国家级运营商归属，不证明某 SDK 实际采用基站定位。
- H3：**缓存 / SDK 独立路径 / Mock 过滤相关**。仅凭高德地图成功、抖音和微信失败，无法判断是旧缓存还是高德 SDK 在第三方 App 中的真实回调。
- H4：**Telephony 字段不同步**。不同 SIM 默认订阅、系统属性覆写或厂商实现导致 `US/T-Mobile/46002` 矛盾。需授权后对每张卡分别比对，避免误认。

## 最小对照操作（先不要清缓存 / 强停）

保持 GoGoGo 美国固定模拟目标、Shizuku 状态和 VPN 节点不变，避免同时改变太多变量。

1. **基线 A**：保留你现在的网络连接方式，打开「实验与诊断 → 跨应用定位来源对照」，分别对腾讯地图和抖音做一次 BEFORE → 外部应用 → AFTER → 观察结果。总日志自动加入 `NETWORK_RADIO_BEFORE` 和 `NETWORK_RADIO_AFTER`。
2. **以太网 B**：若手机此前的 USB 网卡/以太网接法仍可复现，连接以太网；保持 SIM、Shizuku、VPN、模拟位置不变，尽量不改变其它开关。重复腾讯地图与抖音；确认 BEFORE/AFTER 的网络记录出现 `ETHERNET`（或 `visible ... ETHERNET>0`），否则标为“以太网未验证”。
3. **只改一个开关 C**：在以太网继续能联网的前提下，可选择关闭移动数据开关（**不移除 SIM / 不切飞行模式**），重复测试，分别标记。移动数据关闭而 `registered_network_iso=CN` 仍在，是正常可能发生的结果。
4. 同一条件复测微信 1 次即可；先别清空微信数据、撤销 Shizuku 权限或操作 vivo 微信双开进程。
5. 将这几轮总日志发给 GPT 分析。若 `ETHERNET` 与腾讯地图成功稳定相关，但 `registered_network_iso` 保持不变，则优先查默认网络、SDK 请求与缓存而不是直接归因“基站消失”。

**特别注意**：这不是为了伪装运营商字段或规避 App 定位校验，所有新增功能均为只读。真机测试前不要把用户的实际地址、手机号或具体基站身份填进人工备注。

## 权威参考

- Android `TelephonyManager` 各项 SIM/网络数据的来源及权限：
  https://developer.android.com/reference/android/telephony/TelephonyManager
- Android `SubscriptionManager.getDefaultDataSubscriptionId()` 与 default voice/general subscription 独立：
  https://developer.android.com/reference/android/telephony/SubscriptionManager
- `NetworkCapabilities.TRANSPORT_ETHERNET` 等承载类型：
  https://developer.android.com/reference/android/net/NetworkCapabilities
- Android `LocationManager.NETWORK_PROVIDER` 与被动缓存：
  https://developer.android.com/reference/android/location/LocationManager
- 独立 MCC/MNC 对照数据库：
  https://mcc-mnc.org/networks/460_02
  https://mcc-mnc.org/networks/310_260

版本 `2.4.0-lab47.11-radio-evidence` (code 2400065)，基于 PR #42；待 Github CI 与 vivo 实验验证，不直接合并 main。
