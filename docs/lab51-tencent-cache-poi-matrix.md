# GoGoGo Lab 51 — Tencent GEO / POI × allowCache 四组对照

日期：2026-10-12 · Preview `2.4.0-lab51-tencent-matrix` / `2400058`

## 来源与动机

依据研究档案 v11 以及 Lab 24、49 的 Tencent SDK 测试功能：已有请求级别（GEO / POI）、`setAllowCache` 和单次 / 连续定位，但以前的诊断输出是散乱的回调日志，无法可靠对比同一配置下一次测试到底成功、失败、可疑或超时。

这轮只处理**本 App 内 Tencent SDK 的独立消费者**，不读取/修改微信内部数据。采用现成 SDK `com.tencent.map.geolocation:TencentLocationSdk-openplatform:7.6.1.12`。CI Key 使用 DUMMY，不能以「构建通过」声称腾讯 SDK 的真实服务定位通过。

可参考：
- TencentLocationSDK guide: https://github.com/tencentlocation/tencentlocation.github.io
- 开源接口封装（只能作为实现参考，不证明具体本机行为）：https://github.com/jihaobin/expo-qq-location
- 现有 Flutter 封装的 GEO / POI 和单次限制：https://pub.dev/packages/tencent_location_flutter

## 这轮实现的功能

- 新增**可复用的纯 Java** `LabTencentModeComparison`，以 GEO/POI × allowCache OFF/ON 四个固定槽位保留每种配置的**最近一次单次请求**。
- 明确分类：未测试、请求中、SDK 接受、拒绝、回调坐标有效、回调可疑、回调空值、错误、超时。SDK 接受请求绝不等价于返回定位。
- 记录仅限非敏感摘要：请求至回调延迟、估算的腾讯 SDK 墙上时间年龄分段、POI 数量（或者未知）、状态。**不保存坐标、POI 名称、SSID、基站、完整 SDK 回调消息。**
- 已有腾讯单次按钮直接更新对应槽位；已有连续定位继续独立运行，**不计入四组单次对照**。
- 提供本地“清空对照”按钮：取消本页面当前腾讯 SDK 订阅并清理本地四组记录，**不调用 SDK 缓存清除**、不强制刷新第三方应用。
- 页面明确：「关闭缓存」只代表向 SDK 发出 `setAllowCache(false)` 参数；并不证明返回的样本一定来自新鲜传感器扫描，或完全未用缓存。
- SDK Key 不存在时显示 `SDK_KEY=UNAVAILABLE`，不伪造任何结果；Key 看起来存在时只显示 `PRESENT_NOT_VALIDATED`，直到真实 SDK 回调才能作为一次观测。
- 请求名称改为「腾讯单次定位」，避免「Fresh」错误承诺。
- 对照矩阵只比较同一级别下的 wallAge **估算分段**；`SAME_ESTIMATED_AGE_BUCKET_NOT_CACHE_PROOF` / `DIFFERENT_ESTIMATED_AGE_BUCKET_NOT_CAUSAL` 不得表述成“命中缓存”“缓存刷新成功”。
- Android/GMS 的 WGS-84 与腾讯 SDK 页面 GCJ-02 不能未转换就做点对点距离判断；POI 坐标的 local distance 仅在腾讯回调内部同系统参考下作为辅助。
- 保留用户首次发起网络请求前同意隐私处理的交互，不自动发起四次请求，不增加服务端网络请求。

## 已覆盖的纯 JVM 测试

新增 12 项，覆盖：
1. 四组起始未运行；
2. SDK accept≠fix；
3. SDK 拒绝请求；
4. 有效样本记录；
5. 零坐标/无效坐标标可疑；
6. 未来或未知时间不能称“新鲜”；
7. 超 30s 年龄仅估算；
8. 相同 wallAge 分组不能断言 SDK 缓存命中；
9. 错误/空/超时区别；
10. 清空只影响本地记录；
11. 同一组合重新开始只保留最后一次尝试；
12. 四组报告不输出真实坐标和 POI 名称。

## 真实可执行的实机验收

- [ ] GitHub `./gradlew build`：Debug/Release JVM 测试 + Lint + Release 构建通过。
- [ ] GitHub Preview Debug APK 构建通过。
- [ ] vivo Android 13 上无 Key：显示 SDK unavailable，按钮不发出腾讯定位请求；仍可刷新 Android/GMS。
- [ ] 真正的腾讯 Key 且获得用户明确同意：分别选择 GEO/POI × OFF/ON，每次点击「腾讯单次定位」，核对对应记录、超时和错误状态。
- [ ] 连续模式的回调不能污染四组单次结果；切换新的单次请求会取消上一轮。
- [ ] 腾讯结果 `getTime()` 为未来、0、异常时显示 WALL_UNKNOWN；GCJ-02 和 WGS-84 不跨坐标制距离对照。
- [ ] 断网、无定位权限、无 GMS 和关闭腾讯订阅时不会报告假阳性。
- [ ] 快速启停、关闭页面、横竖屏、窄屏与大字号 UI；用户决定是否分享日志。
- [ ] 微信主/双开真实位置依然未知，不宣称腾讯 SDK 成功就是微信成功。

## PR 依赖、合并限制

Lab 51 基于 `feature/lab50-wechat-clone-readonly-20261012`。之前 Lab 48–50 的 PR #46–#48 都未通过 vivo 真机验收，**任何构建通过都不自动授权合并 main**。本轮只修改现有腾讯诊断页面、纯 Java Ledger、测试、构建版本和这份说明，不修改定位注入代码。

下一轮 Lab 52 将在本机真实消费者 E3 样本充分时做 1/5/30Hz 的异步任务性能对比；没有 E3 数据，只可做离线审计/检测，不应直接改变用户既有 Mock 发布频率。
