# GoGoGo · 定位兼容性核心技术研究与落地（2026-10-12）

## 任务与优先级

用户明确要求**停止以增加 Lab 面板代替核心能力**，对影梭上游限制逐条核对、检索论文和开源实现，在 Android 13 vivo 上优先增加真实定位引擎可靠性；如未找到可靠的免 Root 全平台兼容方案，须写“目前没找到”，不能承诺微信/抖音/Claude 必定接受位置。

**已经具备，避免重复**：ServiceGo 33ms 平台 GPS/NETWORK/FUSED test provider + Google Play Services FusedLocationProviderClient mock；持久订阅者保活、服务生命期 journal、任务接管防迟到回调、Shizuku 只读进程信息、缓存/跨应用对照、VPN/IP时区/锁定、地图/轨迹与 UI。仅查 README 并不会证明其它 app 已使用这些模拟坐标。

## 一、本轮找到的真实引擎缺口：GMS 异步队列

源码（分支基准 PR #44）：`ServiceGo.initGoLocation` 每个 HandlerThread tick 间隔约 33ms，连续调用 `setLocationNetwork/GPS/Fused/FusedClient`，其中 `setLocationFusedClient` 旧实现：

```java
mFusedClient.setMockLocation(loc);
mGmsFusedDispatchCount++;
```

异步 Task 未监听完成事件，GMS 比 tick 慢时可能排队提交大量未确认的位置，`mGmsFusedDispatchCount` 仅是**提交次数，不是成功次数**。这不证明它导致微信失败，但它是真实、确定的引擎弱点。

**本 PR 的最小功能修复（不是新增实验）**：
- 新的纯 Java `LabGmsPublishGate`，每个 ServiceGo 实例只允许**一项**未完成的 GMS FLP setMockLocation Task；其间下一帧 GMS 提交跳过，等完成后下一 tick 使用**最新坐标**；不回放旧坐标，不攒无限队列。
- 真实 `Task<Void>.addOnCompleteListener` 返回后释放 in-flight 槽位，按成功/失败分别计数；同步异常按失败处理；停止服务时关闭 gate、忽略迟到回调。
- 保留 GPS/NETWORK/framework FUSED 33ms 发布；不更改位置精度、卫星 extras、provider Mock 标记、GMS setMockMode 启停时序或第三方 app。
- ServiceGo Binder 暴露 GMS 已提交/已成功/已失败/已合并跳过、等待年龄、距上次成功时间；异步成功只证明 **GMS Task 成功完成**，不是其它 app 消费了这个坐标。
- 若 Google Play services 存在永久不完成的 Task，single-flight 会停止新的 GMS 提交而不堆积内存；服务可重启恢复。要不要启用受控超时重试必须在真实设备有依据后判断，不能冒着过期回调乱序风险盲目添加。
- `LabGmsPublishGateTest` 覆盖连续 500 帧单一 in-flight、失败后接新帧、迟到回调不能释放错误槽位、服务关闭后异步回调失效。

Google 官方依据：https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient
Google `setMockLocation` 为异步 Task；要求正常、单调递增的时间戳，FLP 正常处理规则仍有效；Mock 模式及其输入会被标记为模拟。

## 二、开源工程候选（本轮与之前研究去重）

| 仓库 | 核心差异与证据 | 与现有 GoGoGo 差异 | 是否移植 |
|---|---|---|---|
| [warren-bank/Android-Mock-Location](https://github.com/warren-bank/Android-Mock-Location) | 不同 flavor 提供 AOSP / GMS FLP / HMS Location Kit / microG UnifiedNlp；不同环境有不同提供者。其 #14 有 Android 13 失败反馈。GPL-2.0，与 GoGoGo GPL-3.0-only 的许可证兼容性应具体核查。 | AOSP/GMS 路径已有，**HMS/microG** 尚未整合 | **有条件**：先证实手机实际安装/启用 HMS 或 microG；无则不引入冗余依赖。 |
| [urnetwork/android MOCKLOCATION](https://github.com/urnetwork/android/blob/main/MOCKLOCATION.md) | 对 AOSP GPS/NETWORK/FUSED 和 GMS 分离、Android Mock 权限、异步与缓存覆盖有源码指引 | 现有实现一致，缺口是 GMS Task 状态与节流 | **直接采纳**消息队列与状态语义，禁止仅复制相同 provider。 |
| [BuriXon-code/MockGPS](https://github.com/BuriXon-code/MockGPS) | 前台服务、ADB/Termux 外部控制、Android 10+、GPS/NETWORK，无 GMS FLP，GPL-3.0 | 核心覆盖面比现有窄；广播脚本可借鉴自动回归 | **不移植定位引擎**，仅复用用户明确授权的自动化测试思路。 |
| [RikkaApps/Shizuku-API](https://github.com/RikkaApps/Shizuku-API) | shell 或 root 身份的分离式 Binder/UserService，UID 2000 vs 0 | GoGoGo 已有 Shizuku UserService，但尚不能获得 Root 级定位能力 | **保留可选** AppOps / 系统位置服务可访问性核验与受控设置；不宣称 Shizuku 可直接替换 Root/LSPosed。 |
| [microg/android_external_UnifiedNlpApi](https://github.com/microg/android_external_UnifiedNlpApi) | microG 网络定位后端接口，位置更新与反地理编码接口 | vivo 仅有 GMS 时可能完全无用 | **条件性**：只有检测到 UnifiedNlp 并且用户主动启用时才考虑独立 flavor。 |

### 关于 Shizuku 替代 Root（严格分级）

- **可行/可验证**：UserService 以 shell 身份运行，可读取部分 `cmd location`、`dumpsys location`、AppOps、设备设置与进程状态；可提示 mock AppOps 的真正权限状态。需要用户在 Shizuku 授权，且取决于 vivo 固件与具体 Android 版本授权，不能预先承诺。
- **有条件**：部分系统服务接口允许 shell 修改设置，必须仅在用户单独开启选项、记录请求结果并核验系统最终状态时实施；未经同意不能强改 VPN、SIM、时区等系统设置。Android shell 权限集随系统版本变化，不能把另一手机的成功硬套 vivo。
- **不能视为 Root**：免 Root Shizuku shell UID 2000 并不获得 UID 0 的私有文件/内核/SELinux 特权，不能直接拦截或篡改微信/抖音进程的私有定位 SDK 回调、GNSS 芯片读数或擦掉 Android 官方 `Location.isMock()`。即便 Root/Xposed 代码存在，也不是免 Root Shizuku 的功能。

官方依据：https://github.com/RikkaApps/Shizuku-API#differents-of-the-privilege-betweent-adb-and-root

## 三、论文：研究证明多源定位问题，而非有现成万能补丁

1. **“Drive Me Not – GPS Spoofing Detection via Cellular Network”** (ACM WiSec 2019)：
   [研究数据与作者描述](https://github.com/cri-lab-hbku/gps-spoofing-detection-cellular)；采集 GPS、基站、Wi-Fi 三条不同传感来源进行交叉比对。说明**单改 LocationManager 接口不等于其它感知/网络信号也一起改变**。这是理解用户“SIM 中国 MCC/以太网/抖音国内位置”的技术框架，非可直接移植的定位代码。
2. **“GNSS Spoofing in Mobile Devices: A Survey on Impact and Countermeasures”** (2026-10-01，[arXiv:2610.01294](https://arxiv.org/abs/2610.01294))：手机 GNSS 接收机权限/硬件可见性有限，跨层对抗有技术边界。RF/GNSS 信号与 Android 上层 API Mock 是不同层面的研究，不能把论文中的 RF 实验说成可以免 Root 安装 APK。
3. **“AndroCon: Conning Location Services in Android”** (2024，[arXiv:2407.19392](https://arxiv.org/abs/2407.19392))：高维度 GNSS 半处理特征能反映环境与用户行为，间接支持“位置隐私不只等于一组经纬度”，**不是**微信反 mock 或用户设备 GPS 注入补丁。

## 四、实际后续开发优先级（不新增 Lab）

**P0 本 PR**：修正 GMS FLP 发布 backpressure + 回调状态语义，保留全部原有 provider。成功标准：CI tests/lint + vivo 上后台运行不崩，`submitted/success/failure/coalesced` 可取值；这是**引擎可靠性改动**，不能作为微信/抖音已兼容的验收替代。

**P1 条件分支**：检查 vivo 是否有 GMS / HMS / UnifiedNlp，其中**HMS 与 microG 支持只有在确实存在且可调用时才投入开发**。不能因为源码找到了新的 SDK 就盲目依赖引入到主包。最好每个平台一份独立 adapter 与独立小 PR。

**P1 系统持续性**：复查 Android 13/vivo 前台服务被系统回收的问题：重启策略、确实生效的服务保活、失败时恢复原有 provider。已有 wakeLock 和 START_STICKY，并不能保证永不被厂商后台管理杀死。

**P2 跨应用差异**：针对用户自有测试保留微信/抖音/美团/高德/腾讯地图对照，但不继续堆 UI 或尝试改其它 App；第三方 App 的 SDK / 反模拟机制无可靠源码与设备证据时直接标 UNKNOWN。

## 五、本轮没有找到的方案（必须明确）

**目前没有找到**可靠、公开、可在用户的 vivo Android 13 **免 Root + 仅 Shizuku ADB 模式**下，“无须第三方配合就强制所有应用接受同一虚拟位置”的可复用项目或经过真机证实的论文方案。

Shizuku 在免 Root 下绝不能自动替代 Root/Xposed/系统签名权限；Android 对模拟定位标记与不同传感来源的隔离仍然存在。不能保证微信/抖音/Claude/银行/收货业务应用全都采纳位置，也不能保证隐藏实际国家或避开服务地区政策。

## License / 发布

上游影梭 GPL-3.0-only。其它仓库的 GPL-2.0-only 或特殊版权条款必须先做兼容性审查；本 PR **未复制任何第三方源码**，只参考架构与已知 Android API 并自行编写纯 Java 背压策略。继续在单独 PR 中维护，不合并 main，真机测试前保留原版 APK 回退。
