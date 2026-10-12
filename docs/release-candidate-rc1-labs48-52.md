# GoGoGo RC1 — Lab 48–52 整合验收候选版

日期：2026-10-13 · `2.4.0-rc1-labs48-52` · versionCode `2400060` · 包名沿用 `com.soozooq.gogogo.test`

## 发布策略

这是一份**只用于验收的累积候选包**，不是微信兼容性正式成功版本。构建从 Lab 52 已通过的最新分支起步，累计包括 Lab 48–52 证据改造及本轮 RC1 匿名分享入口。保持 `main` 原样，直到至少完成规定的 vivo Android 13 真机验收。

历史评审：#46（GMS Task）、#47（消费者时间轴）、#48（Shizuku 分身只读诊断）、#49（腾讯 GEO/POI × 缓存）、#50（循环节奏与积压）。五个 PR 都是草稿，基础关系是依次叠加；真正需要用户下载安装验收的是**RC1 这一个累积包**，不用回头分别安装五版。

## RC1 本轮新增

- `LabReleaseAcceptanceSummary`：只汇总本次 ServiceGo 已有计数；未连接时返回 UNKNOWN，绝不把无数据当“全部成功”。
- 在已有 `LocationCompatibilityActivity` 追加“分享 RC1 匿名验收摘要（不含坐标）”按钮，**只有用户点击才打开系统分享选择器**；不额外触发 Framework/GMS/Tencent 请求、不尝试读取微信任何内容。
- 摘要内容：Framework GPS/NETWORK/FUSED 提交/失败计数、GMS Task 返回/完成/失败/积压、实际 tick Hz 和平均 Task 完成耗时，明确标 E3 自有消费者、E4 腾讯定位、E5 微信业务为 `NOT_CHECKED`。
- 文案只声明本机 GoGoGo 观察到的证据，不泄漏坐标、微信双开用户编号、SDK key、SSID、IP 或原始错误消息。
- 单测覆盖服务未连接、任务成功≠微信成功、失败/待完成、无数据时 UNKNOWN、隐私字段。

## 自动化核对

- [ ] `./gradlew build`：Debug/Release unit tests，Android Lint
- [ ] `assembleDebug`：签名、包名、versionCode 及 APK
- [ ] Release APK / Gradle compile successful
- [ ] 以前的 5 轮新增测试依然通过（#46–#50）
- [ ] 以上通过后，在 PR 附 CI 链接与提交 SHA；没有自动测试的部分仍显示 UNKNOWN

## 真机验收（不需要安装五个版本）

用户仅安装 RC1，一个候选包即可核对下列区域：

1. **基础功能**：模拟启动/停止正常；首页地图、快捷按键、权限流程不回归。不要在已有数据未备份时卸载旧版；签名不同就先停止，不进行清数据覆盖。
2. **Location Compatibility**：开启模拟后查看 Provider 提交数、GMS Task 成功/失败及 Lab 52 实际 tick；停止服务时不产生新的提交；点击“分享 RC1 匿名验收摘要”把匿名文本发送给自己（例如此聊天），默认不上传服务器。
3. **Consumer Matrix**：主动消费者得到的 fixAge/isMock 是 E3；“Passive Snapshot”仍然只读取缓存，**不新增主动订阅**，如两者有冲突保留原始实测信息。
4. **腾讯 SDK**：无 Key 时应明确 unavailable，不可伪造成功；有真实 Key 且获得知情同意时，验证 GEO/POI × cache 四组，不把 SDK 成功推断成微信采用。
5. **Shizuku 分身**：授权后可按需执行匿名包安装/多用户查询；权限不足标 UNKNOWN；无强停、无清缓存、无微信私有数据访问。
6. **兼容性判定**：必须把微信主号/分身的用户可观察页面结果分别记录，与 E1/E2/E3/E4 分开。**未经这一步不可宣称破解或提高了微信兼容率**。

## 可合并门槛

- 所有 CI 绿只是条件之一，不能替代用户设备验收。
- 真机验证通过后优先评审一个 **RC1 累积 PR**，可一次 squash merge，避免把五个测试 PR 逐个安装/合并，历史 PR 用于追溯；未征得确认之前不改 main。
- 发现无效 Key / ROM 私有机制 / 双开用户空间不可观察，应显示 UNKNOWN 而非补造证据。

## 已知无法凭本轮代码解决的内容

- vivo 原生双微信使用的实际用户/沙箱方案仍未知；Android 13 标准 CLONE 只是一种可能。
- Google FLP Task 成功不代表腾讯 SDK、微信页面采用模拟坐标。
- 33 ms 是休眠时间而非准确的 30Hz；当前并未调整原发布频率，也未实现基于自有 E3 实测的 A/B 频率。
- 微信、抖音、美团之间的网络缓存/地理反查差异仍需实机识别。RC1 只能减少重复验证、提高证据可信度，不是最终定位兼容补丁。
