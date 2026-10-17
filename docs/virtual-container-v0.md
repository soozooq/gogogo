# 天外天 Virtual Container V0 — 主程序内建诊断

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
