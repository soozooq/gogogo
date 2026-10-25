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

## 当前卡点：微信框架兼容与抖音 Guest JIT 崩溃

最新 [原生 ARM64 实测 `40174550024 / ff1a21ee`](research/container-api33/evidence/cuttlefish-verified-signers-and-startup-crashes.json)通过完整系统资格：API33／固定构建 `16596636`、ARM64 内核及主 ABI、Native Bridge 0、UID2000、SELinux Enforcing、超时倍数50、实际4 vCPU。八项报告源码及ZIP核对，`system_server` PID653全程未改变；两款普通原包、自有包均清理，VM已停止。

**两款普通原包界面均自动通过：微信欢迎页、抖音隐私页；两款容器导入、PackageInfo／SigningInfo 与原始证书也通过。** 这实际确认了上一轮免空闲读取与内部签名标志修正。随后两款 Guest 都启动了代码，但在进入预期界面前失败，位置业务没有测试，未接受条款或登录。

微信 Guest 记录了三个独立启动错误：Wi-Fi P2P 缺 `ACCESS_WIFI_STATE`、旧版 `DefaultHttpClient` 类缺失，以及推送服务创建 PendingIntent 时将虚拟包名送到了系统，真实 UID 不匹配。候选修改一起处理：按固定源码补齐两项普通 Wi-Fi 权限；声明平台旧 HTTP 库并保留真实 `SharedLibraryInfo`，绑定时请求共享库元数据；覆盖现代 `getIntentSenderWithFeature`，对系统使用真实宿主归属；Context 的系统 AttributionSource 同样使用真实宿主 UID。没有改签目标包、伪造证书、清除 mock 标记或增加手机实验入口。

抖音 Guest 的 JIT 线程在 ART `FindOatMethodFor → GetOatQuickMethodHeader → StackVisitor → JitCodeCache` 路径 SIGSEGV，系统进程仍健康。**尚不能归因为某个 Hook 组件或用户 vivo。** 现有普通对照显示隐私页即结束，时间范围不同；候选对普通对照和已显示界面的容器各加入300秒持续观察，记录目标进程与异常。没有修改全局 JIT、ART属性或看门狗；Xposed 配置默认关闭，不能凭原生栈猜测 LSPlant 原因。

[新增自有 Guest 的实际回执](research/container-api33/evidence/framework-startup-prerequisite-results.json)（CI33／`073c25db`）已通过：旧 HTTP 类、两类 PendingIntent 创建、真实宿主 AttributionSource UID；宿主UID10129、Guest Process逻辑UID10001、两类Context归属均10129。CI32错误UID断言失败及原生跳过单独保留，没有把运行时代码改回虚拟系统归属。PendingIntent发送／后台投递仍未验证。**CI33的原生目标对照正在运行，最新已完成原生仍为CI31。**

本次[新候选](research/container-api33/evidence/critical-native-and-async-preparation.json)处理另一个确定的源码约束：固定 Android13 的 `Binder.getCallingUid` 是 `CriticalNative`，原替换却使用普通 JNI 签名、回调 Java 并查询虚拟 Binder 服务。API33+保留系统原始方法及真实调用者身份，逻辑身份仍在内部模型；没有改ART/JIT或升级LSPlant。新增自有Binder UID/PID及回执新鲜度检查，**尚待实际运行，不能认定修复抖音崩溃**。另外补齐两种异步基站请求：虚拟模式以空CellInfo列表完成真实回调，关闭模式正常转发。两项新检查经过TelephonyManager包装、Binder回调和调用者Executor，但使用受控服务，不能冒充真实无线或第三方采用；总计7项待新CI。13秒构建、15项驱动检查、APK静态核对及补丁重放通过。两次本地软件模拟在宿主初始化前提失败、0项方法执行，原始失败保留且VM均停止；不重复相同环境。云端基础检查可并行，原生作业共用原来的锁、排队且不取消运行中的对照。

两份自有 APK 各保留11份许可／NOTICE／来源／相容库替换材料，仍不是完整逐文件许可审计或手机发行。

维护者可用[单命令回执核对](research/container-api33/README.md)复核历史归档：从固定 Git 提交取源码，保留真实失败，拒绝摘要／提交／清单错配和不安全路径，缺少原生回执不计通过。接手沿本分支继续，日常#59与手机版本保持原样。

固定 [Cuttlefish 输入](research/container-api33/cuttlefish-inputs.json)仍为同一官方宿主、镜像和manifest，没有 DHCP／NAT、互联网或无线定位业务证明。[两核平台终结崩溃](research/container-api33/evidence/cuttlefish-staged-install-and-runtime-limit.json)及[上一轮签名空值](research/container-api33/evidence/cuttlefish-original-ui-and-package-identity-limit.json)保留。旧[API30](research/container-api33/evidence/official-target-baseline-limit.json)／[API32 转译](research/container-api33/evidence/official-target-api32-baseline-limit.json)、[AOSP ARM64 SDK](research/container-api33/evidence/full-arm64-software-linux-check.json)、[Google API33](research/container-api33/evidence/google-api33-full-arm64-software-check.json)、[Google API32](research/container-api33/evidence/google-api32-full-arm64-software-check.json)及[ARM Mac](research/container-api33/evidence/mac-arm64-tcg-selection-failure.json)失败不替代本轮结果，也不是 vivo 失败。

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

[最近完整核对的默认 CI](research/container-api33/evidence/default-ci-state.json)为 `40197335425 / 073c25db`：Linux／Mac基础作业成功；自有框架0.601秒、计算器6.007秒、普通入口12.216秒、五项代理0.055秒，每项55份源码；变化界面25毫秒、57份源码。主程序447项Debug单元检查及lint零Error/Fatal；自有构建对50份源码、两份签名与每包11份材料核对。原生作业尚在运行，旧转译跳过；跳过与待运行均不算通过。新Binder身份及异步回调两项不在此提交内，必须等新CI，不能沿用CI33旧回执。

用户 10 月 23 日 VPN 开／关日志中，导出时 GPS／NETWORK／GMS 缓存新鲜且接近模拟目标，但微信截图及反馈失败。导出快照不是微信前台连续采样；10 月 13 日旧实验不是本轮结果。#59 顶部保留脱敏失败验收及日常版本产物验证，实际地址截图没有上传公共仓库。更新 #59 时只改标记的后续研发段，保留此前正文。

## 继续操作

1. 先运行新增自有 Guest 的框架启动检查，再保留计算器、普通入口、无线代理和只读界面回归；基础项通过后再进入原生 ARM64 原包／容器检查。不发布手机更新或增加用户实验。
2. 原生结果按普通界面、原包身份、启动、持续观察分别核对。抖音晚发异常尚未确定原因，先做同样时长对照，不更改全局JIT／镜像／mock标记。
3. 仍沿唯一分支／#60提交，禁止取消运行中原生作业。新队列允许基础作业先验证、原生作业共用同一锁依次运行；#59源码固定。记录失败及目标业务缺口；不接受条款、登录或发送消息。
4. 使用仓库外 `CONTAINER_RESEARCH_WORKDIR` 与 `tools/run-build.sh`，保留代理／CA，SDK安装串行，生成物不入库。归档核对命令见[研究说明](research/container-api33/README.md)，许可见[记录](research/container-api33/THIRD_PARTY_NOTICES.md)。
