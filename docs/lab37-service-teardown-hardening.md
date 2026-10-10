# GoGoGo Lab 37 — 服务正常退出与异常清理加固

## 这轮确认的实际问题

Lab 36 为 **GMS 迟到 enable 回调的补偿关闭**加了旧实例请求所有权检查，但正常 `ServiceGo.onDestroy` 调用 `requestGmsMockDisable("SERVICE_STOP")` 时依旧走不检查所有权的旧路径。旧服务在一个进程内晚于新服务创建时，其正常退出关闭请求仍可能重获「最新请求」位置，并对新的 GMS 模拟产生干扰。

另外原先三个 Provider 的退出清理连续执行；如果记录落盘或 OEM API 导致其中一次清理抛出未捕捉的 `RuntimeException`，后续 Provider 清理、GMS reset 和监控撤销可能被跳过。

## 修复

- 统一所有权守卫：**正常停止 SERVICE_STOP** 与迟到 enable 的 LATE_ENABLE 补偿关闭均使用 `beginGmsDisableIfCurrent`，先核对本 ServiceGo 最后请求序号是否仍为最新。如果有新的服务启动屏障或更新的 GMS 请求，就阻止旧服务的这一次额外关闭。
- 保留 Lab 36 的先发起新服务 generation barrier 再初始化各 Provider 的逻辑。
- 将被阻止的关闭请求计入历史 `gms_suppressed_late_retry_count`（保持老测试版数据兼容），但在仪表盘里改称「过期服务关闭请求」，包含正常停止和补偿关闭；不新增页面。
- 新增纯 Java `LabServiceTeardownSequence`：按顺序执行多个清理步骤；若某一步抛出 RuntimeException，只计数，不阻止后续步骤继续运行。应用于三个 Provider 的退出清理、GMS 停止请求以及 AppOps watcher/服务生命周期收尾。
- 保留 `super.onDestroy()` 的 finally 保护，保留早已有的 Provider API 历史取证；无故意触发系统级强停，不改模拟坐标、发射频率、Shizuku 权限与 UI 主路径。

## 测试

- `LabServiceTeardownSequenceTest` 新增 6 个 JUnit 用例：正常调用顺序、单个 Provider 失败不会跳过 FUSED/GMS、多个 RuntimeException、null 阶段、空阶段、返回失败统计。
- 现有 `LabGmsRequestOrderTest` 增加 3 个用例：正常停止时仍持有最新请求允许关闭；新服务屏障或新 enable 已启动，旧服务的正常停止不得打断。

## 谨慎界限

上述修复只是减少软件自身无谓的跨会话关闭请求，并防止某次 Java 异常短路其他 cleanup。它**不是** Google Play Services 的事务锁，也不保证系统 Provider 被卸载成功，更不意味着微信或其他第三方已放弃缓存。在真实 GMS 中，已发送但尚未完成的旧 Task 仍有跨请求实际执行顺序问题，只靠本应用请求 ID 无法消除。

## Android 13 真机验收

- [ ] 从 Lab 36 同包名 `com.soozooq.gogogo.test` 测试签名覆盖安装 `2.4.0-lab37`；签名不同切勿为了更新直接卸载保存着测试数据的旧 APK。
- [ ] GPS/NETWORK/FUSED 正常启动、停止循环 10 次，不出现新的闪退、卡死、额外模拟持续。
- [ ] 用真实 GMS 设备在快速关闭→重开过程中检查实验仪表盘新「过期服务关闭请求」计数，确认旧服务的正常停止不会再次发出 setMockMode(false)。
- [ ] 模拟 AppOps 授权被撤回/权限异常时，仍应尝试清理其余 Provider 和 GMS；不可因单项失败而提前 return。
- [ ] Lab 32/33 摘要仍可复制、历史基准不会被覆盖；隐私摘要不包含坐标、IP、SSID、PID、原始日志。
- [ ] 以 Consumer Matrix 和微信实际 UI **单独测试定位行为**，不把 CI 编译或 GMS 回调成功当作兼容已证实。

版本 `2.4.0-lab37` / `versionCode 2400042`。预览用公开测试签名，不是正式发行安全签名。
