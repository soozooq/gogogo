# 天外天 Virtual Container V0 — 主程序内建诊断

## 2026-10-18 — 2.4.21 云端双工作流已通过（V2.2 待真机）

- 发布版本：`2.4.21-tianwaitian-v2-session` / `versionCode 2400089` / `com.soozooq.gogogo.test`，同一宿主签名方案及原有定位模拟路径保持不变。构建代码 SHA `00693ac0441c8a6953d3a3dc4f5f7c7852a98723`。
- [Build Preview APK #39090948382](https://github.com/soozooq/gogogo/actions/runs/39090948382)：**success**。包括 fixture 新增七项 `GuestV2SessionTest` 单测、原有 V0/V1/V2 静态和 Java 回归、`app:assembleDebug`、原有真实 APK Manifest/fixture 打包检查。
- [Build Check #39090948408](https://github.com/soozooq/gogogo/actions/runs/39090948408)：**success**。完整 Gradle 构建与 Lint 已结束，没有失败步骤。
- 真实 Actions Artifact `gogogo-unified-labs-preview` ID `12122438436`，内唯一 ARM64 Debug APK 大小 `40339198` 字节、SHA-256：`5576063645e00fa24211b54b810a1c1bd31bef78f31ecae84f269ee767521311`。其嵌入测试 APK `assets/twt-fixture-v0.apk` SHA-256：`f8e7501f1157c711dd21a7367355e689fd3199453b0d399a749d90709df37e77`，包含 GuestV2Session 和 GuestV2DeclaredActivity DEX 类，两个 ZIP 包完整性检查均通过。
- **当前状态：仅 CI / 编译 / 独立 fixture JVM 单测完成，2.4.21 V2.2 真机尚未验收。** CI 不能证明真实 Android onPause/onStop/onRestart/onResume 在用户设备触发并被 guest 状态机正确处理。用户需要实测才能宣称 V2.2 PASS。

**一次复制真机流程**：安装/覆盖升级 2.4.21 → Virtual Container V0 → V2 控制页 → 点「V2 第二阶段：连续请求 / 取消 / 后台恢复」；等待自动脚本准备好 D 挂起请求 → 点「打开系统设置并返回」 → 返回 V2.2 → 点「验证挂起请求并完成退出」 → 回 V0，V0 五项一键回归 → 最后仅点一次「一键复制总报告」，自动带 V2 第一阶段和 V2.2 最近一次记录。任何步骤失败就退出并复制一次，避免重复实验。V1 的旧历史附录不算本版复测。

---
## 2026-10-18 — V2 第二阶段 Guest 会话与真实宿主生命周期（2.4.21，待真机）

- 本轮仍沿用已验证的 V0 / V1 / V2 第一阶段架构，只给自编译、内置、经过包名和独立 DEX 校验的 fixture 增加 `GuestV2Session`，不安装外部 APK，也不修改位置模拟。
- V2.2 Guest 纯 Java 会话在 fixture APK 内执行**可重复验证的四个请求**：A 正常完成，B 主动取消，C 先用不匹配 ID 请求完成（必须被拒绝）随后正确完成，D 作为未完成请求保留到**宿主真机前后台往返**之后完成；计数校验目标 `accepted=4, completed=3, cancelled=1, expectedRejected=1`，错误/取消不可计入 completed。
- Guest 状态机只接受明确的生命周期字符串；由真正注册的**天外天宿主** `VirtualContainerV2SessionActivity` 从 `onCreate/onStart/onResume/onPause/onStop/onRestart/onDestroy` 回调转发。**不注入虚构的 Android 回调**：D 只有观察到真实 `onPause→onStop→onRestart→onResume` 后才能完成；未执行实际切后台、出错或提前退出均不报告 PASS。
- 在 V2 控制页新增「V2 第二阶段：连续请求 / 取消 / 后台恢复」按钮；新测试页自动执行请求 A/B/C、建立挂起 D。用户只需打开系统设置再返回，点击“验证挂起请求并完成退出”。清理时会执行 `GuestV2Session.dispose()` 并将真实退出结果持久化，保留异常原因及 session trace。
- 新宿主 Session Activity 在 Manifest 声明为 `android:exported=false`；进入前再次由原 `ContainerV2ManifestRouteProbe` 检查受控 Guest Manifest 与路由，不使用 Guest Activity 的真实系统启动。V2 控制页校验 Activity result nonce、PID/UID、response 和 SHA；与独立的 Guest 会话细节分别保存，失败不可冒充成功。
- 保留 V0 页的**一次复制总日志**。复制内容可同时附加原 V2 第一阶段日志及 V2.2 最近一次请求/真实回调/清理附录；不会用本次 V2.2 的短摘要覆盖以前已验证的 V2 第一阶段结果。V1 较早版本附录也保留各自时间/哈希，不得冒充同版复测。
- 新增 `GuestV2SessionTest` 七项单元测试，覆盖连续请求、取消、错误请求拒绝、后台恢复成功、缺失 onStop 不可成功、重复执行拒绝、失败退出释放；CI 合约保证新 Activity 不导出、报告可一次复制、不得操作位置服务。
- **边界**：仍是天外天宿主 Activity 发出的生命周期 + 受控独立 APK 普通 Java 会话对象。没有独立 UID 权限沙箱、没有真正启动未安装 Guest Activity、没有任意第三方 APK 兼容声明，更不代表微信位置模拟成功。

**真机验收**：直接升级 2.4.21 → 进入 V0 的 V2 控制页 → 点击「V2 第二阶段」→ 等 Guest 自动请求完成 → 打开系统设置再返回 → 点击「验证挂起请求并完成退出」→ 回 V0 → V0 五项一键回归 → 最后**一次复制总报告**；不必再逐项复制或重复之前 V1 的点击测试。如果 Guest 自动阶段失败，只需返回 V0 复制本次单份报告，保留 FAIL 证据。

---
## 2026-10-18 08:48 — 2.4.20 V2 真机 Manifest / Intent 联合验收 PASS

> 用户提供一份由天外天本机「一键复制总报告」生成的完整日志。Android API 33、主程序 `com.soozooq.gogogo.test`，宿主 PID `29615`、UID `10441`；本节记录用户设备真机观测，区别于下文 2026-10-17 云端 Build Preview / Build Check 成功但“V2 待真机”的历史状态。

- 版本基准：2.4.20 `versionCode 2400088`，嵌入受控测试 APK SHA-256 `3ccec41fd8a57f18dc393c6173d9cde9069fa7931859fbd23027895810e74ca2`；这一 SHA 与此前 2.4.20 CI 实际 artifact 中的 fixture 一致。
- **V0 实际回归 5/5 PASS，FAIL/TIMEOUT=0**（2026-10-18 08:47:57）：双 ClassLoader A/B 句柄 `42208756` / `221411613` 且文件互不串线；Binder 主 PID `29615` ↔ 子进程 `30258`，相同 UID `10441`；另一子进程 `30279`，与 A 的状态标记都保留；独立 APK 的 DEX 加载/执行和双子进程代码执行均成功。
- **V2 组件解析及受控显式 Intent 往返：PASS**（2026-10-18 08:48:04）。宿主与代理 PID 均为 `29615`，UID 均为 `10441`；`guest APK SHA-256` 匹配 V0；合成 action `com.soozooq.tianwaitian.fixture.action.V2_PING`，合成 payload `TWT_V2_2026`；`请求与返回的随机标记匹配=true`、`Guest DEX 路由响应正确=true`、`Guest APK Manifest 验证=true`。
- `PackageManager.getPackageArchiveInfo` 从**未安装的受控 fixture APK** 解析到 `com.soozooq.tianwaitian.fixture.GuestV2DeclaredActivity`，确认 `activityExported=false`、`guestPermissionCount=0`，受控组件类及路由类的 ClassLoader 来自 fixture DEX；在宿主代理调用 Guest Route 后收到有效结果，最终 `V2 验收结果：PASS`。
- **V1 附录不是本次新版本的真机回归测试**：它有明确历史时间戳 `2026-10-17 22:04:11`、旧宿主 PID `23965`、旧 fixture APK SHA-256 `c6dc44d6a335ee012710f8a1e566cc4bbe1e76804bcda9f7a6953fa07075f520`。其资源、界面、点击、重建恢复和清理 PASS 为 2.4.19 的已有证据；不得把该旧附录当成 2.4.20 对新 fixture V1 的重新验收。
- **结论边界**：此 V2 PASS 确认“离线读取独立 APK Manifest 中受控 Activity 声明 + 系统启动**已注册的天外天宿主代理 Activity** + 受控 Guest DEX 函数处理合成 Intent + 返回结果核验”。**没有**由 Android ActivityManager 原生启动未安装 Guest Activity；宿主与代理同 UID/进程；不代表完整第三方应用虚拟化，也没有任何新的微信/QQ/抖音位置成功证据。

**建议后续 V2 第二阶段**：仍只用已审计的受控 fixture，试验带明确输入/结果协议的 Host Activity 代理（创建、暂停、恢复、返回、异常中断）与请求状态一致性；区分设备真实回调、宿主转发及 Guest 自有状态，不向任意第三方 APK 或定位数据开放此测试。保留 V0 5/5 和一次复制总报告为回归门槛。

---
## 2026-10-17 — 2.4.20 双工作流构建验收通过（V2 待 Android 真机）

- 版本：`2.4.20-tianwaitian-intent-v2`，`versionCode 2400088`，宿主 applicationId 原样保留 `com.soozooq.gogogo.test`。**构建时的代码 HEAD** 是 `c2601c1ecc56f5ea20e2422ce3ab84ba740b1691`。
- [Build Preview APK #39042874543](https://github.com/soozooq/gogogo/actions/runs/39042874543) **success**：静态组件/安全合约、V0 纯 Java 烟测、fixture V2 JUnit、宿主 JVM 测试、Debug APK 编译、APK Manifest/嵌套 guest Manifest/资源验收通过。
- [Build Check #39042874544](https://github.com/soozooq/gogogo/actions/runs/39042874544) **success**：完整 Gradle Build、Release 相关任务与 Lint 均通过。
- 从本次唯一最新产物 `gogogo-unified-labs-preview` artifact ID `12104234900` 提取了**真实 ARM64 APK**：文件名 `Go_2.4.20-tianwaitian-intent-v2_arm64-v8a_debug.apk`，字节数 `40336854`，宿主 APK SHA-256 `4ef5e4b20951b8a26e67e99b398ca3abd137e184eeaa126d3840f59850e13e84`。
- 主 APK 的嵌套资产 `assets/twt-fixture-v0.apk` SHA-256 为 `3ccec41fd8a57f18dc393c6173d9cde9069fa7931859fbd23027895810e74ca2`，其中确实包含 `classes.dex`、`classes2.dex`、`resources.arsc`、`AndroidManifest.xml`，`GuestV2Route`、`GuestV2DeclaredActivity` 类名存在于独立 guest DEX，主/guest ZIP CRC 检查通过。
- **真机 V2 测试尚未完成**：云端 PASS 只证明代码可以编译、合约/产物检查通过；仍不能声明宿主 V2 显式 Intent 往返或者 Archive Activity 解析在用户 Android 13 上实际成功。

**下一步手机验收**：原位升级 2.4.20 → V0 页面 → V2 组件 Intent 代理实验 → 点击“一键运行 V2 组件 / Intent 往返” → 看到 V2 报告 → 返回 V0，按一次 V0 五项回归 → 最后点**一次“复制总报告”**，V2 附录会自动进入总日志。出现 FAIL 直接复制，无需重复测试。V1 2.4.19 的通过记录保留为历史基准，不要求盲目反复测试。

---
## 2026-10-17 — V2 第一阶段：受控 APK Manifest 与 Intent 代理（2.4.20，待真机）

> 上一阶段 2.4.19 已获得 Android 13 V0 5/5、V1 自有资源/UI/恢复/清理 PASS，完整证据仍保留在下方。本次不是重新修复 V1，而是独立的 V2 新实验。

- `containerFixture` 改为在自身 Manifest **只声明一个不导出、无 intent-filter、无权限的 GuestV2DeclaredActivity**。此类确实存在于独立构建 APK 的 DEX，但**绝不调用 Android 系统直接启动它**，避免把组件声明误解为已安装的 Activity。
- 增加无网络/定位能力的纯 Java `GuestV2Route.handle(action,payload)` 及五项 JVM 单测。只接受硬编码的 V2 合成 Intent action 和 1–40 字母数字/短横/下划线字符；拒绝未知 action、URL、空值与超长值。
- 新增受控 APK 检查器 `ContainerV2ManifestRouteProbe`：使用 V0 的同一受信任 fixture loader，从内置只读 APK 读取 `PackageManager.getPackageArchiveInfo(GET_ACTIVITIES | GET_PERMISSIONS)`，要求**包名完全符合、恰好一个非导出 Activity、无请求权限、目标类及路由类均来自独立 DEX**；随后仅反射执行 `GuestV2Route.handle` 并核对返回值。
- 新增天外天宿主的非导出 `VirtualContainerV2Activity`（控制页）及 `VirtualContainerV2ProxyActivity`（真正由系统启动的**宿主代理**）。用户在控制页点一次按钮，控制页发出显式 Intent，代理在异步线程检验并执行独立 APK 路由，使用 `setResult()` 回传信息；控制页校验 request nonce、响应、进程 PID/UID、SHA-256 和 Manifest 判定，才允许显示 PASS。
- V0 原有一键五项与 V1 页面保持不变；V0 的一次复制现在**自动附上 V1 与最近一次 V2 报告**。无须多次逐项复制；若不运行 V2，就不会凭空把它标记 PASS。
- CI 严格规定 fixture 只有这一个不导出的 Activity 且没有其他组件或权限，两个宿主 V2 页面都不导出；要求从实际宿主 APK Manifest 和嵌入 fixture APK Manifest 读到预期 Activity；既有 V0/V1 资源/UI、Gradle 和 Lint 回归继续执行。
- **特别限制**：此处演示的是“从未安装的独立 APK *读组件声明* + 启动已注册的宿主代理 + 在宿主 UID 下执行已知 guest 处理函数”。不代表未安装 Guest Activity 的真实 Android 生命周期、独立应用进程/UID 或任意第三方 APK 可运行；不验证微信/抖音/QQ 的位置。

**Android 13 真机步骤（只复制一次）**：升级 2.4.20 → 进入诊断中心的 Virtual Container V0 页面 → 点击 V2 控制页 → 点「一键运行 V2 组件 / Intent 往返」→ 等返回 PASS/FAIL → 回 V0 → 点「一键诊断 V0 五项」回归检查 → 最后**一次复制 V0 总报告**（自动带上最近 V1/V2 附录）。V1 的 2.4.19 成功记录可以保留为历史基准，不强迫重复打开 V1；如需要验证新 fixture 的 V1 UI，再补测。

---
## 2026-10-17 22:04 — 2.4.19 真机 V1 资源和界面联合验收通过

> 用户提交 Android 13（API 33）真机截图与 **一份**包含 V0 五项及 V1 附录的完整诊断报告。此节是 **实际设备运行证据**，优先于下方历史记录中的“2.4.19 待真机验证”，不代表所有 Android 机型或任意第三方 APK 可用。

- 安装版本：`2.4.19-tianwaitian-guest-theme` / `versionCode 2400087` / 宿主包名 `com.soozooq.gogogo.test`；代码构建基准 `f93f4ff9631d2dbb6c40b842d00a8905d539de35`，Preview/Build Check 均为 success（见下方 CI 记录）。
- 真机：宿主 PID `23965`，UID `10441`。V0 一键运行 **5/5 PASS、FAIL/TIMEOUT=0**，主机与 Binder 子进程 `26055`，第二个 A/B 子进程 `26087`；不同 PID，同一个 UID。ClassLoader A/B `205416889`、`148869630`，文件与静态状态互不串线。
- 本次 **受控独立测试 APK SHA-256**：`c6dc44d6a335ee012710f8a1e566cc4bbe1e76804bcda9f7a6953fa07075f520`。V0 的 DEX/跨进程执行与 V1 资源报告都指向同一哈希，与 2.4.19 构建产物验证一致。
- V1 真实结果：`生命周期桥加载=true`；`独立 APK 创建 View=true`；`独立 APK 自有资源验证=true`；资源包 `com.soozooq.tianwaitian.fixture`；marker `TWT_GUEST_ONLY_2_4_17`；XML 布局 `guest_panel`；title `独立 APK 的资源布局已经加载`；Drawable 是 `GradientDrawable`；`buttonClass=android.widget.Button`。截图中清楚可见深色面板、粉紫色圆角图案、字符串标题、计数与可点击按钮。
- 重建前 `onSaveInstanceState clicks=3`；`HOST instance recreated; savedClicks=3`；新实例 `GUEST VIEW created by fixture DEX, restoredClicks=3, verified=true`；随后点击一次，最终 `guestViewClicks=4`；`Activity 重建次数=1`；`状态恢复一致=true`；`后台恢复观察=true`。
- 最终 lifecycle `guestState=DESTROYED`、快照显示最新实例 `events=6`、`resumes=1`（重建后状态机重新开始，因此 **不是** 整个会话累计的事件数）。退出日志 `GUEST VIEW disposed=true`、`disposed=true`、`guestResourcesVerified=true`、`stage=complete`，末行 `V1 UI 交互/重建/清理联合验收：PASS`；无相关 FAIL。
- **前两版根因与闭环**：2.4.17 resource+cleanup 失败；2.4.18 已修复 Provider 清理并暴露反射根因，证实是 AppCompat 主题替换 guest XML Button 导致 InflateException；2.4.19 采用全限定 `android.widget.Button` 与 application-context inflater，实测资源成功解析、View 正常创建和交互。现可把这一受控 fixture 的 V1 第三阶段标记为 **Android 13 真机 PASS**。

**边界**：测试 APK 作为内嵌资产且由宿主在同进程、同 UID 上动态装载；Guest 的 XML、strings、drawable、View 构造与点击回调真实来自受控独立 APK，但仍由天外天已注册的 Activity 承载，并使用宿主 Context/UID/权限。未测试任意外部 APK 的 Android Manifest 组件发现、真实未安装 Guest Activity 的系统启动、虚拟 PackageManager、权限隔离或微信/QQ/抖音位置兼容。下一技术阶段可单独研究受控 APK 的组件元数据解析、显式生命周期/Intent 合约与可审计的代理，不要宣称已具备完整第三方应用托管能力。

---
## 2.4.19 双工作流通过、等待真机主题修复验收（2026-10-17）

- 编译基准：`main` commit `f93f4ff9631d2dbb6c40b842d00a8905d539de35`；版本 `2.4.19-tianwaitian-guest-theme` / `versionCode 2400087` / `com.soozooq.gogogo.test`。
- [Build Preview APK #39037389104](https://github.com/soozooq/gogogo/actions/runs/39037389104) **success**，静态契约、fixture JVM 单测、宿主 Java 编译、APK Manifest/嵌套资源打包验证成功。
- [Build Check #39037389088](https://github.com/soozooq/gogogo/actions/runs/39037389088) **success**，完整 Gradle / Lint 通过；未关闭 Lint 或回退已有 V0 诊断。
- 唯一 Actions APK 从 Artifact `gogogo-unified-labs-preview`（ID `12102717779`）提取，大小 `40336298` 字节，宿主 SHA-256 `4eb1746c5f9a8ccc361918caa4ea0c0a6e6ae7d55814986246a7726e3674ed6f`；内置独立测试 APK SHA-256 `c6dc44d6a335ee012710f8a1e566cc4bbe1e76804bcda9f7a6953fa07075f520`。宿主与独立 APK 的 ZIP CRC 均成功，内置资源表、guest XML 布局、圆角 Drawable 和 `GuestUiV1` / `GuestLifecycleV1` 均实际存在。
- 本版只修 Guest XML 与 inflater 对 AppCompat Activity Factory2 的意外依赖：Guest XML 使用 `<android.widget.Button>` / `<android.widget.TextView>` / `<android.widget.ImageView>`；Inflater 从 application 而非 AppCompat Activity 获取，再 clone 到 guest 专用 Context；资源验证日志多报告 `buttonClass`，明确原生类来源。
- **手机运行状态：待验收，不能据 CI success 断言 2.4.19 V1 PASS。** 用户只需进入 V1，若仍失败直接回 V0 一次复制总报告，无需再做无意义的重建或反复操作；显示成功再验证点击、后台、重建、退出，V0 一键 5/5 作为回归基准。

---
## 2026-10-17 2.4.18 真机故障与 2.4.19 修复候选

**新实际反馈**：Android 13（API 33），设备 2026-10-17 21:34 的截图与一键总报告。`com.soozooq.gogogo.test`、宿主 PID `12402`、UID `10441`、fixture APK SHA-256 `15f0191bd6058d1a6cdb3a7a83dad113167a48cd65fd18fb99b980e45bfd51cf`。V0 **5/5 PASS**。V1 **FAIL**：`生命周期桥加载=true`，`独立 APK 创建 View=false`、`独立 APK 自有资源验证=false`、`guest 点击数=0`。

日志已明确解开反射异常：`GuestUiV1.createView()` 到 `stage=inflate guest_panel.xml` 时，XML 第 31 行的 `Button` 被 AppCompat 加载器创建，而 Guest Context 使用系统 `Theme_Material_Light_NoActionBar`。底层异常：`IllegalArgumentException: The style on this component requires your app theme to be Theme.AppCompat (or a descendant)`。此报错由这次手机日志直接支持，不再把它归因于资源哈希、包名或资源表冲突。

- 资源 Provider 清理旧错误在 2.4.18 日志中**不再出现**；日志已经正确写出 `GUEST UI object disposed=true (viewCreated=false)`，避免 `View` 未创建却宣称清理了 View 的误报。
- 因 `LayoutInflater.from(hostContext).cloneInContext(guestViewContext)` 继承宿主 AppCompat Activity 的 `Factory2`，短标签 `<Button>` 会变成 `AppCompatButton`；依赖 AppCompat 主题，而 Guest 实验刻意只采用 framework 主题。AndroidX 官方 `AppCompatViewInflater` 的 `switch(name)` 对 `Button`、`TextView`、`ImageView` 等短标签执行自动替换。

**2.4.19 修复策略（尚未真机验证）：**

1. 独立 APK 的 XML 布局中全部使用完整类名 `<android.widget.Button>`、`<android.widget.TextView>`、`<android.widget.ImageView>`，不触发 AppCompat 的短名称替换；它们仍然是 XML 打包资源，而非改为宿主硬编码界面。
2. 复制 application Context 的系统 LayoutInflater 而不是 Activity 的 inflater，向 Guest 资源 Context `cloneInContext(this)`，避免宿主 Activity `Factory2` 的 AppCompat 主题要求；同时在运行时拒绝客体控件类被替换成 `androidx.appcompat.*`。
3. 保留 2.4.18 的资源加载、清理、错误阶段与根因链日志、V0 五项和一次复制总报告；新增 CI 检查禁止 Guest XML 回退成短标签、禁止恢复 Activity inflater。

**下一步验收**：两条 GitHub Actions 真正构建通过后再安装 2.4.19。在手机上先只打开 V1，看到独立 APK 的标题、粉紫色圆角图片和按钮才进行点击、后台、重建与退出，再回 V0 一次复制总日志。若仍失败，保持当前页面、退出并只复制 V1 报告；不要求连续试多轮。

---
## 2.4.18 云端构建复查通过（2026-10-17）

- 用户 2.4.17 真机失败的最小复现与诊断记录详见下一节；2.4.18 是针对性**待真机确认修复候选**，不得宣称资源 XML 显示已经被手机证实。
- 已在 `main` 提交修复源码，版本号 `2.4.18-tianwaitian-guest-res-fix`，`versionCode 2400086`，构建 head SHA `a8271634c9d56b498d87acdfb9863fbcd38fc498`。公开完整日志：[Build Preview APK #39035358285](https://github.com/soozooq/gogogo/actions/runs/39035358285) **success**；[Build Check #39035358262](https://github.com/soozooq/gogogo/actions/runs/39035358262) **success**。
- 真正提取了新 SHA 的 Actions Artifact `gogogo-unified-labs-preview`（ID `12101872014`），其中唯一 ARM64 debug APK 完整，大小 `40336118` 字节，SHA-256 `78126a9705a77400cca91b7ff4281606ade5a110b27542ceeee18f5ab87e3ea3`。包内资产 `assets/twt-fixture-v0.apk` 的 SHA-256 `15f0191bd6058d1a6cdb3a7a83dad113167a48cd65fd18fb99b980e45bfd51cf`，`resources.arsc`、`res/layout/guest_panel.xml`、`res/drawable/guest_mark.xml` 均存在，两个 ZIP CRC 检查通过。
- 修复：Loader `clearProviders()` 再 close Provider；实验资源以 framework-only Assets 为基底而不混用宿主与 guest 同为 `0x7f` 的 app 资源；布局 Inflate 上下文使用 guest-scoped inflater；V1 日志展开反射 root-cause 并显示资源阶段；仅真实 view 存在时写 `GUEST VIEW disposed=true`。CI 静态契约阻止上述修复退化。
- **下一次真机只做一个 V1 打开即可先判断修复是否成功**：若没有出现独立 APK 资源布局，则退出 V1，返回 V0 复制一次总报告（包括 V1 附录），查具体 stage、cause 链，毋须重复跑多次；若能显示才测点击、返回、重建、退出，再补跑 V0 五项一次作为回归验收。此安排降低用户重复操作。

---
## 2026-10-17 2.4.17 真机故障与 2.4.18 修复候选（尚未通过真机）

**Android 13 / API 33 实测**：2026-10-17 21:02 的用户一键总报告。V0 五项 5/5 PASS，应用包 `com.soozooq.gogogo.test`、主 PID `3364`、UID `10441`，两个子进程 PID `6013` 和 `6049`；受控 fixture APK SHA-256 `46a4196adb330499c32fff21837d7da6e2883c2df9508f307a8b1f1f548581f4`（与本版构建校验一致）。**V1 资源布局实验 FAIL**，界面并未出现；严禁以 CI success 宣称 V1 通过。

- V1 真机日志 `生命周期桥加载=true`、`独立 APK 创建 View=false`、`独立 APK 自有资源验证=false`、`guest 点击数=0`、`状态恢复一致=false`；实际错误 `FAIL：guest UI 构建/嵌入 · InvocationTargetException`，且在重建后再次出现。由此只能判断反射中的 guest 创建调用失败，**不能确认当时内部根因**。
- 另有确切清理故障：`FAIL：guest ResourcesLoader 清理 · IllegalStateException`。官方 API 指出 ResourcesProvider 若仍被 ResourcesLoader 使用则 `close()` 会抛出 IllegalStateException；旧版先 `resources.removeLoaders(loader)` 却未调用 `loader.clearProviders()`，释放顺序不完整。
- 旧版在 `viewCreated=false` 时仍显示 `GUEST VIEW disposed=true`，这仅证明 `GuestUiV1.dispose()` 被调用，并**不能证明实际 View 被创建和释放**；修复版区分 guest 对象清理与真实已创建 View 的释放。

**2.4.18 修复候选**（源码自 `main` 提交）:

1. 修正资源关闭顺序为 `Resources.removeLoaders(loader)` → `loader.clearProviders()` → `provider.close()`，保持 API >=30 的 guard，仍在主线程操作。
2. 独立资源对象使用 `Resources.getSystem().getAssets()` 为基础，再附加 guest APK provider，避免将两个通常同为 `0x7f` 的宿主与 guest 资源表叠在一起；仍只用 Android 公共 API，**资源加载修复尚未经真机证明**。
3. `GuestUiV1.createView` 在 package lookup、marker、string、drawable、layout inflate、控件验证等每一步记录 stage。宿主展开 `InvocationTargetException` 的 `getTargetException()/getCause()`，记录受控的异常类、最深层原因及相关代码位置，一次复制总日志即可定位。
4. 增加 CI 静态约束：必须保留资源独立表、Loader/Provider 正确解绑与异常根因报告；V0 五项一键诊断逻辑未修改。

**真机复测原则**：升级 2.4.18 后可先直接打开 V1，若出现失败，只需回 V0 点击一次复制总报告，并贴出含根因链的 V1 附录；不需要反复点“重建”和“系统设置”来试错。若 V1 页面正常显示，再运行已有点击/后台/重建/退出流程，并用 V0 五项作为回归检查。**没有数据时不要宣称修复已成功。**

---
## 2.4.17 第三阶段云端构建验收（2026-10-17）

- 本轮代码版本 `2.4.17-tianwaitian-guest-res` / versionCode `2400085` / 宿主应用 ID `com.soozooq.gogogo.test`。
- 修复后发布的编译基准 SHA：`5c286ed8e6888738f38937ed65475075cb3d985c`。[Build Preview APK #39031955908](https://github.com/soozooq/gogogo/actions/runs/39031955908) **success**；[Build Check #39031955892](https://github.com/soozooq/gogogo/actions/runs/39031955892) **success**。
- 首次提交的 Preview 已能正确构建和打包独立资源，但完整 Build Check [#39031633325](https://github.com/soozooq/gogogo/actions/runs/39031633325) 报出 `ContainerV1GuestResources.close()` 的两条 `NewApi` Lint 错误：`Resources.removeLoaders` 与 `ResourcesProvider.close` 需 API 30；保持 `minSdkVersion 27` 不变，补入 Android R 及以上版本判断后复跑双工作流通过，未关闭 Lint。
- 将**最新**成功 Preview 的 Artifact 取回验证：宿主 APK ZIP 完整；内置 `assets/twt-fixture-v0.apk` ZIP 完整，且包含 `resources.arsc`、`res/layout/guest_panel.xml`、`res/drawable/guest_mark.xml`；产物宿主 SHA-256：`b06f6ab9cb4e25a77a4bced076adc0bc59bdc758d5b8d231a1dd49609018ed2b`；嵌入 fixture APK SHA-256：`46a4196adb330499c32fff21837d7da6e2883c2df9508f307a8b1f1f548581f4`。
- **仍未有 2.4.17 Android 13 真机第三阶段记录**：是否可以从独立 APK 按预期 inflate 布局并恢复点击计数，必须用户实际运行，不能把 CI success 当作设备 PASS。

---
## 2026-10-17 — V1 第三阶段：独立 APK 自有资源实验（2.4.17，待真机）

- 目标：除了原来能从独立 APK 加载 Java 类、接收 Activity 生命周期并创建 View，现在还要验证独立 APK **实际打包的 `res/layout`、`res/drawable`、`res/values`** 能在天外天宿主页面上成功解析。此阶段仍只有一个宿主安装包，不安装第二个应用。
- Android API 30+ 使用官方 `ResourcesProvider.loadFromApk(ParcelFileDescriptor)`、`ResourcesLoader.addProvider()`、`Resources.addLoaders()`，对宿主 Context 的配置建立**实验专用的独立 Resources 对象**，不修改全局宿主 `getResources()`。为保证先前仅 Android 13 的实际验收，低于 API 30 时明确报告不支持而不试图调用隐藏 API。
- 资源输入只能是 `ContainerV0TrustedGuest` 已核验包名、`classes.dex`、APK SHA-256 的私有只读测试文件。`ContainerV1GuestResources` 用文件描述符加载该 APK 的资源并在退出时移除 loader；原来的可复用 classloader 与 V0 五项实验保持不变。
- `containerFixture/src/main/res/` 增加 `guest_panel.xml`、`guest_mark.xml`、字符串和配色。单独编译的 `GuestUiV1` 类反射执行 `Resources.getString(R.string.guest_resource_marker)`、`getDrawable(R.drawable.guest_mark)` 和 `LayoutInflater.inflate(R.layout.guest_panel)`；校验资源包名必须为 `com.soozooq.tianwaitian.fixture`，并读取仅属于本次测试 APK 的固定 marker `TWT_GUEST_ONLY_2_4_17`。依赖 ResourcesLoader 的资源优先级处理同 0x7f 资源 ID 冲突，但**真机依然要验证能否成功**。
- V1 的深色 guest 界面改成从它自己的 XML 布局创建，包含自己的标题、图案、点击按钮和计数器；Button 的监听器依然属于 guest APK。新增报告字段 `独立 APK 自有资源验证` 与 `资源来源`，失败不可报成功；保留上版点击、后台、重建、清理和单次总日志。
- 构建门槛：`verify_container_fixture_contract.py` 检查独立资源文件与 API 30+ loader 的关键契约；Build Preview 在发放 APK 前用 `aapt dump resources` 检查 fixture APK 的 layout、drawable、string marker 是否真实打包；Build Check 同时跑完整 Gradle/Java 单测。
- **边界**：这验证的是受控 APK 中的资源被加载到 host 进程的独立 Resources 对象，再由 proxy Activity 显示 guest View，既不是完整 Android 组件虚拟化，也不是独立 UID、权限系统、微信定位的成功依据。

**下一次真机**：先 V0 一键 5 项 → 进入 V1，确认深色区域中能看到“独立 APK 的资源布局已经加载”的文字和粉紫色圆角图案 → 点击 guest 按钮几次 → 系统设置返回 → 点击界面重建 → 再点击 guest → 完成退出 → 回 V0 **只复制一次总报告**。检查 `独立 APK 自有资源验证=true`、`resourcePackage=...fixture`、`marker=TWT_GUEST_ONLY_2_4_17`、恢复数与清理结果。

---
## 2026-10-17 2.4.16 真机 V0/V1 UI 联合验收（20:01）

> 来自用户复制的一份真实 Android 13 运行总报告；**2.4.16 的 V0 5/5 与受控 V1 UI 点击/恢复/清理首次真机通过**。本记录取代下文原先「2.4.16 尚无真机数据」的阶段性描述，但保留它作为旧开发历史。

- **版本和构建基准：** `2.4.16-tianwaitian-guest-ui` / `versionCode 2400084`；构建对应源提交 `ac0cf5d4b6c17110656b3551fd660ae851674246`。Build Preview APK [39026571048](https://github.com/soozooq/gogogo/actions/runs/39026571048) 和 Build Check [39026571039](https://github.com/soozooq/gogogo/actions/runs/39026571039) 已成功。
- **真机快照：** 2026-10-17 20:01:33（V0）、20:01:37（V1），Android API 33；宿主包 `com.soozooq.gogogo.test`，PID `22670`，UID `10441`。PID 是该次临时标识。
- **V0 一键诊断：5/5 PASS，0 FAIL/TIMEOUT**。ClassLoader A `117660859`、B `106014168`，A→B→A 文件数据保留；Binder 远端 PID `6292`、UID `10441`；双子进程 A PID `6292`、B PID `6377`，各与宿主 PID 不同，仍与宿主同 UID，双方标记保留 `true`；受控 APK 的单进程 DEX 装载/执行和跨 A/B 双进程运行均 PASS。
- **V1 APK 自有 UI：** `生命周期桥加载=true`、`独立 APK 创建 View=true`、`guest 点击数=4`、`Activity 重建次数=1`、`状态恢复一致=true`、`后台恢复观察=true`，末尾 `V1 UI 交互/重建/清理联合验收：PASS`。
- **重建的关键证据：** 重建前 `HOST onSaveInstanceState clicks=3`；重建后的新宿主实例写出 `savedClicks=3` 与 `GUEST VIEW created by fixture DEX, restoredClicks=3, verified=true`；随后再次点击，最终 `guestViewClicks=4`。这表明点击计数经宿主 `Bundle` 持久化并传回重新创建的 guest 对象，不代表原 guest View/Java 对象在重建后原封不动存活。
- **退出清理：** 最后一个 guest 生命周期实例到达 `guestState=DESTROYED`，`events=16`、`resumes=3`，记录 `GUEST VIEW disposed=true`，最终 UI 快照 `guestViewClicks=4；disposed=true；guestViewClass=com.soozooq.tianwaitian.fixture.GuestUiV1`。日志中的历史 HOST/GUEST 生命周期串跨越重建前、后两个实例；不要把新实例的 16 次事件误当成整场会话事件总数。
- **受控 fixture APK SHA-256：** `4ea4b07d66bceb587932fb1a90c23d0d6de85e1e7de6aeea5012f9242a547606`，V0 两处和 V1 一处完全一致。不同于 2.4.15 的哈希属正常版本差异，不能拿不同版本互相比对。
- **一次复制的交互目标验证通过：** V0 五项 + V1 附录全在一份总日志中。旧五项仍保留作为后续构建的回归基准。

**术语说明：** Host/宿主=天外天；Guest/客体=另外编译、由天外天加载的受控测试 APK 代码。在 V1 中深色区域的按钮由 guest 代码创建并处理点击；但 Android `Activity`、`Context`、进程和 UID 仍属于天外天。**此结果不是直接启动未安装第三方 APK 的真实 Activity，更不是独立 UID 权限沙箱、完整应用虚拟化或微信定位兼容通过。**

**下一阶段候选：** 保留一键总诊断，在不引入任意 APK 或虚拟权限承诺的前提下研究受控 guest 资源访问（布局/图片）、配置更改及进程死亡恢复的可观察测试；每一项需要新一轮构建+真机证据。

---
## 2.4.16 编译验收（2026-10-17）

- 源码版本：`2.4.16-tianwaitian-guest-ui`，`versionCode 2400084`，代码对应 commit `ac0cf5d4b6c17110656b3551fd660ae851674246`。
- [Build Preview APK #39026571048](https://github.com/soozooq/gogogo/actions/runs/39026571048)：**success**；fixture JVM tests、host JVM tests、assembleDebug 和内嵌 APK/Manifest 内容检查通过，产物 `gogogo-unified-labs-preview`。
- [Build Check #39026571039](https://github.com/soozooq/gogogo/actions/runs/39026571039)：**success**；完整 Gradle 构建与 Lint 检查通过。
- 下载后的宿主 APK 解包测试通过（约 40.3MB），嵌入 `assets/twt-fixture-v0.apk`；独立 fixture APK 解包完整，在其 `classes2.dex` 检查到 `GuestEntry`、`GuestLifecycleV1` 和 `GuestUiV1`。
- 2026-10-17 第一次预览尝试 [#39026407064](https://github.com/soozooq/gogogo/actions/runs/39026407064) 因 Java 嵌套回调中异常参数 `failure` 重名编译失败；此问题已在 `ac0cf5d4` 修复，后续双工作流成功。
- **状态**：仅 Android 云端构建通过，尚无 2.4.16 真机 UI 点击、重建恢复与清理的观测数据；不得声称新 UI 交互已在手机验收成功。

---
## 2026-10-17 — V1 第二阶段：独立 APK 的原生 View 与点击/重建实验（2.4.16，待真机）

- 保持宿主唯一应用包名 `com.soozooq.gogogo.test`、原用户数据、签名与定位功能。此阶段只修改受控测试 APK 与 V1 诊断页面，不增加定位权限或引入第三方 APK 安装。
- 新增独立构建的 `containerFixture/.../GuestUiV1.java`：在 **fixture 自己的 DEX** 中创建 Android `LinearLayout`、`TextView`、`Button`，设置原生 `OnClickListener`，按钮每次被点击就更新 guest 的计数器与 guest 自己的日志；对应五个 JVM 单元测试验证持久化计数参数约束。
- 天外天已注册的 `VirtualContainerV1ProxyActivity` 使用 `ContainerV0TrustedGuest` 从**嵌套的独立 APK** 装载 `GuestUiV1`，主线程反射执行 `createView()`，把返回的真实 Android `View` 放入宿主 `FrameLayout`。必须检查 `GuestUiV1` 的 `ClassLoader` 实际属于单独 guest DEX。
- 可点击“测试后台恢复：打开系统设置，再返回”，记录 Android 前后台真实生命周期；可点击“测试界面重建：保留点击数重新加载”，由系统真实执行 `Activity.recreate()`，在 `onSaveInstanceState` 保存点击数及会话痕迹，新宿主实例重新构造 guest View、校验恢复数；退出调用 guest `dispose()` 并写入一次复制报告。
- V1 报告追加 `独立 APK 创建 View`、`guest 点击数`、`Activity 重建次数`、`状态恢复一致`、`后台恢复观察`、`GUEST VIEW disposed` 和 `V1 UI 交互/重建/清理联合验收` 字段。只有用户实际执行点击、后台、重建、退出，且观察条件满足时才可能 PASS。
- V0 原来的“一键诊断 5/5、最后只复制一次日志”不变。V0 复制总报告时仍会自动附上最近 V1 的时间戳日志。
- **不应夸大**：这里确实运行了独立测试 APK 内定义的原生 Android View **创建代码和点击处理器**；但 UI 使用的是宿主 Context、宿主进程/UID，仍没有 Android PackageManager/ActivityManager 组件虚拟化，也没有直接运行未安装的 APK Activity，未验证微信定位。
- GitHub Build Preview APK 与 Build Check、Android 13 真机反馈均必须按新提交重新验收，不沿用 2.4.15 的 PASS。

**真机执行步骤：** 更新 2.4.16 → 进入 V0，运行一次五项自动诊断 → 打开 V1 → 在深色 guest 界面点按钮至少 2 次 → 打开系统设置并返回 → 点击“测试界面重建”，确认 guest 点击数还在 → 再点 guest 按钮 1 次 → 完成退出 → 回 V0 **只复制一次总报告**。

---
## 2026-10-17 2.4.15 真机一键联合验收（V0 + V1）

> 用户提供完整「一次复制」报告，测试时间 17:24（设备本地时间）；以下为实测观察，不是构建期模拟或对未安装第三方应用的证明。此前 2.4.14 验收记录保持不变。

- 版本：`2.4.15-tianwaitian-oneclick-v1` / `versionCode 2400083`；基准主分支提交 `e4da2552cfb61a7b4b2bfa1dc998ab1fe108a5b1`。Build Preview APK [39015164634](https://github.com/soozooq/gogogo/actions/runs/39015164634) 与 Build Check [39015164642](https://github.com/soozooq/gogogo/actions/runs/39015164642) 均完成且成功。
- 真机：Android API 33，应用包名 `com.soozooq.gogogo.test`；宿主 PID `28239`、UID `10441`。PID 是此次运行的瞬时标识，不应作为长期固定判据。
- **V0 自动总诊断 5/5 PASS、FAIL/TIMEOUT=0，证明五项串行调度完成且可一次复制汇总：**
  - 1/5 两份同名类 Class 不同：A loader `184921022`、B loader `246559775`，A→B→A 后 B 文件状态保留。
  - 2/5 Binder：主 PID `28239` ↔ 远端 PID `16417`，UID 都是 `10441`。
  - 3/5 双进程：A PID `16417`、B PID `16445`、宿主 PID `28239`；三者 PID 各不同但 UID 相同；两标记均保留 `true`。
  - 4/5 单宿主的受控 APK DEX 装载与方法执行：两份 guest Class 不同、静态数据互不串线。
  - 5/5 独立 APK 在 A/B 两个 Android 子进程分别运行，标记保留 `true`，报告远端 APK SHA-256。
- **V1 首次受控生命周期桥真机验收：21/21 次 guest 事件转发成功，最终 `guestState=DESTROYED`，累计 `events=21`、`resumes=4`，未观察到 FAIL、错误状态转换或遗漏事件。**
  - 宿主前期先记录 `HOST onCreate/onStart/onResume`，待异步 APK DEX 装载完成才集中回放成 `GUEST #1/#2/#3`，次序与设计一致，但不意味着 guest 在系统回调的同一时刻同步执行。
  - 完整 guest 序列为 `onCreate→onStart→onResume`，接着 **3 轮** `onPause→onStop→onRestart→onStart→onResume`，最后 `onPause→onStop→onDestroy`；状态机最终 DESTROYED。
  - V1 代理执行与宿主使用同一 PID `28239` 和 UID `10441`，只是把真实宿主 Activity 的系统事件反射转发到单独 fixture APK 的**普通 Java 对象**。
- **本次 V0 和 V1 共用的受控 APK SHA-256：** `856b67659b02f032e9c50298661300b4473cd21f1ffac22aa46972fa9b40cf3e`。该值不同于 2.4.14 时的 `97366bc4...` 是允许的，因为新 APK 新增了 V1 状态机；关键是本次 V0 与 V1 哈希相同。
- 用户仅复制一次 V0 总日志，即包含上述五项 V0 和 V1 的最近一次附录，**一键总诊断/一次复制需求在本次手机实测中成立**。

**边界与下一步：** 本次并未验证 guest Android Activity 本身的创建、真实资源（layout、drawable）解析、guest View 的创建/触摸输入、配置更改时状态保存、独立 UID 或 Android PackageManager/ActivityManager 代理，也没有微信/抖音/QQ 定位测试。V1 下一里程碑建议实现**受控 guest APK 自己定义的可显示/可点击 View 契约**，由注册的宿主代理展示；对点击、前后台、旋转/重建、退出清理分别记录 PASS/FAIL。必须保留 V0 5/5 作为每版回归门槛，不能把代理 View 误称为系统原生启动未安装 APK 的 Activity。

---
## V0 一键诊断 + V1 生命周期桥（2026-10-17 开发中）

**核心交互原则：一个按钮完成五项、最后只复制一份日志。**

- V0 页面新增「一键诊断 V0 五项（自动生成总报告）」。调度器 `VirtualContainerV0OneClickDiagnostic` 严格串行运行五个既有实验：① ClassLoader/文件；② Binder；③ A/B 双进程；④独立 APK DEX；⑤独立 APK 双子进程。每项独立超时（15/9/14/22/25 秒）、记录 PASS/FAIL/TIMEOUT、清理 IPC 绑定；失败仍会继续执行其余实验。
- 自动诊断期间禁止复制可能不完整的报告；结束后保存一份总报告在本 App 的私有首选项，重新进入诊断页仍可复制。主动停止时未运行项标记 SKIP，不会误报 5/5 PASS。
- 原五个按钮保留为可选单项复测；上方原始 2.4.14 Android 13 的 V0 五项 PASS 记录继续作为基准。
- 独立 fixture APK 新增纯 Java `GuestLifecycleV1` 生命周期状态机（六项 JUnit 测试）。
- 新增非导出的宿主 `VirtualContainerV1ProxyActivity`。真实 Android 生命周期回调发生在该宿主代理 Activity 上，再顺序转发给从受控 APK 装载的 `GuestLifecycleV1.onEvent()`。用户可打开系统设置并返回，核对 onPause/onStop/onRestart/onStart/onResume，随后退出验证 onDestroy；界面显示 HOST/GUEST 时间线与 APK SHA-256。
- V0 的一次复制操作会附加最近一次 V1 保存的生命周期报告（附录包含自己的开始时间），不必分别复制五次再复制 V1。
- **重要限制**：该 V1 实验只是宿主代理与受控 APK 对象的生命周期桥；不是系统 ActivityManager 直接启动未安装 APK 中的真实 Activity，也没有独立 UID、虚拟 PackageManager、组件代理或微信兼容结果。
- CI 中新增 V0 批处理和 V1 组件的静态约束、fixture JUnit 测试、构建 APK Manifest 代理页面断言。新版本必须通过 Build Check + Preview，接着由 Android 13 真机实际重复一键 V0 五项并验收 V1 流程，才能宣称通过。

**下一次真机验收**：先执行 V0「一键诊断」等五项结束，再进入「V1：打开受控 APK 生命周期代理页」，点击“打开系统设置”并返回，最后点击“完成并退出”。回到 V0 页面只点一次「一键复制总报告」，直接将整份日志发来。

---
## 2026-10-17 Android 13 ARM64 真机验收记录（V0 已通过）

> 此节是之后收到的**真实手机运行反馈**，优先级高于文档下方开发阶段的“尚未真机验证”历史陈述。结果由用户从天外天 V0 页面复制；属于该次测试的观察，不自动代表所有机型、所有 Android 版本或完整第三方 App 容器能力。

**验收配置与产物**

- 天外天主程序：`2.4.14-tianwaitian-guest-ipc`，`versionCode 2400082`，应用包名保持 `com.soozooq.gogogo.test`。
- 构建基准：`main` 提交 `049bf8b128a522c443dde3598991bdfa41dd8160`；[Build Preview APK #39008433771](https://github.com/soozooq/gogogo/actions/runs/39008433771) 与 [Build Check #39008433927](https://github.com/soozooq/gogogo/actions/runs/39008433927) 均已完成且成功。
- 真机环境：Android API 33（Android 13），用户实际使用的 ARM64 手机。测试时宿主 PID=`12440`，UID=`10441`；PID 是当次运行快照，后续会变化。
- **验收结果：5/5 PASS**（所有五个按钮均为用户真实运行并复制的日志）：

| V0 按钮 | 实测证据 | 结论 |
| --- | --- | --- |
| A → B → A 同名类双 ClassLoader / 私有目录 | A loader=`172384739`、B loader=`173869024`；两个 Class 不同；A→B→A 后 B 文件数据不变 | PASS |
| Messenger/Binder 跨进程 | 宿主 PID=`12440`，远端 PID=`15723`；两者 UID=`10441` | PASS |
| 双子进程 A→B→A→B 状态隔离 | A PID=`15723`、B PID=`15773`，与宿主均不同；同 UID=`10441`；两份标记保留均为 true | PASS |
| 从独立测试 APK 装载 DEX 并反射执行 | APK package=`com.soozooq.tianwaitian.fixture`；两份 Class 不同，A→B→A 静态标记互不串线 | PASS |
| 独立 APK 在两个 Android 子进程执行 | A PID=`15723`、B PID=`15773`；两个标记保留 true；远端 APK SHA-256 与上项一致 | PASS |

**已验证的受控测试 APK SHA-256：** `97366bc45fe92c6d346141fc0d0743b7baab7e862c8283466cb4ece46a805ef7`

**结论边界**

1. 同名类双加载器实例、两个不同 Android 进程、跨进程通信、受控独立 APK 的 DEX 方法执行已经在本次 Android 13 手机上得到肯定结果。
2. A/B 的文件目录属于**同一宿主应用的数据空间**；A/B 进程也具有**同一 Linux UID=`10441`**，不构成 Android 独立应用沙箱。观察到标记不串线不等于权限与数据真正隔离。
3. 该测试 APK 当前没有在 Manifest 注册 `Activity` / `Service` 等组件，也没有运行其 Android `Application` / `Activity` 生命周期。它是受控代码载荷，并非第三方 APK 的安装、启动或完整应用托管。
4. **没有任何微信/抖音/QQ 的新定位实测结果**；不得用 V0 的 5/5 PASS 声称这些应用已能跟随模拟位置。
5. 下一里程碑应聚焦单独构建的、可信测试 APK 的 **UI 与受控生命周期桥接**：先在宿主注册的代理 Activity 中接收可审计的 `onCreate/onStart/onResume/onPause/onStop/onDestroy` 对照，并检验屏幕显示、暂停恢复与状态清理；**不能宣称宿主代理等同于 Android 系统直接启动未安装 APK 的真实 Activity**。保留 V0 作为回归基准，代码仍只维护 `main`。

---

2026-10-17 解冻后开发。主线统一使用 `soozooq/gogogo` 的 `main`，不再长期维护独立容器 APK 或 V0 专属构建工作流。历史 PR #57 仅作开发记录。

## 已实现（源码能力，不代表真机通过）

入口：诊断中心 → 高级研究工具 → 系统研究 → Virtual Container V0。

1. **A → B → A 类加载与文件测试**：只从天外天自己安装的 APK 中提取 `GuestPayload` 的两份类定义，分别通过 `DexClassLoader` 实例执行，按 A→B→A 检查私有文件不串线；报告 Android API、PID、UID、PASS/FAIL。对动态载荷增加 R8 保留规则。
2. **跨进程 Messenger/Binder 测试**：用户手动启动本 App 的非导出 `:virtualv0` Service，一次 Ping/Pong 往返。校验主进程与远端 PID 不同、UID 相同，7 秒无应答则 FAIL。
3. **双进程 A/B 标记状态验证**：按用户点击同时绑定 `:virtualv0` 与 `:virtualv0b` 两个非导出 Service；分别写入随机标记，按 A→B→A→B 回读，验证两个 PID 与宿主各不相同、UID 保持相同、标记互不覆盖。进程异常断开或 10 秒超时明确 FAIL。
4. **独立 APK 的 DexClassLoader 加载验证**：`containerFixture` 是单独的 Gradle Android application 模块，仅有无权限、无 Activity/Service 的 `GuestEntry` 测试类；构建时产出真正独立的 APK，再作为 `assets/twt-fixture-v0.apk` 嵌入**同一个天外天安装包**。手动点击第四个按钮：从可信资产复制 APK 至私有代码缓存、复制时标记只读、验证 APK package 和 `classes.dex`，再从独立 APK 分别加载同名类执行 A→B→A 静态状态验证。报告实际 APK SHA-256、PID/UID，删除临时副本。
5. **受控 APK 的双子进程执行（新增）**：在原 `:virtualv0` 和 `:virtualv0b` Service 的私有 HandlerThread 中分别加载内嵌的 APK，执行 guest `GuestEntry.record/read`，经 Binder 将每个远端 PID、UID、实际 SHA-256 与 A/B 状态回传。两个远端均须与宿主 PID 不同，彼此 PID 不同、同 UID、两个 SHA-256 一致且均符合格式、两份标记不串线；任意异常或 20 秒超时均 FAIL。不是独立 UID，也不是 guest Android Activity 生命周期。
6. 增加了单一受控 loader `ContainerV0TrustedGuest`、测试进程结束时的线程内释放逻辑、四项 SHA-256 判定 JVM 用例；原 A/B 文件、单 IPC、双进程判定共十四项，fixture 自带两项，所有结果仍须 CI 证实。
7. 不接受手动选取或网络下载的未知 APK，受控测试 APK 的源码全部位于本仓库且没有网络、定位或特权逻辑。
8. 没有新增定位权限；不启动 `ServiceGo`，不请求 Shizuku 授权，不访问第三方 App，不读取微信。

## 整合到 main 的安装行为

- **保持原应用包名** `com.soozooq.gogogo.test`；不使用临时 `.container.v0` 后缀。
- **保持原签名方案、服务和数据目录**。版本变为 `2.4.14-tianwaitian-guest-ipc`，`versionCode 2400082`，供已有测试版升级。
- 主程序原有 GPS/NETWORK/FUSED/GMS 注入、地点预设、VPN、Shizuku 和诊断入口均不作行为性修改。
- 当前仓库包含公开测试签名材料，不能用于安全的正式公开发行；升级前应备份重要数据。

## 明确不能声称

- 两个 ClassLoader 只是逻辑隔离；跨进程 Service 虽为不同 PID，**仍是相同 Linux UID**。
- 已编写独立 APK 的 DEX 加载/方法调用代码，但没有虚拟 PackageManager / ActivityManager、guest Application/Activity 生命周期、Native Hook 或真正的沙箱边界。反射调用类不等于安装或托管第三方 App。
- 未在 Android 13 ARM64 真机验收，不能声称微信、QQ、抖音采用指定坐标。
- NewBlackbox 是下一阶段候选代码参考，不代表已整合进天外天。原先额外构建它的单分支 job 不再作为主程序构建条件。

## 验收顺序

1. GitHub 主分支运行 `Build Check`（含 Java 单元测试、Lint）与 `Build Preview APK`；通过后下载新的主程序预览包。
2. vivo Android 13 ARM64 打开 V0 页面，运行 A→B→A 三轮，保存每轮 PASS/FAIL。
3. 单独运行 Binder IPC 两轮，比较 PID/UID，必须不同 PID、相同 UID，确认超时/失败提示可用。
4. 执行新的「验证双进程 A/B 状态隔离」两轮，必须分别看到 A/B 两个子进程 PID 不同、均与宿主不同，两轮 A/B 标记未互相覆盖；观察超时、进程断开时 FAIL。
5. 在 V0 页面运行「验证独立 APK 的 DEX 加载与执行」两轮，须确认 APK package 为 `com.soozooq.tianwaitian.fixture`、有 SHA-256、两份 guest ClassLoader 不同、A/B 标记隔离，且不安装第二个 App。
6. 运行「验证独立 APK 在双进程执行」两轮，核对 A/B 子进程 PID、SHA-256、独立标记；需要有明确 PASS，超时/失败必须输出 FAIL。
7. 回归验证原 App 启动、模拟启动/停止、地图点位回填、VPN 与 Shizuku 设置、既有数据不丢失。
8. 满足以上条件后，再在**同一 main 分支**逐步引入可审计的真实 guest Application/Activity 生命周期；单独用自有无敏感测试 APK 验收，不登录真实微信。

任何自动构建状态、安装结果或微信结果，均须以真实 CI/设备记录为准。

## CI 记录边界（2026-10-17）

主程序 `Build Preview APK` 已更新为 `:app:testDebugUnitTest` 通过后才编译 APK，并检查包名、启动 Activity 与 V0 双进程组件确实进入安装包；诊断 artifact 附 JVM 测试结果。纯 Java A/B 判定本地 JDK 21 烟测 **8/8**，不代表 Android 构建或真机通过。

通过 GitHub Actions API 查询：截至此次检查，main 最近一批已登记工作流仍为 2026-10-14 的旧提交 `d49ddeb1`，包括 [Build Check #38508535392](https://github.com/soozooq/gogogo/actions/runs/38508535392) 和 [Build Preview APK #38508535424](https://github.com/soozooq/gogogo/actions/runs/38508535424) 成功。**10 月 17 日新增容器源码尚无已核验的新 CI 运行。** 用户需从 Actions → Build Preview APK → Run workflow（main）手动触发一次；同样触发 Build Check。若 Actions 结果有失败，以真实失败日志为准。

## 独立 APK 载荷里程碑（2026-10-17）

代码：`containerFixture/`、`ContainerV0ExternalApkProbe`、V0 第四个测试按钮。
构建：`settings.gradle` 注册独立模块；`app/build.gradle` 的 `stageContainerV0Fixture` 在资产合并前生成 APK 资产；原 `Build Preview APK` 现在依次执行 `:containerFixture:testDebugUnitTest`、`:app:testDebugUnitTest` 与 `:app:assembleDebug`，并解包主 APK 核查内嵌测试 APK 的 package、`classes.dex` 与 SHA-256。

本地 JDK 21 使用与仓库 `GuestEntry` 相同的 Java 逻辑进行了两加载器反射调用对照烟测，**7/7 项断言通过**；不代表 Android APK 构建、Android ART DexClassLoader 或 vivo 真机实验成功。GitHub Actions 尚需人工启动一次 `main` 构建，失败时以最新日志定位，不得引用 10 月 14 日旧成功运行作为证据。

下一道关卡不再重复类加载实验，而是让受控 fixture 的 Android `Application/Activity` 生命周期能够在容器中真实启动、按键交互并正确结束，同时不破坏现有定位服务。

## 双进程受控 Guest 里程碑（2026-10-17）

- **源码**：`ContainerV0TrustedGuest` 统一了独立 APK 的读取、只读标记、package 检查、`classes.dex` 确认、DEX 实际装载与 SHA-256 计算。原宿主测试重用此实现；两个 `:virtualv0*` 进程用各自的 `HandlerThread` 加载，避免阻塞主线程。
- **协议**：新增 `MSG_GUEST_WRITE` / `MSG_GUEST_READ` / `MSG_GUEST_RESULT`。原有 Ping 与应用内存标记协议不变。回传来源以每个进程实际读入的 SHA-256 和方法返回值核实，宿主必须检验进程身份、A/B 标记和哈希一致性。
- **UI**：现有 V0 页面新增第五个测试按钮，不另起测试 App、不生成新的开发分支。
- **回归**：`VirtualContainerV0GuestDigestVerdictTest` 共四项；另有 `ci/verify_container_fixture_contract.py` 做静态安全边界检查，现已加入 main 的 Build Check / Build Preview APK 两条工作流。
- **限制**：动态加载方法调用并不等于系统正式启动另一 APK 的 `Application` / `Activity`；Android PackageManager 与 ActivityManager 等组件代理仍未实现。未经过 Android 构建/真机测试前不声称已成功。
- **下一步**：尽快取得主分支完整 CI 和 Android 13 真机 PASS/FAIL，先修当前阶段的实际失败，再决定是否推进可审计的受控 guest Activity 代理实现。**不要在没有 CI/真机结果时把阶段推进等同于兼容性完成**。


## 2026-10-17：CI 前真实性核验

本轮停止继续叠加未经真机验证的功能，优先排查最新版本构建与实测。

- 已修复 `VirtualContainerV0TwoSlotProbe` 对远端 `KEY_ERROR` 的错误归因：现在报告 A/B 进程、执行阶段和最长 200 字的失败原因，避免所有动态装载失败都显示为“远端拒绝测试标记”。
- 本地 JDK 21 `javac --release 11` / `java` 对与仓库对应的四个纯 Java 源类（`GuestEntry`、`VirtualContainerV0GuestDigestVerdict`、`VirtualContainerV0IpcVerdict`、`VirtualContainerV0TwoSlotVerdict`）进行等效逻辑烟测：13/13 断言通过。此测试是 Java 层逻辑检查，**不是** Android Gradle 构建、运行时 DEX 加载、Binder 或手机实验。
- GitHub Actions 最新查询仍只有 10 月 14 日旧构建的成功记录。GitHub 的自动化令牌在部分场景中可能抑制二次触发，但未确认当前连接器的凭证类型；不要未经核实就将没有新构建归因为“Actions 被禁用”。
- **验收唯一下一步**：在 `https://github.com/soozooq/gogogo/actions/workflows/build-release.yml` 为 `main` 手动执行 Build Preview APK。必须看到提交 SHA、任务真实运行、产物内置 `assets/twt-fixture-v0.apk` 的打包检查和单元测试；之后才允许安装新版 `2.4.14-tianwaitian-guest-ipc` 做五项 V0 验收。
- 不要直接给已有定位模拟用户部署未经构建验证的调试成果，更不要把五项 V0 PASS 当成微信定位成功。


## 2026-10-17：可复用 JVM 预检及 APK 产物闸门

- `ci/run_container_v0_jvm_smoke.sh` 是纯 JDK、零 Android SDK 依赖的 17 条断言预检：直接编译**仓库里的实际四个 Java 源文件**（fixture `GuestEntry`、GuestDigestVerdict、IpcVerdict、TwoSlotVerdict），使用 `javac --release 11`，临时目录自动清理。执行方式：`bash ci/run_container_v0_jvm_smoke.sh`。需要先在对应环境获得完整的仓库代码。
- Build Preview APK 和 Build Check 均已加入该脚本作为 Android Gradle 前置检查。此前在当前受限执行环境中，用经过仓库核对、手动转录的相同四个 Java 类进行了真实的 JDK 21 / Java 11 目标版本编译与运行，得到 17/17 断言通过；**这并非仓库中脚本已经在 GitHub Actions 运行通过**。
- 修复 `build-release.yml` 的 APK 上传判断：编译通过只是必要条件，只有 `verify_apk` 步骤（包名、启动页、V0 组件、内置 fixture APK 的 ZIP 完整性、包名、DEX、SHA-256）真正成功才上传 APK；验证失败会保留诊断但不得把未验收 APK 当作成功产物。
- 已确认本轮主线没有 Android SDK 构建环境；容器编译与 Android 13 真机测试尚未执行。不要以 JVM PASS 替代这两项验收。
- 如果持续没有新 Actions 运行，排查提交身份对事件触发的影响、仓库 Actions 设置与手动 workflow_dispatch；目前未能确认具体原因。后续应优先取得真实 CI 日志，而不是继续叠加未经验证的虚拟化功能。


## 2026-10-17 CI 自动触发阻碍：有证据的排查结果

当前无法从 ChatGPT 已连接的 GitHub 操作中直接调用 GitHub Actions `workflow_dispatch`，只有仓库代码提交、评论等部分操作与工作流查询权限。2026-10-17 现场查到：

- 主分支最新包含 V0 v2.4.14 的代码，但 GitHub Actions 列表中最新一次已确认的执行仍是 2026-10-14 的旧 SHA `d49ddeb1`（Build Check / Build Preview APK 以前均成功）。
- 试验性地给现有 `Build Preview APK` 增加了 **仅限 `soozooq` 本人、PR #57、精确评论正文 `/twt-v0-build-main`** 的 `issue_comment` 触发条件，然后通过连接器成功创建评论 ID `6229810186`。随后查询 `event=issue_comment` 仍是 **0 条运行记录**，也没有新的整体运行。这说明该次连接器评论未成功派生 Actions 构建，**不能推断 GitHub Actions 被禁用**。
- 该试验性触发逻辑已立即撤回，`Build Preview APK` 恢复原有的 `workflow_dispatch`、`push` 与 `pull_request` 事件；避免永久留下无法证明有用的触发条件。PR #57 旧讨论中的触发评论只是实验历史，不是构建结果。
- GitHub 官方文档指出：仓库内 `GITHUB_TOKEN` 所触发的大多数事件不会新建工作流（`workflow_dispatch` 与 `repository_dispatch` 除外）；**但当前 GitHub 连接器实际使用的令牌类型未核实，不能直接归因**。
- 下一步的确定动作是登录有仓库写权限的 GitHub 网页，在 `https://github.com/soozooq/gogogo/actions/workflows/build-release.yml` 选择 `main` 手动运行一次；若页面显示 `Enable workflow`，先核实并启用。此动作须在用户已登录的浏览器或可操作云浏览器中完成。
- 新运行出现后先取得 Run ID 和 `head_sha` 并核对 v2.4.14 代码；随后抓取失败 job 日志或 APK artifact，再进行有针对性的修复。不得拿旧 SHA 的成功构建证明新 V0 可运行。
