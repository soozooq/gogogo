# 天外天接手入口

更新时间：2026-10-25。此文件定义当前进度；旧 main、旧日志和实验数量不能替代实际结果。

## 目标与唯一研发分支

用户只有一部 Android13／vivo 手机，**没有 Root、没有电脑**。微信发送位置／实时共享已有用户失败反馈；抖音不依赖以太网及海外定位也**尚未完成**。不让用户重复旧实验，不把缓存或自有测试通过写成第三方采用。

| 角色 | 分支与 PR | 约束 |
| --- | --- | --- |
| 继续研发 | **`research/container-api33-20261023` · [Draft #60](https://github.com/soozooq/gogogo/pull/60)** | 本分支 HEAD 是唯一研发主线，继续提交这里 |
| 日常候选 | `fix/location-input-validation` · [Draft #59](https://github.com/soozooq/gogogo/pull/59) | 固定 `2620b741dd6a971143577737c64ebc4496120d77`，`2.4.31-simple-support / 2400099`；本轮未改 |

`main` 的 `0762f60` 是旧基线。保留其他工作区及未提交内容，不新增分支／PR、删除分支、强推、重置他人工作区或自动合并。研究模块未接进日常程序，没有研究手机包待安装。

本分支另准备[简短默认反馈](docs/simple-support-flow.md)：当前状态及缓存优先，旧工作流只列存档索引，去掉嵌套旧报告和重复实验建议，格式上限14,000字符。[本地编译、447项 Debug 单元检查及 lint 通过](research/container-api33/evidence/compact-feedback-preparation.json)，没有手机 UI 或定位业务证明。主程序源码中有此项待发布改动，定位引擎和 #59 日常候选不变；没有发布手机更新或要求复测。

## 当前卡点：原生容器黑屏；修正后的原生检查下载未开始

最新[实际 ARM64 对照 CI33／073c25db](research/container-api33/evidence/cuttlefish-matched-original-and-black-screen-results.json)已核对全部归档及8份源码：固定API33／build16596636、原生ARM64、Native Bridge 0、UID2000、SELinux Enforcing、实际4CPU、超时倍数50；system_server PID653全程稳定，VM与自有包均清理。两款普通原包界面、容器导入及原始证书均通过。**抖音普通原包在隐私页后再观察305.978秒，PID稳定；两个容器仍为黑屏，仅有系统栏。** 微信crash buffer没有Fatal不能证明没有错误；抖音有JIT线程SIGSEGV/SEGV_ACCERR头，缺完整回溯，不能沿用CI31栈或认定具体组件原因。未接受条款、登录或测试位置业务。

[CI34／2c50ca49的基础检查](research/container-api33/evidence/critical-native-and-async-results.json)已实际通过：自有Guest的真实Binder UID/PID、Context归属、新鲜启动nonce、旧HTTP类及两类PendingIntent创建；新增两项异步基站检查与旧五项共7项通过。API33+保留系统CriticalNative Binder方法，避免旧普通JNI桥回调Java／Binder；异步CellInfo请求在虚拟模式完成空列表、关闭模式原样转发。新增无线检查经过真正TelephonyManager包装和Executor但使用受控服务，不证明真实Guest无线Hook或目标SDK采用，持续订阅尚未覆盖。PendingIntent发送及后台投递未验证。

CI34原生作业沿同一个锁在CI33完成后才开始，但在下载官方输入时出现HTTP429及异常链接信息，**Android未启动、目标未测试**，不能当作修正后应用失败。归档、提交与8份源码均核对；准备失败没有QEMU清理标记，工具保留未知，不能伪造已停止或通过。CI34的CriticalNative修正尚无原生目标结果。

本次[驱动补充](research/container-api33/evidence/public-download-and-startup-observation-preparation.json)只修改Python：合法公开viewer下异常链接最多对同页重新读取3次，保留构建／公开访问／GCS主机／文件名／大小／SHA校验和认证拒绝；增加限定、URL脱敏的活动／窗口／进程／框架日志来定位黑屏。22项受控检查通过，两轮历史4归档及8份源码重新核对，3个实时公开viewer链接形状正常；实际新增原生采集待下一轮。应用Java／Native／清单与2c50ca49相同，未修改目标原包、JIT、ART、mock标记或平台超时。

同分支的[简短默认反馈](docs/simple-support-flow.md)保持14,000字符上限与历史索引，未发行。较旧API原生路径、vivo和定位业务仍未验证。旧[CI31启动异常](research/container-api33/evidence/cuttlefish-verified-signers-and-startup-crashes.json)、旧系统／CPU对照及全部失败保留，不重复手机实验。

## 已证实的工程范围

以下实际运行证据均为 Android13 **x86_64**，不是 ARM64／vivo 或目标业务通过。

| 实测 | 已覆盖 |
| --- | --- |
| [原版计算器](research/container-api33/evidence/results.json) | 系统未安装时容器运行，实际界面 1+2=3，回桌面重开保留结果 |
| [定位契约与原签名](research/container-api33/evidence/api33-location-signature-results.json) | 单次／取消、持续批量／注销、次数／时长、单调时间及 Parcel；原 APK 证书查询，解析失败拒绝导入 |
| [独立自有 Guest](research/container-api33/evidence/owned-guest-location-results.json) | 普通 LocationManager 六条 GPS／NETWORK 路径 A→B；停止稳定，取消零回调 |
| [官方 GPSTest](research/container-api33/evidence/official-gpstest-location-results.json) | 正常精确权限、真实界面采用 A→B；GNSS 零卫星，不伪造 first-fix／NMEA |
| [普通导入入口](research/container-api33/evidence/cloud-ordinary-entry-pass.json) | 系统文件选择、导入、精确授权、同进程更新与停止 |
| [七项无线框架受控检查](research/container-api33/evidence/critical-native-and-async-results.json) | 原五项getter及两项异步请求：空列表回调、调用者线程、关闭模式转发；均无真实无线查询 |

普通入口已统一为“选择应用 → 原版导入一次 → 填坐标并打开”，再次应用更新目标，提供停止和状态保存。仅准入固定计算器与 GPSTest，核对大小、SHA-256、包名、版本及原证书；微信／抖音未加入，也未改签或分发。

七项无线信息检查使用受控代理、框架包装和合成对象，没有读取真实无线信息；不证明 Guest Hook 安装、实际权限或腾讯／字节 SDK 采用，也不能归因于用户原来的微信失败。API31+ Wi-Fi 使用公开标准脱敏接口，旧 API 空返回分支未实测。样本保留 mock 标记；Guest 与宿主共享 UID，不是独立 UID 安全沙箱。详见[审计](research/container-api33/AUDIT.md)。

## 验证及用户失败证据

[最近完整核对的默认 CI](research/container-api33/evidence/default-ci-state.json)为 `40201331254 / 2c50ca49`：Linux／Mac成功；自有框架0.495秒、计算器5.593秒、普通入口9.919秒、七项无线0.047秒，每项55份源码；变化界面546毫秒／57份源码。主程序447项检查及lint零Error/Fatal，自有构建对50份源码、两份签名与每包11份材料核对。整轮因原生准备下载失败而失败，旧转译跳过。实际目标运行最近为CI33失败，不能混为CI34目标结果。

用户 10 月 23 日 VPN 开／关日志中，导出时 GPS／NETWORK／GMS 缓存新鲜且接近模拟目标，但微信截图及反馈失败。导出快照不是微信前台连续采样；10 月 13 日旧实验不是本轮结果。#59 顶部保留脱敏失败验收及日常版本产物验证，实际地址截图没有上传公共仓库。更新 #59 时只改标记的后续研发段，保留此前正文。

## 继续操作

1. 沿本分支／Draft#60提交这次Python驱动与真实回执更新，先过基础检查，再自动进入原生对照。原生job共用同一锁且不取消正在运行的检查，基础作业可以先验证；#59日常源码固定。
2. 下一轮确认公开输入下载恢复，并读取新增限定日志。CriticalNative修正的原生目标效果仍未知；普通抖音305秒稳定与Guest黑屏／JIT头是当前证据，不猜测LSPlant，也不更改全局JIT／镜像／mock标记。
3. 继续分别记录普通原包界面、原证书、容器启动、持续观察和定位业务。失败、跳过和未达到阶段不能写成成功；不接受条款、登录或发送消息，不发新手机实验任务。
4. 使用仓库外CONTAINER_RESEARCH_WORKDIR与tools/run-build.sh，保留代理／CA，SDK安装串行，生成物不入库。历史回执用固定Git提交的tools/verify-ci-receipts.py核对，新输出路径保留原文件；原生准备失败保留错误和未知清理。许可见[记录](research/container-api33/THIRD_PARTY_NOTICES.md)。
