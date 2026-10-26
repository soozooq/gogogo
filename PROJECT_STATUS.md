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

[CI47默认](research/container-api33/evidence/persistent-phone-callback-results.json)已完整核对三档API摘要、固定ff12e8d3源码、六项case各58份／自有及移动UI59份、三同签名／每包11份材料、主447项／lint及7份关键主输入。新增持续电话三项确已执行，共15项无线0.178秒；六路径4.303秒、框架0.909秒、已装导入8.091秒、普通入口13.677秒、计算器5.896秒。

[CI46完整原生](research/container-api33/evidence/native-owned-private-snapshot-pass-results.json)已实际通过并核对四档、9份原生输入及三件同轮来源／签名／材料：两个普通原包与两个容器匿名UI、自有框架14.734秒、独立自有Guest六路径46.183秒，A→B／注销稳定／取消全部完成。系统合格、system_server稳定、VM与包／staging清理通过；整轮3983.186秒不是启动耗时。保留mock，未接受条款／登录／发送业务。**这是固定42cd5eb5／旧416补丁；新aad5持续电话候选的原生CI47／121473513629仍在运行，不能借用旧轮背书。**

两份ff12e8d3自有候选已在仓库外重建，主2.4.32／2400100，预览0.1-research／1；签名／对齐和预览全部415非签名文件与CI47相同均已核对。工作区上传Release附件实际返回401，现有草稿仍无附件、未发布。

[仓库交付准备](research/container-api33/evidence/phone-ci-draft-delivery-preparation.json)让标准仓库CI构建固定ff12主包，并用公开APK签名与ZIP元数据恢复原私签预览；415份压缩payload原字节复制，恢复后SHA／签名／对齐全同，私钥不上传。5项有效检查、YAML／各shell块与完整本地打包通过。新工作流只写同一未发布草稿，另外保存一个平铺下载包；不发布Release，不改变运行时源码、不取消原生47，也不要求手机实验。远程交付尚未运行，实际云主包摘要须以该轮回执为准。

## 当前候选：Android13持续电话监听与反馈简化

[持续电话回调候选](research/container-api33/evidence/persistent-phone-callback-preparation.json)补齐`listenWithEventList`及已创建的`TelephonyRegistryManager.sRegistry`缓存。公共框架的本地监听Stub转入同进程Binder，保持注册参数、更新／空事件注销的同一Binder。六类位置相关回调在交付时检查模拟状态；开启或状态无法读取时抑制整条消息，关闭时和其他回调转发原Parcel／flag，不编造基站或网络身份。非本地形状监听明确拒绝，避免改变其远程调用者／死亡身份；没有新增权限、依赖、JNI或mock标志变化。

23秒构建、宿主静态、完整补丁干净索引重放及25项驱动检查通过。自有软件API33环境实际执行**15项检查／2.837秒通过**，包含真实公共TelephonyManager／TelephonyCallback与受控服务、预先缓存旧服务、同一监听关闭→开启→关闭、六类真实AIDL编码消息、关闭时原字节／本地PID与UID、非敏感DataActivity、未知模式不回退原数据、同Binder注销及平台权限拒绝。新引擎下独立Guest原六路径A→B／停止／取消也实际通过51.982秒；自有VM已正常停止。

首轮软件VM在Provider发布超时后、0方法执行时失败，原始证据保留；同一自有VM预热后的两次15项通过分别记录，未更改镜像或平台超时。上述检查不是实际modem、真实Guest持续电话注册、目标SDK、内核死亡事件或vivo证明；已交付给应用的旧缓存也无法回收，SIM／运营商／国家／IP及其他接口未由此覆盖。

**新完整引擎补丁为aad5ff14，CI47默认已通过；匹配原生仍在运行。** 已通过的CI46使用旧416e8dd4，不能代替新候选的原生结果。

[反馈文案简化](research/container-api33/evidence/no-extra-experiment-feedback-preparation.json)让默认摘要直接引导已有的「一键复制反馈」，不再要求先跑Consumer Matrix、另开实验或补做启动／停止对照。权限阻断、历史／第三方不确定性与原判定保留。已有断言同步更新，没有新增实验或测试场景；主程序447项／lint实际通过，最终36秒。此变化只在研发源码中，待重建候选包。

[CI46默认结果](research/container-api33/evidence/owned-private-snapshot-results.json)已完整核对三档、每case58份／自有及移动UI59份来源、三同签名／11材料、主447项／lint及7份关键主输入。原子私有报告六路径3.309秒完成四阶段；框架0.529秒、导入7.590秒、普通入口10.640秒、计算器5.680秒及旧12项无线0.085秒。**该轮原生121455067092现已通过，固定42cd5eb5／旧补丁，不含新持续电话候选。**

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

[三步说明](docs/original-app-preview-flow.md)收敛为地图选点、首次原版导入／精确定位授权、确认并打开。预览需自行登录，原应用保留。当前两份新候选固定ff12e8d3，旧CCD候选仅作历史；私有签名材料保留仓库外，不进入Git、工作流或交付。

当前Release为`preview-2.4.32-container-ff12e8d3`未发布草稿，工作区附件上传401。准备[Phone preview draft工作流](.github/workflows/phone-preview-draft.yml)处理标准仓库CI交付；云主包嵌入自有fixture的签名可改变APK摘要，必须核对其实际回执。匹配原生CI47完成前保持草稿；云端自有通过仍不是微信／抖音／vivo业务验收。主页使用免腾讯Key的MapLibre／OpenFreeMap，旧腾讯功能为占位配置，联网地图未实机验证。

## 证据边界与历史

- [SDK只读核对](research/container-api33/evidence/target-location-sdk-boundary-review.json)：微信`sapp`／独立native库与公开腾讯SDK不同；抖音含AMap／BDLocation及模拟方法标识。公开方法或版本默认值不证明运行配置／拒绝因果。
- [CI40主PID集合失败](research/container-api33/evidence/native-pid-set-and-owned-stage-results.json)、[CI39真实Provider归属拒绝](research/container-api33/evidence/provider-attribution-failure-and-correction.json)、[CI38匿名启动](research/container-api33/evidence/native-container-startup-pass-results.json)及更早失败均保留。旧URI权限恒GRANTED未改，功能正例不能证明权限拒绝或隔离，见[AUDIT.md](research/container-api33/AUDIT.md)。
- 用户10月23日VPN开／关缓存导出新鲜且近模拟目标，微信截图及发送／实时共享反馈仍失败。快照不等于微信前台连续采样，10月13日历史实验不作新结果；实际地址截图未上传公共仓库。更新#59只改指定后续研发标记段。

构建使用仓库外`CONTAINER_RESEARCH_WORKDIR`及`tools/run-build.sh`，保留代理／CA／TLS，SDK安装串行。回执用`tools/verify-ci-receipts.py`固定Git源码核对，新输出保留旧证据。许可／来源见[THIRD_PARTY_NOTICES.md](research/container-api33/THIRD_PARTY_NOTICES.md)。
