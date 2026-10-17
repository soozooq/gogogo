# 天外天 Virtual Container V0 — 可执行第一阶段

2026-10-17 用户已解除开发冻结。代码在独立分支的 PR 中，不直接合并 main。

## 已实现的范围

诊断中心 → 系统研究 → Virtual Container V0 可手动执行。
使用天外天自身已安装 APK 内的 GuestPayload 作为可信受控载荷，通过
两个 DexClassLoader 分别定义相同全限定类名。在同一轮中按 A → B → A 顺序
向独立私有目录写入并检查文件，记录加载器身份、PID 与 PASS/FAIL。
不接触真实设备定位、不自动启动 Mock、无需第三方帐号、无额外联网。

## 不能声称的能力

V0 两个实例仍在同一个 Android Linux UID 和宿主进程中。
两个目录只是逻辑分离，**不是安全隔离边界**；尚不能安装/启动任意第三方 APK，
没有虚拟 PackageManager/ActivityManager、Binder 服务代理、独立 Native 库装载，
也不能证明微信位置模拟兼容。已存在的 Isolated Capsule / Work Profile
实验保持原样，不重复实现。

## 验收

- CI: ./gradlew :app:testDebugUnitTest :app:assembleDebug
- Android 13 ARM64 真机：进入该页面，连续执行三轮，保存每轮 PASS/FAIL。
- 每轮要求同名 Class 对象不同；A/B 文件不串线；无需重装官方微信。
- 下一阶段：在安全审查/许可证审查后，以 NewBlackbox 源码为容器候选，
  引入真正的 guest Application/Activity 生命周期与进程身份隔离。
  只有真机通过后，才进入腾讯 SDK/微信功能验证。

## 首次构建安全措施（补充）

仅 V0 分支的 debug 包使用 `applicationIdSuffix ".container.v0"` 和独立版本名后缀。
**不得覆盖手机上已有的天外天应用及其数据。** 新包安装后与原版并存。
报告页增加一键复制，可直接回传 PASS/FAIL、Android API、PID、宿主 UID。
已为动态加载目标类增加 R8 keep 规则，防止以后 release 混淆去除测试载荷。

GitHub Actions 入口：如果自动 push/PR 没有产生工作流运行记录，使用仓库默认
`Build Preview APK` 的 **Run workflow**，选择 `feat/tianwaitian-container-v0-20261017`。
本分支的 `Tianwaitian Container V0 Preview` 只在该工作流被 GitHub 注册后
才能从 Actions 面板手动分派；代码提交成功并不等于 APK 已编译。
