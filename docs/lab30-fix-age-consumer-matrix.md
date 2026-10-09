# GoGoGo Lab 30 — Consumer Matrix 双年龄与缓存快照判定

## 问题（来自源码，不是对微信内部的猜测）

截至 Lab 29，`ConsumerLocationProbeActivity.Channel.update()` 收到回调时记下 `receivedElapsedMs`，而 `ageMs()` 实际计算的是**回调到达后已经过了多久**。但是 `LabConsumerMatrixEvaluator` 却把这个时间当成 **Location fix 生成后的年龄**来判断实时流（1 s）和单次快照（30 s）是否新鲜。

因此，当 GMS `lastLocation` 返回一个旧 fix 时，应用刚收到该对象就可能错误报告“新鲜”。跨 API 评分也混入了所有旧快照的位置距离，导致健康持续流仍可能因历史缓存坐标偏远而被降级。

## 本轮实际代码变化

- `LabLocationAge`：纯 Java 时钟函数。读取来自同一 Android 启动周期的 `SystemClock.elapsedRealtimeNanos()` 与 `Location.getElapsedRealtimeNanos()` 的差值作为 `fixAgeMs`；接收时记录的单调时钟计算 `callbackAgeMs`。缺失（0）、未来、逆序的时间戳都返回 UNKNOWN(-1)，不强行归零。
- Consumer Matrix 每个通道分开显示 **fixAge（位置样本年龄）** 与 **callbackAge（回调年龄）**；不再使用含混的 `age`。
- 实时流：`fixAgeMs <= 1000` **且** `callbackAgeMs <= 1000` 才判为新鲜。
- 单次快照：以 `fixAgeMs <= 30000` 判是否“近期”（不同于刚刚收到快照）；快照旧值不会直接影响实时流等级。
- 同时报告“所有可用通道之间的最大距离（仅用于观察）”与“**新鲜持续流**之间的最大距离（用于实时一致性评分）”。可以看到旧缓存有多远，但不能让它错误降低三条健康流的评分。
- 页面**显示期间每 1 s 刷新年龄与评分**，不发出额外定位请求。无回调时自然变成 STALE；页面 `onStop` 移除定时回调。
- 版本号：`2.4.0-lab30` / `2400035`。测试 APK 包名和测试签名保持不变。

## 自动化单测（预期）

`LabLocationAgeTest`（5 个用例）：
1. 刚收到的十分钟前 fix 依旧是 OLD；
2. fix 时间戳为 0 是 UNKNOWN；
3. 将来的 fix 时间不强行算成 0；
4. 无效/重启可能造成的不一致时间戳不视为新鲜；
5. 1000 ms 边界内外正确判断。

`LabConsumerMatrixEvaluatorTest`（此前 5 个 + 新增 5 个）：
1. 三条新鲜流一致；
2. 一条旧流明确降级；
3. 旧快照不拖低新鲜流；
4. 地理偏远的持续流不能误报一致；
5. 空输入安全；
6. **刚收到十分钟前的流**不能当新鲜；
7. **刚收到十分钟前的 lastLocation**不能当新鲜；
8. **回调本身老旧**不能当新鲜；
9. **fixAge 为 UNKNOWN** 不得评分成功；
10. **距离很远的旧快照**不污染健康持续流的一致性评分。

**重要**：这份文档是实施/验收说明，不等于 CI 已经通过。CI 结果和真实设备结果必须单独记录。

## 手机上需要验证

1. 保持已安装的可用版本，安装 Lab 30 Preview（包名 `com.soozooq.gogogo.test`）。若提示签名不匹配不要先卸载旧应用。
2. 打开 GoGoGo → 实验与诊断 → Consumer Matrix（五通道），不给出额外 Shizuku 权限。页面每条已获取到的 Android/GMS 数据应显示 `fixAge` 与 `callbackAge`，无数据仍显示 NO SAMPLE。
3. 点击「刷新 One-shot」，观察 `lastLocation`：刚回调后 callbackAge 可以很小，fixAge 可能很大；旧缓存应列入 `old / unknown snapshots`。
4. 在已有合法 Mock 设置下启动/停止模拟；等待更新停止，年龄每秒增长，持续流不能一直保持“新鲜”。
5. 检查未配置 GMS/权限拒绝/禁用 Location 状态：显示不可用或错误，不造成功样本。
6. 如有授权，分别记录位置的原始数据年龄和微信等应用**可见 UI**（自动位置、地名、POI、距离标签），避免把微信 UI 的历史数据当作 Android Provider 的证明。
7. 保留系统深浅色、窄屏和较大字体的可读性测试。

## 明确未改动与未承诺

- 不改 `ServiceGo` 模拟位置生成、Provider 注册/清理、GMS setMockMode/Location、Shizuku、地图、路线、系统权限、版本签名或第三方 App 代码。
- `Lab 30` 只改变**观测与评估**，并不直接提升微信兼容性。Android 的独立消费者进程不代表微信腾讯 SDK 的私有回调。
- 由于定位年龄只能在同一系统单调时钟基线上正确解释，时钟异常场景保守标 UNKNOWN；不能推断未知位置就是假数据。
- Gradle / JUnit 通过 ≠ 手机运行及腾讯/微信实测通过；公有 APK 仍可能使用 DUMMY 腾讯 Key。
