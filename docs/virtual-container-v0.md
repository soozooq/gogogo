# 天外天 Virtual Container V0 — 主程序内建诊断

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
