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


## 第四轮（新增 7 个，累计 25 个）— 外接 GNSS、跨厂商服务、异步验证与无头控制

本轮从 **车载 GPS、SDK 兼容库、脚本自动化、App 状态管理、系统 Binder、地理围栏消费者** 找源码，而不靠 `mock gps` 标题搜索。每条已经确认项目仓库存在，并尽可能追溯到真实源码；下面只代表**静态代码观察**，不代表构建/目标手机/第三方应用验证。

| 候选仓库 / 已核验文件 | 隐藏价值 | 对照 GoGoGo 及处理建议 | 优先级 |
|---|---|---|---|
| [Explore-In-HMS/common-mobile-services](https://github.com/Explore-In-HMS/common-mobile-services) · [GoogleLocationClientImpl](https://github.com/Explore-In-HMS/common-mobile-services/blob/master/location/src/main/java/com/hms/lib/commonmobileservices/location/factory/GoogleLocationClientImpl.kt) / [HuaweiLocationClientImpl](https://github.com/Explore-In-HMS/common-mobile-services/blob/master/location/src/main/java/com/hms/lib/commonmobileservices/location/factory/HuaweiLocationClientImpl.kt) | **通用移动服务库实际藏有两套异步定位实现**，都实现 `setMockMode`, `setMockLocation`, `getLastKnownLocation`, `requestLocationUpdates`；有一致的 Work 结果处理。 | **A**。对照现有 GMS Fused 诊断模型，先做 Google/HMS 是否安装、权限与失败原因的只读能力矩阵；用户设备不具备 HMS 时不引入华为 SDK。库以旧版 API 为主，代码仅供架构对照而非直接拷贝。 | A |
| [AuroraNest/Modify_Positioning](https://github.com/AuroraNest/Modify_Positioning) · [CompositeLocationInjectorTest](https://github.com/AuroraNest/Modify_Positioning/blob/codex/initial-app/app/src/test/java/com/aurora/modifypositioning/CompositeLocationInjectorTest.kt) / [LocationSimulationEngine](https://github.com/AuroraNest/Modify_Positioning/blob/codex/initial-app/app/src/main/java/com/aurora/modifypositioning/simulation/LocationSimulationEngine.kt) | **最值得看的不是定位注入，而是单测**：异步 enable 尚未成功时不能上报成功、inject 失败不能复用旧成功、并发请求保留最新 sample、停止后晚到 callback 不可覆写最终状态、一个注入器失败不妨碍其他健康通道。 | **A**。与 Lab 25 GMS async journal 做逐测试对照，在现有代码增加类似回归用例之前先查缺口；已有模块不另写一套。README 的微信兼容性描述不作为实测证据。 | A |
| [freshollie/UsbGps4Droid](https://github.com/freshollie/UsbGps4Droid) · [NmeaParser](https://github.com/freshollie/UsbGps4Droid/blob/master/app/src/main/java/org/broeuschmeul/android/gps/nmea/util/NmeaParser.java) | 外接 USB GPS 的 NMEA 解析/Provider 适配，原始 NMEA fix 生成 Android Location，并显式报告 Provider 启停和数据有效性。 | **B**。参考 **外部来源→解析有效性→系统 Provider→Consumer** 分层与日志，不为不使用 USB 的用户引入 USB 驱动；项目 README 自述实测平台较老（Android 5/6），不能推断 Android 13 正常运行。 | B |
| [BuriXon-code/MockGPS](https://github.com/BuriXon-code/MockGPS) · [MockGpsReceiver](https://github.com/BuriXon-code/MockGPS/blob/master/app/src/main/java/dev/burixon/mockgps/MockGpsReceiver.kt) / [AndroidManifest](https://github.com/BuriXon-code/MockGPS/blob/master/app/src/main/AndroidManifest.xml) | 通过 Android Broadcast / Termux / ADB 给模拟服务发 on/off/set/drift 命令，UI 可不打开，适合作为测试工程的**外部自动化控制思想**。 | **B（架构）+ 风险提示**。当前源码的 receiver 与 service 均 `android:exported="true"`，广播入口主要靠用户是否在偏好中启用控制判断；不宜原样移入 GoGoGo。若未来建设自动化接口，必须明确用户 opt-in、保护调用者授权、输入校验并防止任意第三方应用操作位置或持续开启服务。GoGoGo 本次不新增外部 IPC。 | B |
| [AlexMelanFromRingo/mirage](https://github.com/AlexMelanFromRingo/mirage) · [MockLocationEngine](https://github.com/AlexMelanFromRingo/mirage/blob/main/app/src/main/java/com/melan/mirage/core/MockLocationEngine.kt) / [MockLocationService](https://github.com/AlexMelanFromRingo/mirage/blob/main/app/src/main/java/com/melan/mirage/service/MockLocationService.kt) | 每次发布前确定统一的 wall-clock 与 elapsed-clock，在 GPS/NETWORK/FUSED 间保持同一时间快照；对每个 Provider 单独观察成功失败，取消 mock app 后在循环中发现错误并停止。 | **B**。GoGoGo 已有部分按 Provider 记录状态，但值得对照**同一 sample 的时间戳/来源一致性**以及服务/UI 的 truthfulness；原仓库存在把某些本地成功简化为“可用”的风险，不直接把其状态视为外部消费者已接收。 | B |
| [AndroidGoLab/binder](https://github.com/AndroidGoLab/binder) · [examples/gps_location/main.go](https://github.com/AndroidGoLab/binder/blob/main/examples/gps_location/main.go) | 一个看起来与定位无关的 **Go/Binder IPC** 工具仓库，实际上有通过 `ILocationManager` 注册 `ILocationListener`，请求并打印 GPS fix，最终注销监听的示例。 | **B/C**。可用作 AOSP 服务层独立观察方案的概念参考；Shell/ADB 身份、SELinux、Binder 权限、Android 版本兼容不能当成普通 APK 默认权限。暂不移入 Java APK。 | B/C |
| [Preston-Landers/QuietPlaces](https://github.com/Preston-Landers/QuietPlaces) · [GeofenceRequester](https://github.com/Preston-Landers/QuietPlaces/blob/master/QuietPlaces/src/main/java/edu/utexas/quietplaces/GeofenceRequester.java) | 地理静音/地理围栏消费者，代码证明 geofence 添加请求存在独立的连接、回调与状态路径；项目还引用独立 Mock 测试应用。 | **C（历史例证）**。说明地图蓝点改变 ≠ geofence 事件必然到达；其 `LocationClient` / 旧 Play Services API 已严重过时，只学习“消费结果分别验收”的设计，不移植代码。 | C |

### 第四轮源码级重点结论

1. **先补异步竞态单测**：重点对照 `CompositeLocationInjectorTest` 的五种情况：请求未完成、不成功、后到旧回调、停机时回调、双通道局部失败。我们的 `LabProviderReliabilityController` 和 Lab 25 GMS 日志不等于已覆盖这些全部竞态；先列测试断言，再改代码。
2. **明确禁止无权限外控**：BuriXon 的 Broadcast 自动化对“未来让测试人员一键执行回归步骤”很有启发，但其 exported 组件是敏感设计点。除非有合理的鉴权/显式授权及撤销机制，否则只研究自动化接口，不开发对任意应用开放的定位控制广播。
3. **统一每轮样本/时间戳**：不同 Provider 应能追溯到同一 session/tick/sampleId，统计其实际提交、API 回调和 Android/GMS 消费读数。模拟数据的产生者和读取者必须分开统计。
4. **无设备时别冒称兼容**：旧外置 GNSS、华为 HMS、Binder Shell、地理围栏都是环境依赖。只有实机具备相关组件且得到明确授权才能做对应验证；不因此宣称微信/腾讯 SDK 已通过。
5. **持续去重**：本文此前 18 个候选包括两款 GNSS 观察工具和 HMS/microG 理论资料。本轮追加的是**具体实现源代码/测试文件**，它们与此前条目相关但不是同一仓库；落地时按“功能缺口”合并结论，不为了凑数重复开发。

### 第五轮检索方向（仅种子，尚未执行）

- `appops mock slot changes receiver`, `LOCATION_PROVIDER cleanup after app crash`, `FusedLocationProviderClient late callback stale request tests`
- `external nmea location source provider implementation`, `GNSS fix timestamp monotonic mixed provider test`
- `android geofence receive mock location null cached provider`, `TencentLocationRequest cache expiration reason code`
- 尤其找普通项目下的 `MockLocationEngine`, `LocationBackendService`, `LocationAvailability`, `GnssMeasurementsEvent.Callback`, `Work<Unit>`，而不是继续搜 GUI 产品名。


## 第五轮（新增 7 个，累计 32 个）— 缓存来源、数据年龄、跨应用消费者、复现测试

本轮重点从**聊天、加油站油价、5G 信号监测、独立 Fused SDK、GPS 测试编排、路线运行竞态、定位 SDK 版本记录**寻找非典型位置逻辑。以下为项目/源码静态审阅结果，**并非**对微信内部缓存、SDK 行为或目标手机的验证。

| 项目与已核验材料 | 与定位研究的非典型联系 | 对 GoGoGo 的具体价值/限制 | 优先级 |
|---|---|---|---|
| [areebahmeddd/airhop](https://github.com/areebahmeddd/airhop) · [place-names-store.ts](https://github.com/areebahmeddd/airhop/blob/main/src/store/place-names-store.ts) | 聊天应用把人类可读的地名作为 **独立于精确坐标的 geohash→名称缓存**；缓存 key 是语言标签 + geohash；同一 key 并发请求去重；清除缓存通过 generation 阻止迟到请求回写。 | **A**：借鉴“地图坐标/地名/缓存/语言/异步请求代次”分层，用作我们对比第三方 UI 的黑盒实验设计。并不意味着微信内部使用同样的缓存策略。 |
| [AlexandreZanata/brazil-fuel-prices-app](https://github.com/AlexandreZanata/brazil-fuel-prices-app) · [ReverseGeocodeRepositoryImpl.kt](https://github.com/AlexandreZanata/brazil-fuel-prices-app/blob/main/data/src/main/kotlin/com/anpfuel/data/repository/ReverseGeocodeRepositoryImpl.kt) · [相关单测](https://github.com/AlexandreZanata/brazil-fuel-prices-app/blob/main/data/src/test/kotlin/com/anpfuel/data/repository/ReverseGeocodeRepositoryImplTest.kt) | 油价 App 的位置解析把**坐标缓存命中、限速、网络出错、解析不合法、城市不在目录**分开；双检锁/Mutex 防并发重复请求；MockWebServer 单测断言缓存命中时不联网。 | **A**：给 GoGoGo 的地图显示/POI/城市名增加独立“缓存命中 vs 请求中 vs API 成功 vs 解析失败”的诊断设计建议；这不等于微信已如此实现。注意第三方公开 Nominatim 的限额、用户隐私、服务条款；目前不添加网络请求。 |
| [kkaasan/5GBC_phone_monitor](https://github.com/kkaasan/5GBC_phone_monitor) · [MonitoringService.kt](https://github.com/kkaasan/5GBC_phone_monitor/blob/main/android_app/app/src/main/java/ee/levira/cbmonitor/MonitoringService.kt) | 5G 广播信号监测器同时追踪 GPS/基站；`getLocation()` 用 `SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos` 算实际样本年龄，以 30s 为近期界限，超期请求 fresh，失败再回退最后已知值。 | **A（直接命中现有诊断缺口）**：区别 sample 本身的年龄和本机收到它后经过的时间。应参考其理念，不照搬同步等待或旧 SDK 代码。不要把系统位置缓存误写成最新 sample。 |
| [ecgreb/LOST](https://github.com/ecgreb/LOST) · [README](https://github.com/ecgreb/LOST/blob/master/README.md) | 名称不显眼：早期用 Android API 提供类似 Google Play Services 的 FusedLocation API 替身，包含 mock mode、单点 mock、GPX mock trace、Location Settings API 结果。 | **B（历史对照，不移植）**：用来厘清“APP 级 Fused SDK 仿真”与“系统级 AOSP/GMS Provider 改变”的区别；不能把 LOST 内部 mock 视为向真实 Google Play Services 或所有第三方 App 注入。部分接口已过时。 |
| [0xfnzero/gps-locator](https://github.com/0xfnzero/gps-locator) · [desktop-cli/suite.mjs](https://github.com/0xfnzero/gps-locator/blob/main/desktop-cli/suite.mjs) · [junit-report.mjs](https://github.com/0xfnzero/gps-locator/blob/main/desktop-cli/junit-report.mjs) · [相关单测](https://github.com/0xfnzero/gps-locator/blob/main/desktop-cli/test/junit-report.test.mjs) | 定位 App 实际自带 **测试场景套件**，支持 step ID 去重、场景 SHA256、每个设备结果、JUnit XML、成功失败记录；比单个坐标点的人工复测更易追踪回归。 | **B**：可借鉴保存“实验步骤/设备条件/请求结果/时间戳/可复现证据”的报告格式；它的 CLI 需要电脑/ADB，不能直接视为用户只有手机时的操作方案。 |
| [vincenzobpt/gps-mock-location](https://github.com/vincenzobpt/gps-mock-location) · [README](https://github.com/vincenzobpt/gps-mock-location/blob/main/README.md) | 看似普通 GPS 模拟器，其文档描述的 run epoch/单锁交通控制、暂停/seek/停止期间避免延迟 tick 发布是与异步可靠性紧密相关的工程问题。 | **B（仅文档级确认）**：待定位具体代码与 JVM 单测后再对照 GoGoGo 路线引擎的 stop/pause/seek 竞态；不以项目自述作为已运行测试结果。 |
| [hypertrack/sdk-android](https://github.com/hypertrack/sdk-android) · [CHANGELOG.md](https://github.com/hypertrack/sdk-android/blob/master/CHANGELOG.md) | 商用定位 SDK 的更新历史提到 `Mocked`、`SignalLost` 与地理围栏错误状态不一致的修复，提示**消费者错误状态也可能有延迟/不一致**。 | **C（版本记录，不是源码审计）**：帮助设计错误分类的术语；不用于第三方反检测绕过，也不推断具体 SDK 内部行为。 |

### 与现有 GoGoGo Lab 16 直接对照：发现“两个年龄被混为一谈”的潜在诊断错误

真实源码：
- [ConsumerLocationProbeActivity.java](../app/src/main/java/com/zcshou/gogogo/ConsumerLocationProbeActivity.java)：`Channel.update(Location value)` 保存 `receivedElapsedMs = SystemClock.elapsedRealtime()`；`Channel.ageMs()` 用当前 elapsed 减此接收时刻，并将其传给 `asSample()`。
- [LabConsumerMatrixEvaluator.java](../app/src/main/java/com/zcshou/gogogo/LabConsumerMatrixEvaluator.java)：针对 stream `ageMs <= 1000` 判新鲜、snapshot `ageMs <= 30000` 判近期。原字段并不包含 `Location.getElapsedRealtimeNanos()` 的实际 fix 年龄。

**因此：如果 Android/GMS 现在回传的是一条很久以前生成的 cached fix，`Channel.ageMs()` 会从此刻重新算 0，而不是反映实际定位结果从生成起已经过了多久。** 特别是一发起 one-shot 就返回缓存结果时，报告可能把“刚收到数据”写成“新鲜定位结果”，属于潜在**观测误判**，并非证明模拟发布失败或微信内部定位失败。

建议改进（**本轮未修改源码**）：
1. **双年龄**：`callbackAgeMs = nowElapsed - receivedElapsedMs` 和 `fixAgeMs = (SystemClock.elapsedRealtimeNanos() - loc.getElapsedRealtimeNanos()) / 1_000_000`；两个都标注年龄来源与时间单位。
2. **防异常**：如果 fix 时间戳 0 / future / API 不可用，则标 UNKNOWN/INVALID，不要 clamp 到 0 后宣称 fresh；跨 reboot/非同一时钟基线也要谨慎。
3. **结果语义**：回调刚到不等于 fix 刚生成；判“定位数据真实新鲜度”时用 `fixAgeMs`，服务是否活跃另用 `callbackAgeMs`/事件计数，避免混用。
4. **四组单测种子**：新到新 fix；新到旧 fix（模拟 LastKnown）；重复上报相同 fix；时间戳未来/缺失。Snapshot 超过阈值以后不应错误影响直播状态；保留已存在的 stream-vs-snapshot 差异。
5. **UI 证据链**：在设备自己的可控实验下独立记录 Android/GMS 返回的数据年龄、腾讯独立 SDK 的 fresh/缓存结果、地图坐标/城市名称/POI/距离标签各自更新时间。仅用可见第三方 UI 做黑盒观察，不猜测私有实现。

### 本轮去重与后续行动

- Lab 24 已有独立腾讯 GEO/POI + Fresh/缓存请求，Lab 16 已有五通道 Consumer Matrix，Lab 25 已有 Provider/GMS 生命周期日志。这轮**不是提议重新开发这些功能**，而是指出报告时间语义和消费者 UI 分层可能存在缺口。
- 优先级：**先做 Consumer Matrix 双年龄与纯单测 → 完成自己设备的 Android/GMS/腾讯 SDK 分层实验 → 再比较城市/POI/距离标签的可见变化**。无实机数据前，不得宣称修复微信。
- 本提交是研究文档追加，未更改模拟服务、版本号、CI 流程、APK 或任何现有功能。

## 后续验收的硬边界

- 不通过伪造测试、隐藏 `Location.isMock()` 或更改第三方 App 的私有进程来制造“兼容成功”假象。
- 任何清理失败、授权拒绝、SDK 不可用都保留显式失败状态。
- CI 通过仅代表构建/静态或单元测试过关，不代表手机实测及微信定位成功。
- 外部项目许可各不相同，真正移植之前必须再次核查 LICENSE、依赖闭源 SDK 与引用署名。
