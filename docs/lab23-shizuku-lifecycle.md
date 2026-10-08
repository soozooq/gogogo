# Lab 23 — Shizuku 授权与 GoGoGo 模拟服务生命周期取证

> 目标：区分“Shizuku 撤权引起应用被强制停止”和“定位 SDK 采用/拒绝模拟坐标”，不修改微信，不隐藏 mock 标记。

## 为什么要做

实测 Lab 22 Consumer Matrix：独立进程 GPS、NETWORK、Google Fused 的五条消费者通道全部读取同一个公共测试坐标；微信聊天“发送位置”界面却继续显示旧地区，并出现约 12900km 的异常距离。仅凭外部 UI 不能判定哪个 SDK 读取/拒绝了哪个样本。

[Shizuku 官方源码](https://github.com/RikkaApps/Shizuku/blob/b844bc491f1790c72328e1a8e5b2349f8978f0ea/server/src/main/java/rikka/shizuku/server/ShizukuService.java#L373-L418) 的 revoke 分支可能调用 `forceStopPackageNoThrow`，同时移除对应 UserService。因此“撤销 GoGoGo 的 Shizuku 授权后微信恢复真实定位”，有一个不需要推测微信内部 SDK 的替代解释：GoGoGo 主进程可能被强行停止。

## 本版本实现

- `LabServiceLifecycleJournal` 在 `ServiceGo.onCreate` 持久化一次启动记录，并在 `onDestroy` 清理结束后写入正常退出记录。
- 记录：服务启动/结束时间、所属进程 PID、每次进程启动的匿名 generation、总会话次数、疑似中断次数。**不会保存经纬度、IM、手机号、SIM 标识或 Wi-Fi 指纹。**
- 「实验仪表盘」新增「模拟服务 / 进程生命周期（Lab 23）」区域，同时显示 `ServiceGo.sRunning`、`MOCK_LOCATION AppOps`、上次停止记录及状态判断。
- 环境快照也收录以上状态，可比较 Shizuku 权限变更前后。
- 不增加任何服务常驻、注入、Root 调用或网络拦截代码。
- 单元测试覆盖：无记录、正常停止、运行一致、进程终止无退出记录、服务运行标志不一致、跨重启不确定，以及 BOOT_COUNT、旧记录回退与时钟变化。

## 安卓设备验证步骤

1. 安装同签名的 Lab 23 测试包（不要卸载旧包清数据），进入「地图实验室 → 实验 → 实验仪表盘」。
2. 查看是否显示「模拟服务 / 进程生命周期」；先保存环境快照。
3. 启动 GoGoGo 的模拟服务，回来查看「ServiceGo 当前运行标记: RUNNING」，并保存第二张截图。
4. **正常停止**模拟服务，打开仪表盘：应显示「上一次服务执行过正常停止回调」。
5. 再次启动模拟服务，前往 Shizuku 管理器**仅撤销 GoGoGo 的 Shizuku 授权**，不要关闭 Shizuku 服务、Nrfr 或系统定位。重新打开 GoGoGo 仪表盘，查看进程 PID 和「上一次启动后未记录正常停止」。
6. 如果发现非正常退出候选，只能说明 ServiceGo 的 onDestroy 未写入记录，不能单凭这一点证明第三方 App 收到了真实位置，也不能说明系统 Provider 仍然残留。用 Consumer Matrix 再检查一次。
7. 不要清除微信数据或更改微信权限；必要时只做系统设置恢复。

### 如何解读

| 记录状态 | 能支持的结论 | 不能支持的结论 |
|---|---|---|
| 上次正常停止 | 服务执行过 onDestroy 后的记录更新 | GMS mock mode 一定完全清理 |
| 记录与本进程运行一致 | 同一进程里 ServiceGo 运行标志为 true | 微信已采用该坐标 |
| 上次未记录正常停止 | 可能是强停、崩溃或初始化失败 | 一定是 Shizuku 造成、Provider 一定残留 |
| 跨设备重启 | elapsedRealtime 记录不再具有同一启动周期可比性 | 一定发生了强制停止 |

### 已知边界

- PID 可能复用，因此同时用每进程随机 generation 验证，不只比较 PID。
- 生命周期记录另外保存 Android BOOT_COUNT（若可读取）；相同开机周期优先以 BOOT_COUNT 判定，防止手机重启后运行时长比旧记录更长而被误认成强停。
- 对旧版记录或 BOOT_COUNT 不可读取的 ROM，会比较启动时刻估算值（`wallTime - elapsedRealtime`，容差 5 分钟）；修改系统时间可能触发“不确定”，不能据此判断撤权或 SDK 行为。
- Lab 23 新增 6 个开机周期回归测试，总计 12 个单元测试用例。
- 日志只在 ServiceGo onCreate/onDestroy 时写入，不记录底层 Binder/系统进程实际 Provider 清理完成状态。
- Shizuku 实际撤销授权的效果取决于装机版本、路径和系统 OEM 实现。
- 此功能是兼容性取证，不负责腾讯定位 SDK 的观测。Tencent Consumer Probe 是后续独立任务。
