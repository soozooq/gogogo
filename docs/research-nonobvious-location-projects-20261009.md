# GoGoGo · 非典型命名开源项目追踪库（第 1–2 轮）

- 记录日期：2026-10-09（已追加第三轮）
- 项目仓库：https://github.com/soozooq/gogogo
- 基线：`main` 的 Lab 29（`2.4.0-lab29`）及 Lab 20–25 的生命周期/模拟源可靠性、Lab 24 的腾讯 SDK 客户端探针。
- 性质：研究线索库，不是已完成的修复、第三方应用兼容性保证或实机测试报告；研究项目页面/技术文档并不等于审过每一个源码文件。
- 本次提交**只记录资料**，不修改定位行为、权限、Shizuku、地图、签名或 APK。

## 研究方法与去重原则

与其只搜索 `fake gps` / `mock location`，应从运行链路反查非定位类仓库：**AppOps 变更 → 模拟位置授权 → Android Provider 生命周期 → GMS Fused 与时间戳 → 消费者读取缓存 / fresh 回调 → 应用 UI / POI 显示**。

每一个候选必须记录：
1. 可打开的原仓库/原始文档链接；
2. 可信度（已查看的源代码、项目自述、或尚需确认）；
3. 已有能力和 GoGoGo 的差异；
4. 可借鉴的诊断或测试方法及目标 Android 版本；
5. 禁止将“调用成功”当作“第三方应用已接受”。

GoGoGo **已实现/已开始研究**：GPS / NETWORK / framework FUSED、可用设备的 GMS Mock、Provider 冷启动扫尾与 AppOps 监听、服务异常日志、Shizuku 诊断、Consumer Matrix、独立腾讯 SDK GEO/POI + Fresh/缓存测试、轨迹/重放、MapLibre + OpenFreeMap。重复功能不优先移植。
源码对照入口：
- [LabProviderReliabilityController.java](../app/src/main/java/com/zcshou/gogogo/LabProviderReliabilityController.java)
- [LabServiceLifecycleJournal.java](../app/src/main/java/com/zcshou/gogogo/LabServiceLifecycleJournal.java)
- [ServiceGo.java](../app/src/main/java/com/zcshou/service/ServiceGo.java)
- [TencentLocationProbeActivity.java](../app/src/main/java/com/zcshou/gogogo/TencentLocationProbeActivity.java)
- [Lab 25 发布阻断清单](lab25-release-readiness.md)

## 第一轮（7 个）

| 候选及证据 | 非典型入口 | 值得对照 | 当前判断 |
|---|---|---|---|
| [lesterppo/pikmin-android-tools](https://github.com/lesterppo/pikmin-android-tools) · [fix-mock-slot.sh](https://github.com/lesterppo/pikmin-android-tools/blob/main/fix-mock-slot.sh) | 皮克敏游戏辅助套件 | 开发者选项变化后 AppOp 与选择槽位可能不一致，命令后校验；针对自己设备的恢复步骤 | **A**：研究授权变更/恢复 UX；shell/ADB 需要显式授权，不移植成无提示的特权修改 |
| [urnetwork/android · MOCKLOCATION.md](https://github.com/urnetwork/android/blob/main/MOCKLOCATION.md) | VPN 网络软件 | AOSP Provider、不同 Android API 版本、撤销授权/强杀后的残留、fresh 时间戳与清理语义 | **A**：与 Lab 20/25 的 ORPHANED / PENDING 和 GMS 回调逐项核对；文档中的 AOSP 结论仍须在目标机复测 |
| [1zumiii/AppOpsNext](https://github.com/1zumiii/AppOpsNext) | Shizuku 权限管理器 | Read → Write → Read-back → 尝试恢复、真实允许/拒绝状态、权限访问监控 | **A**（设计参考）：项目要求 Android 15+，不能认为二进制可在 Android 13 运行 |
| [noellegazelle6/kail_location](https://github.com/noellegazelle6/kail_location) · [技术说明](https://github.com/noellegazelle6/kail_location/blob/main/%E9%A1%B9%E7%9B%AE%E6%8A%80%E6%9C%AF%E6%96%87%E6%A1%A3.md) | 多模式应用及 Sandbox | Developer/Root/Xposed/Sandbox 技术边界和资源兼容性 | **B**：只做分层架构研究；Root/LSPosed/容器技术不可宣称免 Root 同等能力 |
| [jihaobin/expo-qq-location](https://github.com/jihaobin/expo-qq-location) | Expo/React Native 的 native module | 腾讯 SDK 连续请求、单次 fresh 请求、允许缓存、回调与状态变化 | **B**：Lab 24 已有类似功能，仅对比异常处理、回调超时和一致性 |
| [ZForest000/gpstest](https://github.com/ZForest000/gpstest) | GNSS 诊断仪 | Shizuku dumpsys、卫星信息/TTFF、辅助定位状态 | **B**：加强“Android API 读数 vs 原始 GNSS”观察；无证据说明它可修改腾讯/微信定位 |
| [tqxy/gps-mock](https://github.com/tqxy/gps-mock) | README 品牌是 Location Tools，仓库名仍是 gps-mock | Photon 搜索 + OSRM 路线、免 key 地图与体验 | **C**：地图/路线方案已高度重合；侧重产品交互，不需要重复做底图 |

## 第二轮（8 个新增）

| 候选及证据 | 为什么可能被漏掉 | 可学习的具体点 | 当前判断 |
|---|---|---|---|
| [rushiranpise/Shizuku-Next](https://github.com/rushiranpise/Shizuku-Next) | Shizuku 管理器而非定位 APP | 服务启动时序、旧进程清理、配对/断连状态、故障诊断；注意这是 Shizuku 管理端而非 GoGoGo 的注入端 | **A**：核查绑定恢复状态和用户提示；不照搬未经验证的权限扩张 |
| [kreza6173-pixel/void-apps](https://github.com/kreza6173-pixel/void-apps) | 应用管理器 | applied / not applied / unverifiable / refused / failed 五类结果，执行后重新查询系统值；配合只读取证 | **A**：可将分类思想应用于 Lab 诊断日志，避免“命令返回 0 = 成功”的误报 |
| [RGPtv/GPSLink-BETA](https://github.com/RGPtv/GPSLink-BETA) | 外部 USB/蓝牙 GNSS 桥接工具 | NMEA 数据、精度/速度/方位、测试 Provider 注入前后的一致性；README 仅为自述需做源码验证 | **B**：适合建立真实数据基线，不计划在 GoGoGo 中硬件依赖 |
| [Manabu-GT/android-mock-location-mcp](https://github.com/Manabu-GT/android-mock-location-mcp) | 测试自动化 MCP，不是手机定位 App | GPX/KML 回放、漂移、地理围栏、路线多站点自动化回归设计 | **B**：使用模拟器 `geo fix`，**不是**真机注入方案；优先借鉴测试脚本结构 |
| [konkomaji/geotagcamera](https://github.com/konkomaji/geotagcamera) | 相机/地理标签软件 | 主动请求 fresh location 与本地逆地理编码缓存分离，避免误把缓存当实时位置 | **B**：与 Lab 24/Consumer Matrix 对照定义“新鲜度”字段；不得推断第三方 UI 内部逻辑 |
| [Gegirhasut/route-spoofer](https://github.com/Gegirhasut/route-spoofer) · [README](https://github.com/Gegirhasut/route-spoofer/blob/main/README.md) | 原生 Kotlin + Capacitor 路线工具 | 位置发布与路线行进两个开关分离；常驻终点、等待/继续、RouteEngine 纯单测 | **B/C**：GoGoGo 已有轨迹引擎，重点找可复用的**测试用例**和产品交互差异 |
| [MaxwellDPS/Flock-You-Android](https://github.com/MaxwellDPS/Flock-You-Android) · [GNSS 检测说明](https://github.com/MaxwellDPS/Flock-You-Android/blob/main/docs/detections/GNSS_SPOOFING_DETECTION.md) | 反监控/无线检测应用 | Mock Location API 与原始 GNSS 测量可能出现不同结果，可设计只读交叉检查 | **B/C**：仅用于一致性分析和实验设计；不开发绕过第三方反作弊/反检测机制 |
| [aospbooks/aosp-internal-book · 33-location](https://github.com/aospbooks/aosp-internal-book/blob/main/33-location.md) | Android 内部机制学习文档 | LocationManagerService、MockLocationProvider、AppOps 边界 | **A（理论材料）**：与 AOSP 官方代码核对，优先解决误解而不是堆功能 |

> 分级：A = 应先研究；B = 可做专项对照；C = 价值偏低/与现有功能明显重合。分级不是代码质量/可靠性认证。

## 对照结论与研究假设（未经实机确认）

1. **最高优先级：授权恢复与数据真实状态。** GoGoGo 已有 `startWatchingMode`、`sweepBeforeRegistration`、`retryDeferredCleanup` 和 Lab 23 事件日志。应核对在“进程强杀/关闭开发者选项/改选其他模拟定位应用/恢复授权”组合下，屏幕有没有把本地记录误写成“系统已恢复”。对照 urnetwork、Pikmin 和 AppOpsNext/VOID 的严格回读设计。禁止在没有用户明确许可的情况下自动更改系统特权设置。
2. **其次：分离定位生产、消费、地图展示。** 把发送到 GPS/NETWORK/FUSED 的结果、Android/GMS 自有探针获取值、Tencent GEO/POI 请求、第三方 UI 显示拆成独立时刻/来源字段。第三方 UI 只做黑盒观察；不能以“距离未变”倒推存在特定微信私有缓存。
3. **Shizuku：优先诊断可用性/连接中断。** 确认 binder 存活、权限状态、服务恢复阶段和错误提示，而不是新增暗箱特权。Shizuku-Next 的管理端机制不能无条件搬到 GoGoGo。
4. **路线/地图不是本轮核心。** 现有 GoGoGo 已有路线模拟、OpenFreeMap，优先补纯单测边界和可操作的功能状态，不大规模改架构。
5. **实验平台优先 Android 13。** Android 15+ 专用 AppOpsNext 只能参考设计；模拟器的 geo fix 行为不能替代安卓真机；有 Root/Xposed 依赖的功能不纳入免 Root 支持承诺。
6. **公开 APK 使用 DUMMY Tencent Key。** 不能靠 CI 或第三方 README 声称微信/Tencent SDK 兼容已解决；需要在合规、知情且可控的自有测试环境做实测。

## 下一轮检索种子（避免只搜项目标题）

- Android 内部：`MockLocationProvider`, `LocationProviderManager`, `appops mock_location`, `location provider died`, `addTestProvider removeTestProvider`, `elapsedRealtimeNanos`, `getCurrentLocation`, `last known location`。
- 故障语义：`permission revocation`, `mock slot`, `fused cache stale`, `force stop location remains`, `provider cleanup after crash`。
- 不显眼的应用领域：GNSS/NMEA USB、照片地理标签、车载导航 QA、VPN exit location、AppOps 权限管理、Shizuku 运行管理、相机地理位置取证、模拟器测试编排、地理围栏测试。
- 中文与英文混搜：`定位回调 缓存 新鲜度`, `模拟位置 开发者选项 残留`, `腾讯定位SDK 单次 fresh`, `GNSS dumpsys shizuku`。
- 每一轮追踪：项目链接 → 明确源码文件/文档 → 当前 GoGoGo 对照 → 不兼容风险 → 可重复的只读试验 → 是否真正需要开发。


## 第三轮（额外 3 个，合计 18 个）

这轮的重点是 **不同安卓定位服务的分岔** 与 **客观日志格式**，不要把它误读成微信可以直接兼容。

| 候选及证据 | 不明显的价值 | GoGoGo 对照结论 |
|---|---|---|
| [warren-bank/Android-Mock-Location](https://github.com/warren-bank/Android-Mock-Location) · [service 分支 README](https://github.com/warren-bank/Android-Mock-Location/blob/service/README.md) | 多种构建 flavor 分别覆盖 AOSP GPS/NETWORK/FUSED、Google Play Services FLP、**华为 HMS Location Kit**，并存在 **microG/UnifiedNlp backend** 变体。这比单纯“提供 GPS 坐标”的仓库更值得看。 | **A**（跨生态研究）：当前 GoGoGo 有 Android 与 GMS 路径；HMS/microG 仅是后续架构候选，需检查设备上是否实际安装相应服务、SDK 条款、授权和 Android 13 兼容性；不能把该项目 README 作为真机兼容证明。 |
| [microg/android_external_UnifiedNlpApi](https://github.com/microg/android_external_UnifiedNlpApi) | 名字像普通 Android 外部 API 依赖，实际上包含 `LocationBackendService` 和 `GeocoderBackendService` 示例。它的 `update()/report()/onOpen()/onClose()` 描述了替代网络定位后端的请求与报告机制。 | **B**：了解非 GMS 手机上的网络定位路径和回调生命周期；不为不具备 UnifiedNlp 的手机盲目增加服务。 |
| [barbeau/gpstest](https://github.com/barbeau/gpstest) · [LOGGING.md](https://github.com/barbeau/gpstest/blob/master/LOGGING.md) | 公认的 GNSS 诊断软件，但隐藏价值是**可对照的日志格式**：CSV Fix 行包含 Provider、精度、`elapsedRealtimeNanos`、Android 12+ 的 `MockLocation` 字段；同时可导出原始 GNSS、NMEA、Status 和方向数据。 | **A**（验证工具）：给 GoGoGo Consumer Matrix 设计更严谨的时间戳/Provider/Mock 来源对照表；只保存必要字段，敏感坐标必须经用户确认再导出。 |

### 本轮最值得考虑的新方向

- **多服务提供商矩阵**：`AOSP Provider` / `Google FLP` / `Huawei HMS` / `microG UnifiedNlp` 是不同接口/部署条件，不应简单互相替代。GoGoGo 当前验证重点仍是 Android 13 的 AOSP + 可用 GMS 路径；HMS 和 microG 暂列候选，不投入实现。
- **客观定位证据格式**：使用 `source, provider, request_type, request_wall_time, callback_elapsedRealtimeNanos, result_age_ms, mock_flag, availability, sdk_error` 之类字段来区分新的回调、缓存、系统服务返回和应用 UI 变化。字段名仅为设计建议，不宣称已实现。
- **先验证产品可用性，再扩大适配**：不为了项目数量把 GMS/HMS/microG 三套 SDK 全塞进 APK；先验证实际手机环境，再决定是否需要可选模块。
- **警惕旧工程**：UnifiedNlp 示例和一些安卓插件长期未更新；API 概念可以研究，代码/依赖必须单独做版本、license 和安全审计。

检索来源均为公开项目文档及仓库页面；未做第三方 App 内部运行行为验证。

## 后续验收的硬边界

- 不通过伪造测试、隐藏 `Location.isMock()` 或更改第三方 App 的私有进程来制造“兼容成功”假象。
- 任何清理失败、授权拒绝、SDK 不可用都保留显式失败状态。
- CI 通过仅代表构建/静态或单元测试过关，不代表手机实测及微信定位成功。
- 外部项目许可各不相同，真正移植之前必须再次核查 LICENSE、依赖闭源 SDK 与引用署名。
