# 天外天 Virtual Container V0 — 主程序内建诊断

2026-10-17 解冻后开发。主线统一使用 `soozooq/gogogo` 的 `main`，不再长期维护独立容器 APK 或 V0 专属构建工作流。历史 PR #57 仅作开发记录。

## 已实现（源码能力，不代表真机通过）

入口：诊断中心 → 高级研究工具 → 系统研究 → Virtual Container V0。

1. **A → B → A 类加载与文件测试**：只从天外天自己安装的 APK 中提取 `GuestPayload` 的两份类定义，分别通过 `DexClassLoader` 实例执行，按 A→B→A 检查私有文件不串线；报告 Android API、PID、UID、PASS/FAIL。对动态载荷增加 R8 保留规则。
2. **跨进程 Messenger/Binder 测试**：用户手动启动本 App 的非导出 `:virtualv0` Service，一次 Ping/Pong 往返。校验主进程与远端 PID 不同、UID 相同，7 秒无应答则 FAIL。
3. 复制结果按钮，A/B 三项及 IPC 判定四项 JVM 单元测试（合计七项）。
4. 没有新增定位权限；不启动 `ServiceGo`，不请求 Shizuku 授权，不访问第三方 App，不读取微信。

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
4. 回归验证原 App 启动、模拟启动/停止、地图点位回填、VPN 与 Shizuku 设置、既有数据不丢失。
5. 满足以上条件后，再在**同一 main 分支**逐步引入可审计的真实 guest Application/Activity 生命周期；单独用自有无敏感测试 APK 验收，不登录真实微信。

任何自动构建状态、安装结果或微信结果，均须以真实 CI/设备记录为准。
