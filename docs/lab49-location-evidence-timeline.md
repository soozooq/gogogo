# GoGoGo Lab 49 — 多定位来源事件时间轴（v11 研究成果工程化）

日期：2026-10-12 | Preview 版本：`2.4.0-lab49-evidence-timeline` / `2400056`

## 这一轮真正解决什么

Lab 30 已把 callbackAge 与 fixAge 分开；Lab 48 将 GMS `setMockLocation()` 的提交与异步 Task 成功分开。但是 `ConsumerLocationProbeActivity`、`TencentLocationProbeActivity` 的事件往往按墙上时间、请求耗时、样本年龄等不同概念混排，很容易把一条旧的 Location 误判成新鲜定位。

Lab 49 只做**可追溯的本应用自有消费者诊断**，不改任何 Mock 发布路线、Shizuku 权限、微信缓存、无线扫描策略和 GMS 注入频率。

## 已实现

- 新增可复用纯 Java `LabLocationEvidenceTimeline`。固定的来源枚举：Framework GPS/NETWORK/FUSED、GMS last/current/updates、腾讯 SDK、被动 GPS/NETWORK；固定阶段：请求、SDK 接受/拒绝、Task 返回、回调含样本/空样本/失败、超时和停止。限定最近 48 条事件，不保存原始 Location、经纬度、POI、网络标识或任意第三方错误字符串。
- 所有事件的**到达/记录时刻**采用 `SystemClock.elapsedRealtime()`，呈现为单次页面会话内的相对 `+ms`。不输出跨进程的绝对时间关联，也不把并发事件顺序当作 SDK 内部因果证明。
- Android `Location.getElapsedRealtimeNanos()` 的 fixAge 复用 `LabLocationAge.fixAgeMs()`，标为 `ANDROID_MONOTONIC`，未来/丢失时为 `UNKNOWN`。腾讯 SDK `getTime()` 是另一种墙上时间来源，只作为非可信度等同的 `TENCENT_WALL_ESTIMATE`；时间跳变或未来样本不当作新鲜。
- **Consumer Matrix**：在现有跨 API 结果文本后追加最近事件的时间轴，不添加新实验入口；标准 GPS/NETWORK 订阅、GMS updates、GMS last/current 的请求/回调按来源记录。每次 passive snapshot 只记录本已有缓存查询的请求与结果，不新增任何主动定位。
- **Tencent Probe**：沿用现有腾讯 SDK 用户确认和有效 Key 门槛，在原日志区域附加同样格式的时间轴；请求返回码只代表 SDK 接受/拒绝；单调时钟统计请求至回调延迟。Android/GMS 快照的 fixAge 改为单调时钟语义，以免跨时钟混淆。
- 8 项纯 Java 单元测试，覆盖相对时间、单调/墙上时钟区别、未知/未来时间、容量上限、清空重置、隐私输出及失败不会误报收到样本。
- 沿用测试包名及签名，预览版号增加，不清数据。

## 证据边界

| 观察 | 现在能说 | 仍然不能说 |
|---|---|---|
| GPS / NETWORK 请求登记 | 本应用发起注册或缓存读取 | 系统一定收到了新鲜实际位置 |
| GMS last / current / updates 回调 | 本消费者某回调返回了位置/空值/失败 | 服务端或微信也接收了相同 fix |
| Android fixAge | 本样本生成时间与现在的同启动单调时钟年龄 | SDK/POI 地理数据库是新的 |
| 腾讯 SDK callback | 自有 SDK 实际回调并带墙上时间估算 | 该位置等于微信内部定位来源 |
| Lab 48 Task success | GMS API 操作成功 | 目标业务采用了模拟位置 |

**尤其注意**：这是一套跨两个现有页面**共用的数据模型和时间语义**，不是可窥视微信、跨进程抓包或跨用户的真实因果追踪。独立 `:consumer` 与 `ServiceGo` 发布服务之间没有建立私有位置同步或干预通道。无法知道的项必须标记 UNKNOWN。

## 开发和手机验收

- [x] 纯 Java 时间轴及 8 项新单测已编写
- [x] Consumer Matrix 实时和被动模式接入
- [x] Tencent SDK + Android/GMS 对照接入
- [x] 没有引入新权限、不读取第三方进程
- [ ] GitHub `./gradlew build` / Debug APK 均成功
- [ ] vivo Android 13 实测新版本，无 Key 时原有禁用提示仍正常
- [ ] 被动快照页面不产生主动定位订阅
- [ ] 新时间轴与已有 fixAge / callbackAge 一致，不出现旧回调被判为新样本
- [ ] 深浅主题、360dp、大字体、快速切换和异步回调注销后的行为验收

## 关联关系 / 下一步

本分支基于尚未合并的 [Lab 48 PR #46](https://github.com/soozooq/gogogo/pull/46)；创建相对 main 的草稿 PR 是为了让 GitHub Actions 运行，不意味着可以绕过 PR #46 的手机验收直接合并。

Lab 50：vivo 双开用户隔离最小权限只读检查，要求本机授权；
Lab 51：腾讯 SDK 真实 Key + cache/POI 比对，明确用户知情同意；
Lab 52：有 E3 数据之后再评估 1/5/30Hz 性能，不做未经验证的频率改动。
