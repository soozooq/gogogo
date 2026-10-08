# GoGoGo 统一 Preview · 安卓手机验收步骤（2026-10-09）

## 目前确实已完成的事情

- Labs 1–25 已经通过 [PR #5](https://github.com/soozooq/gogogo/pull/5) 合并至 `main`：`8115bdfc12de7aab517f7873e8f35954c8fae10c`。
- **同一提交**的完整 Build Check [通过](https://github.com/soozooq/gogogo/actions/runs/37855976504)。
- **同一提交**的 Debug APK [构建通过](https://github.com/soozooq/gogogo/actions/runs/37855976542)。
  Actions 页面下方 **Artifacts** 里选择 `gogogo-unified-labs-preview`，下载的是 **ZIP**（约 21 MB）；在安卓上解压 ZIP 才能取得 APK。可能需要登录 GitHub 才能下载构建工件。
- 旧 `main` 保存在 `backup/main-before-labs-20261009`，以备对照。
- 这是 **Preview 测试版**，不是「微信兼容问题全部解决」的声明。

## 装机之前的两件事

1. **不要先卸载当前能正常使用的版本。** 本 Preview 的包名为 `com.soozooq.gogogo.test`，它使用公开仓库中已有的**测试签名证书**。只有包名和证书都匹配时才能原地升级。若系统提示签名不兼容或覆盖失败，先停止安装并保留现有应用数据，不要为了测试强行卸载。
2. 以自己的备用/测试定位场景操作。不要上传私人真实位置、Shizuku/系统完整日志、Wi-Fi 信息、联系人信息或任何私有 Tencent Key 到公开 issue。导出诊断前应检查并打码。

## 最小实机验收（先走 A、B，不必一次做完）

### A. 安装、服务正常生命周期（优先级 P0）

- [ ] 在手机上取得并解压上面的 Preview APK；确认版本显示为 `2.4.0-lab25`，确认可以正常打开。
- [ ] Android 设置 → 开发者选项 → 「选择模拟位置信息应用」，选择此 GoGoGo 测试包，确保系统定位、应用定位权限已开启。**首次试验不必启用 Shizuku 高级模式。**
- [ ] 打开「GoGoGo Lab · 实验仪表盘」（Lab Diagnostics），点「📸 保存环境快照」作**启动前基线**。
- [ ] 在 GoGoGo 中选一个易识别且没有隐私风险的**测试坐标**，启动常规模拟位置。进入「Consumer Matrix（五通道）」或「刷新 One-shot」，记录每条实际可用通道的坐标、Mock 标识、时间/新鲜度。**不存在的 SDK/数据源记为不可用，不能伪造 5/5 成功。**
- [ ] 正常停止 GoGoGo 模拟服务，回到实验仪表盘点「↻ 刷新服务取证」，查看「Test Provider 清理取证（Lab 25）」中 `startup_` 和 `provider_` 结果、`REMOVE_RETURNED`/`ALREADY_ABSENT` 或明确的失败状态。
- [ ] 再保存一份快照并对比。**已缓存的 Last Location 仍显示旧坐标，不一定代表当前持续 Mock；应结合 Mock 状态、时间戳、Provider 清理结果判断。**

### B. 异常恢复与 Shizuku（优先级 P0 / P1）

- [ ] 仅在 A 阶段稳定后：模拟位置正在运行时，从系统设置里对 GoGoGo 执行「强行停止」，重新打开 GoGoGo；查看上一次权限、`previous_outcome`、`PENDING` 和下一次启动扫尾结果。强停期间没有 `onDestroy()` 回调，**不能假设它执行了正常清理**。
- [ ] 对照启动前、强停后、重新打开后的 Consumer Matrix；如可用，检查 GMS 最近的 `ENABLE_*`、`DISABLE_*` 事件。**`DISABLE_REQUESTED` 不是成功，必须区分 `DISABLE_SUCCEEDED`、失败和无权限。**
- [ ] 如平时确实使用 Shizuku，单独测试一次取消/恢复授权，确认能记录权限变化与恢复失败。不要求用户更改系统隐藏设置，也无需在第三方 App 注入代码。
- [ ] 若出现闪退、持续假坐标或服务无法停止，先暂停进一步实验，保存最少量的相关错误截图；原 APK 和旧 `main` 备份都保留，不继续反复卸载。

### C. 位置消费者行为对照（优先级 P1）

- [ ] Android / GMS：确认 Consumer Matrix 里 GPS、NETWORK、framework FUSED、GMS Fused 各自的可用性、时间戳、坐标与 Mock 标识。GMS 调用计数目前记录**请求提交数**，不是 SDK 接收成功数。
- [ ] 腾讯 SDK：公开 CI 使用 `TENCENT_MAP_KEY=DUMMY`，所以公开 APK **不能当成腾讯定位测试成功的证据**；需要另外制作带有效的**应用自有 Key** 的私密版本，并在许可下分别测试 GEO/POI、缓存启用/关闭、Fresh/持续回调。
- [ ] 若需要对照微信：分别观察地图中心、自动定位的城市/坐标、搜索 POI、距离标签及首次/再次打开时的变化。微信显示旧城市不自动等于系统 Mock Provider 有残留。请仅在用户自己的设备上用普通应用 UI 做黑盒对照。

## 如何反馈最有价值

建议先发 **三张打码后的截图**，不用一口气录很长的视频：

1. 「实验仪表盘 → 模拟服务 / 进程生命周期（Lab 23）」及「Test Provider 清理取证（Lab 25）」；
2. 「Consumer Matrix（五通道）」的结果；
3. 如果发生异常，再发对应操作前后的状态或报错截图。

请同时注明：Android 版本、是否开启 Shizuku、是否做过强行停止、测试的是正常停止还是权限撤销、测试坐标采用 WGS84 还是 GCJ-02（如果知道）。

**通过标准**：服务可正常启动停止；记录能区分真实的 API 返回、调用失败、未结束和缓存旧值；异常退出后的恢复符合记录；不把未经验证的 Tencent / 微信兼容性宣称为成功。另行决定正式发布签名，才能进入稳定版发布阶段。

详见 [Lab 25 完整发布阻断清单](lab25-release-readiness.md)。
