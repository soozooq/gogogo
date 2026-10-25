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

## 当前卡点：容器包身份查询与界面读取

最新 [ARM 实测 `40149216340 / 7a2eaf5`](research/container-api33/evidence/cuttlefish-original-ui-and-package-identity-limit.json)通过完整系统资格：API33／固定构建 `16596636`、ARM64 内核及主 ABI、Native Bridge 0、UID2000、SELinux Enforcing、超时倍数50、实际4 vCPU及存活 QEMU 身份。ZIP及八项报告源码均核对。全程记录的 `system_server` PID651未改变，两款原包及自有包清理成功、VM已停止；没有重现上一轮终结超时，仍不能证明4核是因果修复。

两款原包均普通安装成功，提交耗时391.443／480.764秒。**微信截图实际显示 Log In／Sign Up 欢迎页，但旧 `uiautomator dump` 报“could not get idle state”，原始自动判定仍为 false，微信容器因此跳过。** 抖音读取到真实隐私页且没有接受条款，普通对照通过。

**抖音首次进入实际 ARM64 容器导入并成功，随后 PackageInfo／SigningInfo 的空值断言失败，尚未执行抖音启动。** 原始同一行断言无法区分两个空值；不能称为抖音自身崩溃或模拟位置拒绝。固定镜像的框架源码显示，归档接口只有传 `GET_SIGNATURES` 才收集、验证证书，而 `GET_SIGNING_CERTIFICATES` 单独请求会留下空 SigningInfo。候选补丁内部同时请求两个标志，仍返回原包真实证书、只暴露调用者请求的字段；另记录异常类型和各阶段结果。修正后的 ARM64 结果尚未取得。

候选界面读取改用当前 Accessibility 节点，跳过空闲等待。只启动自有测试 APK 的普通 Application，不启动容器 Application、点击条款或登录；核对真实目标窗口、文字及回执，清理两份自有包后再进入对应容器。默认没有自有构建对时仍保留旧 CLI 路径。15秒构建及14项受控驱动／材料检查通过。[首次本地变化界面检查未通过](research/container-api33/evidence/nonidle-ui-and-signature-preparation.json)：启动 instrumentation 会停止同一个被测进程，原先的自有界面因此不适合作对照。现将变化界面放在独立自有消费者内，加入默认云端自动检查；修正后的实际结果待取得。自有界面通过也不能替代微信／抖音对照。

自有宿主／测试对仅在明确选择时通过同一 CI 传递，固定提交／源码／大小／SHA。两份 APK 各保留11份许可／NOTICE／来源／相容库替换材料，字节逐一匹配，两个 ABI 实际动态链接 LSPlant；这是编译与材料范围，不是完整逐文件许可审计或手机发行。原包单独下载核验，不进入产物。

固定 [Cuttlefish 输入](research/container-api33/cuttlefish-inputs.json)为同一官方构建的宿主、镜像及 manifest；摘要为本地计算，非官方独立校验和。没有 DHCP／NAT、互联网或无线业务证明。不改镜像、ART属性或终结看门狗。之前[安装超时](research/container-api33/evidence/cuttlefish-qualified-boot-install-timeout.json)及[两核平台终结崩溃](research/container-api33/evidence/cuttlefish-staged-install-and-runtime-limit.json)均保留。

不重复相同旧环境：[API30](research/container-api33/evidence/official-target-baseline-limit.json)／[API32 转译](research/container-api33/evidence/official-target-api32-baseline-limit.json)原包在容器外原生崩溃；[AOSP ARM64 SDK](research/container-api33/evidence/full-arm64-software-linux-check.json)和[Google API33](research/container-api33/evidence/google-api33-full-arm64-software-check.json)有 ART／system_server 崩溃；[Google API32](research/container-api33/evidence/google-api32-full-arm64-software-check.json)有 keystore2 崩溃；[ARM Mac](research/container-api33/evidence/mac-arm64-tcg-selection-failure.json)仍错误启用 HVF。这些不是 vivo 失败，也不代表所有 ARM64 路径不可用。

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

[最近完整核对的默认 CI](research/container-api33/evidence/default-ci-state.json)为 `40149216340 / 7a2eaf5`：Linux／Mac 默认作业成功，五项代理0.058秒、普通入口12.625秒、计算器6.157秒，各轮48项源码及两份 ZIP均核对。主程序447项 Debug单元检查、四项源码及 lint零Error/Fatal也核对。自有构建对单独核对46项源码、两份签名及许可材料。整轮因原始微信文字判定和抖音容器身份查询而失败；旧转译作业跳过，**跳过不是通过**。

用户 10 月 23 日 VPN 开／关日志中，导出时 GPS／NETWORK／GMS 缓存新鲜且接近模拟目标，但微信截图及反馈失败。导出快照不是微信前台连续采样；10 月 13 日旧实验不是本轮结果。#59 顶部保留脱敏失败验收及日常版本产物验证，实际地址截图没有上传公共仓库。更新 #59 时只改标记的后续研发段，保留此前正文。

## 继续操作

1. 默认 CI 先检查独立自有变化界面的免空闲读取及新签名查询的计算器回归；保留失败、源码与产物摘要。仍沿当前分支／#60提交，不发布手机更新。
2. 云端重新核对本次系统；每个原包普通对照通过且自有包清理后，立即检查对应容器，再处理另一款。修正结果必须实际读回，欢迎／隐私页不等于定位业务。运行中不推送代码取消任务。
3. 平台进程崩溃或更换时停止对应检查并单独记录。不接受条款、登录、发送消息或隐藏 mock 标记，不增加手机实验。
4. 构建命令见[研究说明](research/container-api33/README.md)，许可见[记录](research/container-api33/THIRD_PARTY_NOTICES.md)。使用仓库外 `CONTAINER_RESEARCH_WORKDIR`、`tools/run-build.sh`；保留代理／CA，SDK安装串行，生成物不入库。
