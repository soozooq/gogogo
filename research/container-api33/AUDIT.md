# 可复用基础审计

本轮结论：已有可编译、能在 API 33 云端环境运行一款真实第三方 APK 的研究基础；现代定位的部分接口契约及原签名信息已修正，真实 Guest 进程内的自有消费者也已通过定位调用链检查。第三方定位 SDK 采用、微信／抖音兼容、完整组件及权限隔离仍未完成。接手从根目录 [PROJECT_STATUS.md](../../PROJECT_STATUS.md) 开始。

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

普通研究入口和计算器运行仍保持虚拟定位关闭。不能把本轮接口修正直接归因于用户此前系统微信的失败，也不能由此断言第三方已经采用位置。PendingIntent、被动定位、GNSS 状态、全部 LocationRequest 过滤条件和 GMS 融合定位仍未完整验证。VirtualApp fork 没有应用这些修正。

## 真实 Guest 调用链：自有消费者

[消费者源码](location-consumer/README.md)作为独立 APK 构建，没有引擎／宿主库、反射或预期坐标；测试只通过常规 Intent 传递随机关联 nonce。它从 Guest Context 的系统服务获得正常 `LocationManager`，不由测试代理替换接口。宿主控制器设置合成目标并操作消费者的真实界面，核对实际 Android 回调生成的报告。

系统未安装该包时，宿主 PID 2892、Guest PID 2959，系统 `ps` 核验 Guest 使用宿主 UID 10126。引擎会把 argv[0] 改成 Guest 包名，因此不能仅凭名称判定进程归属；这也不提供独立 UID 隔离。

GPS／NETWORK 的缓存、单次回调和持续批量回调均从 A 跟随到 B，同一 Guest 进程不重启。停止后计数稳定，取消单次后回调为零；样本保留 mock 标记、正确 provider 和新鲜单调时间。整轮 18.919 秒通过，见[源码／APK 绑定及 Guest 原始报告](evidence/owned-guest-location-results.json)。自编消费者声明粗略／精确定位权限，但本轮没有验证真实运行时授权流程。

早期两轮失败保留在工作区并记录摘要：进程名称断言不准确、刷新操作未确认而超时。随后核对系统 PID／UID，对持续变化的界面关闭全局空闲等待，并增加读取序号确认，最终检查通过。没有把失败轮次的截图、旧报告或部分目标匹配当成本轮成功。

本轮不是第三方定位 SDK 采用，也没有在微信、抖音中调用业务页面。后续优先真实第三方采用验证，不继续以增加自有样例数量替代目标应用进度。

## 身份、组件与目标软件

证书解析不再在现代接口之后再次调用已不存在的旧方法；现代分支执行签名验证，解析／验证错误向上传播以中止导入。`GET_SIGNATURES` 使用虚拟包自身证书，`GET_SIGNING_CERTIFICATES` 从该 Guest 的归档读取真实 SigningInfo，避免空对象构造或用另一份系统已安装版本覆盖身份。运行时代码不填写固定证书。

系统未安装计算器时，原始发布文件 SHA-256、旧版 signatures 与新版 SigningInfo 均核对原证书 `affdb124d3f4720c2f98dbca9eacba0514fba4306e20a2786c861c3c0d6ff292`，真实 UI 操作及回桌面重开也再次通过。本轮相关日志没有旧证书反射错误；仅覆盖该已固定的签名输入，不代表签名轮换、多签名、分包及全部软件安装路径已通过。

当前验证覆盖计算器的 Splash／Main Activity、资源、实际按钮运算和从桌面返回。Service、Provider、Intent result、通知、GMS、Native Guest、账号登录、异常隔离和 vivo 厂商行为均无完整回归。共享宿主 UID 的执行方式不等于为 Guest 创建独立 Linux 沙箱。

下一步应验证真实第三方定位应用／SDK 的采用，以及权限／组件／GMS 兼容，再评估真实微信能否进入并正确使用发送位置／实时共享页面。抖音无线及海外地点覆盖需要各自的业务结果，不能由本轮替代。
