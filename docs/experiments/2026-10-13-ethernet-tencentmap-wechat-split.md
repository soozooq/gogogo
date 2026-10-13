# 天外天 · 2026-10-13 晚间以太网定位差异（待验证）

## 用户新提供的截图观察

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
