# 天外天接手入口

更新时间：2026-10-26。本文件是当前状态入口；以下已核对结果分别绑定源码提交，历史记录不能冒充当前结果。

## 用户目标与唯一研发分支

用户只有一部Android13／vivo手机，**无Root、无电脑**，要求减少实验并持续推进。微信发送位置／实时共享已有明确失败反馈；抖音无以太网及海外定位也**尚未完成**。不让用户重复旧实验，不把缓存、自有检查或匿名启动写成目标应用采用。

| 角色 | 分支与PR | 当前约束 |
| --- | --- | --- |
| 唯一研发主线 | **`research/container-api33-20261023` · [Draft #60](https://github.com/soozooq/gogogo/pull/60)** | 本分支HEAD是继续位置，所有修正留在这里 |
| 日常候选 | `fix/location-input-validation` · [Draft #59](https://github.com/soozooq/gogogo/pull/59) | 固定`2620b741dd6a971143577737c64ebc4496120d77`，`2.4.31-simple-support / 2400099`，未更新 |

保留其他工作区及未提交内容。不新增分支／PR、删除分支、强推、重置他人工作区或自动合并。研究入口与首页衔接只在本分支候选源码中，没有发布新的手机包或复测任务。

## 已核对：普通入口及地图选点

[CI43／dfe8f511](research/container-api33/evidence/map-point-prefill-results.json)的Linux／Mac与五项case通过：自有框架0.539秒、已装导入7.623秒、普通入口12.604秒、计算器5.876秒、九项受控无线0.082秒。每case58份源码、移动UI59份、主程序447项／lint及7份主程序关键输入均按固定提交核对；两归档API摘要已验证。本轮原生及APK传递有意跳过，不混用CI42的产物。

- 普通页保留文件选择，新增“导入手机已装版本”：只读选定应用公开的完整APK，精确核对版本、大小、摘要、包名及原证书，拒绝分包／版本不符。计算器真实按钮导入、原应用UID／路径／版本保留、原证书和临时／虚拟副本清理均已通过，结构化JSON已核对。
- 原有首页在ARM64且预览入口已安装时显示衔接按钮，选择微信／抖音后将地图WGS84目标带入普通页。接收端真实界面已验证无效输入忽略、有效值预填，且不会自动启动Guest或启用模拟；随后原版GPSTest经文件选择、精确授权完成A→B／停止。首页发送端仅有编译／单元及静态常量一致性证据，没有真实主页面或vivo操作结果。
- 清单包含固定计算器、GPSTest、微信8.0.79／3200及抖音40.8.0／400801。后两者限主ABI为原生ARM64；清单准入不证明定位采用，也不确认用户已装版本匹配。不迁移账号／私有数据，不改签、嵌入或分发目标原包。

此前[CI42默认及三件同轮来源](research/container-api33/evidence/installed-original-import-results.json)也已核对：58份case输入、59份自有传递源码、三份同签名及每包11份材料。固定CI42驱动没有传递导入自定义JSON，CI43已补齐，不能倒填CI42的证据。

[CI44／774c6e7a最新默认回归](research/container-api33/evidence/stale-ui-location-read-results.json)也已实际通过：新增的既有自有Guest六路径3.298秒完成A→B、停止稳定及取消零回调，四阶段和最终JSON核对；本次没有出现旧节点重试，不声称实际注入了旧节点故障。六项case各58份来源、移动UI59份、主447项／lint与7份关键输入均核对；同轮三件自有APK的59份来源、三份签名及每包11份材料通过。**原生作业121405382190仍运行，固定774c6e7a及旧补丁258c75a9；不包含本轮网络回调候选。**

## 最新原生：匿名启动与框架通过，定位整项失败

[CI42／96e92522完整原生回执](research/container-api33/evidence/native-owned-location-stale-ui-results.json)已核对四归档、9份原生源码及三件自有APK。固定Android13／build16596636、原生ARM64、Native Bridge 0、UID2000、SELinux Enforcing、实际4CPU及超时倍数50合格；system_server PID651稳定，VM、自有包及staging清理通过。

| CI42实际阶段 | 结果与边界 |
| --- | --- |
| 普通原包 | 两款匿名UI通过；普通抖音PID7062保持304.205秒 |
| 容器原包 | 导入、PackageInfo、原证书和两款匿名UI通过。微信登录页PID5841；抖音隐私页主PID9406／宿主UID10102，按Core角色及实际ActivityManager身份保持301,832ms，末尾隐私页仍可见。两款本轮均无PNG，不声称查看了截图 |
| 原生自有框架 | 15.548秒／1项通过。调用PID11428、远程Provider11460、真实Binder UID10104／逻辑10001；call和query新鲜nonce及远程PID一致，Context归属、旧HTTP和两类PendingIntent创建通过。非投递、权限拒绝或独立UID隔离证明 |
| 原生自有定位 | 145.73秒／1项失败，`StaleObjectException`发生在动态状态节点`getText`，位于目标B等待。此前A断言已执行，但固定版本未单独落盘阶段；最终失败后PNG可见六条B坐标与mock标志，仍不能当作完整时效／停止／取消通过。没有最终成功JSON |

整轮4265.017秒不是启动耗时。目标应用始终关闭虚拟定位，没有接受条款、登录、发送消息或位置业务。自有Guest与宿主共享UID，样本保留mock标记；不代表微信发送／实时共享、无线／海外抖音或vivo成功。

## 当前修正及下一项

[动态界面读取修正](research/container-api33/evidence/stale-ui-location-read-preparation.json)仅捕获旧节点异常，沿原120秒等待重新寻找节点；不返回缓存报告，不吞SDK错误、JSON错误或断言失败。30秒样本时效、坐标、provider、mock、真实PID／UID、六路径A→B、注销稳定、GNSS零卫星和取消零回调均保留。已过阶段另存`stages.json`，明确`passed=false`且只作部分证据；最终成功仍需全部条件。即使界面取证失败也执行虚拟清理及idle恢复。

16秒构建、宿主静态核验及25项驱动检查通过；CI44同一云端VM的既有六路径已经实际通过，完整原生结果仍在运行。这个读取修正没有新增手机实验或SDK／引擎修改。

另准备[网络回调TransportInfo副本](research/container-api33/evidence/network-callback-transport-preparation.json)：固定API33的`requestNetwork`／`listenForNetwork`回调携带NetworkCapabilities，位置权限允许时可含WifiInfo。候选只转接Messenger，原死亡Binder、请求flag／能力／超时、返回请求及释放路径保留；在交付时检查模拟开关，采用框架TransportInfo／WifiInfo标准脱敏副本，不改变Network句柄、连接或路由、不修改原对象、不编造网络身份。复制失败没有原始敏感数据回退。18／14秒构建、宿主静态及完整补丁重放通过，新增三个受控公共API回调检查已编译，[CI45实际12项受控检查0.145秒通过](research/container-api33/evidence/network-callback-transport-results.json)，新增三项均实际执行；完整默认、三档／来源／签名／材料、447项及lint均核对。真实Guest此路径、SDK采用、其他NC敏感字段或持续Telephony仍未由此覆盖，也未认定用户微信失败原因。

1. 跟进CI44固定774c6e7a／121405382190的原生自有定位；CI45固定ccd6d3e2默认及12项回调已过，新原生121414860784按同锁pending，不取消旧轮。两轮补丁与源码不同，不能把旧原生结果冒充新回调证据。按各轮固定提交核对来源、归档摘要、签名、JUnit和清理，保留失败。
2. 原生push提交须带齐`[cuttlefish-boot] [cuttlefish-container]`，或显式手动选项。原生共用锁，不取消运行中的前轮；pending替换／跳过不算通过。当前CI42已完成失败，CI43有意仅默认，二者不能混写。
3. 账号、目标SDK和实际位置业务仍需各自证据；不自动接受条款、登录或发送消息。原生自有六路径通过也不能宣布用户目标完成。

[最新默认CI45](research/container-api33/evidence/network-callback-transport-results.json)六项case均过：框架0.976秒、六路径3.727秒、已装导入8.116秒、普通入口12.417秒、计算器6.402秒及12项受控无线／回调0.145秒。每case58份、移动UI／三件自有各59份来源、三份签名／每包11份材料、主447项／lint及7份关键输入均核对。原生45只排队，不算通过；原生44持续运行且没有被取消。

[手机三步交付说明](docs/original-app-preview-flow.md)已收敛为地图选点、导入一次、应用并打开；预览需自行登录且不迁移私有数据。本页是交付说明草稿，当前没有新发布包，不要求现在重复实验。

## 已知证据边界与历史

- [SDK只读核对](research/container-api33/evidence/target-location-sdk-boundary-review.json)：固定微信含`sapp`组件／独立native库；抖音含AMap／BDLocation及模拟判断方法标识。公开腾讯SDK开关、方法名及2024年公开版本默认值不能推到目标运行配置，也未认定用户失败原因。不执行SDK或读账号／私有数据。
- [CI40失败](research/container-api33/evidence/native-pid-set-and-owned-stage-results.json)：抖音已到隐私页，PID集合9665→9665/10333使旧集合相等检查失败；原PID仍在、新PID角色未知，自有定位被跳过。CI42验证了按主角色观察的完整保持，但没有重现同名集合变化，不能把成功归因于单一修正。
- [CI39真实归属拒绝](research/container-api33/evidence/provider-attribution-failure-and-correction.json)保留：Binder真实UID10129而AttributionSource错误为10001；修正真实宿主归属后call／query通过。原引擎旧URI授权恒GRANTED未改，功能正例不验证权限拒绝／隔离。详见[审计](research/container-api33/AUDIT.md)。
- [CI38完整匿名启动](research/container-api33/evidence/native-container-startup-pass-results.json)、CI35严格PID集合失败及[更早黑屏／环境失败](research/container-api33/evidence/cuttlefish-matched-original-and-black-screen-results.json)均保留。此前x86_64六路径与GPSTest定位是其各自历史范围。
- 用户10月23日VPN开／关导出缓存新鲜且接近模拟目标，微信截图及发送／实时共享反馈仍失败。导出快照不等于微信前台连续采样，10月13日旧实验也不算新结果；#59此前脱敏验收保留，实际地址截图未上传公共仓库。更新#59只改指定后续研发标记段。

构建使用仓库外`CONTAINER_RESEARCH_WORKDIR`及`tools/run-build.sh`，保留代理／CA／TLS，SDK安装串行。回执用`tools/verify-ci-receipts.py`按固定Git提交核对，新输出保留旧证据。许可与来源见[记录](research/container-api33/THIRD_PARTY_NOTICES.md)。
