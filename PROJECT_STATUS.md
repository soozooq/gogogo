# 天外天接手入口

更新时间：2026-10-24。此文件定义当前进度；旧 main、旧日志和实验数量不能替代实际结果。

## 目标与唯一研发分支

用户只有一部 Android13／vivo 手机，**没有 Root、没有电脑**。微信发送位置／实时共享已有用户失败反馈；抖音不依赖以太网及海外定位也**尚未完成**。不让用户重复旧实验，不把缓存或自有测试通过写成第三方采用。

| 角色 | 分支与 PR | 约束 |
| --- | --- | --- |
| 继续研发 | **`research/container-api33-20261023` · [Draft #60](https://github.com/soozooq/gogogo/pull/60)** | 本分支 HEAD 是唯一研发主线，继续提交这里 |
| 日常候选 | `fix/location-input-validation` · [Draft #59](https://github.com/soozooq/gogogo/pull/59) | 固定 `2620b741dd6a971143577737c64ebc4496120d77`，`2.4.31-simple-support / 2400099`；本轮未改 |

`main` 的 `0762f60` 是旧基线。保留其他工作区及未提交内容，不新增分支／PR、删除分支、强推、重置他人工作区或自动合并。研究模块未接进日常程序，没有研究手机包待安装。

## 当前卡点：原包安装与启动

原版微信／抖音输入都只有 ARM64 库。**现已取得完整启动、无转译的原生 ARM64 Android13 云端环境，但两款原包尚未成功安装／启动，容器与定位业务未执行。** 云端没有连接用户手机。

当前走固定官方 Android13 Cuttlefish 构建 `16596636`，同一构建的原生 ARM64 宿主工具、镜像与 manifest 已下载核对。[输入摘要](research/container-api33/cuttlefish-inputs.json)为本地计算，非官方独立校验和。宿主没有 KVM，使用明确 TCG；实际组成员、Bionic 三个根目录、发行版网卡 ROM、专用 TAP IPv4／广播、静音音频后端及 QEMU 存活检查均已处理。没有配置 DHCP／NAT，也没有互联网或无线业务证明。

最新完成的 [ARM 实测 `40107227047 / 8311ed0`](research/container-api33/evidence/cuttlefish-qualified-boot-install-timeout.json)通过完整系统资格：API33／构建 `16596636`、ARM64 内核及主 ABI、Native Bridge 0、普通 UID2000、SELinux Enforcing、实际超时倍数50，以及存活 QEMU 的二进制／完整参数均符合。ZIP 与六项源码摘要已核对。随后两款固定原包的 `adb install` 都在180秒超时，未启动应用；卸载返回失败，安装会话未记录，不能声称包清理成功。本轮 VM 最终已停止。

先前 [QMP 固件卡点](research/container-api33/evidence/cuttlefish-qmp-bootloader-stall.json)为五组完全相同的寄存器／`PC=0xff86da00`。旧固件有符号 TCR 位与 LPA2 冲突的分析已由[最小原固件对照](research/container-api33/evidence/uboot-lpa2-firmware-control.json)支持：同一 QEMU／原固件只关闭 LPA2 后继续执行，默认模式精确复现全部原生 CI 寄存器。另实际读取到物理 PC 的 32 字节，与原固件同步异常入口逐字节相同，虚拟读取失败。原生 ARM 上 `max,lpa2=off` 也已实际进入内核。此修复没有修改原始固件、镜像或宿主二进制。

官方软件运行超时配置已实测生效，并解决前轮[网络栈 ANR／system_server Java 异常导致的启动失败](research/container-api33/evidence/cuttlefish-post-firmware-anr.json)。固定上游只在跨架构时自动选择倍数50，本次同架构 TCG 需显式请求；实际属性已核对，不是取消看门狗或安全检查。启动仍限定2400秒，明确 Java 系统进程致命异常／内核 panic 提前停止。QMP 已实际读取数值 PC 的原始字。

当前将原包安装改为“普通用户存储已解锁 → 独立传输 → 远端 SHA256 → 自有 PackageInstaller 会话写入／提交”。每阶段记录耗时及超时前输出，失败仅放弃本次已记录会话及临时目录，保留已有应用；新流程待实际云端验证。它仍先验证本次系统、只读欢迎／隐私页，不接受条款或登录。

另已准备容器匿名启动的后续自动衔接，尚未执行：通过同一 CI 的提交／源码／大小／SHA 绑定自有宿主与测试 APK，原包继续从官方源单独核验，不进入传输产物。CVD 模式独立核对固定产品、构建及安全状态，SDK 入口原限制保留。当前仅本地编译通过及10项受控驱动检查通过；没有容器 ARM64 运行通过或手机更新包。只有对应原包普通对照成功，才允许该目标进入容器检查。

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

[最近完整核对的默认 CI](research/container-api33/evidence/default-ci-state.json)为 `40107227047 / 8311ed0`：Linux／Mac 默认作业成功，五项代理0.054秒、普通入口12.142秒、计算器5.843秒，ZIP、提交及每轮36项源码均核对。整轮仍因原包安装超时失败，不能写成整体成功。ARM 系统资格与原包启动结果分开保存；旧转译作业跳过，**跳过不是通过**。

用户 10 月 23 日 VPN 开／关日志中，导出时 GPS／NETWORK／GMS 缓存新鲜且接近模拟目标，但微信截图及反馈失败。导出快照不是微信前台连续采样；10 月 13 日旧实验不是本轮结果。#59 顶部保留脱敏失败验收及日常版本产物验证，实际地址截图没有上传公共仓库。更新 #59 时只改标记的后续研发段，保留此前正文。

## 继续操作

1. 本轮完整系统资格已通过，继续定位原包传输／安装阶段；新一轮仍须核对系统身份。保留每个阶段原始回执，不重复未改配置的整轮检查。
2. 原包普通对照成功后才进入对应目标的容器匿名启动；欢迎／隐私页不等于位置业务成功。不接受条款、登录或发送消息。运行中不要推送代码取消任务。
3. 若系统失败，按实际内核／QMP 证据定位下一处，不盲调 CPU／重复完整等待，不增加手机实验。保留失败、受控测试和目标业务之间的区别。
4. 构建命令见[研究说明](research/container-api33/README.md)，许可见[记录](research/container-api33/THIRD_PARTY_NOTICES.md)。使用仓库外 `CONTAINER_RESEARCH_WORKDIR`、`tools/run-build.sh`；保留代理／CA，SDK 安装串行，生成物不入库。
