# 天外天接手入口

更新时间：2026-10-24。此文件定义当前进度；旧 main、旧日志和实验数量不能替代实际结果。

## 目标与唯一研发分支

用户只有一部 Android13／vivo 手机，**没有 Root、没有电脑**。微信发送位置／实时共享已有用户失败反馈；抖音不依赖以太网及海外定位也**尚未完成**。不让用户重复旧实验，不把缓存或自有测试通过写成第三方采用。

| 角色 | 分支与 PR | 约束 |
| --- | --- | --- |
| 继续研发 | **`research/container-api33-20261023` · [Draft #60](https://github.com/soozooq/gogogo/pull/60)** | 本分支 HEAD 是唯一研发主线，继续提交这里 |
| 日常候选 | `fix/location-input-validation` · [Draft #59](https://github.com/soozooq/gogogo/pull/59) | 固定 `2620b741dd6a971143577737c64ebc4496120d77`，`2.4.31-simple-support / 2400099`；本轮未改 |

`main` 的 `0762f60` 是旧基线。保留其他工作区及未提交内容，不新增分支／PR、删除分支、强推、重置他人工作区或自动合并。研究模块未接进日常程序，没有研究手机包待安装。

## 当前卡点：原生 ARM64 系统资格

原版微信／抖音输入都只有 ARM64 库。尚未得到可正常运行它们的 ARM64 Android 环境，**目标原包容器启动及定位业务未执行**。云端没有连接用户手机。

当前走固定官方 Android13 Cuttlefish 构建 `16596636`，同一构建的原生 ARM64 宿主工具、镜像与 manifest 已下载核对。[输入摘要](research/container-api33/cuttlefish-inputs.json)为本地计算，非官方独立校验和。宿主没有 KVM，使用明确 TCG；实际组成员、Bionic 三个根目录、发行版网卡 ROM、专用 TAP IPv4／广播、静音音频后端及 QEMU 存活检查均已处理。没有配置 DHCP／NAT，也没有互联网或无线业务证明。

最新完成的 [ARM 实测 `40104761802 / de74ecd`](research/container-api33/evidence/cuttlefish-post-firmware-anr.json)已越过固件卡点，进入 Linux 5.15.206 和 Android 服务，但**完整启动仍未通过**。网络栈服务执行 ANR 后被杀，随后 `system_server` 因 `Lost network stack` 发生 Java 致命异常并触发进程重启；原驱动最终枚举为 BOOT_TIMEOUT，不能改写成 ART 原生崩溃。ZIP、四项源码摘要、实际 CPU 和清理已核对，VM 已停止；原包未安装。

先前 [QMP 固件卡点](research/container-api33/evidence/cuttlefish-qmp-bootloader-stall.json)为五组完全相同的寄存器／`PC=0xff86da00`。旧固件有符号 TCR 位与 LPA2 冲突的分析已由[最小原固件对照](research/container-api33/evidence/uboot-lpa2-firmware-control.json)支持：同一 QEMU／原固件只关闭 LPA2 后继续执行，默认模式精确复现全部原生 CI 寄存器。另实际读取到物理 PC 的 32 字节，与原固件同步异常入口逐字节相同，虚拟读取失败。原生 ARM 上 `max,lpa2=off` 也已实际进入内核。此修复没有修改原始固件、镜像或宿主二进制。

当前新增官方软件运行超时配置：固定上游只在跨架构时自动设置 `hw_timeout_multiplier=50`，本次同架构 TCG 选择较晚而遗漏。现在通过受支持 bootconfig 请求相同值，并在系统资格里核对实际 `ro.hw_timeout_multiplier=50`；保留 Enforcing、UID2000、ABI 和完整启动检查。按已测冷启动耗时将等待限定为 2400 秒，明确系统进程 Java 致命异常或内核 panic 会提前停止；新配置待实际验证。QMP 改读数值 PC 的原始指令字，避免实际后端不支持 `$pc`／反汇编的问题。

同一驱动已接好可选原包普通安装对照：系统完全合格后才下载固定原包，复核当前身份和同一存活 QEMU，依次安装、只读欢迎／隐私页及清理；无需研究宿主 APK 或账号。七项本地受控检查包含八种 ADB 情况、旧报告／错误连接拒绝、已有应用保留、安装超时清理、界面归属及 QMP 协议；**这些是驱动逻辑检查，原包尚未执行**。

不重复相同旧环境：[API30](research/container-api33/evidence/official-target-baseline-limit.json)／[API32 转译](research/container-api33/evidence/official-target-api32-baseline-limit.json)原包在容器外原生崩溃；[AOSP ARM64 SDK](research/container-api33/evidence/full-arm64-software-linux-check.json)和[Google API33](research/container-api33/evidence/google-api33-full-arm64-software-check.json)有 ART／system_server 崩溃；[Google API32](research/container-api33/evidence/google-api32-full-arm64-software-check.json)有 keystore2 崩溃；[ARM Mac](research/container-api33/evidence/mac-arm64-tcg-selection-failure.json)仍错误启用 HVF。不能把这些扩大为所有 ARM64 路径不可能，也不能当作 vivo 失败。

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

[最近完整核对的默认 CI](research/container-api33/evidence/default-ci-state.json)为 `40102349634 / 24584ae`：整体成功，五项代理 0.055 秒、普通入口 12 秒、计算器 5.543 秒及 ARM Mac 构建；ZIP、提交及每轮 35 项源码摘要均核对。ARM 启动与旧转译本次跳过，**跳过不是通过**。后续 `40103018384`／`40104761802` 都因可选 ARM 启动失败而整体失败；后一轮 Linux／Mac 默认作业仍成功，但不冒充完整系统通过。

用户 10 月 23 日 VPN 开／关日志中，导出时 GPS／NETWORK／GMS 缓存新鲜且接近模拟目标，但微信截图及反馈失败。导出快照不是微信前台连续采样；10 月 13 日旧实验不是本轮结果。#59 顶部保留脱敏失败验收及日常版本产物验证，实际地址截图没有上传公共仓库。更新 #59 时只改标记的后续研发段，保留此前正文。

## 继续操作

1. 沿本分支核对最新 ARM 作业实际结果，下载证据并核对 ZIP、提交、源码与真实 QEMU 参数；不要在它结束前推送源文件，同一工作流会取消旧任务。完整启动仍须验证 API33／构建、ARM64／无转译、UID2000、Enforcing 及软件超时配置。
2. 若系统合格，再在同一驱动启用已准备的原包普通安装对照。只有原包对照通过后才进入容器匿名启动；欢迎／隐私页不等于位置业务成功。不接受条款、登录或发送消息。
3. 若系统失败，按实际内核／QMP 证据定位下一处，不盲调 CPU／重复完整等待，不增加手机实验。保留失败、受控测试和目标业务之间的区别。
4. 构建命令见[研究说明](research/container-api33/README.md)，许可见[记录](research/container-api33/THIRD_PARTY_NOTICES.md)。使用仓库外 `CONTAINER_RESEARCH_WORKDIR`、`tools/run-build.sh`；保留代理／CA，SDK 安装串行，生成物不入库。
