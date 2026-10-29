# 天外天接手入口

更新：2026-10-29。用户只有一部Android13／vivo手机，**无Root、无电脑**，要求持续推进并减少实验。微信发送／实时共享、抖音无线／海外及vivo真机效果仍未完成验收。

## 唯一研发主线

| 角色 | 分支／来源 | 当前状态 |
| --- | --- | --- |
| 继续研发 | `research/container-api33-20261023` · [Draft #60](https://github.com/soozooq/gogogo/pull/60) | 从本分支HEAD继续，不另开分支或PR |
| 日常代码保留 | `fix/location-input-validation` · [Draft #59](https://github.com/soozooq/gogogo/pull/59) | 固定2620b741／2.4.31／2400099，不自动更新或合并 |
| 新版固定运行源码 | `20ff7a0ef8fec5fc0caca788f98095f5eba2701f` | 2.4.36／2400104＋预览宿主0.5／5；CI67原生仍在运行，尚未交付 |
| 新版交付控制器 | 本分支HEAD的 `ci` 脚本及2.4.36策略 | 和运行源码分开；实际交付提交／作业另以回执记录 |
| 已核验可下载候选 | 2.4.35／运行c8052d31／Phone10 | 保持原字节，见下文；不把57原生结果借给新版 |

不删除、强推或重置其他工作区；两个PR保持Draft。`main`的用户修改及日常分支不动。新版本的“构建／自有界面通过”不能写成微信／抖音位置业务通过。正常使用有问题时沿用一键反馈，不重跑旧实验、不切换VPN做矩阵。

## 2.4.36：界面与默认检查已过，等待同源原生及交付

[实际Android截图](docs/visual-preview-2.4.36/README.md)集中展示地图、搜索、深色预览和大字体键盘（示例图固定65轮原图，来源和当时失败单列）。[DESIGN.md](DESIGN.md)统一颜色、字阶、圆角、图标和留白。地图主操作固定在滚动区外；预览保持“选择应用 → 确认地点 → 明确打开”，键盘弹出时收起页头，辅助操作缩为短文案。无新手机实验、权限、依赖或引擎修改；引擎SHA仍 `16440679…`。

[CI67](https://github.com/soozooq/gogogo/actions/runs/40870835491)固定20ff7a0e：Linux123437152191与Mac123437152360实际成功。四profile、20个正确阶段及外层收图全部通过，覆盖360dp、320dp＋1.5字体、浅／深色与纬度／经度真实键盘；48dp要求、未导入禁用、不开模拟、不启动Guest、不请求定位权限均保留。UI JUnit48.433秒；三己方包清理、设置恢复与系统稳定通过。七运行case、7定位／19无线、真实Guest九路径、447项主检查及lint通过。三档API归档／73来源／同签名与每包11材料已独立核对；导入后实际View与AX按钮均启用。见[完整默认结果](research/container-api33/evidence/visual-complete-pass-results.json)。

**原生67作业123441209819仍在运行，尚无通过结论；2.4.36尚无合格下载。** 新[交付策略](ci/release-qualifications/2.4.36.json)固定本轮源码／作业／已完成归档，等待同源原生7方法、独立Guest九路径、匿名重开、300秒主PID／UID观察及系统／包清理。之后还须核对本轮两APK、版本、签名、ARM64主包、447／lint、423份非签名内容、20界面阶段和最新源码，才能产生qualified下载。发布作业和下载结果要实际取回核对。

[打包准备回执](research/container-api33/evidence/phone-visual-update-preparation.json)：新版宿主沿25bfd6e2…证书，4603006字节／SHA2198b7d6…；与本轮CI宿主全部423非签名内容相同，公开配方可精确恢复，CI不需私钥。主包沿6eec4ddd…证书，必须使用干净固定源码的实际云端构建，不能拿渲染x86包或研发目录增量APK交付。新策略明确73来源／423内容／20阶段；旧2.4.35的59／415及其配方不适用新版。控制器22项本地边界／恢复／打包检查已通过（含实际复制配置与回执摘要一致的结构回归），这不算新的云端交付。

[Phone11实际回执](research/container-api33/evidence/phone11-manifest-profile-failure-results.json)：云端已构建主2.4.36（SHA bff006e1…）与宿主0.5，两APK的版本／签名／ARM64及423内容独立核对通过。但打包回执误写旧版配置SHA67f15d33…，实际随包配置正确为6a2c0c8e…，校验以 `Phone profile differs` 拒绝，尚未尝试Release POST、没有合格下载。现回执直接散列实际配置文件；策略generation2拒绝旧回执，并通过原有live策略校验让旧等待作业退出，后续新轮复用同一原生67，不取消或重跑原生。新打包轮的实际结果另记。

旧65已装导入启用状态失败保留，未采集当时实际View，不能断言缓存原因；66已验证新的完整状态事件、显式AX刷新及实际View启用。65／66的20阶段JUnit均过，但外层遗漏仍要求16张而失败、原生跳过；67才验证数量同步后的整轮默认检查。[66回执](research/container-api33/evidence/visual-collector-count-failure-results.json)及[65回执](research/container-api33/evidence/visual-20-stage-and-import-state-results.json)均保留。

## 当前可下载：2.4.35合格候选

[下载已核验安装包ZIP](https://github.com/soozooq/gogogo/actions/runs/40626531080/artifacts/12789442894)（GitHub需登录，保留至2026-11-11T03:25:33Z）。26833656字节／SHA43c8b4c9…；工作区同字节副本 `/workspace/deliverables/Tianwaitian-2.4.35-qualified.zip`。

运行源码c8052d31，原构建4de425d2／Phone9，交付执行beab6368／Phone10。主2.4.35／2400103：40408202字节／SHAf1796794…／证书6eec4ddd…；宿主0.4／4：4594179字节／SHA4d1f0725…／证书25bfd6e2…。只含两份自有APK和说明／校验／回执，不含目标原版APK、账号资料或私钥。见[实际下载验真](research/container-api33/evidence/phone10-qualified-artifact-verified-delivery-results.json)。

57同源原生的匿名重开、7方法、九路径和300秒主身份已核对，仅覆盖该旧源码／旧包。[原生57](research/container-api33/evidence/native-retired-process-owner-pass-results.json)。实际交付为published=false的Artifact；创建Release仍返回已知403，完整资格通过后才转为合格Artifact。公开Release仍2.4.34，旧6625草稿及全部旧附件保持原样。其他API错误或资格失败不能套用403回退。

## 手机流程与结果边界

安装两份自有APK → 地图选点 → 首次导入所选完整原版并授予精确定位 → 确认后打开。结束时点“停止预览”；正常使用异常时点“复制反馈”，只选择已经看到的结果，不做实验矩阵。固定支持微信8.0.79／3200、抖音40.8.0／400801；原应用保留，登录由用户自行操作，不复制账号私有数据。见[手机三步说明](docs/original-app-preview-flow.md)。

预览中的位置来自选点合成；系统标准模拟位置不能强制已安装微信采用。自有契约、兼容位置对象、消费者调用计数及匿名页面，均不能证明目标SDK采用、账号发送／实时、无线／海外、IP属地或vivo效果。没有自动接受目标条款、登录或发送消息。用户10月23日VPN开／关的微信失败保持原结果，10月13日旧记录不充作新验收；原始真实地址截图不上传公共仓库。

## 接下来与历史

继续查看同一个CI67原生及2.4.36交付工作流的实际结果；新下载必须独立取回验真。待资格期间保持运行源码及其输入不变，控制器／文档修改与运行源码分别记录。若有实际失败，先取回证据再修，不复用其他轮成功。需要新原生轮时提交同时带 `[cuttlefish-boot] [cuttlefish-container]`，共用锁，不取消前轮；排队／跳过不算通过。

使用 `research/container-api33/tools/verify-ci-receipts.py` 核对API摘要、源码、签名和材料；新输出不覆盖旧证据。构建用仓库外 `CONTAINER_RESEARCH_WORKDIR` 与 `tools/run-build.sh`，保留代理、CA与TLS。许可／对应来源见[THIRD_PARTY_NOTICES.md](research/container-api33/THIRD_PARTY_NOTICES.md)，已知范围见[AUDIT.md](research/container-api33/AUDIT.md)。

[完整旧进度与失败历史](docs/project-status-history-20261029.md)保存原接手文档至66轮的全部记录；先读本入口，按需追溯，不从旧文档恢复过期的“待运行／最新版本”。
