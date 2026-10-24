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

[官方 Android 12L／API32 ARM64 实测](research/container-api33/evidence/google-api32-full-arm64-software-check.json)也未通过：全新 userdata 下，`max` 和 SDK 标准 `cortex-a53` CPU 均出现 keystore2 原生崩溃循环，ADB 尚未就绪。前一例依据真实控制台故障人工提前停止，后一例由新增控制台检查自动判失败；不能混写成模拟器自行退出或启动耗时为零。原包和普通入口均未执行。当前没有仍在运行的 VM 或待结束的应用检查。

用户再次要求继续后，已核对[官方 Cuttlefish 的另一条 QEMU 路径](research/container-api33/evidence/cuttlefish-arm64-feasibility.json)，并取得[原生 ARM Linux 宿主实际检查结果](research/container-api33/evidence/cuttlefish-arm-linux-host-result.json)：QEMU 8.2.2、vhost-vsock、局部 vsock 绑定和 tun 均可用，115GB 可用空间、16GB 内存；没有 KVM，后续仍须明确选择软件运行。此结果只是宿主前提，不是 Android 启动。当前核对匹配的 Android13 镜像／工具，尚无 Cuttlefish Guest 或目标应用结果，不增加手机实验。

已下载并核对同一官方 Android13 构建 `16596636` 的镜像、原生 ARM64 宿主工具与 manifest，[输入摘要](research/container-api33/cuttlefish-inputs.json)是实际下载后本地计算，非官方独立校验和。新增同一工作流的可选 `[cuttlefish-boot]` 系统启动检查：替换冲突的 KVM 选择为明确 TCG，保持原始镜像与二进制，使用独立路径、真实构建／架构／普通权限／有效进程参数核验。未配置 Guest DHCP／NAT，没有联网或应用结果；新启动待实际执行，不能复用上一轮宿主通过作为 Android 通过。

[首次云端启动任务](research/container-api33/evidence/cuttlefish-first-download-failure.json)在完成宿主包下载后，镜像的官方公共页面返回 HTTP503，VM 尚未准备或启动。这是下载阶段失败，不是 Android 崩溃；已增加仅针对临时服务错误的三次内重试，403／404 等仍直接停止，不改变凭据、路由或 TLS。

[下一轮下载全部通过](research/container-api33/evidence/cuttlefish-host-data-failure.json)，原生 ARM64 二进制及专用 TAP 已核对，随后官方工具在组装阶段因未设置 `ANDROID_DATA` 中止，VM 仍未启动。已为宿主 Bionic 工具补充独立运行缓存目录，未覆盖 HOME；原始输入归档保持固定，官方组装生成运行磁盘及后续 userdata 变化另行注明，不冒充输入镜像字节始终不变。

该目录修正后，[下一项真实故障](research/container-api33/evidence/cuttlefish-host-timezone-failure.json)为同一原生工具缺少 `ANDROID_TZDATA_ROOT`，仍在 VM 前。已按实际 manifest 对应的 Bionic tzcode 源码一次补齐全部三个根目录，使用宿主包自带的真实时区数据库，并统一传给组装、runner 和所有 ADB 子进程；新增 avbtool／ADB 预检，尚待新 CI 实测。

同时修正了虚拟模式下三个基站 getter 的明确缺口：缺少 BCell 时不再查询真实基站，内部 BCell 列表不再直接当作框架对象返回；关闭模拟时沿原查询路径。[新增两项受控 Android13 代理回归已在云端通过](research/container-api33/evidence/radio-privacy-contract-results.json)，测试使用受控基站服务计数，未读取真实基站。它不证明 Guest Hook 安装或第三方采用，也不能认定微信失败根因或无线兼容。见[审计范围](research/container-api33/AUDIT.md)。

`qualify-arm64-software-boot.py` 要求明确选择 API32／33、完整启动、ARM64 内核／主 ABI、无 x86 ABI、Native Bridge 为 0、UID 2000、SELinux Enforcing。除 ADB 的 system_server 取证外，新增可选控制台检查，在 ADB 未启动时也识别两个不同 system_server／keystore2 原生崩溃；重复同一 PID、等待服务的文字不会误算。实际 API32 故障和解析边界已验证。原包驱动的 full-arm64 与转译模式仍各自复核环境；定位关闭，停在欢迎／隐私页，不接受条款或登录，没有目标运行结果。

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

[最近完成的 CI](research/container-api33/evidence/default-ci-state.json) `40082724586`，实测提交 `d1439c0`：Linux 构建／审计、普通入口 10.388 秒、计算器 5.841 秒、新增两项受控基站代理回归及 ARM Mac 构建成功。已下载证据 ZIP 并核对摘要、实测提交和 32 项研究源码摘要。ARM Linux 宿主前提来自上一提交 `dbb640a` 的独立记录，本次该检查跳过；两者都不是 ARM64 Android Guest 运行。已失败的转译与 Mac 运行检查本次跳过，**跳过不是目标兼容通过**。

用户给出的 VPN 开／关日志中，复制时 GPS／NETWORK／GMS 缓存都接近模拟目标，但微信截图及用户反馈仍失败。导出时快照不是微信前台期间的连续采样，10 月 13 日旧实验不是本轮结果。脱敏验收在 #59 顶部，实际地址截图没有上传公共仓库。

## 接手操作

1. 使用本分支与 #60，日常候选仍留 #59；不要从旧 main／APK 覆盖进度。
2. 固定源码与补丁、构建、复现命令见 [研究说明](research/container-api33/README.md)；另见 [API 审计](research/container-api33/AUDIT.md)及[许可记录](research/container-api33/THIRD_PARTY_NOTICES.md)。
3. 仓库外构建目录使用 `CONTAINER_RESEARCH_WORKDIR` 与 `tools/run-build.sh`，保留代理／CA，SDK 安装串行，生成物不入库。
4. 保留实际失败和证据范围；未经原包启动、容器与具体业务验证，不发布“微信已成功”的手机包，不增加用户操作步骤。
