# 天外天接手入口

更新时间：2026-10-29。以本文件为主入口；运行源码、交付源码和证据提交分别记录，不能混用旧轮成功。

## 用户目标与唯一主线

用户只有一部Android13／vivo手机，**无Root、无电脑**，希望减少实验、持续推进。**微信发送／实时共享、抖音无以太网／海外定位仍未完成真机验收。** 云端匿名页面和自有定位通过，不证明目标SDK已采用位置。

| 角色 | 分支与PR | 状态 |
| --- | --- | --- |
| 唯一继续研发 | `research/container-api33-20261023` · [Draft #60](https://github.com/soozooq/gogogo/pull/60) | 从本分支HEAD继续，修正与证据都留在这里 |
| 日常候选保留 | `fix/location-input-validation` · [Draft #59](https://github.com/soozooq/gogogo/pull/59) | 固定2620b741／2.4.31／2400099，代码未更新 |

不新增分支／PR、不自动合并、不删除／强推／重置其他工作区。两个PR保持Draft。手机用户不需要重跑旧实验或切换VPN。

## 界面优化候选：2.4.36／预览宿主0.5，尚未交付

用户新增要求：美术与排版一起优化。地图首页、统一搜索、设置和共享控件统一靛蓝色、字阶、圆角、留白、矢量图标及禁用态；地图选点卡按实际可用高度滚动，署名保留在卡片上方，加载失败可重试。原版预览改为01选择应用、02确认地点的卡片表单，打开／停止／一键反馈固定在底部，小屏大字体转纵向；浅／深色主题完整。显式结果自动滚入可见区，普通刷新不抢位置。设计集中在[DESIGN.md](DESIGN.md)。

主2.4.36／2400104与宿主0.5／5均未交付。沿用原导入、明确打开、停止和1.5秒反馈协议，无新手机实验、权限、依赖或引擎修改。旧2.4.35字节／下载及Phone发布策略不动；57原生只属于旧c805，不借给新界面APK。正常使用有问题仍一键反馈，不要求手机做矩阵。

本地447／lint／主ARM64与宿主／驱动构建、launcher检查通过。软件VM系统Lost network stack导致两包安装Broken pipe，0有效截图，已停止，见[原始准备](research/container-api33/evidence/visual-refresh-preparation.json)。下列云端失败全部保留：

| 轮次 | 实际结果与后续修正 |
| --- | --- |
| [58](research/container-api33/evidence/visual-feedback-visibility-failure-results.json)／7e90cf09 | 普通入口复制成功文本不在可见区，后续断言未执行；现显式结果滚入可见区，59已验证原复制／内容／只读断言通过 |
| [59](research/container-api33/evidence/visual-stop-overlay-and-capture-probe-failure-results.json)／0b3348f3 | 复制已过，Stop后10秒PID3443仍在；日志有剪贴板浮层但原因未证实。驱动只处理已知Android13浮层再点Stop，原10秒PID／CLOSE断言保留；60、61全入口实际通过。截图前pm path缺包正常exit1误判也已修正 |
| [60](research/container-api33/evidence/visual-clean-boundary-failure-results.json)／47550800 | 全7运行case、7定位／19无线／独立九路径／447lint及Mac build-only均过，反馈1381字符、正常停止通过；截图因运行case有意保留己方包而按保护拒绝。已改到moving capture验证清理后执行，未删现存安装保护 |
| [61](research/container-api33/evidence/visual-first-layout-geometry-failure-results.json)／117563a2 | 全7运行case／447lint／Mac再过。实际UI测试首个搜索控件48dp可见高度断言失败，未保存当时几何／截图，原因未知；system_server555稳定、三己方包清理及显示设置恢复过。现等待稳定几何、用同一自动化视口与dp换算，记录实际矩形并收集全部profile，再统一判失败；48dp和全部profile要求不变 |
| [62](research/container-api33/evidence/visual-large-type-layout-results.json)／a4b0d107 | 全7运行case／447lint／Mac过，实际取回16张PNG／XML（14个常规阶段＋2张失败现场）。UI 71.086秒：360dp两主题流程完成；320dp／1.5字体两主题坐标框未被自动化滚动找到，整轮失败。普通“键盘”PNG早于输入法出现，不能算真实键盘验收。根据原图确认地图主操作隐藏、大字搜索按钮换行、预览头占空间，正按下文修正 |

| [63](research/container-api33/evidence/visual-pinned-map-remaining-form-results.json)／d222ced0 | 地图主操作固定与搜索精简在原图生效；正常360dp真实键盘与坐标／固定操作通过，但页头仍未收起。大字320dp两主题仍未找到坐标框，14常规＋2失败图，整轮失败。Linux七case／447lint及Mac过，Linux＋Mac归档独立核对；1日own传输档取回前已过期，未独立核对APK，不能补认。 |

58～62实际三档API摘要／73来源／三同签名与每包11材料均已独立核对。渲染x86包不是ARM手机包，62地图瓦片及地图SDK联网加载未验收。**完整新界面尚未通过，也没有新候选交付。** 新修改将地图主操作固定在滚动区外，地点库等保留在“更多”，大字体去除装饰标语；搜索服务改矢量图标、说明与署名压缩；预览页键盘显示时收起页头。仍是2.4.36／宿主0.5，未分发前继续同一候选。主447／lint／ARM64构建52秒、宿主／驱动33秒及launcher检查通过，引擎未改。

当前修正进一步缩短导入按钮文案：320dp／1.5字体保留两列导入，坐标及底部操作仍纵排；IME监听移到DecorView获取未消费的原始可见状态。云端驱动改慢速真实滑动并保存有上限的滚动高度／输入框几何，不改产品状态；实际IME出现后还必须确认页头消失。宿主／驱动构建13秒通过，待下一轮实际截图；不把编译当作修复验证。原48dp、四profile全部通过及16个正确阶段要求保留。无新手机实验或昂贵原生目标重跑。

## 当前可下载候选：2.4.35，Phone10合格Artifact已实际交付

[下载已核验安装包ZIP（GitHub需登录）](https://github.com/soozooq/gogogo/actions/runs/40626531080/artifacts/12789442894)，26,833,656字节、SHA43c8b4c9…，保留至2026-11-11T03:25:33Z。工作区同字节副本 `/workspace/deliverables/Tianwaitian-2.4.35-qualified.zip`。ZIP内只有两份自有APK及六份说明／校验／资格元数据，不含原版微信／抖音APK、账号资料或私钥。完整[实际交付与下载验真回执](research/container-api33/evidence/phone10-qualified-artifact-verified-delivery-results.json)。

[Phone10](https://github.com/soozooq/gogogo/actions/runs/40626531080)的交付作业122612702022实际成功，执行源码**beab6368d0e35b4abc12fa89b293c37583b06f1d**；直接复用Phone9成功stage122515194094／原构建源码4de425d2与原包归档12776327526，打包作业跳过、没有重建APK或重跑原生。云端重新下载四档实际证据并核对全部来源／签名材料、原生7方法／九路径／300秒身份／清理前活主快照／系统清理及两包、profile／447检查／lint／415内容／最新源码。下载后再次独立复核，服务器ZIP与工作区副本摘要相同。

| 文件 | 实际版本 | 摘要与大小 |
| --- | --- | --- |
| `Tianwaitian-2.4.35-container-preview-arm64.apk` | 2.4.35／2400103 | SHA f1796794…；40,408,202字节；证书6eec4ddd… |
| `Tianwaitian-Original-Preview-c8052d31.apk` | 0.4／4 | SHA 4d1f0725…；4,594,179字节；证书25bfd6e2… |

手机解压后，已有2.4.34主程序时只更新“Original-Preview”预览APK；首次或仍在2.4.31时安装两份。沿用地图选点→预览入口导入原版→确认打开的正常流程；当前固定支持微信8.0.79／3200、抖音40.8.0／400801，登录由用户自行操作。正常使用遇到问题，一键复制已有观察即可，不做旧实验矩阵。

这次**成功交付的是合格Artifact，不是公开Release**。Release创建仍实际返回HTTP403 Resource not accessible by integration；仅在完整资格通过后，已知403才转为明确published=false的Artifact交付，其他错误继续失败。没有新c805草稿／tag，公开Release仍2.4.34，旧6625草稿与全部旧附件不变。后续运行源码更新使用新候选版本并重新绑定同源证据，不把已分发的2.4.35／宿主0.4当作可随意修改的未交付草稿；仅controller修复可在冻结字节不变时复用。

## 57同源原生与默认结果已通过，目标位置业务仍未验收

运行源码固定**c8052d318bed3cf9c0e37ddc864abea4cc74c816**／引擎16440679。退出回调仅清理完全相同的当前ProcessRecord，主动停止在拥有登记时清理资源；合成被动GPS来源、微信兼容呈现／通用mock、合成extras及零卫星保留，无新手机页面、权限或实验。

[57原生实际通过已独立验真](research/container-api33/evidence/native-retired-process-owner-pass-results.json)：run40595256036／原生122512447262成功，四档API归档／9原生与59自有来源／三同签名与11材料匹配c805。微信关闭5942→OWN新6420／UID10097、抖音10142→11954／UID10102均到匿名UI；旧主实际消失，清理前Core及真实AMS快照确认新主活着。目标有界日志分别记录2／1次已退休回调被忽略；不倒推56旧失败一定由该竞态导致。抖音普通原包主7696／UID10099经48次AMS＋内核检查、实际304.425秒稳定。自有框架14.796秒、7方法6.703秒、独立Guest九路径46.374秒，最老样本1606.406毫秒、取消零回调；system_server657稳定，VM及包／staging清理完成。整轮4391.975秒不是启动耗时。

[57默认结果](research/container-api33/evidence/retired-process-owner-linux-results.json)：Linux7定位／19无线／447主检查与lint、受控退出归属fixture、真实Guest九路径与1380字符反馈均过，Mac仅为实际build-only通过。**微信SDK采纳／发送／实时、抖音无线／海外及vivo效果仍未验收**。匿名UI及自有契约不是目标账号位置业务，没有自动同意条款、登录或发送消息。

## 交付失败与修复均保留

[Phone9最终失败](research/container-api33/evidence/phone9-publication-download-failure-results.json)是下载own ZIP误带octet-stream Accept，实际HTTP415，云端四档核对未执行、无qualified下载。早期Release创建403是独立问题；Phone7未记录HTTP原因，不能倒推相同原因。原两APK字节与Phone10交付内容完全相同，原失败不追认。

[恢复准备](research/container-api33/evidence/phone9-download-recovery-preparation.json)修正Actions ZIP正常API媒体类型、release asset保留octet-stream，冻结复用原成功stage及归档ID／大小／摘要；所有资格门槛保持。17项本地检查、策略片段及真实归档／APK的本地记录API回放通过，回放不算云端交付。随后Phone10实际执行12项云端发布边界及全部实际证据／APK检查、生成8文件合格归档并成功上传，最终回执取代准备中的尚未启动字段。

## 最近完成结果：56原生微信重开失败，Phone7已打包但未发布

运行源码固定**99940ea20ef4e7bee27a65fe759a810bf0942672**／引擎420b8399。[56 Linux完整通过](research/container-api33/evidence/passive-provider-linux-results.json)：7定位、19无线、七case＋变化UI、447主检查／lint；真实Guest九路径4.278秒，被动样本来源gps、通用mock与合成标记保留，普通反馈1381字符。两档API／58及59来源／三同签名及材料已独立核对。固定原包[被动请求／监听绑定](research/container-api33/evidence/wechat-sdk-passive-provider-routing-results.json)发现的passive请求来源应为合成gps修正保留。

[56原生实际失败已验真](research/container-api33/evidence/native-passive-wechat-restart-failure-results.json)：作业122374016460结束失败，三档API归档／9原生与59自有来源／签名材料匹配。微信关闭模式欢迎页通过、旧PID5931已消失，但OWN重开300秒未回到匿名页、登记主PID为0，最后显示自身预览页；crash日志0字节，DeadObject警告不能单独证明崩溃原因，旧记录无清理前快照。抖音双模式匿名启动通过（9841→11986／UID10102），关闭观察实际300635毫秒，同名pidof集合变化但登记主身份稳定。三个自有原生case均过：框架、定位九路径45.871秒／最老1742.188毫秒、7方法6.429秒；system_server643稳定，VM及包／staging清理完成。整轮4498.523秒不是启动耗时。**分项通过不能把整轮或微信重开改写成通过**。56 Mac无runner／0步骤容量取消，未编译；原生失败后不允许单独Mac重试，未POST重试。

[Phone7实际字节与接口失败](research/container-api33/evidence/phone-99940ea2-build-and-release-api-failure-results.json)：交付源码37a15f6b／run40564477274，已实际完成主447检查／lint、固定profile、扁平六文件、版本／签名／对齐及宿主415非签名内容核对。主40,408,206字节、SHA46e92a5d…；宿主4,590,083字节、SHA28556e78…，证书沿用。创建release草稿的POST失败，原helper没有保留HTTP响应，**原因未知**；发布作业跳过，无新99940草稿或tag，不能称已经交付／发布。

旧99940交付策略已被暂停，新增preflight在构建前暂停旧候选的打包与发布；Phone8实际验证了关闭。当前[冻结交付策略](ci/release-qualifications/2.4.35.json)已重建为57／c805，见上文。脚本现保留有界API失败详情；仅草稿附件步骤可继续，同源四档证据／7方法／九路径／300秒主身份／系统清理／本轮APK／签名／415内容／最新源码等门槛全部仍强制。只有完整核对后才创建qualified-phone下载目录；这是后续交付韧性修正，尚未实际执行或取得Release权限。旧56冻结输入不得借给新源码；curator现读取已冻结策略并在读现场前拒绝错源码／轮次，57还强制检查退出归属fixture与清理前主进程快照。旧6625草稿425599161及public2.4.34保持原样。所有旧准备中的“等待／运行中”字段是当时快照，以本入口最新实际结果为准。

[55失败](research/container-api33/evidence/passive-fixture-attribution-failure-results.json)保留；99940仅修其generic fixture身份后56 Linux／原生7方法均实际通过，不追认55。上一轮[54原生通过](research/container-api33/evidence/native-original-main-identity-pass-results.json)仅覆盖66a293fa／839947fa及6方法／六路径，不能借给新引擎。微信发送／实时、抖音无线／海外定位与vivo仍未验收，手机用户无需重跑旧实验。

## 上一轮：修正原包主进程身份对照（66a293fa）

[CI53原生失败已独立验真](research/container-api33/evidence/native-original-pidof-set-failure-results.json)：固定66258d1d的三档API摘要／9原生输入／59自有来源及签名材料核对，系统合格且VM已停止。微信原包及容器关闭→停止旧PID→OWN模式新PID5887→6513／真实UID10097的匿名启动通过。抖音直装原包已到隐私页，但300秒观察在32.763秒时因pidof输出7690→“7690 8696”失败；原PID仍在列表、crash日志0字节。旧记录没有系统登记主进程身份，不能倒推真实主进程一定退出或一定稳定，也不能把这次失败归因于容器定位摘要。抖音容器与自有框架／定位契约未执行，不得写成新ARM64通过。

[修正准备](research/container-api33/evidence/original-main-identity-preparation.json)仅改云端Python对照脚本：以PackageManager用户0真实UID、唯一AMS登记主进程及内核PID／UID交叉核对，pidof列表仅作辅助记录。增加同名辅助进程或列表重排不等于主进程更换；真正主进程变化（即使旧PID还在列表）、UID错误、登记缺失／歧义或内核身份缺失仍失败。保持完整300秒、目标及system_server崩溃检查、普通shell／Enforcing、环境和清理要求；只保存有上限的身份头信息，不复制完整AMS转储或账号资料。

27项本地驱动检查0.666秒通过，包含两项身份解析检查及辅助进程、列表重排、主进程替换、UID／内核缺失等集成场景。修正已提交66a293fa，[CI54](https://github.com/soozooq/gogogo/actions/runs/40547400219)固定该源码运行；[默认结果已独立验真](research/container-api33/evidence/original-main-identity-default-results.json)：云端27项驱动0.644秒，Mac实际构建及Linux6定位4.203秒／19无线0.138秒／七case＋变化UI／447主检查与lint均通过。三档API摘要、58／59来源、三同签名及11材料核对；真实Guest3017、宿主2950，消费者准备／接收PID均3017，六路径3.264秒／最老313.919毫秒、取消零回调，普通反馈仍1322字符。原生54随后已完整验真通过，见独立原生回执；它不追认53失败，也不覆盖新的420b引擎。[415非签名内容逐项相同](research/container-api33/evidence/original-main-identity-candidate-content-results.json)，并核对手机源码／profile与66258d1d无差异；Phone CI6仍保留真实构建来源66258d1d，资格对照为66a293fa，不虚构新手机构建。手机Java、AIDL、manifest、839947fa引擎和2.4.35／0.4版本不变，继续同一个未公开候选，不要求手机重测。旧Mac等待任务因原生失败已按条件停止，没有POST重试；后续验证修正后的源码，不重跑旧53。

## 候选功能：自动记录消费者调用与交付摘要（仍未公开）

现有短反馈只说明配置了哪种兼容呈现，不能判断虚拟应用实际是否请求了这条链。本轮在原“一键复制反馈”中自动附加消费者缓存／current准备次数、持续注册／Binder发送返回次数、缺样本／失败计数及最近事件年龄。配置页读取不计入；摘要查询不读坐标、不请求新定位、不启停模拟或目标应用。记录最多16个用户／包组合，仅在Core内存，不跨重启持久化，也不等同本次应用会话。无记录可能是尚未调用、Core重启或记录淘汰，不能据此认定SDK失败。

缓存／current计数只表示样本已准备，持续发送返回可能只是单向Binder已排队，都不证明SDK采用。真实Binder调用／接收PID用于受控检查，不复制到手机反馈；不保留坐标、Location对象、IP、SSID、基站ID、Key或SDK栈。沿用1.5秒复制上限及旧回调防护、1800字符运行约束，未知和超时明确标注；不新增页面、操作、权限、依赖或JNI，也不改变2.4.34的微信／通用mock呈现规则。

[准备记录](research/container-api33/evidence/consumer-delivery-preparation.json)：宿主候选0.4／4，主profile预留2.4.35／2400103。28秒与16秒编译、宿主静态核对、完整补丁干净重放及25项驱动检查通过；新增AIDL仅追加，旧事务顺序保持。[CI53 Linux实际已验真通过](research/container-api33/evidence/consumer-delivery-linux-results.json)，固定66258d1d／839947fa：6方法定位4.265秒、19受控无线0.234秒、七case／变化UI与447主检查／lint均过；两档API摘要、58／59来源、三同签名及每包11材料核对。真实独立消费者PID3031与准备／接收PID对应，宿主2965不同；GPS／NETWORK各缓存2、current2、发送返回3，通用mock与合成来源保留，六路径4.283秒。受控微信上下文的计数差分、快照隔离和配置不计数也通过；原普通复制实际1322字符，保留1800上限／Guest主PID／模式，未记录坐标。普通入口14.779秒、已装导入7.948秒、计算器6.041秒。[ARM Mac构建122257025243因GitHub hosted runner容量不足取消](research/container-api33/evidence/ci53-mac-runner-capacity-results.json)，runner=0、0步骤，未运行编译，不能算通过或代码失败。该作业在排队时没有执行编译。此前单独重试曾因整轮运行收到403；当前53原生已失败，旧条件等待任务已停止且未POST，改验修正的对照源码，不借CI52或把旧失败当成功。没有启动本地软件VM或要求手机实验；公开2.4.34及旧版附件保持原样。 [Phone CI6实际草稿交付已核对](research/container-api33/evidence/phone-66258d1d-verified-draft-delivery-results.json)，交付源码da2d9135／run40527332462、运行时66258d1d：两档API归档／固定profile／六文件／版本与签名／415非签名内容／59来源及11材料／两附件服务器摘要均过。主程序2.4.35-code2400103，40,408,206字节，SHA94019ea6…、沿6eec4ddd…证书；宿主0.4-code4，4,590,083字节，SHAa85bf353…、沿25bfd6e2…证书，公开配方逐字节恢复相同APK，私钥不传入CI。两附件仍在未公开草稿425599161，同源原生与Mac门槛尚未通过，不能发布或称定位业务已成功。此前有界等待因53原生失败停止、未执行Mac重试；新资格验证须绑定修正后的固定源码。

## 已发布基础：微信定位SDK兼容呈现（2.4.34）

用户确认继续以「微信自身SDK会不会采用」为重点。已从固定微信8.0.79原包的`assets/TencentLocationComp_sapp/1`离线解码出537,976字节DEX，原包及DEX头长度／SHA1／Adler32完整核对；外层加载器请求的TencentLocationManagerProxy确实存在于该组件。421类／2510方法扫描发现四处isFromMockProvider，独立apkanalyzer确认：其中b.a(Location)与g.a(Location)在常规前置检查后，对API≥18的mock样本提前退出。另两处有配置条件。见[原包组件与过滤分支证据](research/container-api33/evidence/wechat-bundled-sdk-mock-filter-results.json)。这证明始终暴露mock=true与这些分支冲突，不证明用户手机当前运行了哪条分支或失败因果。

2.4.34改变此前「所有合成返回均保留系统mock标记」的策略：只对预览中固定原版微信的虚拟会话使用兼容呈现，GPS／NETWORK缓存、单次及持续回调统一返回不带框架mock标记的位置；仍在extras及短反馈明确标注合成来源，卫星数保持零。普通／未知／其他客户端继续保留mock标记。坐标仍由选点合成，没有变成真实GNSS；系统原微信、系统模拟Provider和原APK均未修改。兼容对象只绕开这些已确认的标记检查，其他SDK／网络／账号／服务端决策仍未知。

已交付预览宿主0.3／3、主程序2.4.34／2400102。[CI52默认已独立验真通过](research/container-api33/evidence/wechat-compatibility-presentation-default-results.json)，固定a26d14a5／d820补丁：6方法定位4.214秒、19受控无线0.148秒，七case／变化UI、主447项／lint、三档API摘要和58／59来源／同签名／11材料核对。公共框架＋受控微信包上下文的六路径、两个mock别名、合成extras、零卫星和恢复默认检查均过；原通用mock断言保留，自有真实Guest四阶段六路径3.282秒继续标记。普通入口12.989秒、已装导入8.608秒、计算器5.593秒。它不是实际微信进程或SDK采用结果。

[同源ARM64 CI52已完整验真通过](research/container-api33/evidence/native-wechat-compatibility-presentation-pass-results.json)：四档API摘要／9原生输入／59自有来源及签名材料匹配a26d14a5，新增受控契约6方法6.086秒，逐个成功方法与固定源码核对。真实自有Guest六路径46.627秒、框架15.604秒，通用mock保留，最老样本1223.857毫秒，取消零回调。微信关闭6315→OWN模式新6861、抖音10969→13068，真实UID与匿名页、旧主进程消失、system_server650稳定及VM／包／staging清理均通过；300秒抖音观察仍仅关闭模式。两目标无PNG，不声称看过截图；整轮4676.744秒不是启动耗时。受控微信包上下文不是真实SDK采用。[准备记录](research/container-api33/evidence/wechat-compatibility-presentation-preparation.json)保留24秒／13秒编译、静态核对、完整补丁重放及25项驱动结果。稳定私签宿主与CI52全部415非签名内容相同，公开签名配方可逐字节恢复；交付源码d606261b的[Phone CI5](https://github.com/soozooq/gogogo/actions/runs/40491596045)实际两档归档、安装包、签名、版本与附件摘要均独立核对后，已公开2.4.34；打包作业仍只写草稿，公开是后续单独执行。没有新增手机实验页、权限、依赖或JNI，不要求手机重测。公开2.4.33不包含新兼容策略，附件和tag保持原样。

## 最近已独立核对的公开交付：2.4.34（新2.4.35以自动回执为准）

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

[抖音内部样本路径补查](research/container-api33/evidence/douyin-internal-sample-branch-review.json)确认com.loc中一条路径除mock外还检查extras整数及部分质量字段，helper为true时在后续样本打包前返回。这尚未被证明是页面定位结果路径，活跃设置和失败因果未知；未据此改变抖音呈现策略，也未编造卫星或运动值。

继续沿唯一研发分支HEAD。新增运行变化必须绑定固定源码核对；原生push带齐[cuttlefish-boot]和[cuttlefish-container]，共用锁不取消前轮，排队／跳过不算通过。证据工具为tools/verify-ci-receipts.py；构建用仓库外CONTAINER_RESEARCH_WORKDIR及tools/run-build.sh，保留代理／CA／TLS。新结果用新输出，不覆盖旧证据。许可与来源见[THIRD_PARTY_NOTICES.md](research/container-api33/THIRD_PARTY_NOTICES.md)，已知边界见[AUDIT.md](research/container-api33/AUDIT.md)。
