# GoGoGo 一键复制诊断日志（接管后新增；测试版）

## 给使用者的操作（不需要看懂日志）

1. 正常打开 GoGoGo，执行你想观察的模拟与微信黑盒对照。
2. 按需打开 Consumer Matrix（主动采样）或「被动快照（无定位请求）」；从该页面返回即可，程序保留近期记录。
3. 首页直接点 **「📋 一键复制诊断日志（发给 GPT）」**，回到聊天窗口粘贴即可；实验与诊断页及 Consumer 探针页也有复制入口。**不需要逐张截图、手动整理字段。**
4. 如果粘贴内容含 `NO_RECORD`，表示尚未运行相应探针，**不是**自动断言定位失败。

用户不需要理解输出格式；只是复制给自己选择的聊天对象。应用不联网传日志，不自动打开第三方 APP，不会因点击复制去订阅/刷新位置，不申请额外权限。

## 工程实现

- `LabSupportLog` 在点击复制时只组装已存在的 Lab 33 只读快检/恢复证据、Lab 23 生命周期证据、上次合规模拟**请求目标**及最近进程独立消费者/腾讯 SDK 事件。
- `LabSupportLogText` 提供有界记录（每个来源最多 14 条）、SDK 文本白名单过滤、目标经纬度有效性验证、与最近**用户请求**目标的距离档位计算。请求目标不代表 ServiceGo 或第三方一定成功。
- `ConsumerLocationProbeActivity` 的五通道**主动模式**最多每三秒保存一次已经收到的样本摘要，**被动模式**只记录现有 `getLastKnownLocation` / `getLastLocation` 回调结果和原 shot ID；写日志没有额外定位调用。被动模式仍依赖未合并 [PR #28](https://github.com/soozooq/gogogo/pull/28)。
- `TencentLocationProbeActivity` 只保存请求类型/SDK 状态码/通用异常名称；**原始 SDK reason、POI 地名、腾讯 Key、真实坐标不持久化到支持日志**。
- 使用应用私有目录下的独立 `AtomicFile` 保存有限长度日志；让 `:consumer` 进程的观测结果能被主进程复制，不使用不可靠的跨进程 SharedPreferences 缓存作为消费者数据交换。
- 保留 `isMock`、fixAge、callbackAge、provider、accuracy。**仅当消费者提供系统 Mock 标记时**才包含精细坐标；非 Mock 的真实坐标一律 `REDACTED`，仅输出与用户最近请求测试点的**距离档位**（如 `LT_1KM`、`GE_1000KM`），不能反推出精确真实坐标。
- `report_process` 标记读取日志的进程；`ServiceGo.sRunning` 是**当前进程局部标志**，在 `:consumer` 下为 `false` 并不能证明主服务已停。日志包含此说明。

## 研发 / 手机验收要求

- CI `./gradlew build`（JUnit 纯 Java 文本规则）与 `assembleDebug` 通过只证明编译通过，**不等于手机复测已通过**。
- 首页启动、停止、权限拒绝、强行停止以后点击复制，不得引入模拟状态变化或读取真实位置的主动请求。
- 正常 Consumer Matrix 与 Passive Snapshot 必须独立标记；打开被动模式不进入 `startConsumers()`；不额外生成 `getCurrentLocation` / 更新订阅 / GMS mock。
- 手动要求在缓存不变化与缓存变化条件下各拍两张被动快照，对比 elapsed/fixAge、Mock、距离档位。
- 模拟北京、恢复真实位置后，比对 last GMS Cache 是否仍 `LT_1KM` 或已经 `GE_1000KM`，但不得认定微信内部 API 或其逻辑被识别。
- 只复制最近记录；日志中没有 IP、SSID、手机号、腾讯 Key、腾讯 SDK 自由文本、POI 名字。实际系统复制内容由用户选择发给谁，Clipboard 受 Android 系统管理。
- 若用户先从地图而非首页直接触发模拟，最后请求目标可能仍为先前首页请求的值；此时距离字段为历史参考，不可当本次模拟目标。
- 禁止自动上传或后台远程监控。不要把此诊断日志作为微信定位兼容通过的证据。

## 依赖/合并顺序

此改动基于 PR #28 的 `feature/passive-snapshot-cache-probe` 头提交，接管过程中不合并。**先审查并验收 #28，再考虑合并该一键日志 PR；成功自动构建不表示实机验收已完成。**
