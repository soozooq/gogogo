# GoGoGo Lab 52 — 33ms 休眠循环与 GMS Task 异步积压（只读）

日期：2026-10-12 · Preview `2.4.0-lab52-performance-observe` / `2400059`

## v11 研究成果落地

此前使用了“30Hz 更新”的叫法，但源码 `ServiceGo.initGoLocation()` 实际执行的是：

```
HandlerThread.handleMessage(): Thread.sleep(33ms)
  -> 更新运动/策略
  -> setLocationNetwork()
  -> setLocationGPS()
  -> setLocationFused()
  -> setLocationFusedClient()
  -> sendEmptyMessage()
```

因此平均循环间隔一般包含 **33ms 休眠 + 其它工作与调度开销**，不能用 1000/33 代替真实测量。Lab 48 已区分 GMS Task 返回和异步结果，但没有 Task 完成耗时和瞬时积压峰值证据。Lab 52 用只读数据评估这些假设。

## 已实现

- 新增 `LabPublishPerformanceEvidence`：一个 ServiceGo 生命周期内的纯 Java、有锁 O(1) 计数器，不保留 Location、日志队列、GPS 轨迹、设备 ID，也不会创建轮询线程；每个 tick 只追加极少量整数计算。
- 循环时刻由原有 ServiceGo `HandlerThread` 自己调用 `SystemClock.elapsedRealtime()` 记录。有效间隔数、累计/最大间隔、超过 50/100ms 的间隔数及平均实际 tick Hz 均独立统计。
- GMS `setMockLocation()` 返回 Task 后记录 pending，完成回调从**Task 返回时刻**到监听回调运行时刻的耗时、最大延迟、超过 100ms 次数、峰值未完成数，以及异步成功/失败和 observer unknown。
- `Task` 调用返回本身不能证明异步成功，成功回调不能证明应用使用了 fix。无 GMS 时 Task 相关维度为零/UNKNOWN，不推断健康。
- 无效或倒退时钟不计作新鲜样本，重复/孤立终态回调标记 `UNMATCHED_CALLBACK`，pending 不出现负数。
- 在原 `LocationCompatibilityActivity` 同一 Provider 状态卡片内加入性能节奏数据，**不新增 Lab 页面**。
- 原 `Thread.sleep(33)`、Handler 调度策略、GPS/NETWORK/FUSED/GMS 发布方式、模拟参数、Mock 权限设置、Shizuku 和其它应用状态全部保持不变。
- 新增 11 项 JVM 回归测试覆盖空数据、实际间隔、慢间隔、Task 返回≠成功、平均和最大异步耗时、无回调、反向时间、重复终态、并发读写、隐私输出。
- 测试 APK 保持 `com.soozooq.gogogo.test` 与原有签名方案，versionCode 增至 `2400059`。

## 证据范围与限制

| 观察 | 能说明什么 | 不能推断什么 |
|---|---|---|
| tick Hz | 原服务循环实际平均执行速率 | 系统 Provider 或微信拿到相同频率 |
| >50 / >100 ms | 累计异常间隔次数 | 一定是 GMS 慢；可能是线程调度和其他逻辑 |
| Task submitted / pending / peak | 本 ServiceGo 实例有多少 Task 尚未得到完成观察 | Google Play services 内部队列长度 |
| Task completion latency | Task 返回后到应用收到 completion 的延迟 | 从硬件扫描到消费者收到位置的完整延迟 |
| Consumer Matrix E3 | 已有独立消费者收到的 fixAge / callbackAge | 微信/小程序内部使用相同来源 |
| 无 GMS Task 结果 | UNKNOWN / 未提交（需结合 Lab 48） | 自动断言 Android / 微信定位失效 |

## 1 / 5 / 30Hz 对比方向

本轮**没有**实现动态频率切换、AB 开关或优化发布频率，因为目前没有经过用户自有设备证实的 E3 (Consumer Matrix) 对照样本。贸然降频会改变模拟精度，贸然升频会增加负载，且都没有可靠证据提升腾讯系兼容。

拿到真机实测后可按相同路线 / 相同权限 / 相同前后台状态，对比 1/5/近30Hz 的**真实 tick、Task pending 以及 E3 fixAge/callbackAge**，用真实数据决定是否调整，所有参数变更必须可回退。

## 自动验收与设备验收

- [x] 新计数器 + ServiceGo 接入
- [x] 原兼容性页面显示节奏/延迟/积压
- [x] 11 项新增 JVM 测试
- [ ] Github `./gradlew build`、Debug/Release 单元测试和 Lint 通过
- [ ] Preview APK 构建成功并核对版本号/包名
- [ ] vivo Android 13：无 GMS 时维度 UNKNOWN，有 GMS 时 pending/finished 计数符合完成事件
- [ ] 正在发布及停止后页面不出现重复任务、负 pending 或无真实请求却声称成功
- [ ] 长时间运行的实际频率、延迟与发热需真机观察（构建不能替代）
- [ ] 与 Consumer Matrix 的实际新鲜 fix 进行独立对照
- [ ] 旧版覆盖安装和运行不回归

## 合并与下一阶段

本分支建立在 Lab 51 尚未合并的 PR #49 之上，再向上依次依赖 #48、#47、#46。**只有 CI 通过不允许直接合并 main**；必须按顺序验收或形成一个经过验证的累积候选包，且不把“工程五轮完成”表述为“微信兼容成功”。

后续研究重点不再盲目加 Lab 编号，而是根据实机样本修正相关 API 读取、性能热点和腾讯 SDK 真正缓存差异的证据质量。
