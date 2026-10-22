# Codex 工单：天外天 V3.1 未安装独立演示 APK 的 Host 代理 UI 首次运行

**状态：容器已按用户 2026-10-21 指令解冻。V3.1 编码及双 CI 已完成，真实手机尚待回传；用户随后明确要求简化实验操作、继续开发，下次统一回传。当前扩展见 [应用运行与状态保存](../virtual-container-runtime.md)。**

**执行更新（2026-10-21）：** 已实施独立 demo、精确 SHA 准入、Host 代理 View 与配套 CI；[实现与五步手机验收](../virtual-container-v31.md)。无 Root、单手机、无电脑为当前约束，代码、构建及真机分别验收，Draft PR 保持待审查。

## 代码基准和目标

仓库 `soozooq/gogogo`，以当前 `main` 为准。上次已验收的稳定基线：2.4.24，`versionCode 2400092`，`applicationId com.soozooq.gogogo.test`，签名沿用原项目。已有 V0–V2.4 受控 APK 的 ClassLoader、资源 View、Manifest、Intent 代理、Session、Activity.recreate、独立子进程重启回放协议等真机 PASS；V3.0 仅完成外部 APK 只读归档分析，**未实现执行第三方 APK**。

主任务：将 V3 从「只读扫描 APK」推进到 **一个独立构建、未安装到手机的开发者受控 APK，可以在天外天私有宿主代理 Activity 里真正显示自有 UI、响应点击并在退出时清理**。

这只是可复现工程原型，不能将它写成已支持微信、QQ 或任意 APK 虚拟化。

阅读并复用：
- `docs/virtual-container-roadmap.md`
- `docs/virtual-container-v0.md`
- `app/src/main/java/com/zcshou/gogogo/VirtualContainerV3ArchiveActivity.java`
- `app/src/main/java/com/zcshou/gogogo/virtualv0/ContainerV3ArchiveProbe.java`
- `app/src/main/java/com/zcshou/gogogo/VirtualContainerV1ProxyActivity.java`
- `app/src/main/java/com/zcshou/gogogo/VirtualContainerV23RecreationActivity.java`
- `containerFixture`、`ci/verify_container_fixture_contract.py`、`.github/workflows/build-release.yml`

## 实施顺序（必须做真实编码，不要纯研究报告）

1. **外部 demo 制作**：提供能够单独从源码产出 APK 的极小 sample（不同 package，独立于天外天宿主和当前嵌入 `assets/twt-fixture-v0.apk` 的 fixture），带一个布局、文字/颜色资源、Button 点击计数、Guest 生命周期/状态接口。说明如何生成 APK 和在用户手机中作为普通文件选择；严禁偷偷把它改成宿主已预埋的同一 APK。
2. **最小可信导入**：只对这份开发者受控 demo APK 开启执行路径。显式选择 APK 后校验 package、APK 内容哈希或已核实签名/公钥、ZIP/Manifest/DEX/资源、最大文件大小、版本/期望入口。任意其他 APK 继续**只读解析、拒绝执行**，包括微信和 QQ。不要安装 demo APK、绕过系统权限或执行不受信任的 DEX。
3. **受控 Host 代理界面**：新建 `exported=false` 的 Host stub Activity，复用/改良已验证的 V1 APK 资源桥，在明确准入后经隔离 ClassLoader 载入 demo Java 入口并实际创建来自 demo APK 资源的 View。验证按钮响应、Activity 回退、重建（可逐步先保证安全 fail-closed）、状态隔离、退出清理。所有 Activity 实际由天外天宿主注册和启动。不要把“代理 View”伪装成未安装 Guest Activity 由 Android 系统原生启动。
4. **诊断闭环**：复用 V0 一次复制总报告，V3.1 独立附录注明 demo APK SHA、resource package、ClassLoader、Host PID/UID、Guest View 创建和按钮计数、生命周期/失败阶段、退出时清理证据。旧 V0–V2.4 已通过的附录不覆盖。失败必须 FAIL/INCOMPLETE，不得造 PASS。
5. **CI 回归与可复现产物**：新增 demo 构建/单测与 V3.1 安全静态契约；保留原有 Gradle lint，确保主 APK 内确有注册 Host 代理、新 demo APK 是真正独立构建产物且不被安装到系统。两条现有 GitHub Actions 需都绿，输出代码 commit、运行 URL、APK SHA、文件改动清单。注意现有用户只有 Android 手机，需要提供云端 APK 和非常短的实际测试手顺。
6. **同步更新路线图**，写清实际运行的边界；如果遇到 Android 13 API/资源桥阻碍，必须给出最小可运行的可证伪试验和具体 blocker，而不是绕过边界制造假通过。

## 不能改变的部分

- `applicationId=com.soozooq.gogogo.test`、原有签名配置，`versionCode` 单调增长（**大于 2400092**），原位覆盖安装，**不卸载**。
- 原正常位置模拟启动/停止/ServiceGo、Shizuku、微信/缓存实验代码不能修改；本工单**不研究位置缓存**。
- V0–V2.4 已通过代码不为了本功能重构或删除，现有 V3 只读解析应继续可用。
- 不能要求 ROOT、任意未知来源安装权限、任意第三方 DEX 执行、访问微信/QQ 私有数据；不得添加隐藏 API/危险提权方案。
- 第三方项目只能参考经许可核对可合法使用的具体实现，不要无授权搬运 VirtualApp 商业代码。
- 少分支，**一个临时功能分支 + 一份 Draft PR**，暂不合并、不要覆盖 `main`，等 GPT 与用户审查。若实际 Codex 工作环境不支持草稿 PR，至少提交可审核的普通 PR，并注明仍待验收。

## 何时才算完成

- CI：构建/单元测试/Lint 双绿；
- 代码：单独 demo APK 经明确选择后，受控 Host 确实创建资源 View、点击响应且有真实回调证据；
- 物理机：用户的 Android 13 vivo 回传 V3.1 真实一键诊断 **PASS**。**Codex 不能代替用户宣称真机已通过。**
- Codex 最终回传：**PR 链接、CI 链接、APK 下载位置、哈希、风险/局限、Android 手机 3–6 步测试流程**。

目标是尽快得到第一个可用的独立 APK 原型，而不是泛化整个 Android 虚拟包/权限/系统组件代理。
