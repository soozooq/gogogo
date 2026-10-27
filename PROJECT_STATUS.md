# 天外天接手入口

更新时间：2026-10-27。以本文件为主入口；运行源码、交付源码和证据提交分别记录，不能混用旧轮成功。

## 用户目标与唯一主线

用户只有一部Android13／vivo手机，**无Root、无电脑**，希望减少实验、持续推进。**微信发送／实时共享、抖音无以太网／海外定位仍未完成真机验收。** 云端匿名页面和自有定位通过，不证明目标SDK已采用位置。

| 角色 | 分支与PR | 状态 |
| --- | --- | --- |
| 唯一继续研发 | `research/container-api33-20261023` · [Draft #60](https://github.com/soozooq/gogogo/pull/60) | 从本分支HEAD继续，修正与证据都留在这里 |
| 日常候选保留 | `fix/location-input-validation` · [Draft #59](https://github.com/soozooq/gogogo/pull/59) | 固定2620b741／2.4.31／2400099，代码未更新 |

不新增分支／PR、不自动合并、不删除／强推／重置其他工作区。两个PR保持Draft。手机用户不需要重跑旧实验或切换VPN。

## 当前研发：微信定位SDK兼容呈现（2.4.34预览已发布）

用户确认继续以「微信自身SDK会不会采用」为重点。已从固定微信8.0.79原包的`assets/TencentLocationComp_sapp/1`离线解码出537,976字节DEX，原包及DEX头长度／SHA1／Adler32完整核对；外层加载器请求的TencentLocationManagerProxy确实存在于该组件。421类／2510方法扫描发现四处isFromMockProvider，独立apkanalyzer确认：其中b.a(Location)与g.a(Location)在常规前置检查后，对API≥18的mock样本提前退出。另两处有配置条件。见[原包组件与过滤分支证据](research/container-api33/evidence/wechat-bundled-sdk-mock-filter-results.json)。这证明始终暴露mock=true与这些分支冲突，不证明用户手机当前运行了哪条分支或失败因果。

2.4.34改变此前「所有合成返回均保留系统mock标记」的策略：只对预览中固定原版微信的虚拟会话使用兼容呈现，GPS／NETWORK缓存、单次及持续回调统一返回不带框架mock标记的位置；仍在extras及短反馈明确标注合成来源，卫星数保持零。普通／未知／其他客户端继续保留mock标记。坐标仍由选点合成，没有变成真实GNSS；系统原微信、系统模拟Provider和原APK均未修改。兼容对象只绕开这些已确认的标记检查，其他SDK／网络／账号／服务端决策仍未知。

已交付预览宿主0.3／3、主程序2.4.34／2400102。[CI52默认已独立验真通过](research/container-api33/evidence/wechat-compatibility-presentation-default-results.json)，固定a26d14a5／d820补丁：6方法定位4.214秒、19受控无线0.148秒，七case／变化UI、主447项／lint、三档API摘要和58／59来源／同签名／11材料核对。公共框架＋受控微信包上下文的六路径、两个mock别名、合成extras、零卫星和恢复默认检查均过；原通用mock断言保留，自有真实Guest四阶段六路径3.282秒继续标记。普通入口12.989秒、已装导入8.608秒、计算器5.593秒。它不是实际微信进程或SDK采用结果。

[同源ARM64 CI52已完整验真通过](research/container-api33/evidence/native-wechat-compatibility-presentation-pass-results.json)：四档API摘要／9原生输入／59自有来源及签名材料匹配a26d14a5，新增受控契约6方法6.086秒，逐个成功方法与固定源码核对。真实自有Guest六路径46.627秒、框架15.604秒，通用mock保留，最老样本1223.857毫秒，取消零回调。微信关闭6315→OWN模式新6861、抖音10969→13068，真实UID与匿名页、旧主进程消失、system_server650稳定及VM／包／staging清理均通过；300秒抖音观察仍仅关闭模式。两目标无PNG，不声称看过截图；整轮4676.744秒不是启动耗时。受控微信包上下文不是真实SDK采用。[准备记录](research/container-api33/evidence/wechat-compatibility-presentation-preparation.json)保留24秒／13秒编译、静态核对、完整补丁重放及25项驱动结果。稳定私签宿主与CI52全部415非签名内容相同，公开签名配方可逐字节恢复；交付源码d606261b的[Phone CI5](https://github.com/soozooq/gogogo/actions/runs/40491596045)实际两档归档、安装包、签名、版本与附件摘要均独立核对后，已公开2.4.34；打包作业仍只写草稿，公开是后续单独执行。没有新增手机实验页、权限、依赖或JNI，不要求手机重测。公开2.4.33不包含新兼容策略，附件和tag保持原样。

## 当前公开交付：2.4.34

[2.4.34微信兼容预览已发布](https://github.com/soozooq/gogogo/releases/tag/preview-2.4.34-container-a26d14a5)，2026-10-27T10:56:29Z，prerelease。运行时**a26d14a5b9e2b7dcfe1dc98874f07440be4b9113**／引擎d820ef13，交付源码d606261b／Phone CI5。实际tag指向运行时源码，两份公开下载均HTTP200且完整字节／摘要相同。

- [主程序APK](https://github.com/soozooq/gogogo/releases/download/preview-2.4.34-container-a26d14a5/Tianwaitian-2.4.34-container-preview-arm64.apk)：2.4.34-container-preview／2400102，40,408,202字节，SHAdde0b903…，沿用6eec4ddd…证书。
- [原版预览APK](https://github.com/soozooq/gogogo/releases/download/preview-2.4.34-container-a26d14a5/Tianwaitian-Original-Preview-a26d14a5.apk)：0.3-preview／3，4,585,987字节，SHA1f271efa…，沿用25bfd6e2…证书。

见[实际发布与下载回执](research/container-api33/evidence/phone-a26d14a5-published-delivery-results.json)。主447项／lint、固定profile、扁平六文件、同源原生52、59自有来源／同签名及每包11材料、415非签名内容、公开签名配方、两附件服务器摘要和真实tag均核对。草稿manifest的published=false保留为历史打包快照；不是当前发布状态。旧2.4.33／2.4.32的正文、tag和附件均独立复核未变。

已有2.4.33主程序时只更新原版预览APK即可；首次或仍在2.4.31时安装两份。正常使用中有问题时一键复制已有观察，不要求重做实验。用户手机是否已安装、微信SDK是否采用、发送／实时及抖音无线／海外仍未知。

## 上一轮：状态不可读时禁止真实定位回退（源码1e6a10ff）

继续审查发现，Guest定位／无线Hook共用的判断函数调用兼容getter；该getter在RemoteException时返回关闭模式，使请求可能转向真实系统。新候选直接读取控制服务，仅在明确、成功读到关闭模式时放行；服务缺失、读取异常及未知模式继续走原虚拟保护路径。「保护路径」不表示服务一定在运行。公开2.4.33仍固定414源码，不包含这次修正，附件和标签保持原样。

[CI51默认实际通过](research/container-api33/evidence/unreadable-mode-default-results.json)：固定1e6a10ff／da217a39补丁，5项公共定位契约3.921秒、19项受控无线0.231秒；七项case、变化UI、主447项／lint及三档API摘要、58／59来源、三同签名／11材料均核对。故障覆盖RemoteException、IllegalStateException和未知99：不向受控真实后端委托GPS／NETWORK缓存、current或基站／扫描；Wi-Fi／NetworkInfo副本继续脱敏，明确CLOSE后正常恢复。自有六路径4.214秒、四阶段及mock保持，普通入口15.045秒、已装导入7.520秒、计算器6.016秒。它是受控框架／自有回归，不是目标SDK采用。

本地软件模拟器两次均在测试前因自有Provider发布超时、随后空Bundle读取而崩溃，0个JUnit方法执行；失败及摘要保留，VM已正常结束，未改系统超时、安全或ART。硬件CI51独立通过不能抹去该记录。[CI51匹配原生也已核对通过](research/container-api33/evidence/native-unreadable-mode-pass-results.json)：四档API／9原生输入／59自有来源匹配旧1e6a10ff，微信5893→6527、抖音10268→12380，两关闭／停止／OWN2新主PID及真实UID匿名启动、自有框架14.480秒／六路径47.696秒通过，system_server657稳定，VM与包清理完成，样本最老1974.449毫秒；整轮4412.724秒不是启动耗时。微信PNG存在但未查看，抖音无PNG。它没有新兼容策略，不能证明a26候选或目标SDK采用；该轮没有mock标志变化，不归因用户旧失败，不证明能收回既有SDK缓存或此前物理回调。

## 保留旧交付：2.4.33

[2.4.33手机预览已发布](https://github.com/soozooq/gogogo/releases/tag/preview-2.4.33-container-41438448)，2026-10-27T04:23:30Z，prerelease。固定运行时**414384487b22a576aa1c6a29fca1df6a167ebd47**／引擎cf4dffbb，交付源码bd8b3e42／[Phone CI4](https://github.com/soozooq/gogogo/actions/runs/40425812277)。实际tag指向运行时源码，两份公开下载均已核对HTTP200。

- [主程序APK](https://github.com/soozooq/gogogo/releases/download/preview-2.4.33-container-41438448/Tianwaitian-2.4.33-container-preview-arm64.apk)：2.4.33-container-preview／2400101，40,408,206字节，SHA9242b55a…，证书6eec4ddd…。这是实际云端字节，不能借用本地a7f3bca…摘要。
- [原版预览APK](https://github.com/soozooq/gogogo/releases/download/preview-2.4.33-container-41438448/Tianwaitian-Original-Preview-41438448.apk)：0.2-preview／2，4,585,987字节，SHA513f8f98…，沿用25bfd6e2…证书。

见[实际交付及发布回执](research/container-api33/evidence/phone-41438448-published-delivery-results.json)。两档交付归档API摘要、包名／版本、签名／对齐、新profile、六文件、同轮59来源／三同签名／每包11材料、415份非签名内容与两个附件服务器摘要均独立核对。公开签名／ZIP配方逐字节恢复相同宿主，私钥不进入Git、CI或附件。打包工作流只写草稿，实际公开发生在同源原生与交付核验之后。草稿manifest中的published=false是历史打包快照，不是当前发布状态。

已发布不表示用户手机已安装；当前手机版本及目标应用位置效果仍未知。旧[2.4.32](https://github.com/soozooq/gogogo/releases/tag/preview-2.4.32-container-e0811cf4)的源码e081、两附件3ddd90ae…／14cdb415…及tag保持原样。

## 本轮实现与实际验证

Android13补齐getServiceStateForSubscriber和预加载TelephonyManager.sITelephony缓存。模拟开启／模式不可读时，请求平台放弃精细／粗略位置，再返回标准脱敏副本，去除注册CellIdentity及框架指定粗略字段。保留原对象、权限拒绝、null与非位置状态，失败无原始回退；无新权限、依赖、JNI或mock标志变化。旧缓存也进入引擎既有电话Hook，不代表全部无线／SIM／国家／IP已处理，SDK旧缓存不能收回，也不认定这是用户旧微信失败的原因。

预览页增加「一键复制反馈」：只选择已经看到的入口／结果，复制时读取己方状态，不读坐标、不重新定位、不启停模拟或Guest。Core读取1.5秒上限，超时明确未知，晚回调不覆盖剪贴板；用户观察与复制时快照分开标注。默认界面没有实验清单。

[CI50默认实际通过](research/container-api33/evidence/preview-compact-feedback-results.json)：固定414源码，18项受控电话／无线0.257秒、六项case、主447项／lint通过，三档摘要及来源／签名／材料核对。六路径3.800秒、框架0.949秒、普通入口15.548秒、已装导入8.552秒、计算器6.700秒。实际剪贴板780字符无坐标，原Guest主PID／模式保持，再完成停止清理；这是GPSTest与自有回归，不是目标页面验收。

[CI50同源原生完整通过](research/container-api33/evidence/native-preview-feedback-virtual-startup-pass-results.json)：四档API摘要、9原生输入、同轮三件自有APK的59来源／同签名／11材料核对。普通原包对照通过后，两款容器应用先在关闭模式进入匿名页，回到自有页停止旧进程，再以模式2、不同的新主PID及真实宿主UID重新进入匿名页。微信整项676.514秒，抖音整项965.161秒；300秒抖音主进程观察只属于关闭模式，不声称开启模式持续300秒。

原生自有框架15.112秒、六路径49.845秒，完成A→B／注销稳定／取消四阶段，保留mock、GNSS零卫星、取消零回调及30秒时效要求，报告来自私有原子快照。Android13／build16596636、ARM64、无Native Bridge、UID2000、SELinux Enforcing、4CPU及超时倍数50合格；system_server656稳定，VM、包及staging清理通过。整轮4581.798秒不是应用启动耗时。目标截图情况以该轮回执为准，不借用旧截图。

## 手机使用与未完成范围

[手机三步说明](docs/original-app-preview-flow.md)：安装两份自有APK，在天外天地图选点带入，首次导入手机已装的固定完整原版并授权精确定位，确认后点击「应用坐标并打开」。结束时点「停止预览」。作用于从预览入口打开的应用；收到选点不会自动启动或开启模拟。

微信8.0.79／3200、抖音40.8.0／400801限原生ARM64，只接受核对过版本／大小／摘要／原证书的完整APK；系统原应用保留，预览需自行登录，账号私有数据不迁移。目标原包不改签、不内嵌、不分发。正常使用遇到问题时一键复制短反馈即可，不要求遍历入口或另做实验；未用入口继续保留未验证。

没有自动接受条款、登录或发送业务。**微信发送／实时、抖音无线／海外、实际目标SDK采用、vivo真机、主程序真实地图发送端及联网地图仍需各自证据。** 自有Guest共享宿主真实UID，功能正例不能证明权限拒绝或隔离。位置页、附近内容和IP属地分别判断，不能从模拟缓存或海外输入推定这些业务成功。首页使用免腾讯Key的MapLibre／OpenFreeMap，旧腾讯Key功能是占位配置。

## 历史结果与继续规则

- [CI49原生通过](research/container-api33/evidence/native-service-state-virtual-startup-pass-results.json)固定1441旧宿主，服务状态／双模式启动、框架及六路径已过；当前新宿主414由自己的CI50验证，不能互相借用。
- [CI48完整通过](research/container-api33/evidence/native-phone-transaction-name-pass-results.json)固定e081／d6，属于旧2.4.32；[CI47失败](research/container-api33/evidence/native-phone-callback-field-failure-results.json)的私有TRANSACTION字段读取异常及旧包备份保留。运行时AIDL名称修正没有改变全局隐藏API策略或mock标志。
- [CI44／45时效失败](research/container-api33/evidence/native-owned-location-freshness-results.json)、[CI42旧UI节点读取失败](research/container-api33/evidence/native-owned-location-stale-ui-results.json)、[CI40主PID集合问题](research/container-api33/evidence/native-pid-set-and-owned-stage-results.json)和[Provider归属拒绝](research/container-api33/evidence/provider-attribution-failure-and-correction.json)保留，不用旧成功覆盖新失败，不放宽时效／清理要求。
- [SDK只读核对](research/container-api33/evidence/target-location-sdk-boundary-review.json)指出微信私有sapp不同于公开腾讯SDK；抖音含AMap／BDLocation及模拟方法标识，不证明运行设置或拒绝因果。精确方法表补查：微信classes16四个sapp公开入口共52项声明没有公开SDK中的同名mock开关；抖音classes48有隐私／mock声明。后续对选定SDK命名空间的DEX指令扫描与独立apkanalyzer核对，确认抖音所含com.loc代码有两处isFromMockProvider调用；微信请求包装层反射调用Method.invoke，构造层引用库加载／原生初始化。因此不能由少量直接调用推定微信不读某定位接口，也不能由识别mock的代码推定抖音实际拒绝；活跃实现／设置／业务采用仍未知。均未执行SDK、读取Key／账号值或变更同意／mock状态。用户10月23日VPN开／关快照与微信失败仍保留，10月13日历史不重跑，真实地址截图未上传公共仓库。

继续沿唯一研发分支HEAD。新增运行变化必须绑定固定源码核对；原生push带齐[cuttlefish-boot]和[cuttlefish-container]，共用锁不取消前轮，排队／跳过不算通过。证据工具为tools/verify-ci-receipts.py；构建用仓库外CONTAINER_RESEARCH_WORKDIR及tools/run-build.sh，保留代理／CA／TLS。新结果用新输出，不覆盖旧证据。许可与来源见[THIRD_PARTY_NOTICES.md](research/container-api33/THIRD_PARTY_NOTICES.md)，已知边界见[AUDIT.md](research/container-api33/AUDIT.md)。
