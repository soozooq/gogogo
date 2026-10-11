# GoGoGo 项目接管与工程基准总账（2026-10-11）

> **性质：**跨对话续接用的工程事实索引与初步源码审查。区分已核验、设备实验、假设及待处理事项。本文件只保存项目状态，不改位置发布、权限、Shizuku、地图、签名或 APK。以后以 GitHub 实际提交/CI/实机结果优先。此文件本身不等于对仓库所有 380 项文件逐行审计。
>
> **不要自动合并 PR #28 或本交接 PR；保留用户审查。**

## 1. 身份、基线、打包

- **仓库：** [soozooq/gogogo](https://github.com/soozooq/gogogo)，公开 Fork，默认 `main`。
- **核验时间与头提交：** 2026-10-11，`main` = `7aa4970a9788e2eff610401850ec9a60f5c56fe1`（Merge Lab 46；2026-10-10）。
- **主分支 APK 配置：** `applicationId=com.soozooq.gogogo.test`、`versionName=2.4.0-lab46`、`versionCode=2400051`、`minSdk=27`、`targetSdk=35`、`compileSdk=36`。源码命名空间沿用 `com.zcshou.gogogo`。Java 11，CI JDK 17。
- **构建：** `.github/workflows/build-check.yml` 执行 Gradle build；`.github/workflows/build-release.yml` 在 PR 和 main push 构建调试预览 APK，工件名 `gogogo-unified-labs-preview`。
- **上游/历史：** GoGoGo/影梭源于 ZCShou/GoGoGo；备份分支 `backup/main-before-labs-20261009` 仍有历史主分支快照。GPL-3.0-only。
- **公开预览限制：** Actions 的 `local.properties` 注入 `TENCENT_MAP_KEY=DUMMY`，故公开预览包**不能**验证真正的 Tencent 定位/POI/地图相关私有 Key 功能。预览测试签名不等于生产签名，稳定发布前必须设计独立、保密的正式签名流程。
- **最近 CI 证据：** main 的 Build Check [38040053689](https://github.com/soozooq/gogogo/actions/runs/38040053689) 与预览 APK [38040053699](https://github.com/soozooq/gogogo/actions/runs/38040053699) 通过。

## 2. PR 与开发进度

- 历史 Labs 1–25 合并于 [PR #5](https://github.com/soozooq/gogogo/pull/5)，后续文档、诊断修正、UI 和地图改进进入 main。
- **Lab 26–29**：17 个离线地点预设、MapLibre + OpenFreeMap 无须配置瓦片 API Key、主页/地图/实验入口去重、深浅色适配。
- **Lab 30–37**：双年龄 Consumer Matrix（fixAge/callbackAge）、只读基础快检、异常退出恢复证据、合并报告、历史事件时间窗、GMS 异步 Task requestId 顺序、跨会话迟到回调保护与退出隔离。
- **Lab 38–41**：诊断中心/首页折叠收口、地图选点回填首页且不自动启动模拟、旋转保留地点和路线状态。
- **Lab 42–46**：全球 157 个城市预设、109 个国家/地区标签、搜索/共用收藏、国旗+国家分组、列表复用与缓存、拼音 A–Z 索引、按住滑动字母快速跳转。
- **最新未合并 PR：** [#28 — Passive Snapshot](https://github.com/soozooq/gogogo/pull/28)；分支 `feature/passive-snapshot-cache-probe`，头提交 `a34cf700c67e4fdcf71f38994b95809df6ddf49e`，Draft、未合并，基于 main。仅修改 `ConsumerLocationProbeActivity.java` 和 `LabHubActivity.java`。
- **#28 CI 成绩：** Build Check [38057577231](https://github.com/soozooq/gogogo/actions/runs/38057577231) 成功；Preview APK [38057577245](https://github.com/soozooq/gogogo/actions/runs/38057577245) 成功，工件 ID `11672098529`，约 21.2 MB（ZIP）。**未完成被动性手机端验收；不能直接合并。**

## 3. 源码关键路径（已读取的核心文件）

| 组件/入口 | 源码 | 边界或责任 |
| --- | --- | --- |
| 首页 | `SimpleMockActivity.java` | 手动坐标、开始/停止模拟、地图/研究入口、首轮懒加载诊断；不得把选点直接视为启动 |
| 地图与路线 | `LabMapActivity.java` | MapLibre/ OpenFreeMap、离线预设、路线、样式、回填首页；WGS-84 经纬度 |
| 地点选择 | `LabLocationPresets`、`LabPlacePickerActivity`、`LabPlaceCountries`、`LabPlaceAlphabet`、`LabStore` | 157 城市、109 国家/地区、拼音字母索引、收藏共享 |
| 注入与生命周期 | `ServiceGo.java` | 前台服务、33ms 定位发布循环、GPS/NETWORK、可用时 Framework FUSED + GMS FLP、持久 LocationListener、路线/漫游/重播、停止清理 |
| 状态与恢复 | `LabProviderReliabilityController.java`、`LabGmsRequestOrder.java`、`LabServiceTeardownSequence.java` | Provider CLEAN/ACTIVE/DEGRADED/ORPHANED、冷启动扫尾、AppOps 观察、GMS 异步 requestId 与过期回调保护 |
| 消费者矩阵 | `ConsumerLocationProbeActivity.java`、`LabConsumerMatrixEvaluator.java`、`LabLocationAge.java` | 读取者单独进程 `:consumer`，区分收到回调的时间与 fix 本身的时间，评分时排除过时快照干扰 |
| 被动观察（PR #28） | 同一 Consumer Activity + `LabHubActivity.java` | 启动前读取 EXTRA_PASSIVE_MODE，仅读 GPS/NETWORK getLastKnownLocation 与 GMS getLastLocation；不订阅或 getCurrentLocation |
| 腾讯对照 | `TencentLocationProbeActivity.java`、`TencentProbeMath.java` | Tencent GEO/POI SDK、允许缓存/Fresh 与 Android/GMS 对照；有效腾讯 Key 另行实机核验 |
| 实验诊断 | `LabHubActivity`、`LabQuickCheckActivity`、`LabDiagnosticsActivity`、`LabRecoveryTriageActivity` | 默认只读，不修改模拟位置，不应宣称外部 app 已接受 |
| 隔离/运动研究 | `CrossProfileObservatoryActivity`、`IsolatedCapsuleLabActivity`、`SandboxLabActivity`、运动/路线引擎 | Work Profile、隔离服务、确定性情景、轨迹运动与 provenance；需与正常模拟业务隔离 |

旧工程还保留 `MainActivity`、腾讯地图 SDK 和 `JoyStick` 模块，与主力 `SimpleMockActivity/LabMapActivity` 的新工作流并存。绝不因重复命名直接删除历史路径，应先对照 Activity 入口、依赖和实际使用场景。

### 当前已审查到的关键实现事实

- `ServiceGo` 的发布间隔常量 `TICK_INTERVAL_MS=33`；系统 test providers 为 GPS/NETWORK 和尝试 Framework FUSED；GMS 使用 `setMockMode(true)` 和 `setMockLocation()`（异步 Task）。
- GMS 计数器 `mGmsFusedDispatchCount` 记录 `setMockLocation` **调用提交**，不表示 Task 成功或微信消费成功。
- `ServiceGo.onCreate` 先开启生命周期 journal 和 GMS session barrier，再做扫尾、重新注册 Provider；`onDestroy` 分阶段移除 Provider 和请求 GMS 停止，防止单项故障短路全局。
- 服务里 `ENABLE_JOYSTICK_OVERLAY=false`：当前预览包保留摇杆代码，但悬浮功能默认**未启用**。项目宣传/文档不能将它描述为当前构建已经启用。
- `ConsumerLocationProbeActivity` 正常模式启动会订阅 GPS/NETWORK/GMS 更新并请求当前新位置；它会影响准备观察的缓存；这正是 PR #28 改用独立被动入口的原因。
- 被动模式中的 1 秒 `ageRefresh` 只重绘已有样本年龄，不主动请求位置。新的 `shot/epoch` 让异步 GMS 回调归属原快照、清空结果作废未完成的旧回调；手机端仍需确认没有任何额外定位请求。
- `AndroidManifest.xml` 中 Consumer 和 Tencent 两个探针均在非导出的 `:consumer` Activity 进程；服务 `ServiceGo` 非导出，类型 `location`。
- `CLAUDE.md` 的 compile/target SDK、CI 分支、UI 主路径以及部分地图 Key 说明是旧版本记录，不能优先于实际 `app/build.gradle`、workflow 和最新 README。

## 4. 手机实验：2026-10-10（用户实际观察）

- 模拟目标北京天安门附近 WGS-84 `116.3974700,39.9054900`。Consumer Matrix 五个消费者显示北京，`isMock=YES`，报告当时为 fresh；**微信聊天发送位置仍显示福建闽侯附近，距离约 12,914.1 km，朋友圈“所在位置”持续搜索**。
- 第一轮停止 GoGoGo 后，微信有一次显示天安门附近；在 GMS 实时源已恢复福建时，`GMS lastLocation` 一度仍北京、`fixAge≈123.9s`、`isMock=NO`。强行停止微信重开后恢复福建。
- 第二轮复现中，启动模拟北京时朋友圈仍持续搜索；停止 GoGoGo 后微信直接恢复福建，GMS lastLocation 与实时均福建、约 `fixAge≈92.7s`。**不能认定“停止后显示北京”必现，也不能锁定微信内部缓存因果机制。**
- 早前实验发现 Shizuku 参与/开关状态可能影响微信，但只凭组合成功的个例**不能**证明 GoGoGo 单独生效，也不能推断第三方私有接口。
- 实验需分别记录：系统发布尝试；Consumer Matrix GPS/NETWORK/FUSED/GMS 读取结果；腾讯 SDK GEO/POI（有有效 Key 才能验）；微信聊天“自动定位”、手动选择、朋友圈/POI 和距离标签；各自时间、操作顺序与是否强停。
- **安全边界：**只做本机授权的模拟与公开接口黑盒对照，不绕过反检测、不注入第三方进程、不收集/发布真实位置和私密 Key。

## 5. 研究资料积累，不要重做一遍

- [docs/research-nonobvious-location-projects-20261009.md](research-nonobvious-location-projects-20261009.md)：已累计至少四轮非典型命名项目筛选，从 AppOps、Shizuku、GNSS、外接 USB 定位、Android Binder、HMS/microG、模拟器编排等领域追源码。**是候选与静态证据库，不是手机兼容证明。**
- 优先比较的现有线索包括 `urnetwork/android` 的 MOCKLOCATION 文档、`lesterppo/pikmin-android-tools` 的 AppOps 修复脚本、`1zumiii/AppOpsNext`、`warren-bank/Android-Mock-Location` 的不同 provider flavor、`barbeau/gpstest` 的原始 GNSS/CSV 证据、`AuroraNest/Modify_Positioning` 的异步竞态测试。
- `docs/research-roadmap.md`：早期 TaintDroid provenance、MockDroid 资源影子、MOSES/TrustDroid Profile 域、Boxify 的隔离过程概念映射；不应被解释成 OS 全局信息流跟踪或透明 Hook 第三方 App。
- 现有路线/运动、Provider 生命周期、OpenFreeMap 地图等已实现部分，不再因为开源项目多就重复移植；新研究必须有**明确缺口、证据、可回归测试、授权与维护成本**。

## 6. 源码审查发现与风险分级

| 等级 | 发现 | 根据/边界 | 建议 |
| --- | --- | --- | --- |
| P0 发布阻断 | 公开仓库使用已提交的测试签名材料，`app/build.gradle` 中配置公开静态签名口令，不能把这套签名用于安全敏感的正式发行 | `app/build.gradle`、`keystore/`；本项目依旧只是 Preview | 设计独立私密发行签名，保护升级路径；保留原测试 APK 数据，未经规划不更换签名覆盖 |
| P0 正确性 | README 历史部分声称“微信已正常 mock/三路径修复”，与最近的微信实机现象冲突 | `README.md` 靠后旧章节 vs 2026-10-10 实测；说明可能适用旧场景，但不能泛化 | README 顶部已有 Preview 免责声明；后续单独澄清旧断言与验证条件 |
| P0 验收 | PR #28 的正常模式与被动模式静态分支已检查，CI 编译/旧单测通过，但 PR 两个变更文件中**没有新增行为回归测试**，并且未完成真机检查 | [PR #28](https://github.com/soozooq/gogogo/pull/28) 两文件；CI 成功不等于系统无请求 | 保持 Draft；实机取样前后比较；今后可抽纯逻辑测试守住“被动禁止主动订阅” |
| P1 跨版本诊断 | `ConsumerLocationProbeActivity.Channel.isMock()` 在 Android 12 以下读取 `extras["mockLocation"]`，没有采用 API 18 已提供的 `Location.isFromMockProvider()`；旧系统可能漏报 Mock | 只影响旧系统 Consumer 的诊断准确性，不是 Android 13 主故障的证据 | 在独立诊断修复 PR 里补旧系统兼容测试，不改变注入行为 |
| P1 产品/文档 | `ENABLE_JOYSTICK_OVERLAY=false` 与旧 README “悬浮摇杆可用”的泛化介绍不一致 | `ServiceGo.java`；有旧 UI/功能并不代表当前预览已启用 | 不直接打开开关；复核权限、UI 和安全后再决定 |
| P1 文档漂移 | `CLAUDE.md` 记有旧 SDK 32、旧 CI 触发、旧主地图说明 | 以真实 Gradle、Manifest、CI 为准 | 另开文档维护变更，避免接手者配置错误 |
| P2 可维护性 | `ServiceGo.java` 约 2778 行，混合路线、定位发布、状态/观测、生命周期；盲目重构风险高 | 已核对主服务关键启动、循环、GMS 关闭、退出路径 | 回归测试覆盖真实责任边界后渐进拆分，禁止同轮“大翻修” |
| 待确认 | 真机 Android 13 上微信缓存、POI、GMS Task 时序变化 | 尚无第三方内部可信证据 | 使用 PR #28 的被动快照与微信黑盒时间顺序对照 |

## 7. 下一步应按此顺序继续

1. **完成 PR #28 只读源码检查与手机验收，不合并：**启动前不进普通 Consumer Matrix；打开 Lab Hub → 被动快照，记录 Shot #1；重复两次，比较 fixAge、采集结束时间、isMock、NULL/失败。验证正常 Consumer Matrix 独立仍可主动采集。
2. **把微信现象做成可复现矩阵：**按“无模拟 → 模拟北京 → 微信聊天/朋友圈分别观察 → 停止 → 再观察 → 可选强停微信”顺序记录各路位置及时间；不要求用户反复切 Shizuku 或清除 App 数据。
3. **只有得出新增证据后才改 ServiceGo：**目前禁止“加卫星 extras”、改 NETWORK/FUSED 注入参数、反检测、额外 Lab 编号及第三方进程 Hook。
4. **处理独立、低风险的确定性缺陷：**旧系统 Mock 标记诊断、文档误导、生产签名规划，分别做小 PR。
5. **日后再做地图/产品 UX 手机验收：**157 城市选择器、搜索、收藏、旋转、深色/小屏与恢复路径；已合并代码不等于手机视觉和功能都过关。

## 8. 接手纪律

- 此文件是**续接索引**，不能代替直接读取源码、最新分支、PR diff、CI 与手机验收。仅有静态核验的项目不能写“微信兼容已解决”。
- 不因 CLI/构建环境成功而宣称手机测试成功。不可把“请求发送”写成“应用消费/定位成功”。
- 继续遵守用户偏好：不重头研究已有模块；尽量自动化开发和仓库工作，不让只有安卓手机的用户完成大量繁琐步骤；不擅自合并待用户审查的 PR。
- 变更只在小范围分支上做；保持历史恢复基线 `backup/main-before-labs-20261009`，保护已有主线功能。
