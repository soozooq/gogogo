# 天外天接手入口

更新时间：2026-10-26。本文件定义当前主线；每份运行结果均绑定固定源码，历史成功不能代替新候选验证。

## 用户目标与唯一研发主线

用户只有一部Android13／vivo手机，**无Root、无电脑**，要求减少实验并持续推进。微信发送位置／实时共享已有明确失败反馈；抖音无以太网及海外定位也**尚未完成**。不要求重复旧实验，不把系统缓存、自有接口检查或匿名启动写成目标应用已采用定位。

| 角色 | 分支与PR | 当前约束 |
| --- | --- | --- |
| 唯一研发主线 | **`research/container-api33-20261023` · [Draft #60](https://github.com/soozooq/gogogo/pull/60)** | 从本分支HEAD继续，修正和证据留在这里 |
| 日常候选 | `fix/location-input-validation` · [Draft #59](https://github.com/soozooq/gogogo/pull/59) | 固定`2620b741dd6a971143577737c64ebc4496120d77`，`2.4.31-simple-support / 2400099`，未更新 |

不新增分支／PR、删除、强推、重置他人工作区或自动合并。保留其他工作区与未提交内容。手机交付仍为草稿，当前不安排新手机实验。

## 当前实际结果与交付

**[CI47原生失败](research/container-api33/evidence/native-phone-callback-field-failure-results.json)，不能发布旧ff12候选。** 四档API摘要、固定源码、9份原生输入及三件自有APK的59份来源／同签名／每包11份材料已完整核对。合格Android13原生ARM64系统、两个普通原包、容器抖音匿名UI、自有框架16.280秒及六路径51.538秒全部通过；微信容器在电话监听注册时反复崩溃，596.421秒未到登录页。栈直接指向`PhoneCallbackRelay`读取`TRANSACTION_onCellLocationChanged`失败，13个微信／sandbox PID记录同类错误。没有独立区分字段被隐藏或被省略，不能直接认定微信主动拦截。

已实际查看微信截图：应用区域为黑屏，系统状态栏及导航栏可见。抖音主PID10875／宿主UID10102保持300,427ms，末尾隐私页仍在；system_server647稳定，VM与自有包／staging清理通过。自有六路径保留mock，A→B／注销稳定／取消四阶段完成，来源为私有原子快照。这不是目标SDK、真实modem事件、微信位置业务或vivo验收。

[CI47默认](research/container-api33/evidence/persistent-phone-callback-results.json)仍为真实通过：六项case、15项受控无线、主447项／lint及三档来源／签名／材料均核对。另有[CI46完整原生通过](research/container-api33/evidence/native-owned-private-snapshot-pass-results.json)，固定42cd5eb5／旧416补丁，不包含失败的aad5持续电话变更，不能替代新候选验证。

[交付CI2](research/container-api33/evidence/phone-ci-draft-delivery-results.json)已成功上传同一草稿中的两份ff12自有APK，主2.4.32／2400100、预览0.1-research／1。实际云主包SHA033c0f6b…、预览2308c0ba…；归档API／服务器摘要、包名／版本、签名及对齐均已独立核对。**匹配原生47失败，草稿继续未发布，说明已标注阻断；不要求手机安装或重做实验。** 工作区上传401及交付首轮SDK准备失败保留，第二轮标准仓库CI已解决上传，私钥未上传。

## 当前修正：电话回调读取兼容

[修正准备](research/container-api33/evidence/phone-transaction-name-preparation.json)移除私有`TRANSACTION_*`字段依赖，改用运行时生成AIDL的`getDefaultTransactionName`识别六类回调。沿用现有库读取方法，生产代码不改变全局隐藏API策略；在有界范围内解析，缺名或重名明确失败，不回退原始敏感数据。交付时检查模拟状态、原始Parcel／调用身份、原注册参数及同Binder注销保持原逻辑，没有新增权限／依赖／JNI或mock标志变化。

30秒构建、宿主静态、完整补丁干净索引重放及25项驱动检查已通过。新增一项受控回归要求私有常量查询先真实抛出异常，再走公共TelephonyManager／TelephonyCallback、旧服务缓存替换、关闭→开启→关闭和同Binder注销；仅在自有检查进程临时收窄可见范围，finally恢复。16项软件API33检查实际3.298秒通过，包含“常量不可读”的前置断言及公共框架完整流程。软件VM首轮在0方法执行前因Provider发布超时／空Bundle初始化失败，原记录保留；同一VM及同一APK预热后通过，未改镜像或平台超时。自有软件VM已按时限停止，新源码六路径将由匹配的默认／原生CI复核。当前完整补丁SHA为d6b29e92…；旧aad5失败与旧416通过分别保留。

[反馈文案简化](research/container-api33/evidence/no-extra-experiment-feedback-preparation.json)已进入ff12源码及交付CI2主包，默认摘要直接引导「一键复制反馈」，不再要求先跑Consumer Matrix或补做实验。原权限阻断和证据范围保留；高级检查供维护使用，不要求手机重复操作。

## 原生历史：CI44／45整项失败保留

[两轮完整回执](research/container-api33/evidence/native-owned-location-freshness-results.json)均已核对四档API摘要、固定Git源码、9份原生输入及三件自有APK的59份来源／同签名／每包11份材料。两轮固定Android13／build16596636、原生ARM64、Native Bridge 0、UID2000、SELinux Enforcing、4CPU及超时倍数50合格；system_server分别662／650稳定，VM、包及staging均清理。

| 实际阶段 | CI44：774c6e7a | CI45：ccd6d3e2 |
| --- | --- | --- |
| 普通原包匿名UI | 两款通过，普通抖音保持302.539秒 | 两款通过，普通抖音保持302.477秒 |
| 容器原版匿名UI | 微信登录页通过；抖音主PID9667／宿主UID10102保持300,758ms，末尾隐私页仍在 | 微信登录页通过；抖音主PID9448／宿主UID10102保持300,431ms，末尾隐私页仍在 |
| 原生自有框架 | 15.192秒通过，跨进程call／query及真实归属 | 14.195秒通过，跨进程call／query及真实归属 |
| 原生自有定位 | 162.206秒失败，等待A超时，未形成阶段成功 | 202.585秒失败，A阶段已落盘为部分证据，等待B超时 |

两轮末尾文本的六类值均为对应模拟目标并保留mock；单次样本比持续回调旧超过120秒，不能算30秒时效通过。没有新的旧节点异常；确切首次读取耗时未单独记录，不认定引擎或目标SDK因果。CI45的`stages.json`只有A且`passed=false`，没有最终成功JSON；停止／取消未完成。微信PNG存在但此轮未查看，抖音均无PNG；不声称看过目标图像。

目标应用定位始终关闭，未接受条款、登录或发送业务。框架正例不证明权限拒绝、PendingIntent投递或独立UID隔离；Guest共享宿主真实UID。上述结果不能替代微信实时／发送、抖音无线／海外或vivo真机验收。旧[CI42读取异常失败](research/container-api33/evidence/native-owned-location-stale-ui-results.json)仍保留。

## 当前修正：自有报告走私有原子快照

[准备记录](research/container-api33/evidence/owned-private-snapshot-preparation.json)改动独立自有消费者与控制器的结果传输：消费者保持原公共Android定位调用，不接收预期坐标；同一主循环合并字段后写本应用私有AtomicFile，并显示相同JSON。控制器读取这份自有报告，仍通过真实界面按钮开始／刷新／停止／取消。

随机nonce、旧文件清理、序号不回退、真实单调时钟均核对；报告和原六类样本仍须在30秒内，120秒等待不变。坐标、provider、mock、真实PID／UID、A→B、注销稳定、GNSS零卫星和取消零回调断言保留，JSON／API错误仍失败。两个驱动要求新的报告来源，不能拿旧UI报告或绿色JUnit冒充成功。阶段文件仍为部分证据，最终成功需所有条件。

15秒构建、宿主／独立消费者静态核验及25项受控驱动检查通过，含旧来源拒绝场景；CI46默认实际3.309秒完成全部四阶段。该轮原生也已完整通过。引擎416e8dd4未改；持续电话候选属于后续独立变更。

1. 同一主线提交后先核对默认六项，再核对同轮原生；按各自固定Git提交、API摘要、来源、签名、JUnit与清理判断，保留失败。
2. 原生push须带齐`[cuttlefish-boot] [cuttlefish-container]`，或显式手动选项。共用锁不取消前轮；排队、被替换和跳过不算通过。
3. 自有原生六路径通过后，目标SDK／账号／实际位置业务仍需各自证据，不自动接受条款、登录或发消息。

## 已完成的默认回归与普通入口

[CI45默认](research/container-api33/evidence/network-callback-transport-results.json)六项case、移动UI、主447项／lint通过。框架0.976秒、六路径3.727秒、已装导入8.116秒、普通入口12.417秒、计算器6.402秒、12项受控无线／回调0.145秒；每case58份、移动UI与自有传递59份、三同签名／11材料及7份主关键输入均核对。CI44默认六路径3.298秒的[旧结果](research/container-api33/evidence/stale-ui-location-read-results.json)也保留。

API33网络回调仅转接requestNetwork／listenForNetwork的Messenger，交付时检查模拟状态并标准复制／脱敏TransportInfo；保留死亡Binder、请求参数／ID／释放、Network句柄和路由，失败无原始敏感回退。12项是公共ConnectivityManager／标准Messenger与受控服务，不能证明真实Guest此路径、目标SDK、其他NC敏感字段或持续Telephony。CI45原生匿名／框架通过也不是这些路径实际触发证明。

[CI43地图预填与结构化导入](research/container-api33/evidence/map-point-prefill-results.json)已实际通过。普通页保留文件选择，支持只读导入手机已装完整APK，核对精确版本／大小／摘要／包名／原证书，拒绝分包；计算器原应用UID／路径／版本保留、临时与虚拟副本清理均通过。清单包含计算器、GPSTest及固定ARM64微信8.0.79／3200、抖音40.8.0／400801；不改签、嵌入或分发目标原包，不迁移账号私有数据。

首页在ARM64且预览已安装时出现衔接按钮，WGS84选点带入普通页。接收端无效输入忽略、有效预填且不自动启动／模拟，随后GPSTest正常文件选择／精确授权、A→B／停止均通过。首页发送端只有编译、主单元和静态常量证据，没有真实主页面或vivo结果。

## 手机交付草稿

[三步说明](docs/original-app-preview-flow.md)收敛为地图选点、首次原版导入／精确定位授权、确认并打开。预览需自行登录，原应用保留。当前同一Release草稿`preview-2.4.32-container-ff12e8d3`中的两份APK均已上传并核验，但匹配原生47失败，必须保持未发布；它们不是新修正候选。私有签名材料保留仓库外，不进入Git、工作流或交付。

[Phone preview draft工作流](.github/workflows/phone-preview-draft.yml)只上传两份自有APK及平铺回执，不自动发布、不改写原包、不启动或取消原生任务。公开签名／ZIP配方保持415份非签名压缩payload原字节，恢复私签预览时不使用私钥。主页使用免腾讯Key的MapLibre／OpenFreeMap，旧腾讯功能为占位配置，联网地图未实机验证。

## 证据边界与历史

- [SDK只读核对](research/container-api33/evidence/target-location-sdk-boundary-review.json)：微信`sapp`／独立native库与公开腾讯SDK不同；抖音含AMap／BDLocation及模拟方法标识。公开方法或版本默认值不证明运行配置／拒绝因果。
- [CI40主PID集合失败](research/container-api33/evidence/native-pid-set-and-owned-stage-results.json)、[CI39真实Provider归属拒绝](research/container-api33/evidence/provider-attribution-failure-and-correction.json)、[CI38匿名启动](research/container-api33/evidence/native-container-startup-pass-results.json)及更早失败均保留。旧URI权限恒GRANTED未改，功能正例不能证明权限拒绝或隔离，见[AUDIT.md](research/container-api33/AUDIT.md)。
- 用户10月23日VPN开／关缓存导出新鲜且近模拟目标，微信截图及发送／实时共享反馈仍失败。快照不等于微信前台连续采样，10月13日历史实验不作新结果；实际地址截图未上传公共仓库。更新#59只改指定后续研发标记段。

构建使用仓库外`CONTAINER_RESEARCH_WORKDIR`及`tools/run-build.sh`，保留代理／CA／TLS，SDK安装串行。回执用`tools/verify-ci-receipts.py`固定Git源码核对，新输出保留旧证据。许可／来源见[THIRD_PARTY_NOTICES.md](research/container-api33/THIRD_PARTY_NOTICES.md)。
