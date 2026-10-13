# GoGoGo 远程分支盘点与归档（2026-10-13）

## 盘点基础

- 核对仓库 `soozooq/gogogo` 的 GitHub branches API，盘点开始时共 **85** 个远程分支，当前主线 `main` 为 `e279fbb7e8b8bf2dc37d8149188822812e81ec50`。
- GitHub open PR 数为 **0**。历史积压 PR #33–#45、#46–#50 均关闭；PR #51、#52、#53 已整合。
- 对 85 个分支逐个比较其 Git tree 全量文件路径与最新 `main`。**只有** `research/wechat-location-20261008` 含一个主线缺失路径：`docs/research/2026-10-08-wechat-location-compatibility-round2.md`。本次在维护 PR 中从原分支完整复制到 `main`，没有重写或改动原文。
- 除了上述研究文档，所有旧分支包含的源码和文档**文件路径**均已存在于 `main`。这说明没有独有路径遗落，**不证明**所有历史行级改动或语义均应该在最新主线中存在；早期的兼容性探索与后期重构可能故意有所不同。
- 主线当前整合工作由 PR #53 完成，版本 `2.4.0-vpn-follow-integrated` / `2400068`，恢复旧 VPN 跟随、锁定、时区、自动诊断以及网络/跨应用工具，同时保留 Lab48–52。

## 保留的分支

1. `main` — 唯一当前发布/开发主线，永不删除。
2. `backup/main-before-labs-20261009` — 旧版紧急恢复基准，不等同于最新版。
3. `x8-compat` — 较早 X8 兼容实验，内容不是主线发布计划的一部分，保留供差异研究。
4. `fix/x8-modern-amap` — 旧 X8/高德兼容方案，独立技术对照。
5. `fix/amap-modern-android` — 较早高德/新版 Android 兼容方案，独立技术对照。

对这些保留的特殊分支不声称它们已验证或优于 `main`，日后逐一验证后再决定是否删除。其他旧阶段性分支主要用于历史追溯，GitHub closed/merged PR、提交记录与 Actions 日志仍可查看。

## 清理规则（删分支，不删源码、不重写历史）

- 清理对象限定于本次清单中的旧 feature/feat/fix/perf/docs/release/integration/research 分支；不碰 `main` 或上面的 4 个保留分支。
- 删除前检查分支 HEAD SHA 与盘点时的期望值一致；任何人有新提交则**跳过**，避免删除新增工作。
- 若 GitHub 检测到仍有 open PR 引用分支，跳过；如权限不足，输出错误并中止，不谎报清理成功。
- 完成后重新读取 GitHub branch 列表，核实删除数与剩余数。删除 Git ref 不能当成代码删除；但未合并提交可能随保留引用减少而失去长期可达性，因此本次额外保留特殊分支和独有文档。
- 保留这份清理报告与 PR 讨论记录，不保留永久自动清理作业；任何临时作业执行后应从 `main` 移除。

## 非本轮范围

不改 GoGoGo 的定位注入、微信兼容性、GMS/腾讯请求策略或 VPN 开关逻辑。微信主号/分身此前仍未采用模拟坐标，不能因合并或删分支而更改 E5 验收结论。当前仓库含公开旧签名材料，旧测试签名不适合生产公开发布。

## 清理执行记录（2026-10-13）

- 删除前：**87 个远程分支**（最初盘点 85 个 + 维护/统一归档分支各 1）。
- 一次性 [GitHub Actions 清理任务 #1](https://github.com/soozooq/gogogo/actions/runs/38332222401) 结果：`PRUNE_TOTAL 81 DELETED 81 SKIPPED 0 FAILED 0`，Job SUCCESS。
- 删除后：**6 个分支**：`main`、`backup/main-before-labs-20261009`、`x8-compat`、`fix/x8-modern-amap`、`fix/amap-modern-android`、`archive/pruned-branches-20261013`。
- 统一归档分支顶端提交 `debe8cddb2b2f1a1f585096e6a36f673bc1a9ca8`；通过 7 个合成多父提交保留原 81 个旧分支 HEAD 的 Git 历史引用。归档不是功能开发主线，**不要合并回 main**。
- 精确清理快照：`docs/maintenance/branch-prune-snapshot-20261013.json`，包含已删分支名与当时 HEAD SHA，可用于未来按 SHA 寻找历史版本。
- 一次性清理 workflow `.github/workflows/one-shot-prune-branches-20261013.yml` 已在任务完成后从 main 删除，不保留未来自动删分支任务。
- 本次唯一遗漏的研究文档 `docs/research/2026-10-08-wechat-location-compatibility-round2.md` 已经通过 [PR #54](https://github.com/soozooq/gogogo/pull/54) 原样归档到 main。
- 删除旧分支不意味着抹掉 closed PR、GitHub Actions 或已归档历史；更不意味着 81 个旧版本的每个差异均适合重新加到最新 Android App。
