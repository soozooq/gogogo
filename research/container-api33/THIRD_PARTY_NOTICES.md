# 许可与来源记录

本目录发布研究源码、补丁及脱敏云端证据；未打包分发两份候选容器、Fossify APK 或预编译 Native 库。下列为已核对的声明及发布前仍需完成的材料，不是逐文件授权审计的完成证明。

| 输入 | 固定来源 | 已核对声明 |
| --- | --- | --- |
| BlackBox | `ccgjson/BlackBox@df6249da` | 根 LICENSE 为 Apache-2.0；本目录补丁保留该来源，文本见 [Apache-2.0.txt](licenses/Apache-2.0.txt) |
| 两份内置 Dobby 源码 | BlackBox 固定源码树内 | Apache-2.0，原 LICENSE 保留在获取的源码中 |
| Apache Commons Lang | BlackBox Xposed 模块 | Apache-2.0，原 LICENSE／NOTICE 保留在源码中 |
| LSPlant standalone 5.0 | 解析后的宿主运行时依赖 | LGPL-3.0；[原版许可文本](licenses/LGPL-3.0-LSPlant.txt)，正式分发须处理对应源码、修改及重链接相关义务 |
| xCrash 3.1.0 | 解析后的宿主运行时依赖 | POM 标为 MIT；[上游完整声明](licenses/xCrash-LICENSE.txt)另列 BSD 风格及公共领域部分，不能只按 POM 名称省略 |
| AndroidX、Kotlin、Guava 等 | 实际解析的运行时依赖图 | POM 声明见机器清单；Guava ListenableFuture 的 Apache-2.0 声明来自 `guava-parent:26.0-android` |
| Fossify Calculator 1.4.0 | 官方 release 与 Git tag `1.4.0` | GPL-3.0；源码及发布地址见 `sources.json`，测试直接使用原始发布文件，不嵌入宿主 |

清单覆盖解析后的 53 个组件节点（含本地工程）。它收集宿主运行时的 POM 及父 POM 声明，不覆盖全部构建／测试工具，也不能替代原生源文件、字体、资源、NOTICE 和完整源码提供要求的逐项核对。

ISEKHON/VirtualApp 根许可证为 Apache-2.0，但所带 Cydia Substrate 源码声明 LGPL-3.0，旧 FB JNI 源码指向 BSD 风格 LICENSE 与 PATENTS，树中未同时带齐这些声明。构建通过不消除该缺口；该 fork 仍未通过本项目的分发／整合门槛。VirtualApp 原作者商业版的最新适配记录也不作为当前公开源码的授权或运行证据。
