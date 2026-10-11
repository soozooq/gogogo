# Lab 47 — 实验前置检查 / vivo 微信分身对照

## 目的

此前用户在 vivo 自带应用分身环境测试主微信和分身微信，主微信“强行停止”确认之后按钮仍可以点击。**不能把按钮可点击理解为强停失败**，也不能把主微信的运行状态推广到分身实例。

## 功能与约束

- **实验与诊断** 和 **Consumer Matrix**（含被动快照）加入「实验前置检查」。
- 模拟状态从同 UID、非导出的 `LabRuntimeStatusProvider` 请求当前**主进程** `ServiceGo.sRunning` 标记，不再把 `:consumer` 的 `STOPPED` 当成主进程结论。
- 主进程的 RUNNING/STOPPED 只表示 `ServiceGo` 的运行标记；**不保证 GPS、NETWORK、GMS、微信位置已成功改变，也不保证清理完成**。具体通道以 Consumer Matrix 的新鲜回调为准。
- 通过 PackageManager 检查当前用户 `com.tencent.mm` 的 `ApplicationInfo.FLAG_STOPPED`。YES/NO 仅表示系统提供的 stopped 标记，**不等于已证明进程终止/仍运行**。
- 分身微信可能位于 vivo 的独立用户空间。使用 `LauncherApps.getProfiles()` + `getActivityList()` 仅检查**可访问**的备用用户配置有没有微信启动入口；不访问分身数据、不枚举用户 ID、不假称 stopped 状态。如果没有可见入口，明确输出 UNKNOWN。
- 快捷按钮分别跳转现有 GoGoGo 首页（使用原有坐标验证与权限/服务启动流程）、主微信应用详情（用户手动点强行停止）和 Android 应用管理（寻找 vivo 分身）；GoGoGo **不直接强停微信**，不修改或删除微信数据，也不要求 ROOT/Shizuku/额外跨用户权限。
- 前置检查只读、异步，**不主动请求定位**，被动快照仍只在进入或手动点击时读取缓存。
- 复制日志新增 `wechat_main_flag_stopped`、`wechat_clone_stopped=UNKNOWN_CROSS_PROFILE_NOT_READABLE`，保留真实坐标隐藏策略。
- 版本 `2.4.0-lab47-preflight` / `2400054`，原模拟位置服务发布逻辑、Provider 注入及 GMS 异步流程**未更改**。

## 使用建议

1. 实验前打开「实验与诊断」，确认**主进程标记**；如 STOPPED，点「去首页启动／停止模拟」按原有首页流程启动，再回实验页刷新。
2. 打开普通 Consumer Matrix，用新鲜的 GPS / NETWORK / GMS updates 与 Mock 标记确认公开定位接口的观察结果。
3. 若要测试主微信停止，点「打开主微信应用详情」，自行确认强停，然后返回 GoGoGo 点「刷新模拟与微信状态」。对照 stopped 标记和微信打开后的表现，保留时间顺序。**系统设置中的强停按钮是否灰掉不是有效依据。**
4. 对 vivo 分身使用应用管理入口；若设备不向普通应用开放分身用户空间，明确报告 UNKNOWN，不能据此下任何关于分身停止状态的结论。
5. 禁止为实验关闭 vivo 应用分身或清理微信数据（可能造成分身数据丢失）。

## 需要实机验证

- 单击状态刷新不会触发新的定位请求，仍不会启动 ServiceGo。
- 主进程 RUNNING 标记与首页开启/停止的相对变化。
- vivo 系统在主微信强行停止后实际返回的 `FLAG_STOPPED`（OEM 可能自行改变呈现）。
- vivo 分身是否被 `LauncherApps` 枚举，不可见时保持 UNKNOWN。
- 返回测试页时刷新和 10/30/60 秒复制仍可用。
