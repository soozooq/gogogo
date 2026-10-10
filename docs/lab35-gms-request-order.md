# GoGoGo Lab 35 — GMS 异步回调顺序保护

## 问题来源

ServiceGo 使用 Google Play Services FusedLocationProviderClient.setMockMode(true/false) 的异步 Task。Lab 25/32/33/34 以前只记录最后一个抵达的回调：例如先发起 ENABLE，随后停止服务发起 DISABLE，但 ENABLE 的迟到成功回调可能把最近的 DISABLE_REQUESTED 状态覆盖掉，导致判断失真。相反，更早的 DISABLE_SUCCEEDED 也可能覆盖较新请求。

Lab 34 只是对保存时间与最近一次服务启动进行比较，无法区分同一次服务内的多个并发 Task。Lab 35 改为**显式请求顺序号**。

## 技术变化

- 新增纯 Java LabGmsRequestOrder，判断某个异步回调是否属于**当前最新发起**的请求。
- LabProviderReliabilityController.beginGmsRequest 在发起 GMS Task **之前**记录递增请求号、当前事件和时间；finishGmsRequest 只有在 requestId 与已保存最新请求号一致时才更新 GMS 最后事件。
- 过期 Task 只增加「已忽略过期异步回调次数」，**不覆盖较新请求的结果**，也不导出原始事件参数或请求号。
- 改造 ServiceGo initFusedMock/requestGmsMockDisable 的相关审计调用，已超时或过期的回调不再把其审计结果视为最近请求。若已停止后 enable 才完成，仍保留原有再次请求 disable 的 best-effort 路径。
- Lab 25 实验仪表盘、Lab 32 恢复诊断和 Lab 33 综合报告能看到过期回调计数，用于识别异步乱序。
- 更改仅限证据记录及本 ServiceGo 内部对收到回调的状态记号，未改变定位坐标、发布频率、system test-provider 注册、第三方 SDK 接口、Shizuku 权限或 UI 入口。

## 不应过度解释

- **只维护「最后发起的请求」的审计状态**，不保证 Android GMS 内部按照该顺序执行 Task，也不能据此判断真实 Google Play Services 当前 mock 状态。
- 不保证旧 ServiceGo 实例的迟到 enable 所触发的额外 best-effort disable 与新实例物理操作完全无竞态；仅保护持久化诊断状态。
- 从旧 Preview 升级的记录此前不带 requestId。新请求建立后才参与此顺序策略。
- 请求顺序只在本应用当前 Android 进程的同步记录流程中形成；SharedPreferences 并非跨独立进程数据库锁或硬件可信时间源。
- **微信、腾讯 SDK、GMS 缓存以及实际定位消费者行为依然没有因这次代码修复而获得验证。**

## 自动化测试

LabGmsRequestOrderTest 新增 11 个纯 Java 回归测试，包括 token 从 1 开始、递增、只接纳最新、迟到 enable/disable 被拒、无效 token、旧服务 token、旧计数器、极值溢出和 Lab 32 精简输出计数（不输出原始标识）。

## Android 13 实机验收

- [ ] 可用同包名 com.soozooq.gogogo.test 和已公开的现有**测试签名**覆盖安装 2.4.0-lab35；签名不同不要卸载旧版。
- [ ] 在手机正常模拟启动、停止时，ServiceGo/GMS 有可用 SDK 的情况下，查看 Lab Diagnostics 的 GMS 请求、成功或失败事件。
- [ ] 重复快速启停，不应有较早的 enable/disable 完成状态冒充当前最新请求；Lab 32/33 综合诊断应显示过期回调计数（仅当确实发生）。
- [ ] 不要把「已忽略过期异步回调」理解成系统已经恢复原始位置；打开 Consumer Matrix 单独观察实时和缓存定位。
- [ ] Shizuku 关闭、Mock AppOps 撤销和权限改变后依然能查看诊断，不自动索取权限。
- [ ] 不应改变 Provider 清理效果或老版本用户现有收藏、预设和地图。
- [ ] 完整测试 Android 13、多个品牌设备和第三方应用实际行为，才能考虑稳定版；CI 自动测试只能证明代码层面通过。

版本：2.4.0-lab35（versionCode 2400040），ARM64 Preview，沿用公开测试签名，绝不能用作正式私钥。
