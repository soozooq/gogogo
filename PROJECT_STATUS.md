# 天外天接手入口

更新时间：2026-10-24。本文件是唯一接手入口；旧日志、旧分支和实验数量不能替代当前结果。

## 目标与设备约束

用户只有一部 Android 13／vivo 手机，没有 Root、没有电脑。目标是微信发送位置／实时共享、抖音不依赖以太网的定位及海外地点功能。**这些目标均未完成；微信两个入口已有用户失败反馈。** 不让用户重复旧实验，不把构建或缓存成功当作微信采用位置。

## 唯一继续研发分支

| 角色 | 分支与 PR | 状态 |
| --- | --- | --- |
| 继续研发 | **`research/container-api33-20261023` · [Draft #60](https://github.com/soozooq/gogogo/pull/60)** | 本分支 HEAD 是后续研发主线，继续提交这里 |
| 日常候选 | `fix/location-input-validation` · [Draft #59](https://github.com/soozooq/gogogo/pull/59) | 固定 `2620b741dd6a971143577737c64ebc4496120d77`，`2.4.31-simple-support / 2400099`；本轮未改 |

`main` 仍是旧基线 `0762f60`，不是研发进度。保留其他工作区及其未提交内容，不删除分支、强推、重置他人工作区或自动合并。研究模块没有接进日常应用，没有新研究手机包需要安装。

## 当前阻塞与下一步

目标原包只有 ARM64 原生库。目前还没有得到可正常运行它们的 ARM64 Android 环境，目标容器启动／定位业务尚未执行。云端环境没有连接用户手机。

- [API30](research/container-api33/evidence/official-target-baseline-limit.json)／[API32 官方转译对照](research/container-api33/evidence/official-target-api32-baseline-limit.json)：微信、抖音原包普通安装成功后，均在容器外原生崩溃。微信调用栈涉及 libowl／Native Bridge，精确原因未定；抖音报告 ubidi_openSized_66／68。不是容器或真机失败证明。
- [API33 AOSP 整机 ARM64 软件核查](research/container-api33/evidence/full-arm64-software-linux-check.json)：官方 Linux SDK 实际有 ARM64 TCG 后端，切换标准机器参数后进入 ARM64 内核及系统服务；两种 CPU 配置均发生 ART／system_server 原生崩溃，完整启动未通过。
- [ARM Mac 新核查](research/container-api33/evidence/mac-arm64-tcg-selection-failure.json)：修正探测库路径后，二进制确实列出 TCG，但最终 `accel=tcg` 没有取消此前启用的 HVF，仍报 `HV_UNSUPPORTED`。不能据此认定 TCG 没实现；不重复相同参数。
- [较新 Google API33 ARM64 rev17](research/container-api33/evidence/google-api33-full-arm64-software-check.json)：两核及单核／单 TCG 线程均记录到两个不同 system_server 的 ART 崩溃，完整启动未通过，没有安装目标原包。单核沿用失败启动后的自有 userdata，基础系统／内核／SDK 二进制未改；不能称作另一份全新 userdata 证明。单核配置没有解决该故障，精确根因仍未知。

当前仅继续一个改变系统版本的候选：官方 Android 12L／API32 ARM64 镜像。它必须先完整启动并核验身份，再跑普通 GPSTest 入口与计算器，随后做原版微信／抖音普通安装及容器对照。即使通过，也只能记为 API32 整机环境，不是 Android 13／vivo 成功。不重复已失败的相同镜像和参数，不增加手机实验或隐藏失败。

`qualify-arm64-software-boot.py` 要求明确选择 API32／33、完整启动、ARM64 内核／主 ABI、无 x86 ABI、Native Bridge 为 0、UID 2000、SELinux Enforcing；两个不同 system_server 原生崩溃即停止。原包驱动新增 `--runtime full-arm64` 并显式选择 API；Python 与设备端均复核身份。原有转译模式仍只接受 API30／32。原包检查仍先普通启动，再容器，定位关闭，停在欢迎／隐私页，不接受条款或登录；没有目标运行结果。

## 已证实的工程进度

| 实际证据 | 已覆盖的范围 |
| --- | --- |
| [容器运行记录](research/container-api33/evidence/results.json) | Android 13 x86_64，原版计算器在系统未安装时运行，真实界面完成 1+2=3，回桌面重开保留结果 |
| [回调与原签名修正](research/container-api33/evidence/api33-location-signature-results.json) | 单次回调／取消、持续批量监听／注销、次数／时长、单调时间、Parcel 字段；原 APK 证书查询，解析失败中止导入 |
| [独立自有 Guest](research/container-api33/evidence/owned-guest-location-results.json) | 真实 Guest 通过普通 LocationManager 调用，GPS／NETWORK 六条路径跟随 A→B，停止后计数稳定，取消后零回调 |
| [官方原版 GPSTest](research/container-api33/evidence/official-gpstest-location-results.json) | 正常精确权限授权，实际界面采用 A→B；API31+ GNSS 修正旧隐藏类崩溃，只报告零卫星，无 first-fix／伪造 NMEA |
| [普通入口回归](research/container-api33/evidence/cloud-ordinary-entry-pass.json) | 正常系统文件选择、导入、精确授权、同一 Guest 进程更新坐标及停止；全新 GitHub KVM 10.563 秒，计算器 5.778 秒，绑定实测源码与 APK |

研究宿主已统一为“选择应用 → 导入原版 APK 一次 → 填坐标并打开”，再次应用更新目标，提供停止，保存导入状态与坐标。仅准入固定计算器与 GPSTest，核对大小、SHA-256、包名、版本及原证书；微信／抖音没有加入普通入口，也未改签或重新分发。

上述应用运行证据仍在 x86_64；ARM64 目前完成构建，尚无 ARM64／vivo Guest 运行通过记录。自有消费者、GPSTest、GMS、腾讯／字节 SDK 和目标软件业务不能混为一项。样本保留 mock 标记；Guest 与宿主共享 UID，不是独立 UID 安全沙箱。

## 验证状态与用户失败证据

[最新默认 CI](research/container-api33/evidence/default-ci-state.json) `40060063936`，实测提交 `b39a9b4`：Linux 构建／审计／普通入口与计算器回归成功，ARM Mac 构建成功；已失败的转译预检和 Mac 运行核查默认关闭，本次跳过，**跳过不是目标兼容通过**。新增 ARM64 模式另有本地编译证据，不把旧 CI 重标成新模式运行。

用户给出的 VPN 开／关日志中，复制时 GPS／NETWORK／GMS 缓存都接近模拟目标，但微信截图及用户反馈仍失败。导出时快照不是微信前台期间的连续采样，10 月 13 日旧实验不是本轮结果。脱敏验收在 #59 顶部，实际地址截图没有上传公共仓库。

## 接手操作

1. 使用本分支与 #60，日常候选仍留 #59；不要从旧 main／APK 覆盖进度。
2. 固定源码与补丁、构建、复现命令见 [研究说明](research/container-api33/README.md)；另见 [API 审计](research/container-api33/AUDIT.md)及[许可记录](research/container-api33/THIRD_PARTY_NOTICES.md)。
3. 仓库外构建目录使用 `CONTAINER_RESEARCH_WORKDIR` 与 `tools/run-build.sh`，保留代理／CA，SDK 安装串行，生成物不入库。
4. 保留实际失败和证据范围；未经原包启动、容器与具体业务验证，不发布“微信已成功”的手机包，不增加用户操作步骤。
