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

## 当前卡点：原包界面与平台运行稳定性

原版微信／抖音输入都只有 ARM64 库。**原生 ARM64 Android13 云端已完整启动，两款原包均普通安装成功并请求启动，但欢迎／隐私界面尚未确认，容器及定位业务没有通过结果。** 云端没有连接用户手机。

最新 [ARM 实测 `40143188975 / 5e87841`](research/container-api33/evidence/cuttlefish-staged-install-and-runtime-limit.json)核对 API33／固定构建 `16596636`、ARM64 内核及主 ABI、Native Bridge 0、UID2000、SELinux Enforcing、实际超时倍数50及存活 QEMU 身份。ZIP、七项报告源码及额外宿主探测脚本均核对。两款原包的传输、远端摘要和安装会话均成功，提交阶段分别耗时757.125秒和680.084秒。

微信只截到黑色应用区域和系统栏，未读到文字；日志在启动请求约131秒后才出现 `WelcomeActivity` 启动记录，旧观察上限120秒，不是欢迎页渲染成功证明。抖音观察期间，`system_server` 因 `ApkAssets.finalize()` 10秒超时发生 Java 致命异常，截图为 Android 启动画面，卸载时包服务不可用。微信包清理成功，抖音包清理未确认，整个 VM 已停止。保留原始 UNKNOWN 和后续证据的区别，不能归因为目标 SDK／模拟标记或 vivo 故障。

下一轮准备：原包及 CVD 容器界面等待限定为300秒，检查系统进程 PID／致命异常；宿主及亲和性实测至少4核后使用4个 vCPU，并核验 Guest `0-3`，内存仍3072MiB。容量调整能否避免后续故障尚未验证，不改镜像、ART 属性或终结看门狗。每款应用在自己的普通对照通过且清理后，立即进入对应容器检查，再处理另一款；容器前后再次核对系统进程。

自有宿主／测试对仅在明确选择时通过同一 CI 传递，固定提交／源码／大小／SHA。原包单独下载核验，不进入产物。CVD 模式核对固定产品、构建及安全状态，SDK 原限制保留。[本地14秒重建及13项受控驱动／材料检查](research/container-api33/evidence/ci-own-apk-materials-preparation.json)已通过；两份 APK 各保留11份许可／NOTICE／来源／相容库替换材料，字节逐一匹配，两个 ABI 实际动态链接 LSPlant。此为编译和材料范围，不是完整逐文件许可审计、目标容器或手机运行成功。

固定 [Cuttlefish 输入](research/container-api33/cuttlefish-inputs.json)为同一官方构建的宿主、镜像及 manifest；摘要为本地计算，非官方独立校验和。实际 TCG、组成员、Bionic 目录、ROM、TAP、静音后端和 QEMU 身份均已处理，没有 DHCP／NAT、互联网或无线业务证明。`max,lpa2=off` 的[原固件对照](research/container-api33/evidence/uboot-lpa2-firmware-control.json)及[后续网络栈 ANR](research/container-api33/evidence/cuttlefish-post-firmware-anr.json)保留；软件超时50已独立通过系统资格，不是取消安全检查。旧[180秒安装超时](research/container-api33/evidence/cuttlefish-qualified-boot-install-timeout.json)也保留，新流程已实际越过该限制。

不重复相同旧环境：[API30](research/container-api33/evidence/official-target-baseline-limit.json)／[API32 转译](research/container-api33/evidence/official-target-api32-baseline-limit.json)原包在容器外原生崩溃；[AOSP ARM64 SDK](research/container-api33/evidence/full-arm64-software-linux-check.json)和[Google API33](research/container-api33/evidence/google-api33-full-arm64-software-check.json)有 ART／system_server 崩溃；[Google API32](research/container-api33/evidence/google-api32-full-arm64-software-check.json)有 keystore2 崩溃；[ARM Mac](research/container-api33/evidence/mac-arm64-tcg-selection-failure.json)仍错误启用 HVF。不能扩大为所有 ARM64 路径不可用或当作 vivo 失败。

## 已证实的工程范围

以下实际运行证据均为 Android13 **x86_64**，不是 ARM64／vivo 或目标业务通过。

| 实测 | 已覆盖 |
| --- | --- |
| [原版计算器](research/container-api33/evidence/results.json) | 系统未安装时容器运行，实际界面 1+2=3，回桌面重开保留结果 |
| [定位契约与原签名](research/container-api33/evidence/api33-location-signature-results.json) | 单次／取消、持续批量／注销、次数／时长、单调时间及 Parcel；原 APK 证书查询，解析失败拒绝导入 |
| [独立自有 Guest](research/container-api33/evidence/owned-guest-location-results.json) | 普通 LocationManager 六条 GPS／NETWORK 路径 A→B；停止稳定，取消零回调 |
| [官方 GPSTest](research/container-api33/evidence/official-gpstest-location-results.json) | 正常精确权限、真实界面采用 A→B；GNSS 零卫星，不伪造 first-fix／NMEA |
| [普通导入入口](research/container-api33/evidence/cloud-ordinary-entry-pass.json) | 系统文件选择、导入、精确授权、同进程更新与停止 |
| [五项基站／Wi-Fi 受控检查](research/container-api33/evidence/wifi-radio-privacy-contract-results.json) | 缺失基站不回退真实数据；Wi-Fi 空值、关闭模式及标准脱敏副本，原对象不变、无固定路由器标识 |

普通入口已统一为“选择应用 → 原版导入一次 → 填坐标并打开”，再次应用更新目标，提供停止和状态保存。仅准入固定计算器与 GPSTest，核对大小、SHA-256、包名、版本及原证书；微信／抖音未加入，也未改签或分发。

五项无线信息检查使用受控代理和合成对象，没有读取真实无线信息；不证明 Guest Hook 安装、实际权限或腾讯／字节 SDK 采用，也不能归因于用户原来的微信失败。API31+ Wi-Fi 使用公开标准脱敏接口，旧 API 空返回分支未实测。样本保留 mock 标记；Guest 与宿主共享 UID，不是独立 UID 安全沙箱。详见[审计](research/container-api33/AUDIT.md)。

## 验证及用户失败证据

[最近完整核对的默认 CI](research/container-api33/evidence/default-ci-state.json)为 `40143188975 / 5e87841`：Linux／Mac 默认作业成功，五项代理0.035秒、普通入口10.997秒、计算器5.69秒，两份 ZIP、提交及 Linux 每轮37项源码均核对。整轮因界面未确认及后续平台崩溃而失败。系统资格、安装及界面结果分开保存；旧转译作业跳过，**跳过不是通过**。

用户 10 月 23 日 VPN 开／关日志中，导出时 GPS／NETWORK／GMS 缓存新鲜且接近模拟目标，但微信截图及反馈失败。导出快照不是微信前台连续采样；10 月 13 日旧实验不是本轮结果。#59 顶部保留脱敏失败验收及日常版本产物验证，实际地址截图没有上传公共仓库。更新 #59 时只改标记的后续研发段，保留此前正文。

## 继续操作

1. 分阶段安装已通过，继续独立验证300秒界面观察、4核容量及系统稳定性判定。重新核对本次系统，保留失败及每阶段回执。
2. 每个原包普通对照通过且清理后，立即检查该目标容器，再处理另一款。欢迎／隐私页不等于位置业务，不接受条款、登录或发送消息。运行中不要推送代码取消任务。
3. 平台进程崩溃或更换时停止对应检查并单独记录，不能写成目标 SDK 或手机失败。不重复未改配置的等待，不增加手机实验。
4. 构建命令见[研究说明](research/container-api33/README.md)，许可见[记录](research/container-api33/THIRD_PARTY_NOTICES.md)。使用仓库外 `CONTAINER_RESEARCH_WORKDIR`、`tools/run-build.sh`；保留代理／CA，SDK 安装串行，生成物不入库。
