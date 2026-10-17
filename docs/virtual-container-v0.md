# 天外天 Virtual Container V0 — 主程序内建诊断

2026-10-17 解冻后开发。主线统一使用 `soozooq/gogogo` 的 `main`，不再长期维护独立容器 APK 或 V0 专属构建工作流。历史 PR #57 仅作开发记录。

## 已实现（源码能力，不代表真机通过）

入口：诊断中心 → 高级研究工具 → 系统研究 → Virtual Container V0。

1. **A → B → A 类加载与文件测试**：只从天外天自己安装的 APK 中提取 `GuestPayload` 的两份类定义，分别通过 `DexClassLoader` 实例执行，按 A→B→A 检查私有文件不串线；报告 Android API、PID、UID、PASS/FAIL。对动态载荷增加 R8 保留规则。
2. **跨进程 Messenger/Binder 测试**：用户手动启动本 App 的非导出 `:virtualv0` Service，一次 Ping/Pong 往返。校验主进程与远端 PID 不同、UID 相同，7 秒无应答则 FAIL。
3. **双进程 A/B 标记状态验证**：按用户点击同时绑定 `:virtualv0` 与 `:virtualv0b` 两个非导出 Service；分别写入随机标记，按 A→B→A→B 回读，验证两个 PID 与宿主各不相同、UID 保持相同、标记互不覆盖。进程异常断开或 10 秒超时明确 FAIL。
4. 复制结果按钮，A/B 文件三项、单 IPC 判定四项、双进程状态判定七项 JVM 单元测试（合计十四项）。
5. 没有新增定位权限；不启动 `ServiceGo`，不请求 Shizuku 授权，不访问第三方 App，不读取微信。

## 整合到 main 的安装行为

- **保持原应用包名** `com.soozooq.gogogo.test`；不使用临时 `.container.v0` 后缀。
- **保持原签名方案、服务和数据目录**。版本变为 `2.4.12-tianwaitian-container-v0`，`versionCode 2400080`，供已有测试版升级。
- 主程序原有 GPS/NETWORK/FUSED/GMS 注入、地点预设、VPN、Shizuku 和诊断入口均不作行为性修改。
- 当前仓库包含公开测试签名材料，不能用于安全的正式公开发行；升级前应备份重要数据。

## 明确不能声称

- 两个 ClassLoader 只是逻辑隔离；跨进程 Service 虽为不同 PID，**仍是相同 Linux UID**。
- 没有虚拟 PackageManager / ActivityManager、第三方 APK 安装启动、Native Hook 或真正的沙箱边界。
- 未在 Android 13 ARM64 真机验收，不能声称微信、QQ、抖音采用指定坐标。
- NewBlackbox 是下一阶段候选代码参考，不代表已整合进天外天。原先额外构建它的单分支 job 不再作为主程序构建条件。

## 验收顺序

1. GitHub 主分支运行 `Build Check`（含 Java 单元测试、Lint）与 `Build Preview APK`；通过后下载新的主程序预览包。
2. vivo Android 13 ARM64 打开 V0 页面，运行 A→B→A 三轮，保存每轮 PASS/FAIL。
3. 单独运行 Binder IPC 两轮，比较 PID/UID，必须不同 PID、相同 UID，确认超时/失败提示可用。
4. 执行新的「验证双进程 A/B 状态隔离」两轮，必须分别看到 A/B 两个子进程 PID 不同、均与宿主不同，两轮 A/B 标记未互相覆盖；观察超时、进程断开时 FAIL。
5. 回归验证原 App 启动、模拟启动/停止、地图点位回填、VPN 与 Shizuku 设置、既有数据不丢失。
6. 满足以上条件后，再在**同一 main 分支**逐步引入可审计的真实 guest Application/Activity 生命周期；单独用自有无敏感测试 APK 验收，不登录真实微信。

任何自动构建状态、安装结果或微信结果，均须以真实 CI/设备记录为准。

## CI 记录边界（2026-10-17）

主程序 `Build Preview APK` 已更新为 `:app:testDebugUnitTest` 通过后才编译 APK，并检查包名、启动 Activity 与 V0 双进程组件确实进入安装包；诊断 artifact 附 JVM 测试结果。纯 Java A/B 判定本地 JDK 21 烟测 **8/8**，不代表 Android 构建或真机通过。

通过 GitHub Actions API 查询：截至此次检查，main 最近一批已登记工作流仍为 2026-10-14 的旧提交 `d49ddeb1`，包括 [Build Check #38508535392](https://github.com/soozooq/gogogo/actions/runs/38508535392) 和 [Build Preview APK #38508535424](https://github.com/soozooq/gogogo/actions/runs/38508535424) 成功。**10 月 17 日新增容器源码尚无已核验的新 CI 运行。** 用户需从 Actions → Build Preview APK → Run workflow（main）手动触发一次；同样触发 Build Check。若 Actions 结果有失败，以真实失败日志为准。
