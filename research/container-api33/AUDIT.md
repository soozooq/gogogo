# 可复用基础审计

本轮结论：已有可编译、能在 API 33 云端环境运行一款真实第三方 APK 的研究基础。微信／抖音兼容、现代定位替换、完整组件及权限隔离仍未完成。

## 构建与普通应用运行

- BlackBox `df6249da`：固定工具链首次遇到 AIDL 包声明错误，修正后核心 AAR、独立宿主及 Android 测试 APK 构建成功；核心含 ARM64、ARMv7、x86、x86_64，研究宿主仅 ARM64／x86_64。
- ISEKHON/VirtualApp `b3c634ad`：使用同一 JDK 17／NDK 25 环境完成 ARM64、ARMv7、x86_64 及通用 APK 构建；本轮未据此认定该 fork 已适配微信，也未将其代码并入主程序。
- 实际运行使用专用的 AOSP API 33 x86_64 软件模拟器。无硬件加速；app UID 为普通应用 UID，shell UID 为 2000，SELinux Enforcing。ARM64 仅有构建证据。
- 最初运行发现资源镜像缺失、研究宿主遗漏 `REORDER_TASKS`，以及云端 System UI 无响应。镜像和宿主配置已修正；云端恢复后先通过原生安装对照，再验证容器。中途的启动命令、不可见按钮选择错误归类为测试脚本问题，不算作第三方应用不兼容。
- 最终判断仅依据成功记录、APK 摘要和真实 UI；不沿用旧失败轮次的截图或状态。最初失败的完整日志仍在本地研究工作区。

## Android 13 定位接口尚需改造

依据固定的 [AOSP ILocationManager](https://github.com/aosp-mirror/platform_frameworks_base/blob/d27f9960456403533af0a0d5d9f14cf71c9c8cf6/location/java/android/location/ILocationManager.aidl#L53) 与 [ILocationListener](https://github.com/aosp-mirror/platform_frameworks_base/blob/d27f9960456403533af0a0d5d9f14cf71c9c8cf6/location/java/android/location/ILocationListener.aidl)：

| 检查项 | 固定候选源码中的缺口 | 后续完成标准 |
| --- | --- | --- |
| 单次定位 | BlackBox `GetCurrentLocation` 直接返回 `Location`；API 33 实际返回取消通道，通过 `ILocationCallback.onLocation` 交付 | 正确的返回类型、回调、取消及一次结束语义 |
| 持续监听 | API 33 使用 `registerLocationListener`，回调为 `List<Location>` 和可空完成回调；两候选仍存在旧单点监听形状／旧注册路径 | 实际框架 Binder 回调、批量样本及注销正确 |
| 数据年龄 | BlackBox 创建位置未设置 elapsed 时间；VirtualApp 写死 `277000000` | 合法、单调时钟一致的样本时间；不把刚收到旧值当新定位 |
| 停止监听 | BlackBox server 在 listener 已移除时仍 `continue`，可能持续空转 | 取消、死亡、移除均停止调度并释放记录 |
| 样本标记 | VirtualApp 直接清除 mock 标志 | 不能把未标记或旧样本当作已被目标应用正确采用的证据 |

以上为源码／契约核查，未把某一候选缺口归因于用户此前系统微信的失败。研究宿主保持虚拟定位关闭，因此计算器运行没有验证上表接口。

## 身份、组件与目标软件

BlackBox 的证书解析在运行日志中仍出现反射调用错误。外部 APK 字节和原发布签名已在云端验证，但虚拟 PackageManager 提供的原签名信息仍需独立核验；不能用填写固定假签名解决身份解析。

当前验证覆盖计算器的 Splash／Main Activity、资源、实际按钮运算和从桌面返回。Service、Provider、Intent result、通知、GMS、Native Guest、账号登录、异常隔离和 vivo 厂商行为均无完整回归。共享宿主 UID 的执行方式不等于为 Guest 创建独立 Linux 沙箱。

下一步优先完成现代定位契约和原签名解析，再评估真实微信能否进入并正确使用发送位置／实时共享页面。抖音无线及海外地点覆盖需要各自的业务结果，不能由本轮替代。
