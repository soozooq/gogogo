# 天外天 · 2026-10-13 晚间以太网定位差异（双应用被动对照已完成）

## 第一阶段：用户截图观察（21:00 前，未同步采集系统缓存）

- 用户表示本次已使用**以太网**，并在 GoGoGo / 天外天模拟场景下对比腾讯地图与微信的定位。
- 腾讯地图 App 截图中，**蓝色定位点**显示于北美太平洋西岸一带，底图上可见美国西海岸地形。具体经纬度、系统 GNSS 缓存与是否每次按了“定位”刷新**没有同步日志**。
- 微信「发送位置」截图中，绿色选点标记、蓝色定位精度圆和附近 POI 显示在福建省福州市闽侯县上街镇附近。截图没有提供 SDK 内部坐标或回调来源。
- 两个画面不是同一 App/同一定位数据通道的技术证据；“腾讯地图 App 成功显示美国”**不等于微信会使用相同腾讯 SDK 数据源**。
- 这次测试的 Ethernet transport 是否为 GoGoGo 的实际 active default network、VPN 是否分应用或其 underlay、蜂窝注册与 Wi-Fi 扫描状态，尚缺少同步读数。
- 无法排除：第三方 App 独立位置 SDK / 地理编码服务、App 缓存、不同 provider、Wi-Fi/蜂窝射频证据、IP/网络定位、Mock 筛选等候选。不能据截图单独确定某一项。

## 已有可复用工具

- `LabCrossAppLocationActivity`：单应用 BEFORE / AFTER 被动 `GPS/NETWORK/GMS_LAST` 缓存对照，附 `LabCellularEthernetSnapshot.capture` 记录网络承载和 SIM/注册状态、人工观察类别。**无主动请求和第三方内部数据读取**。
- `ConsumerLocationProbeActivity` 的 Passive Snapshot：直接区分 GPS、NETWORK、GMS lastLocation 缓存，有 `fixAge` 和 mock 标记。
- `LabWeChatProcessSummary`：Shizuku 只读快照，不能单凭进程名归因主微信或 vivo 双开，更不能证明微信定位来源。

## 下一轮最小实验（同条件、少变量）

1. 保持当前美国固定模拟目标、VPN 节点、以太网接法、Wi-Fi 与移动数据状态、Shizuku 状态**全程不变**，不清缓存、不强停微信、不手动拖动微信地图。
2. 天外天 → 实验诊断 → 高级研究工具 → 专项实验与对照 → **跨应用定位来源对照**。
3. 选「腾讯地图 App」→ BEFORE → 打开腾讯地图（主动点击定位按钮验证蓝点而非旧地图视角）→ 返回 AFTER → 选择界面观察结果 → 保存。
4. 同条件选「微信」单独再做一轮。进入聊天「发送位置」后点击定位按钮观察蓝色位置圆和 POI，不改变地图中心或手动选择点。返回 AFTER，保存。
5. 复制这两轮总日志；仅发送脱敏字段：`app_default_network`、`visible_network_count_by_transport`、`registered_network_iso`、`wifi_radio_switch`、`PASSIVE BEFORE/AFTER`、`fixAge`、`isMock` 及人工观察。不要发送真实地址、电话号码、完整精确坐标、账号信息。

## 证据判别

- 如果 GPS=mock，但 NETWORK/GMS 为非 mock 或缓存很新：系统位置消费者拿到**不一致来源**是较强候选；仍不能证明微信真实读取了哪条。
- 如果 GPS/NETWORK/GMS 都为 mock、腾讯地图在美国、微信在福州：进一步怀疑微信独立 SDK、自己的缓存／网络解析或模拟位置筛选，但不能确认机制。
- 如果 GPS/NETWORK/GMS 在微信返回 AFTER 后变化：可能发生新的其他 App 或系统缓存写入，但不能凭时间顺序确定是微信写的。
- 如果以太网未被 `NETWORK_RADIO_*` 识别：实验的 Ethernet 标签只依据用户外部连接方式，不能宣称应用实际默认流量已走以太网。

## 实验边界

这是一份**观察结果记录和下一步取证方案**，不是定位兼容修复成功声明。当前不修改注入频率、Provider 或 GMS 参数，不增加 Shizuku 权限，不自动强停、清缓存、删除微信数据或干预 vivo 分身。旧方案和数据可用于下一次重复对照。

相关资料：`docs/cellular-ethernet-vs-location-evidence-20261012.md` 与 `docs/cross-app-location-evidence-20261012.md`。

## 第二阶段：2026-10-13 21:51–21:53 正式被动对照结果（已完成）

以下来自用户直接提供的两轮 `CROSS_APP_ROUND` 脱敏文本日志，不是推测或仅凭截图。为保护隐私，本文件只保留关键证据字段与大致区域，**不保存真实地址、用户帐号或精确经纬度**。原始完整日志仍由用户设备 `cross_app_location_evidence/total_log` 的 SharedPreferences 保存，除非用户自行清空应用数据或在实验页执行清空操作。

### 实验 1：腾讯地图 App

- `CROSS_APP_ROUND` 开始：**21:51:29.560**；点击外部应用：21:51:35.505；返回天外天并拍摄 AFTER：**21:51:59.659**；人工结果保存：**21:52:36.847**。
- 网络 BEFORE/AFTER 一致：`app_default_network=VPN+ETHERNET`；可见 transport 计数为 `CELLULAR:0,WIFI:0,ETHERNET:2,VPN:1`；`wifi_radio_switch=false`；`airplane_mode=1`；`cellular_data_state=DISCONNECTED`。VPN underlay/其他 App 路由 `UNKNOWN`。
- BEFORE：GPS `fixAge=29ms mock=YES LT_1KM`，NETWORK `51ms mock=YES LT_1KM`，GMS_LAST `51ms mock=YES LT_1KM`。
- AFTER：GPS `73ms mock=YES LT_1KM`，NETWORK `51ms mock=YES LT_1KM`，GMS_LAST `259ms mock=YES LT_1KM`。
- 人工观察：`蓝点和附近地点均为 GoGoGo 模拟目标地区`；`opened_by_user=true`；这是用户见到的 App 界面，不是读取到了腾讯地图的内部 SDK 调用路径。

### 实验 2：微信「发送位置」

- `CROSS_APP_ROUND` 开始：**21:52:47.644**；点击外部应用：21:52:50.138；返回天外天并拍摄 AFTER：**21:53:16.141**；人工结果保存：**21:53:34.626**。
- 网络 BEFORE/AFTER 同为 `VPN+ETHERNET`，且 CELLULAR/WIFI 可见 transport 均为 0、以太网 2、VPN 1；Wi-Fi 关闭、飞行模式开启；第三方 App 实际路由未验证。
- BEFORE：GPS `fixAge=17ms mock=YES LT_1KM`，NETWORK `35ms mock=YES LT_1KM`，GMS_LAST `52ms mock=YES LT_1KM`。
- AFTER：GPS `91ms mock=YES LT_1KM`，NETWORK `26ms mock=YES LT_1KM`，GMS_LAST `183ms mock=YES LT_1KM`。
- 人工观察：`蓝点和附近地点均为国内实际地区`；`opened_by_user=true`；未证明微信内部读取了哪个位置来源。

### 网络及 SIM 旁证的边界

- 用户设备对天外天报告的默认网络为 VPN+ETHERNET，说明**本 App**当时走该默认通道；`vpn_underlay=UNKNOWN`、`other_app_routes=UNKNOWN`，不代表所有 App 都一定经过同一个 VPN 出口。
- `default_sim_iso=US` 而 `sim_mccmnc=46002`，程序标记 `INCONSISTENT_SIM_ISO_US_MCC_CN`。它只是配置字段不一致，**不能据此识别真实地理位置，也不能单独作为微信定位原因**。没有读取手机号、IMSI 或 ICCID。
- `registered_network_iso/mccmnc/operator=UNKNOWN`，`READ_PHONE_STATE` 未授予，故不能用这轮数据确认或排除蜂窝注册证据。飞行模式/移动数据开关状态亦不等价于所有无线电完全断电。

### 可以确定的结论

1. 两轮中，**天外天有权限读取的 GPS、NETWORK、GMS_LAST 公共末次缓存**在 BEFORE 与 AFTER 都很新，均 `mock=YES` 且距模拟目标 `LT_1KM`。
2. 腾讯地图 App 的用户可见蓝点和附近地点跟随模拟目标；微信「发送位置」用户可见蓝点和附近地点没有跟随，仍为国内地区。
3. 因此该异常**不能简单归因为 GPS/NETWORK/GMS 公共缓存没有写入模拟坐标**。但不能推断微信一定读取了某一个公共缓存，也不能证明微信用了独立 SDK、服务端地理编码、Wi-Fi/蜂窝定位、旧缓存或模拟检测中的任一单独路径。
4. 两个 App 的可见 UI 与本 App 的公共缓存来源没有一对一读取证据，缺少第三方 App 内部日志和分支路由证据；现阶段只能表述为**跨应用消费结果不一致**，并不构成微信绕过机制已查明或已修复。

### 对下一阶段工作的指导

- **不要重复本次已稳定的条件**：VPN+ETHERNET、Wi-Fi 关、飞行模式开、同目标持续模拟，三条公共缓存前后均模拟。
- 下一阶段优先做能够区分微信内部缓存、系统返回值与第三方请求差异的**只读、可重复**对照；先给出能证伪的假设，再决定改哪一层。
- 不建议只凭本次截图盲调 `ServiceGo` 注入频率、清空微信数据、干预应用分身、强停微信或宣称“腾讯地图用的 SDK 就是微信用的 SDK”。
- 版本基线：记录生成时最新 UI 预览 **天外天 2.4.10**，这次实验取证实际发生在升级前的旧 UI 上；切勿将本轮成功归功于新三步界面。

**留存方式**：仓库保留这份脱敏实验结论；天外天设备本地保留原始总日志；此对话中用户已经提供两轮完整脱敏文本，可作为未来复核的原始参照。
