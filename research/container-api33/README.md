# Android 13 免 Root 容器可行性验证

2026-10-23。此目录保存独立研究宿主、固定源码补丁与云端验证证据。天外天日常版本仍为 `2.4.31-simple-support / 2400099`；主程序、签名、定位服务及默认入口均未改动。本轮没有需要用户安装的手机更新包。

## 已观察到的能力

在 **Android 13 / API 33、x86_64 云端模拟器**中，未改动的 Fossify 计算器 `1.4.0 / 10` 完成了两项实际界面检查：

1. 系统正常安装的对照：通过可见按钮计算 `1＋2＝3`，回桌面后重新打开，结果仍为 `3`。
2. 系统未安装该软件时：研究宿主从固定 APK 导入并请求启动，真实计算器界面完成相同操作；检查前后 `pm path org.fossify.math` 均为空。

宿主不包含计算器 APK 或它的类定义，不要求计算器实现天外天接口。实际执行的是第三方 APK 的代码和资源，Android 窗口由宿主的 Activity 代理承载。Guest 与宿主共享 Linux UID 和权限，**这不是独立 UID 安全沙箱**。此结果仅覆盖这一款明确准入的软件及本轮流程。

![云端计算器结果：1+2=3](evidence/container-calculator.png)

截图为云端研究结果。微信、抖音、GPS／GMS 替换及海外地点功能均未在本轮验证。ARM64 已构建，尚无 ARM64／vivo 真机运行结果。

## 源码和修正

固定输入见 [sources.json](sources.json)：BlackBox `df6249da`、VirtualApp fork `b3c634ad`、原版 Fossify APK 的 SHA-256。两份候选均完成了云端构建；本轮的实际运行宿主使用 BlackBox。

[BlackBox 补丁](patches/blackbox-api33-build.patch)修正了 AIDL 包名与文件／Java 类型不一致，以及资源反射镜像被 Android Gradle Plugin 当作生成的 `R` 类过滤的问题；镜像中的数组类型同步改为框架实际使用的 `int[]`。补丁已在干净的固定提交上重新应用并核对完整 diff。

研究宿主清除了引擎清单的大量权限继承，仅声明内部签名权限及切换自身任务所需的普通 `REORDER_TASKS`。准入范围仅限已核验的计算器发布文件。未开启虚拟定位、Root／Xposed 隐藏或守护服务。

## 尚未跨过的门槛

[API 与运行审计](AUDIT.md)保留了现代定位回调、单次定位取消、时间戳、注销及原签名解析等缺口。此处的 UI 通过不能替代定位接口通过，更不能替代微信发送位置或实时共享成功。

[许可记录](THIRD_PARTY_NOTICES.md)及[运行时依赖声明清单](evidence/dependency-licenses.json)区分根许可证、传递依赖、原生代码和待补齐的发布材料。当前不将这份研究产物标为正式发行容器。

## 在研发环境复核

这些命令供云端／开发环境使用，不是手机用户的操作清单。准备 JDK 17，SDK 平台 30、33、34，Build Tools 33.0.2，NDK 25.1.8937393，CMake 3.22.1；设置对应的 `JAVA_HOME`、`ANDROID_SDK_ROOT`、`GRADLE_USER_HOME`，保留所在环境的代理及 CA 设置。SDK 安装串行完成，构建脚本禁止自动安装 SDK。

```bash
export CONTAINER_RESEARCH_WORKDIR=/tmp/gogogo-container-research-work
python3 tools/prepare.py --workdir "$CONTAINER_RESEARCH_WORKDIR"
bash tools/run-build.sh blackbox :BCore:assembleDebug :probeHost:assembleDebug :probeHost:assembleDebugAndroidTest :probeHost:writeResearchDependencyInventory
bash tools/run-build.sh virtualapp :app:assembleDebug
python3 tools/license-inventory.py --graph "$CONTAINER_RESEARCH_WORKDIR/evidence/runtime-dependencies.json" --gradle-cache "$GRADLE_USER_HOME" --output /tmp/dependency-licenses.json
python3 tools/inspect-apk.py --sdk "$ANDROID_SDK_ROOT" --host "$CONTAINER_RESEARCH_WORKDIR/probe-host-build/outputs/apk/debug/probeHost-debug.apk" --output /tmp/host-artifact.json
```

准备器将源码放到主仓库以外，固定提交与补丁；已有未知修改或不符的 APK 会保留并报错。构建前重新验证源码，不在默认天外天 Gradle 工程加入模块。

使用专用 API 33 x86_64 模拟器后，运行两项检查；脚本拒绝物理设备 serial，要求普通 shell UID 2000 与 SELinux Enforcing：

```bash
python3 tools/run-cloud-check.py --serial emulator-5580 --label installed-control --installed-baseline
python3 tools/run-cloud-check.py --serial emulator-5580 --label container-guest
```

每轮使用独立且不允许覆盖的证据目录，记录 APK 输入摘要、真实界面、JUnit 原文和结束状态。正常安装仅用于第一项对照，之后移除；第二项要求系统中不存在该包。原始日志留在研究工作区，仓库只收录摘要、必要报告和云端截图。
