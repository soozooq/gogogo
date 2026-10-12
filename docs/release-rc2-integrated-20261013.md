# GoGoGo 2.4.0-rc2-integrated — 主分支整合候选版

日期 2026-10-13 · Android arm64-v8a · `com.soozooq.gogogo.test` · `versionCode=2400061` · `versionName=2.4.0-rc2-integrated`

## 已整合

- **PR #51**：通过 squash merge 将 Lab 48–52 + RC1 累积工程真正并入 `main`，提交 `ee15a29ed163900308053ff2575f03860d47d39d`。此前五个历史草稿 PR #46–#50 的变化均被这次合并包含；只作为历史记录保留，不再单独合并。
- **RC2 可辨识性**：应用图标下面的显示名称从「影梭」变为「GoGoGo」；通知提示同步调整。应用包名、签名、模拟逻辑以及已存在的数据存储均保持不变。
- **单包更新**：版本从 RC1 的 2400060 提升为 RC2 的 2400061，可在签名一致时覆盖升级；无需安装五个独立 Lab 版本。
- **数据安全**：不自动更改定位权限、不清除微信数据、不强停微信、不引入新定位注入方式；保留 RC1 的诊断与匿名分享。

## vivo Android 13 真机证据，必须准确陈述

| 层次 | 真实结果 | 局限 |
|---|---|---|
| E1/E2 | GMS 模拟 Task 完成 583 / 583、无失败或 pending，平均任务回调 4.4ms | Task OK ≠ 任意第三方采用模拟 |
| 发布节奏 | 原 33ms sleep 实测循环约 16.5Hz，无超过 100ms 的记录间隔 | 不能通过平均 Hz 判断特定 SDK 读取 |
| 被动缓存 | GPS/NETWORK/GMS 各两次，共 6/6 含 Mock 标记的新鲜样本 | 仅缓存读取，非新请求 |
| E3 主动消费者 | Framework GPS/NETWORK 和 GMS Updates 的 12/12 条记录新鲜、mock=YES | 仅 GoGoGo 独立 `:consumer` 进程 |
| 正常停止 | Provider 清理 API 返回；GMS 最新历史为 DISABLE_SUCCEEDED | 历史回调不证明 OS 缓存完全清空 |
| E4 腾讯 | 尚无带真实 Key 的 SDK 对照 | 本 CI 使用 DUMMY |
| E5 微信主号 | 自动位置与点击重新定位后仍显示福建 | 未采用模拟，根因 UNKNOWN |
| E5 vivo 双开 | 也显示福建，具体地点不同；距离显示不变 | 未采用模拟，不能确定两实例是同源缓存 |

## 预览签名安全

当前仓库中已有 `keystore/GoGoGo.jks` 和明文签名配置，故 **目前的构建仅用于现有自有设备的测试升级**，并非具备可信软件供应链的正式公开发布。保持旧签名是为避免用户手机无法覆盖升级；未来正式发布必须使用未放入仓库的新签名、可审核的构建秘密管理和迁移说明。不要在公开仓库新增秘密。

## 可重复验收

- [ ] 新分支完整 CI：Debug、Release unit tests 与 lint
- [ ] `main` 合并 RC2 PR 后的 Build Preview APK 成功
- [ ] 主分支 APK 内包名 `com.soozooq.gogogo.test`、版本 2400061、应用标签 GoGoGo
- [ ] vivo 设备覆盖安装 RC1，不清除用户数据且能启动模拟
- [ ] Location Compatibility 与 Consumer Matrix 功能和 RC1 一致
- [ ] 不把微信未兼容写成成功，也不将旧签名版宣传为正式安全版

本页是 RC2 集成候选版的验收证据，不承诺微信主号/分身模拟成功。