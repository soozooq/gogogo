# 可复用基础审计

本轮结论：API 33 云端中，原版计算器与官方 GPSTest 均实际运行；后者在正常精确定位授权后，其可见坐标跟随两次目标。现代定位、GNSS 回调和原签名信息已修正，自有消费者用于相关接口回归。腾讯／字节 SDK、微信／抖音、完整组件及权限隔离仍未完成。接手从根目录 [PROJECT_STATUS.md](../../PROJECT_STATUS.md) 开始。

## 构建与普通应用运行

- BlackBox `df6249da`：固定工具链首次遇到 AIDL 包声明错误，修正后核心 AAR、独立宿主及 Android 测试 APK 构建成功；核心含 ARM64、ARMv7、x86、x86_64，研究宿主仅 ARM64／x86_64。
- ISEKHON/VirtualApp `b3c634ad`：使用同一 JDK 17／NDK 25 环境完成 ARM64、ARMv7、x86_64 及通用 APK 构建；本轮未据此认定该 fork 已适配微信，也未将其代码并入主程序。
- 实际运行使用专用的 AOSP API 33 x86_64 软件模拟器。无硬件加速；app UID 为普通应用 UID，shell UID 为 2000，SELinux Enforcing。ARM64 仅有构建证据。
- 最初运行发现资源镜像缺失、研究宿主遗漏 `REORDER_TASKS`，以及云端 System UI 无响应。镜像和宿主配置已修正；云端恢复后先通过原生安装对照，再验证容器。中途的启动命令、不可见按钮选择错误归类为测试脚本问题，不算作第三方应用不兼容。
- 最终判断仅依据成功记录、APK 摘要和真实 UI；不沿用旧失败轮次的截图或状态。最初失败的完整日志仍在本地研究工作区。

## Android 13 定位接口：已修正与验证边界

依据固定的 [AOSP ILocationManager](https://github.com/aosp-mirror/platform_frameworks_base/blob/d27f9960456403533af0a0d5d9f14cf71c9c8cf6/location/java/android/location/ILocationManager.aidl#L53) 与 [ILocationListener](https://github.com/aosp-mirror/platform_frameworks_base/blob/d27f9960456403533af0a0d5d9f14cf71c9c8cf6/location/java/android/location/ILocationListener.aidl)：

| 检查项 | 本轮 BlackBox 修正 | 受控云端结果／边界 |
| --- | --- | --- |
| 单次定位 | 返回真实框架 `ICancellationSignal`，经 `ILocationCallback.onLocation` 一次交付；无目标返回 null | 公共 API 回调及提前取消通过；不含第三方 Guest Hook 安装 |
| 持续监听 | 拦截现代注册／注销，通过真实 `ILocationListener` 的 `List<Location>`、可空完成回调交付；携带 provider、间隔、次数与时长限制 | 框架监听对象经引擎服务 Binder 收到批量样本；次数、时长、注销及停用释放通过 |
| 数据年龄 | 每个合成样本设置当前单调时钟，保留对应 GPS／NETWORK provider；修正 BLocation Parcel 浮点字段顺序 | 样本年龄合法且连续样本 elapsed 时间增长；不是物理 GNSS 测量 |
| 停止调度 | 用有界延时任务替代每监听器忙循环，移除／超时／次数耗尽／Binder 死亡／关闭模式均释放记录 | 注销、次数、时长、停用通过；Binder 死亡清理代码尚无独立故障注入证据 |
| 样本来源 | 本轮 BlackBox 合成样本明确设置 mock 标志 | API 33 `isMock()` 为 true；不隐藏来源。未采用 VirtualApp 清除标记或固定旧时间的路径 |

[受控测试源码](probe-host/src/androidTest/java/top/niunaijun/bcore/fake/service/LocationContractInstrumentedTest.java)在宿主测试进程构造公共 `LocationManager`，由框架创建真实回调／监听对象；测试代理显式调用四个修正后的 Hook 入口。它不冒充真实 Guest 的定位调用，也不请求系统真实定位。共 4 项检查通过，原文和输入摘要见[本轮结果](evidence/api33-location-signature-results.json)。

计算器运行仍保持虚拟定位关闭；普通入口选择 GPSTest 后才按用户输入启用它的虚拟会话。不能把接口修正直接归因于用户此前系统微信的失败。PendingIntent、被动定位、全部 LocationRequest 过滤条件和 GMS 融合定位仍未完整验证。VirtualApp fork 没有应用这些修正。

## 普通入口与目标架构

普通入口的原包导入、系统授权、坐标更新、重复进入和停止经过真实界面检查，63.652 秒通过；定位控制没有由测试直接替代。清单来自固定来源，导入同时核对文件与归档身份。详见[本轮输入与实际可见文本](evidence/unified-entry-results.json)。后加的云端启动脚本／工作流尚不属于这次本地运行，等待 GitHub 的独立结果。

官方微信 8.0.79 与抖音 40.8.0 已下载并验证原包签名结构、记录 SHA-256，均仅含 ARM64 原生库。它们没有进入准入清单或运行；这也不确认用户手机安装版本。本机官方模拟器因宿主架构拒绝 ARM64 镜像，不能据此判定应用或引擎在 ARM64 上不兼容。GitHub ARM Mac 已完成实际构建／静态核验，镜像则在 HVF 初始化失败，未启动系统；修正后的流程只保留该机器的构建检查。Linux 全新构建／静态核验也通过，运行脚本的 GUI 版本探测缺库已修正，真正的运行仍等待结果。官方 Google 镜像的内置 ARM 转译另行核查，不改变目前没有 ARM64／vivo／微信运行证据的边界。

## 官方 GPSTest 与 GNSS 兼容

固定官方 `barbeau/gpstest` v3.10.6 的 osmdroid release，APK SHA-256 与官方 asset digest 一致，使用原证书，不嵌入宿主。原版代码使用普通 LocationManager、GNSS 回调及其 Service；31.322 秒完成正常精确定位授权和实际坐标 A→B 显示。系统前后均未安装该包，自动化仅在看到 Guest 资源 ID 的实际窗口中读取文本。Android 将可访问节点包名归为宿主，不能据此要求每个节点名都等于 Guest。

第一次失败是真实引擎崩溃：Android 13 已没有旧 GnssStatusListenerTransport，加载旧镜像抛出初始化错误。API31+ 路径改用真实 IGnssStatusListener，延时任务发送 started 和零卫星状态，注销／Binder 死亡／关闭模式释放记录；不声称物理 first-fix，不生成 NMEA、测量、导航电文或天线信息。虚拟模式抑制新物理观测注册，注销仍向系统转发以处理旧注册。旧系统路径未在本轮验证。

新增的官方 Android 11 转译检查只做匿名启动，显式断言目标的虚拟模式为 `CLOSE_MODE`，不能复用 Android 13 的定位通过结论。只读复查发现 API30 及更旧的上游分支仍有合成 NMEA 逻辑；该路径没有修正或验证，后续不得直接在这条云端启动流程中打开模拟定位并宣称 GNSS 兼容。若继续做旧系统定位，需先移除制造卫星／原始观测的路径并单独核对实际框架契约。

自有 Guest 相关回归 24.271 秒通过：真实公共 GnssStatus.Callback 收到 started 和零卫星，first-fix 为零；BLocation 附加卫星数为零；注销后状态计数稳定，六条定位路径及 mock 标记仍通过。同轮受控定位契约 4 项和计算器运行／原签名回归也通过。证据与失败分类见[本轮结果](evidence/official-gpstest-location-results.json)。这只覆盖 GPSTest 的前台流程，未验证通知／后台服务生命周期、GMS 或目标软件业务。

## 真实 Guest 调用链：自有消费者

[消费者源码](location-consumer/README.md)作为独立 APK 构建，没有引擎／宿主库、反射或预期坐标；测试只通过常规 Intent 传递随机关联 nonce。它从 Guest Context 的系统服务获得正常 `LocationManager`，不由测试代理替换接口。宿主控制器设置合成目标并操作消费者的真实界面，核对实际 Android 回调生成的报告。

系统未安装该包时，宿主 PID 2892、Guest PID 2959，系统 `ps` 核验 Guest 使用宿主 UID 10126。引擎会把 argv[0] 改成 Guest 包名，因此不能仅凭名称判定进程归属；这也不提供独立 UID 隔离。

GPS／NETWORK 的缓存、单次回调和持续批量回调均从 A 跟随到 B，同一 Guest 进程不重启。停止后计数稳定，取消单次后回调为零；样本保留 mock 标记、正确 provider 和新鲜单调时间。整轮 18.919 秒通过，见[源码／APK 绑定及 Guest 原始报告](evidence/owned-guest-location-results.json)。自编消费者声明粗略／精确定位权限，但本轮没有验证真实运行时授权流程。

早期两轮失败保留在工作区并记录摘要：进程名称断言不准确、刷新操作未确认而超时。随后核对系统 PID／UID，对持续变化的界面关闭全局空闲等待，并增加读取序号确认，最终检查通过。没有把失败轮次的截图、旧报告或部分目标匹配当成本轮成功。

本轮不是第三方定位 SDK 采用，也没有在微信、抖音中调用业务页面。后续优先真实第三方采用验证，不继续以增加自有样例数量替代目标应用进度。

## 身份、组件与目标软件

证书解析不再在现代接口之后再次调用已不存在的旧方法；现代分支执行签名验证，解析／验证错误向上传播以中止导入。`GET_SIGNATURES` 使用虚拟包自身证书，`GET_SIGNING_CERTIFICATES` 从该 Guest 的归档读取真实 SigningInfo，避免空对象构造或用另一份系统已安装版本覆盖身份。运行时代码不填写固定证书。 固定构建 `16596636` 的 `PackageManager.getPackageArchiveInfo` 仅在传统标志存在时验证证书，因此内部归档查询同时带 `GET_SIGNATURES | GET_SIGNING_CERTIFICATES`，向外仍按调用者标志返回字段。新断言禁止现代单独请求泄漏 legacy signatures；目标 ARM64 修正结果尚待实测。

系统未安装计算器时，原始发布文件 SHA-256、旧版 signatures 与新版 SigningInfo 均核对原证书 `affdb124d3f4720c2f98dbca9eacba0514fba4306e20a2786c861c3c0d6ff292`，真实 UI 操作及回桌面重开也再次通过。本轮相关日志没有旧证书反射错误；仅覆盖该已固定的签名输入，不代表签名轮换、多签名、分包及全部软件安装路径已通过。

当前验证覆盖计算器的 Splash／Main Activity、资源、实际按钮运算和从桌面返回。Service、Provider、Intent result、通知、GMS、Native Guest、账号登录、异常隔离和 vivo 厂商行为均无完整回归。共享宿主 UID 的执行方式不等于为 Guest 创建独立 Linux 沙箱。

下一步应验证真实第三方定位应用／SDK 的采用，以及权限／组件／GMS 兼容，再评估真实微信能否进入并正确使用发送位置／实时共享页面。抖音无线及海外地点覆盖需要各自的业务结果，不能由本轮替代。

## 模拟开启时的基站读取边界

2026-10-24 源码审查发现，`getCellLocation` 在开启虚拟定位但没有 BCell 时仍调用真实服务；另外两个列表 getter 直接把内部 BCell 列表当作 Android 的 CellInfo／NeighboringCellInfo 返回。缺少虚拟数据不应成为读取真实基站的条件，内部模型也不是框架 Parcelable。

修正限定这三个已覆盖 getter：模拟开启时，单个位置返回空 CellIdentity／旧接口空 Bundle，两个列表返回空列表；不编造基站或运营商身份。关闭模拟时保留原有查询路径。没有更改物理网络、VPN、mock 标记或原包证书，也没有申请新的手机权限。

新增自动化使用真实 Android13 ITelephony 接口类型及记录调用次数的受控服务，检查“没有模拟基站时不调用真实服务”和关闭后的正常转发；另检查空列表及旧 Bundle 返回形状。两项已在提交 `d1439c0` 的云端 Android13 x86_64 中通过，JUnit `OK (2 tests)`，证据 ZIP 摘要与 32 项研究源码摘要已核对，见[实际结果](evidence/radio-privacy-contract-results.json)。测试没有请求真实基站；它是受控代理契约，不能当作 Guest Hook 安装、腾讯／字节 SDK 采用或微信成功。异步 cell-info 更新、全部无线接口与第三方实际决策不在这三个 getter 的结果内。

## Wi-Fi 空值与关闭模式

继续审查发现，原 `getConnectionInfo` 无条件改写返回对象中的 SSID／BSSID／MAC，返回空值时也会继续反射写入；`getScanResults` 则在关闭虚拟定位时仍返回空列表。它会改变关闭模式的正常查询，也可能在无连接信息时崩溃。这是容器源码缺口，不是此前用户系统微信失败的因果证明。

修正后，关闭模式和空连接信息保持原返回；虚拟模式在 API31+ 使用公开的 `WifiInfo.makeCopy(getApplicableRedactions())` 返回标准脱敏副本，保留原对象及不需要脱敏的链路字段，不制造路由器身份。旧系统缺少该公开契约时返回空连接信息；旧 API 分支尚未运行验证。扫描列表仅在虚拟模式为空，关闭时沿原调用路径。不改变系统 Wi-Fi／VPN、权限、mock 标记或原证书。

新增三项受控 Android13 检查使用真实 IWifiManager 方法类型和合成 WifiInfo／ScanResult，验证脱敏字段与原对象不变、空连接信息、关闭后的正常转发；没有查询真实 Wi-Fi。五项在独立云端工作区的软件模拟器中实际 OK (5 tests)，1.335 秒，并在 GitHub KVM 独立运行 0.055 秒通过；均为 API33 x86_64。证据 ZIP、35 项源码及实际 APK 摘要逐一核对，专用 VM 已停止，见[实际结果](evidence/wifi-radio-privacy-contract-results.json)。普通入口及计算器同时回归通过。它不覆盖真实 Guest Hook 安装、实际 Wi-Fi 权限、异步网络回调、腾讯／字节 SDK 或无线定位业务，也不证明 ARM64／vivo。
