# 许可与来源记录

本目录提交研究源码、补丁及脱敏云端证据。明确选择时，同一 CI 可通过单日保留的产物传递自编宿主／测试对，APK 内随附许可、NOTICE、来源及相容库替换说明；原始第三方 APK 与系统镜像不包含在内。下列为已核对的声明及范围，不是逐文件授权审计的完成证明，也不是正式手机发行。

| 输入 | 固定来源 | 已核对声明 |
| --- | --- | --- |
| BlackBox | `ccgjson/BlackBox@df6249da` | 根 LICENSE 为 Apache-2.0；本目录补丁保留该来源，文本见 [Apache-2.0.txt](licenses/Apache-2.0.txt) |
| 两份内置 Dobby 源码 | BlackBox 固定源码树内 | Apache-2.0，原 LICENSE 保留在获取的源码中 |
| Apache Commons Lang | BlackBox Xposed 模块 | Apache-2.0，原 LICENSE／NOTICE 保留在源码中 |
| LSPlant standalone 5.0 | 解析后的宿主运行时依赖；v5.0 对应 `9324d71097a273ae7ce63fa2c2d676e17a1a2e04` | LGPL-3.0；[该版本许可原文](licenses/LGPL-3.0-LSPlant.txt)及 [GPL-3.0](licenses/GPL-3.0.txt)随附；实际 `libxposed.so` 动态链接 `liblsplant.so`，来源及相容库替换见[说明](CI_SOURCE_AND_RELINK.md) |
| xCrash 3.1.0 | 解析后的宿主运行时依赖 | POM 标为 MIT；[上游完整声明](licenses/xCrash-LICENSE.txt)另列 BSD 风格及公共领域部分，不能只按 POM 名称省略 |
| AndroidX、Kotlin、Guava 等 | 实际解析的运行时依赖图 | POM 声明见机器清单；Guava ListenableFuture 的 Apache-2.0 声明来自 `guava-parent:26.0-android` |
| Fossify Calculator 1.4.0 | 官方 release 与 Git tag `1.4.0` | GPL-3.0；源码及发布地址见 `sources.json`，测试直接使用原始发布文件，不嵌入宿主 |
| GPSTest 3.10.6（osmdroid） | 官方 `barbeau/gpstest` release，源码 `ffcf2a974e3c6b34cc3aac1c7086f467d24b8439` | Apache-2.0；[上游版权及完整许可](licenses/GPSTest-LICENSE.txt)。只下载、核验原 APK 用于云端运行，不嵌入或重签 APK；第三方自身依赖不属于宿主运行依赖图 |

后续加入的 `location-consumer` 是本项目自编、独立构建的 Android 测试消费者，无第三方库依赖；它不是新的第三方兼容样本。消费者、宿主或测试 APK 不提交到 Git 历史。

自有 CI 对的 manifest 固定同一提交、源码、许可材料及 APK 大小／摘要。两份自有 APK 内的 `assets/research-notices/` 字节与仓库材料逐一核对，并拒绝嵌入任何其他 APK。JUnit 4.13.2 的 EPL-1.0 与 Hamcrest Core 1.3 的 BSD 声明从实际解析 JAR 提取，Commons NOTICE 来自固定引擎源码；附加材料来源见[记录](licenses/ci-additional-provenance.json)。保留了运行时清单不覆盖全部测试／构建工具与逐文件内容的边界。

清单覆盖解析后的 53 个组件节点（含本地工程）。它收集宿主运行时的 POM 及父 POM 声明，不覆盖全部构建／测试工具，也不能替代原生源文件、字体、资源、NOTICE 和完整源码提供要求的逐项核对。

ISEKHON/VirtualApp 根许可证为 Apache-2.0，但所带 Cydia Substrate 源码声明 LGPL-3.0，旧 FB JNI 源码指向 BSD 风格 LICENSE 与 PATENTS，树中未同时带齐这些声明。构建通过不消除该缺口；该 fork 仍未通过本项目的分发／整合门槛。VirtualApp 原作者商业版的最新适配记录也不作为当前公开源码的授权或运行证据。

云端 Cuttlefish 系统资格检查的[独立输入清单](cuttlefish-inputs.json)固定官方 Android13 构建、对应源码 manifest、宿主包和公共镜像；另固定官方 `google/android-cuttlefish` 的能力声明脚本。该脚本的 Apache-2.0 版权及许可头保留在云端下载文件中，未将其声明替代实际设备能力测量。QEMU 来自临时 Ubuntu runner 的发行版包。此处不将宿主包、系统镜像、QEMU、内核或 helper 二进制重新打包或上传为本项目产物；这不是这些运行工具全部依赖的许可审计完成证明。
