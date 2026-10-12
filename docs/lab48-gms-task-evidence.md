# GoGoGo Lab 48 — GMS 异步发布证据分级（v11 研究落地第一轮）

日期：2026-10-12

## 目标和依据

依据资料库《GoGoGo 定位兼容性研究档案 v11》，先解决自身证据不可靠的问题，再进入腾讯定位 SDK / vivo 用户隔离等后续实验。

P0：原来的 `ServiceGo.setLocationFusedClient()` 在 `setMockLocation` 返回 Task 后马上增加 `mGmsFusedDispatchCount`。这个数**只表示调用返回 Task**，没有监听 Task 最终成功或失败。`LocationCompatibilityActivity` 之前用「mock 已开启 + dispatch 年龄短」判定 GMS 链健康，对消费者是否收到位置提供了不充分的肯定印象。

P1：README 曾直接宣称微信某版本/小程序必定使用指定 Android/GMS API，且已修复兼容；这是未经该用户 vivo Android 13 主/双微信实测的说法，应改为待证假设。

## 实施范围

1. 新建 `LabGmsPublishEvidence`：线程安全、同一 ServiceGo 实例内的轻量计数器。维度：Task 已返回、Task 异步成功、异步失败、未能附加回调的未知结果、同步异常、尚未完成。没有位置、手机号、IP、Wi-Fi 或基站标识。
2. `ServiceGo` 在 `setMockLocation` 返回后再标记「提交」，通过 `Task.addOnCompleteListener(Runnable::run, ...)` 监听结果；仅成功任务更新最近 Task 成功时间。回调不做日志或网络操作。保留原来约 33ms 的计划发布间隔、GPS/NETWORK/Framework FUSED 以及 GMS setMockMode 逻辑，**本轮不调整注入频率**。
3. `LocationCompatibilityActivity` 将 GMS「最近成功」从「最近提交」分离，并显示 Task 各类结果。页面内明示 Task OK 不代表其他应用采用位置，修改「健康/兼容已成功」的误导性文字。
4. README 微信兼容 FAQ 改成经证据审查的表述。
5. 测试预览版版本号变为 `2.4.0-lab48-gms-evidence` / `2400055`；不变更包名 `com.soozooq.gogogo.test`、签名和用户数据库。

## 证据分级（不可越级）

| 级别 | 说明 | Lab 48 能否证明 |
|---|---|---|
| E0 | 发起调用 | 是（发送逻辑） |
| E1 | GMS 返回 Task（submitted） | 是 |
| E2 | 该 Task 异步成功/失败 | 是（有 callback 时） |
| E3 | 自有 Consumer 收到符合时间条件的 fix | 否，本轮未修改 Consumer Matrix；需另行交叉比对 |
| E4 | 腾讯 SDK 独立消费者收到对应 fix | 否；已有 Lab 24 需要真实 Key/用户同意 |
| E5 | 微信/抖音等业务页面采用模拟位置 | 否，须另行做自愿且合法的真机观察 |

注意：`mGmsFusedDispatchCount` 现存字段沿用其历史意义，只能叫「Task 返回/提交」，不应被外部统计系统误读为成功。

## 单测与验证

新增 `LabGmsPublishEvidenceTest` 八项，覆盖：
- 提交 ≠ 成功；成功与失败分开；同步异常不冒充提交；
- 无法绑定观察回调为 UNKNOWN，而不是失败；
- in-flight 待完成数；空样本为零不能冒充健康；
- 异步线程安全。

CI 必须跑 `testDebugUnitTest` 和 Debug/Release 构建；在 CI 结果出来前不能声称通过。

手机验收（本轮**未执行**）：
- [ ] 同签名覆盖安装新 Preview，不先卸载旧版本，保留数据。
- [ ] 打开「Location Compatibility」，未启动时显示未运行，而非微信已兼容。
- [ ] 启动模拟；GMS 可用时 submitted 增长，异步 OK/failed/pending 独立变化。
- [ ] 无 GMS 或无权限：应清楚显示不可用、异常或未确认，不出现虚假的 Task OK。
- [ ] 关闭模拟后历史 Task 结果不等同设备已恢复全部真实位置；对照 Lab 32/35/37 恢复状态。
- [ ] Consumer Matrix 分别查看 fixAge / callbackAge / isMock，不拿本页面的 Task 成功代替收到数据。
- [ ] 不通过任何方法读取微信进程、其私有缓存或未经授权的定位来源。
- [ ] 核对窄屏、深色模式、大字体和权限拒绝路径。

## 已知限制和待复核事项

- 计数是 `ServiceGo` 本次实例的内存观测，退出或进程结束后不会保证保留，不能冒充跨会话 GMS 真实状态。
- 异步 Task OK 只代表 Google Play services 接受该操作；不表示某第三方定位消费者收到、信任或展示它。
- `Runnable::run` 是不经主线程排队的轻量完成观察，但实际发布仍按既有高频循环运行。未来应在有真机数据后比较 1 Hz / 5 Hz / 30 Hz 的任务延迟、积压与实际 E3 成功率，**本轮不擅自改频率**。
- 既有 Framework GPS/NETWORK/FUSED 计数也不是消费者确认；本轮仅修复 GMS 异步可观察性，不夸大为「所有 Provider 已被证实成功」。
- 若腾讯 SDK 定位 Key 不可用，Lab 24 只能显示 SDK unavailable；不得伪造返回。
- 只有在 E2 / E3 / E4 各层数据可信后，才能针对真实设备讨论其定位来源差异。当前 vivo 双微信使用的 clone 机制、微信内部调用链仍 UNKNOWN。

## 后续迭代方向（不属于本 PR）

- Lab 49（P0）：Provider / FLP / 腾讯消费者之间统一时间轴；标记 request time、Task completion、fix sampled time、callback arrival time，不默认导出坐标。
- Lab 50（P1）：合法自有测试设备下核验 vivo 双开用户/配置隔离，只读、最小权限。
- Lab 51（P1）：单独比较腾讯定位请求允许缓存/禁用缓存与 POI 业务输出（需要有效 Key 和明确同意）。
- Lab 52（P2）：有真实 E3 数据后评估降低高频发布的性能、功耗、回调积压；任何变更需要可回退 A/B 验证。

## GitHub 交付约束

只在 `feature/lab48-gms-task-evidence-20261012` 分支开发；建 PR，**暂不合并 main**，直到 CI 和用户设备验收完成。不要把构建通过称为微信已兼容。
