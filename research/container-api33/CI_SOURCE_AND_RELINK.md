# 自有 CI 应用的来源与库替换

该构建对是本项目的研究宿主及测试程序，不是微信／抖音原包，也不是日常手机更新。每份 APK 的 `assets/research-notices/` 保存许可原文、NOTICE、本说明及来源记录。CI manifest 记录实际提交、源码摘要和 APK 摘要；同一工作流的依赖清单记录实际解析组件。

原生自有定位阶段可另携带独立的`location-consumer`应用；它没有引擎、宿主或SDK依赖，同样携带上述材料。该模式的manifest严格列出宿主、测试、消费者三份APK及消费者源码；旧两份模式继续支持。两种模式均不包含第三方目标APK，也不是手机发行包。

## 来源

| 组件 | 对应源代码／材料 |
| --- | --- |
| 宿主、测试、引擎修改 | 本仓库同一提交中的 `research/container-api33/`；固定上游及补丁在 `sources.json`、`patches/` |
| BlackBox、内置 Dobby、Commons Lang | `ccgjson/BlackBox@df6249da4b032649689a98242f147da6c5342172`；Apache-2.0 及 Commons NOTICE 随附 |
| LSPlant standalone 5.0 | [LSPosed/LSPlant v5.0](https://github.com/LSPosed/LSPlant/tree/9324d71097a273ae7ce63fa2c2d676e17a1a2e04)，tag 对应提交已核对；随附 LGPL-3.0 与 GPL-3.0 全文 |
| xCrash 3.1.0 | [iqiyi/xCrash](https://github.com/iqiyi/xCrash/tree/v3.1.0)；保留其完整 MIT／BSD 等声明 |
| AndroidX、Kotlin、Guava 等 | 对应 Maven 坐标见实际 CI 依赖清单；Apache-2.0 全文随附 |
| JUnit 4.13.2、Hamcrest Core 1.3（测试） | [JUnit](https://github.com/junit-team/junit4/tree/r4.13.2)、[Hamcrest](https://github.com/hamcrest/JavaHamcrest/tree/hamcrest-java-1.3)；许可文本直接从本次解析的对应 JAR 提取 |

运行时清单及逐文件审计的范围见 `THIRD_PARTY_NOTICES.md`，不将此材料包写成所有资源或全部依赖的审计完成证明。未将候选 VirtualApp 分支的二进制加入此构建。

## 重建

按本目录 README 使用固定 SDK／NDK，设置仓库外工作目录后执行 `tools/prepare.py` 与 `tools/run-build.sh`。它们提供固定引擎源码、保留修改的补丁、宿主及测试源码和构建参数。原始目标 APK 单独下载核验，不嵌入这些 APK。

## LSPlant 相容库替换

实际 APK 中的 `libxposed.so` 通过 ELF `DT_NEEDED` 动态链接 `liblsplant.so`。可修改 LSPlant、按其上游构建说明产生相同 ABI／接口的库，并替换**自有宿主 APK** 内 `lib/arm64-v8a/liblsplant.so` 或 `lib/x86_64/liblsplant.so`。

替换时在新的工作目录解压自有宿主，替换相应库，移除旧签名文件并重新打包；使用 Android SDK 的 `zipalign`、`apksigner` 和你自己的密钥签名。自有测试 APK 也用同一密钥签名，保持测试与宿主配对。可在全新研究实例安装，或按 README 从修改后的源码重新构建。原始微信／抖音 APK 保持不变。

依 LGPL 授予的库修改及为调试库修改进行逆向工程的权利，不受此研究宿主额外限制。CI 中的固定摘要用于记录某次测量的输入；修改后的构建应生成自己的 manifest，不冒充先前构建的实测结果。
