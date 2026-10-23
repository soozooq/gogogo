# Android 13 免 Root 容器可行性验证

2026-10-23。此目录保存独立研究宿主、固定源码补丁与云端验证证据。天外天日常版本仍为 `2.4.31-simple-support / 2400099`；主程序、签名、定位服务及默认入口均未改动。本轮没有需要用户安装的手机更新包。

项目接手只认根目录 [PROJECT_STATUS.md](../../PROJECT_STATUS.md)。本轮继续使用同一个研发分支和 Draft #60，没有按修复或检查新增分支／PR。

## 最新进展：统一的普通导入／选点入口

研究宿主现在用一个页面完成选择、导入、填坐标、打开与停止。固定计算器和 GPSTest 的清单从 `sources.json` 生成；导入核对原 APK 的大小、摘要、包名、版本、证书。已导入状态和目标会保留，修改坐标后直接再次应用；不会改动系统安装的微信、抖音或日常天外天。

[完整普通入口检查](evidence/unified-entry-results.json)通过 **63.652 秒**：真实系统文件选择器导入、精确定位授权、宿主界面设置 A→B、原版 GPSTest 同一进程更新，最后停止实际 Guest 进程并关闭虚拟模式。此检查没有直接调用引擎设置目标或用测试代理替代界面，输入绑定当时 24 项源码与 APK 摘要。中途一次文件选择器文字定位失败已分类保留；现在每轮清空自己的旧输出，失败不复用历史截图／报告。

![统一入口：修改坐标后应用，一键停止](evidence/unified-entry.png)

当前只准入上述两个固定发布文件，**微信／抖音、腾讯／字节 SDK、GMS、海外业务和 ARM64／vivo 尚未成功**。[已核验的官方目标安装包](evidence/official-target-apk-readiness.json)都只有 ARM64 原生库；未加入普通入口、未改签或分发，版本也不等于用户手机版本。

本云端 x86_64 模拟器[拒绝 ARM64 镜像](evidence/arm64-cloud-runtime-limit.json)。[GitHub ARM Mac 构建通过，VM 因 HVF 不可用未启动](evidence/cloud-arm-mac-build-and-vm-limit.json)。Linux 的 KVM 启动、存储和构建已通过，最近[文件选择器抽屉遮挡导致未完成选择](evidence/cloud-picker-occlusion-failure.json)，现改为默认下载目录并等待真实返回，已通过导入并实际打开 Guest；随后[首次启动更新说明遮挡坐标](evidence/cloud-fresh-gpstest-dialog-failure.json)，已按实际 XML 修正关闭按钮，完整流程仍待确认。失败不算引擎或定位结果。[云端流程](../../.github/workflows/container-research.yml)只上传证据，不上传 APK，继续研发统一留在 #60。

[官方 Google API33 x86_64 镜像实测](evidence/google-api33-nativebridge-check.json)没有 ARM64 转译。[官方 API30 镜像实测](evidence/google-api30-nativebridge-check.json)已确认内置 ARM64 转译、UID 2000 与 SELinux Enforcing。`tools/run-target-startup-check.py` 和独立云端 KVM 检查要求这一专用环境，固定两份官方目标原包，平台安装选项选择宿主 ARM64 ABI 并核对实际状态。它先普通安装对照，再容器匿名启动，保持定位关闭，停在欢迎或隐私页；不接受条款、不登录。代码已编译，尚未通过运行，不加入普通入口。即使通过，也仅是 Android 11 转译环境的启动证据。

## 前轮：官方 GPSTest 实际采用位置

未修改的官方 GPSTest `3.10.6 / 24100`（osmdroid）在系统未安装该包时进入容器，正常系统精确定位授权后，其实际坐标界面跟随 A→B 目标，31.322 秒通过。原 APK 的字节摘要及证书已核对；没有向 Guest 传入目标坐标，没有测试代理或直接调用 Hook。见[绑定 23 项源码摘要、实际界面文本及 JUnit](evidence/official-gpstest-location-results.json)。

真实应用暴露的 Android 13 GNSS 旧内部类崩溃已修正。API31+ 使用真实框架 GNSS 回调；虚拟会话只报告零颗卫星，不产生 first-fix、NMEA 或原始观测值。相关自有 Guest 回归通过六条定位路径、mock 标记与 GNSS 注销停止；4 项受控接口和原版计算器回归也通过。两次早期未通过的记录分别归类为引擎崩溃和自动化筛选错误，保留原输入，不当作成功。

![官方 GPSTest 在容器内显示第二个合成目标](evidence/official-gpstest-location.png)

**范围仍是一款第三方应用的前台定位采用，不代表腾讯／字节 SDK、GMS、微信／抖音、ARM64 或 vivo。** 日常应用及其手机安装包没有改变。该轮仍由云端驱动直接导入 GPSTest，后续普通入口验证见上节。

## 前轮：真实 Guest 中的自有定位调用

[独立的自有定位消费者](location-consumer/README.md)在系统未安装时，由容器在另一真实进程运行；通过普通 Android `LocationManager` 读取 GPS／NETWORK 的缓存、单次回调、持续更新。六条路径均跟随宿主设定的 A→B 目标，停止后计数稳定，取消后的回调为零。消费者不知道预期坐标，没有引擎、宿主或反射依赖；测试没有代理框架接口或直接调用 Hook。系统核验了不同 PID、共享宿主 UID。

[本轮结果](evidence/owned-guest-location-results.json)包含 18.919 秒通过的 JUnit、Guest 原始报告、全部输入摘要及两次早期自动化失败的分类。它覆盖真实 Guest 调用链，**仍是自编消费者，不能代表第三方 SDK、微信、抖音或 ARM64／vivo**。宿主 APK 与前轮相同，SHA-256 仍为 `e3131646a7d52c048cd6ed942492468e578f42d736075bb6b2ed52a288d1e1d0`；消费者和新测试 APK 分别绑定。此前证据保留原提交，不冒充新结果。

![自有 Guest 完成目标切换、停止与取消](evidence/owned-guest-location.png)

## 前轮基础：定位契约与原签名

[前轮结果（提交 4be23be）](evidence/api33-location-signature-results.json)绑定当时源码、补丁及宿主／测试 APK 摘要，覆盖：

- API 33 的公共 `LocationManager`：单次回调与取消、GPS／NETWORK 的新鲜且明确标记为模拟的样本、持续批量回调、注销、次数／时长限制及停用后释放，共 4 项受控检查通过。测试代理显式调用真实 Hook 入口，持续样本经过引擎的定位服务 Binder；**不包含真实 Guest 进程的 Hook 安装或第三方位置采用验证**。
- 原版计算器：系统未安装该包时，虚拟包管理器的 `GET_SIGNATURES` 与 `GET_SIGNING_CERTIFICATES` 均返回官方 APK 的原证书；真实界面的计算与从桌面重开保留结果再次通过。该项仍保持虚拟定位关闭。

本轮宿主 SHA-256 为 `e3131646a7d52c048cd6ed942492468e578f42d736075bb6b2ed52a288d1e1d0`。最初的 [results.json](evidence/results.json)、[source-snapshot.json](evidence/source-snapshot.json)和下方截图对应历史提交 `c134948`，保留原记录，不作为本轮 APK 的证据。微信／抖音／海外定位及 ARM64／vivo 仍未通过。

## 已观察到的能力

在 **Android 13 / API 33、x86_64 云端模拟器**中，未改动的 Fossify 计算器 `1.4.0 / 10` 完成了两项实际界面检查：

1. 系统正常安装的对照：通过可见按钮计算 `1＋2＝3`，回桌面后重新打开，结果仍为 `3`。
2. 系统未安装该软件时：研究宿主从固定 APK 导入并请求启动，真实计算器界面完成相同操作；检查前后 `pm path org.fossify.math` 均为空。

宿主不包含计算器 APK 或它的类定义，不要求计算器实现天外天接口。实际执行的是第三方 APK 的代码和资源，Android 窗口由宿主的 Activity 代理承载。Guest 与宿主共享 Linux UID 和权限，**这不是独立 UID 安全沙箱**。此结果仅覆盖这一款明确准入的软件及本轮流程。

![云端计算器结果：1+2=3](evidence/container-calculator.png)

截图为云端研究结果。微信、抖音、GPS／GMS 替换及海外地点功能均未在本轮验证。ARM64 已构建，尚无 ARM64／vivo 真机运行结果。

## 源码和修正

固定输入见 [sources.json](sources.json)：BlackBox `df6249da`、VirtualApp fork `b3c634ad`、原版 Fossify APK 的 SHA-256。两份候选均完成了云端构建；本轮的实际运行宿主使用 BlackBox。

[BlackBox 补丁](patches/blackbox-api33-build.patch)保留原来的 AIDL 包名及资源镜像修正，并加入现代定位回调、取消、调度／注销、时间戳和原签名修正。沿用同一个补丁文件和固定上游提交；已从原始文件重新应用并逐文件核对本轮结果。

研究宿主清除了引擎清单的大量权限继承，保留内部签名权限及 `REORDER_TASKS`，并为原版 GPSTest 增加普通的精确／粗略定位、网络、网络状态及前台服务权限。精确定位经过真实系统授权界面；Guest 共享宿主 UID／权限。普通入口准入固定计算器（定位关闭）与 GPSTest（应用坐标后启用）；自有消费者仅用于云端回归。未开启 Root／Xposed 隐藏或守护服务。

## 尚未跨过的门槛

[API 与运行审计](AUDIT.md)区分已经修正并受控验证的接口，以及尚未完成的真实 Guest 定位、GMS、组件及权限兼容。此处的 UI 与接口结果均不能替代微信发送位置或实时共享成功。

[许可记录](THIRD_PARTY_NOTICES.md)及[运行时依赖声明清单](evidence/dependency-licenses.json)区分根许可证、传递依赖、原生代码和待补齐的发布材料。当前不将这份研究产物标为正式发行容器。

## 在研发环境复核

这些命令供云端／开发环境使用，不是手机用户的操作清单。准备 JDK 17，SDK 平台 30、33、34，Build Tools 33.0.2，NDK 25.1.8937393，CMake 3.22.1；设置对应的 `JAVA_HOME`、`ANDROID_SDK_ROOT`、`GRADLE_USER_HOME`，保留所在环境的代理及 CA 设置。SDK 安装串行完成，构建脚本禁止自动安装 SDK。

```bash
export CONTAINER_RESEARCH_WORKDIR=/tmp/gogogo-container-research-work
python3 tools/prepare.py --workdir "$CONTAINER_RESEARCH_WORKDIR"
bash tools/run-build.sh blackbox :BCore:assembleDebug :probeHost:assembleDebug :probeHost:assembleDebugAndroidTest :locationConsumer:assembleDebug :probeHost:writeResearchDependencyInventory
bash tools/run-build.sh virtualapp :app:assembleDebug
python3 tools/license-inventory.py --graph "$CONTAINER_RESEARCH_WORKDIR/evidence/runtime-dependencies.json" --gradle-cache "$GRADLE_USER_HOME" --output /tmp/dependency-licenses.json
python3 tools/inspect-apk.py --sdk "$ANDROID_SDK_ROOT" --host "$CONTAINER_RESEARCH_WORKDIR/probe-host-build/outputs/apk/debug/probeHost-debug.apk" --output /tmp/host-artifact.json
```

准备器将源码放到主仓库以外，固定提交与补丁；已有未知修改或不符的 APK 会保留并报错。构建前重新验证源码，不在默认天外天 Gradle 工程加入模块。

使用专用 API 33 x86_64 模拟器后，运行两项检查；脚本拒绝物理设备 serial，要求普通 shell UID 2000 与 SELinux Enforcing：

```bash
python3 tools/run-cloud-check.py --serial emulator-5580 --label installed-control --installed-baseline
python3 tools/run-cloud-check.py --serial emulator-5580 --label container-guest
python3 tools/run-cloud-check.py --serial emulator-5580 --label location-contract --location-contract
python3 tools/run-cloud-check.py --serial emulator-5580 --label owned-guest-location --guest-location
```

每轮使用独立且不允许覆盖的证据目录，记录源码／APK 摘要、真实界面、JUnit 原文和结束状态。正常安装仅用于第一项对照，之后移除；容器检查要求系统中不存在对应包。`--guest-location` 只在云端测试中准入本次独立构建并校验摘要的消费者，默认宿主入口的计算器准入规则不变。原始日志留在研究工作区，仓库只收录摘要、必要报告和云端截图。
