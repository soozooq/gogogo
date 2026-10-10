# GoGoGo Lab 36 — 跨会话 GMS 迟到回调稳定性修复

## 已确认的问题

Lab 35 已通过递增请求 ID 防止较早的 Google Play Services Task 回调覆盖**较新请求的诊断记录**，但 ServiceGo 在停止后接到迟到的 `setMockMode(true)` 成功回调时，仍会无条件调用 `requestGmsMockDisable("LATE_ENABLE")`。这一调用可以在较新的 ServiceGo 已开始后再次执行 `setMockMode(false)`，存在干扰当前活动会话的风险。仅修正日志顺序不足以预防这种旧实例的实际后续请求。

## 修复内容

- ServiceGo 保存自己最后一次发起的 GMS 请求 ID（`mLastGmsRequestId`）。
- 新 ServiceGo **onCreate 初期**生成一个持久化请求世代屏障 `SERVICE_STARTED_GMS_NOT_YET_ATTEMPTED`，不用等待地图、Provider、GMS 初始化，因此旧实例即使在新 GMS 初始化之前收到迟到回调，也不再拥有当前审计请求槽位。
- 如果已停止的旧服务收到迟到 `ENABLE` 回调，仍尝试 best-effort 关闭，但在发起关闭请求前，必须原子核对「自身最后一次请求 ID == 最新持久化请求 ID」。如已有新服务或更新的请求，则**直接跳过旧实例的额外关闭请求**。
- 对被抑制的过期补发进行聚合计数（`gms_suppressed_late_retry_count`），在既有实验仪表盘、恢复取证、综合排障摘要中显示；不增加新 Activity，也不复制现有页面。
- 保留正常 ServiceGo.onDestroy 的关闭请求，不修改地图、路径、坐标、发布频率、Provider 注册/清理算法或 Shizuku 授权逻辑。
- 新增 7 个纯 Java 回归测试到已有 `LabGmsRequestOrderTest`，累计 18 个测试，覆盖：正常同实例迟到回调可补发；旧实例被新服务屏障阻止；新 enable 之后旧实例被阻止；不允许零请求 ID；报告仅含聚合次数。

## 边界

这个修复减少**被旧服务迟到回调再次发起关闭请求**的风险，但不是 Google Play Services 中 `setMockMode()` 的物理事务锁。一次请求的序号检查与 GMS 异步实际执行之间，仍可能出现跨实例的任务执行顺序竞态。序号只表示本应用本进程序列化的审计所有权，不证明最终 GMS、微信或系统位置已恢复。

Android 系统完全强停/杀进程后，原进程里的异步回调一般不再有机会继续执行；主要风险是一个进程生命周期内的异步回调和服务快速重建。无法在 GitHub CI 中模拟所有 OEM/GMS 条件。

## Android 13 真机验收

- [ ] 从 Lab 35 的相同包名 `com.soozooq.gogogo.test` / 公开测试签名覆盖升级，**遇签名冲突不要卸载保存有测试资料的旧版**。
- [ ] 连续正常启动、停止 10 次，确认不影响 GPS/NETWORK/FUSED 的模拟状态和原有 UI。
- [ ] 使用带 GMS 的测试设备执行快速停止→立即重启；关注实验仪表盘「GMS 已阻止的过期服务补发关闭次数」是否增加，以及最新请求是否属于新服务。
- [ ] 在新服务初始化阶段发生旧 enable 迟到回调时，不应主动发起来自旧实例的新的 GMS 关闭请求。难以稳定复现时应保留相关历史日志，不能以未出现计数当成验证通过。
- [ ] Shizuku 不可用、模拟定位 AppOps 取消、只有近似定位等场景仍可查看精简报告；无自动授权或系统设置修改。
- [ ] 验证正常服务停止后原有 best-effort GMS reset 未退化；仅允许最新请求回调更新最后事件。
- [ ] Lab 31/32/33/34 取证报告不能包含经纬度、SSID、IP、设备 ID 或原始异常栈；基准比较仍然可用。
- [ ] 对微信或其他第三方定位消费者进行独立实际位置测试；不能把旧回调被拒或 CI 绿色作为微信兼容已实现的证据。

版本 `2.4.0-lab36`，versionCode `2400041`，仅 ARM64 Preview。公开仓库的测试签名不可作为正式发行签名。
