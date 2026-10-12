# GoGoGo Lab 50 — vivo 双开微信：Android 用户隔离最小只读诊断

日期：2026-10-12 · Preview `2.4.0-lab50-clone-readonly` / `2400057`.

## 研究结论落地边界

v11 研究表明 Android 13 CDD 允许 CLONE 用户配置，且 vivo 原生双开可能是 OEM 自定义实现。Lab 47 的普通权限检查已能报告当前用户 `FLAG_STOPPED`，以及 LauncherApps 可见的另一个用户空间；它**不能**证明另一个微信实际是否运行或被强停。Shizuku 已有 UserService，但旧版 `getUserPolicyReport` 是原始命令输出，可能含设备用户名。本轮提供单独、明确的隐私精简报告。

## 本轮具体改动

1. 复用 **LabDiagnosticsActivity → Shizuku 高级模式**，增加按钮 `Lab 50：微信主号/分身只读检查`，不新增 Lab 页面。
2. UserService AIDL 新增 `getWeChatCloneReadOnlyReport()`，不会自动申请 Shizuku 权限：用户必须自行完成 Shizuku 授权、连接 UserService 并点击按钮。
3. 在 Shizuku UserService 进程内仅执行白名单**只读**命令：
   - `am get-current-user` — 查询当前用户标识；
   - `pm list users` — 查询可见 Android 用户列表；
   - `cmd user list -v` — 若 ROM 支持则寻找标准 `android.os.usertype.profile.CLONE` 类型标记；
   - `pm list packages --user <已枚举的用户ID> com.tencent.mm` — 仅查微信包是否安装，不枚举/返回设备完整安装列表。
4. 原始 Shell 输出、Android 用户昵称及具体数字 ID 只在 UserService 内短暂处理，跨 AIDL 仅返回 `VISIBLE_USER_COUNT`、`CURRENT_USER_WECHAT`、`ADDITIONAL_WECHAT` 等匿名类别信息。
5. 限制读取的用户数为最多 8 个；若权限不足、列举不完整或未查询某个用户，返回 `UNKNOWN`，禁止默认为未安装或已停止。
6. 无新权限、无 `am force-stop`、无 `pm clear`、无 Root 权限提升逻辑、无微信进程/数据/位置信息读取。Shell 运行沿用原有只读工作线程（避免阻塞 UI）。
7. 原有 Lab 47、Lab 48/49 的功能和入口不做改动。

## 判别边界

| 可观测结果 | 能得出的结论 | 不能推出 |
|---|---|---|
| Android 用户列表包含额外用户 | ROM 向 shell 暴露了额外用户 | 一定是 vivo 微信分身 |
| 某额外用户查询到 com.tencent.mm | 该 Android 用户空间可见微信安装 | 微信正在运行、微信定位来源、强停是否成功 |
| 查询到 CLONE 类型标记 | 命令输出有标准 CLONE 类型标记 | 某个具体微信实例一定属于该类型 |
| 未发现额外用户或微信包 | 在这套 API 的访问范围内没有观察到 | OEM 私有双开不存在 |
| Lab 47 当前用户 FLAG_STOPPED | 同用户的包停用标记 | 另一个用户的包/进程已停止 |

**最重要的限制**：这不是修改微信定位或绕过其限制的方法，只是判断 Android 用户/包安装隔离模型。若 vivo 使用特殊私有容器，报告会保持 UNKNOWN。

## 隐私和安全检查

- AIDL 只返回匿名化摘要，不返回用户昵称、账户、应用私有路径、原始进程信息、电话或蜂窝 ID。
- 失败路径只返回固定 UNKNOWN，不附带 Shell stdout、异常消息。
- 包安装是弱证据，不能当进程活动证明。
- 不发送网络请求、不保存用户数据，用户必须主动点击。
- **旧的 User Policy 原始报告入口仍然保留**，但与新按钮分开；用户如不愿共享原始信息，应只使用 Lab 50 精简按钮。
- 新增纯 JVM `LabCloneUserReadOnlyTest` 测试：用户枚举、精确包匹配、隐私泄漏阻止、权限不足 UNKNOWN、多用户、未查询用户、标准 CLONE 类型标记。

## 自动化验证及手机验收

- [x] 代码提交到 `feature/lab50-wechat-clone-readonly-20261012`，基于未合并的 Lab 49 代码。
- [ ] `./gradlew build` 包含单元测试和 Android Lint。
- [ ] Preview APK 构建。
- [ ] vivo Android 13：用户**自行授权 Shizuku**，在设备本地检查用户列表/微信包概况，不上传原始 dumpsys 用户名字。
- [ ] Shizuku 未安装/未授权/权限不足时没有假阳性、没有主线程卡死。
- [ ] 无额外用户但 vivo UI 可见分身时必须提示 UNKNOWN，而不是错误地否定分身。
- [ ] 复测 Lab 47 FLAG_STOPPED 与新增包查询彼此不互相误导。
- [ ] 用户确认主微信、双开微信的实际身份之前，不运行任何强停、清缓存操作。

## 后续

- Lab 51：已经存在的腾讯 SDK GEO / POI / allowCache 对照，先核对真实 Key（CI 使用 DUMMY）和 SDK 回调；不跟微信内部调用链混淆。
- Lab 52：只有收集到有效自有消费者 E3 样本后，才评估 1Hz/5Hz/30Hz 对性能和任务异步积压的影响。
- Lab 48–49、50 都是 **PR 提案，未合并 main**；合并必须具备必要的真机验收。

相关权威参考：
- Android 13 CDD §9.5：https://source.android.com/docs/compatibility/13/android-13-cdd
- Android 用户类型：https://source.android.com/docs/devices/admin/multi-user
- Android shell pm：https://developer.android.com/tools/adb
- Shizuku UserService：https://github.com/RikkaApps/Shizuku-API
