# 天外天虚拟容器：从实验室到可运行外部 APK 的真实路线图

更新时间：2026-10-18。状态：规划及代码审计记录，不是支持或兼容承诺。

**2026-10-21 继续运行能力：** 按用户要求将旧实验折叠，并实现私有可信导入后重复打开、备注/计数/页面保存恢复、实际应用内返回、重建时重新核验恢复、统一导出及单个手机更新包。见 [当前运行能力](virtual-container-runtime.md)。本轮继续代码与云端验证，手机记录下次合并回传；不会把既有内部测试数量当作微信/抖音或任意 APK 兼容进度。

**2026-10-21 V3.1：** 已按 [Codex 工单](tasks/codex-v3-1-external-demo-host-proxy.md) 编写独立 demo、精确哈希准入和私有 Host 代理 View；详见 [实现与手机验收](virtual-container-v31.md)。demo 不预埋在宿主，任意其他 APK 仍仅读取。构建与真机结果分别验收；L2/L3 和微信等复杂应用兼容尚未完成。

## 目标和验收标准

用户真正需要的是天外天 **在不正常安装 APK 的情况下启动另一份应用**，而不是累计更多受控 fixture PASS。分为四层：

| 层级 | 能力 | 具体验收门槛 | 状态 |
|---|---|---|---|
| L0 受控 APK 基础 | 自编 APK 的 DEX、资源、View、Manifest、IPC、Guest 会话与子进程终止恢复 | Android 13 一次总日志与 SHA 对齐 | V0–V2.4 多次真机 PASS，均共享宿主 UID |
| L1 真实 APK 读取 | 用户主动选择非项目提供的第三方 APK，读取 Manifest 组件、DEX、资源表、ABI；不装载执行代码 | 真实 APK 的组件清单与 SHA 可重复，坏包 FAIL，不执行 DEX | 2.4.24 V3.0 候选代码，等待 CI/真机 |
| L2 最小未安装 APK 运行 | 独立安装包的 Launcher Activity，真实 Guest Context / resource / theme，宿主 stub Activity lifecycle，Intent result，权限和崩溃隔离明确 | 至少一份**不是天外天内部的 fixture**、预先单独编译的无特权 APK，在不被系统安装的前提下进入 UI、操作、退出、恢复 | **尚未完成** |
| L3 有限第三方兼容 | 多组件协调、Provider/Service、虚拟 PackageManager、文件隔离、系统服务重定向、目标 API/ABI 测试矩阵 | 多种普通第三方 APK 在 Android 13 实机长期运行，不混淆宿主权限 | **尚未完成** |
| L4 复杂大应用 | 微信、QQ、GMS、Native、完整权限与完整兼容性 | 各软件不同版本、Android/OEM 组合真机回归 | **无法保证**，不可凭底层测试宣称兼容 |

## 下一阶段优先次序（严禁无意义地重复原实验）

1. **P0：V3 只读外部 APK 审计**。Android SAF 选择用户拥有的 APK，按 ZIP/包管理器解析 Manifest、组件、资源和 ABI；**不运行外来代码**。首版 2.4.24 仅交付这一环节。
2. **P1：V3 独立样例 APK 完整最小 UI**。不再把自编 fixture 直接放在 host assets 内视为通用容器；设计一个单独输出、未安装在手机的演示 APK，通过只限该受控演示 app 的 Host stub 与显式准入协议加载，并记录 manifest/activity/theme/lifecycle 意义上的原生行为与代理边界。
3. **P2：基于真实测试的虚拟包管理/Intent/组件注册。** 建立只读映射模型；从最小单 Activity 逐步增加第二 Activity、Intent result、简单 Service 和 Provider。权限应遵循宿主权限边界，不能伪装为真正独立 Linux UID 沙箱。
4. **P3：与成熟框架逐模块比较/复用评估。** BlackBox、VirtualApp 源码需逐模块审计现代 Android 支持、许可、目标 SDK、维护、未知 API、滥用风险。不能未经核实复制商业版或把仓库自述当作实机兼容证明。
5. **P4：独立的 V2.5 故障持久性工作**（延后但不丢弃）。主进程冷启动恢复方案要在安全的专用测试应用/被控环境做；不要在天外天主进程上简单 kill，避免用户定位功能被中断。用日志 schema、generation token、写入前/后故障注入验证。
6. **P5：有限第三方应用兼容矩阵。** 单独逐项测试 Native ABI、Provider、通知、登录、权限、设备/OEM；微信等大应用列为长期独立研究目标，不能因为一个样例成功就承诺微信。

## 时间范围（不构成保证）

下列假设：持续开发、CI 可用、每周真机验证、已有源码可重用；遇到系统 API、厂商保护、软件许可或修复周期会延长。

- L1 外部 APK 可重复解析：1–3 周（首版已在本轮开始）。
- L2 最小独立 APK 真正运行：1–3 个月。
- L3 部分普通 APK 可重复运行：6–12 个月或更久。
- L4 微信/QQ、Native 与服务完整兼容：不可可靠估时；可能要远多于一年，亦可能在无 root / Android 原生保护下达不到目标。

**完成度不得用 V0–V2 的 PASS 计数折算百分比**；以后每版明确写“哪些真实 APK 真正启动和能做什么”。

## 参考实现与许可初筛

- Android Activity/Manifest 官方说明：https://developer.android.com/guide/components/activities/intro-activities
- VirtualApp 原作者架构和商业授权说明：https://github.com/asLody/VirtualApp （官方仓库声明商用/源码授权与商业版区别，未获得授权前不整包搬运）。
- BlackBox 开源参考：https://github.com/gmh5225/Android-BlackBox （README 标 Apache 2.0，但要逐文件及依赖核对）；自述早期只保证 Android 5–12，不应外推至现有 vivo Android 13 或更高。
- Android 11–16 VirtualApp fork 的已知问题示例：https://github.com/ISEKHON/VirtualApp/blob/main/docs/KNOWN-ISSUES.md （维护者自述，需实机验证；Hidden API、ART 和 GMS 是主要风险）。

## 现存限制与安全

- 一直保留 `com.soozooq.gogogo.test`、签名、V0 一键五项及总报告一次复制。
- 外部 APK V3.0 只是只读解析，不安装/启动/加载未经信任的 DEX，不主动读取其他应用数据。
- 将来执行外部 APK 必须先完成可信度、权限及隔离边界审计；Android 宿主同 UID 不等于独立沙箱。
- 位置兼容单列，不把系统 Mock、Shizuku、虚拟组件、地图蓝点、缓存、微信/QQ 服务端结果混为一谈。
