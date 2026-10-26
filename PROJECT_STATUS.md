# 天外天接手入口

更新时间：2026-10-26。此文件定义当前进度；旧 main、旧日志和实验数量不能替代实际结果。

## 目标与唯一研发分支

用户只有一部 Android13／vivo 手机，**没有 Root、没有电脑**。微信发送位置／实时共享已有用户失败反馈；抖音不依赖以太网及海外定位也**尚未完成**。不让用户重复旧实验，不把缓存、自有检查或匿名启动写成第三方定位采用。

| 角色 | 分支与 PR | 约束 |
| --- | --- | --- |
| 继续研发 | **`research/container-api33-20261023` · [Draft #60](https://github.com/soozooq/gogogo/pull/60)** | 本分支 HEAD 是唯一研发主线，继续提交这里 |
| 日常候选 | `fix/location-input-validation` · [Draft #59](https://github.com/soozooq/gogogo/pull/59) | 固定 `2620b741dd6a971143577737c64ebc4496120d77`，`2.4.31-simple-support / 2400099`；本轮未改 |

保留其他工作区及未提交内容，不新增分支／PR、删除分支、强推、重置他人工作区或自动合并。研究容器未接进日常程序，没有新手机包或复测任务。

## 已越过：原生 ARM64 匿名启动

[CI38／adfc6173 的全部4归档及8份原生源码已核对](research/container-api33/evidence/native-container-startup-pass-results.json)。固定Android13／build16596636、原生ARM64、Native Bridge 0、UID2000、SELinux Enforcing、实际4CPU、超时倍数50合格；system_server PID658稳定，VM及自有包均清理。

| CI38 实际阶段 | 结果与边界 |
| --- | --- |
| 两款普通原包 | 微信登录页、抖音隐私页通过；普通抖音随后302.664秒PID稳定 |
| 两款容器 Guest | 导入、PackageInfo、SigningInfo及原证书通过；微信登录页通过，抖音隐私页通过 |
| 抖音持续观察 | 同一PID9567保持301,750ms；最后XML仍有隐私页文本，未接受条款 |
| 图像／日志 | 微信截图已查看，确有登录页；抖音没有PNG，不能声称查看了截图；两份XML与JUnit已核对。crash buffer未匹配Fatal不等于没有任何异常 |
| 未覆盖 | 定位关闭；没有接受条款、登录、发送消息或位置业务。不是vivo真机、微信发送／实时共享或无线／海外抖音通过 |

整轮3993.935秒包含输入、安装及所有阶段，不是启动耗时。微信JUnit445.766秒，抖音784.372秒。专用TAP不等于互联网或物理蜂窝可达；无线业务仍未验证。该历史轮普通入口仅准入计算器和GPSTest。本轮候选增加固定原版微信／抖音及已安装APK导入，实际新入口待CI；不改签、嵌入或分发第三方原包。

[CI35／b04ac30d](research/container-api33/evidence/native-container-startup-pass-results.json)也已进入两个Guest界面：微信通过；抖音在隐私页后的2069ms，pidof集合从9448/10120变为9448，严格集合检查失败。消失PID角色未知，crash buffer没有匹配Fatal，不能写成主进程或JIT崩溃。CI35已有CriticalNative修正、尚无网络数组修正，因此CI38的成功不能归因于单一组件。两轮失败／成功均保留。

CI36原生pending被最新pending替换，结论cancelled；CI37漏带提交标记，原生及同轮自有APK传递均跳过。二者不算原生通过。CI34下载准备失败、CI33黑屏、CI31启动异常与旧环境失败均保留，见[历史对照](research/container-api33/evidence/cuttlefish-matched-original-and-black-screen-results.json)。触发规则见AGENTS.md，CI38已经实际验证补正后的push路径；手动选项只完成静态检查。

## 正在验证：Provider真实调用归属及原生定位阶段

System.err在CI38微信辅助进程6322／线程6401抓到两次旧holder的Field.set空指针，未阻止登录页。API26+现代holder字段修正后，[CI39／53c94bb6的新跨进程契约实际失败](research/container-api33/evidence/provider-attribution-failure-and-correction.json)：调用方2589、Provider2614，Android明确拒绝“Calling uid: 10129 doesn't match source uid: 10001”。真实Binder发送者为宿主10129，ContentProviderStub却把AttributionSource改回虚拟10001。UI及447项主程序检查通过，但框架0.528秒／1项失败，后续无线／计算器／普通入口未运行，原生因基础失败跳过。三归档及56份失败case源码已核对，不算原生失败或通过。

Provider调用归属改为真实宿主UID后，[CI40／cf4e2822公共跨进程调用实际通过](research/container-api33/evidence/provider-attribution-results.json)：调用进程2634、Provider2661、回报调用者PID2634，新鲜nonce一致；真实Binder UID10129、Context归属、旧HTTP和两类PendingIntent创建也通过。反射异常解包保留平台拒绝，没有关闭权限／UID检查或改变exported状态；现代holder及null结果保留，旧API未实测。这是x86_64自有应用的真实调用，不是腾讯SDK或ARM64定位证明。

另准备[原生自有Guest定位阶段](research/container-api33/evidence/native-owned-location-preparation.json)：同一合格ARM64 VM及普通原包对照通过后，独立记录目标Guest匿名UI与自有契约，自动执行自有框架／跨进程Provider，以及原有六条GPS／NETWORK A→B、持续更新、停止稳定与取消零回调。三件自有宿主／测试／独立消费者严格核对源码、大小、摘要、签名及许可材料；消费者只虚拟导入、不安装到系统，保留已有包与文件，清理失败不能通过。没有目标SDK、账号或位置业务。

11秒最终构建、两份静态检查、三件本地材料／同签名、完整补丁重放、25项受控驱动检查及8种工作流选择组合通过；旧CI38完整四档与CI39失败三档也由新核对工具重新验证。**CI40同轮三件传递清单、59份完整源码、三份签名及每包11份材料已实际核对；修正后调用在x86_64通过。原生CI40已返回失败：自有定位阶段没有执行，不是定位通过或定位失败。** 原生输入源码新增模块共9份，当前CI38证据仍是其原来的8份。

限定诊断加入DatabaseUtils错误标签并保留限时、限量、URL脱敏及“采集不可用不改变case判定”；dumpsys／ps发生在JUnit及Guest清理之后，不能当作此前黑屏时的活体状态。

另补[私有Provider查询契约](research/container-api33/evidence/private-provider-query-preparation.json)：固定API33的query还会检查同App UID／exported／URI权限，call通过不能证明查询。自有未导出Provider只返回合成nonce和PID，新增公共query验证游标往返；原引擎checkUriPermission仍返回GRANTED，本轮未改，查询成功不代表权限拒绝或隔离通过。[CI41实际功能查询通过](research/container-api33/evidence/private-provider-query-results.json)，call／query均回传本轮nonce、同一远程PID2685，调用者2658；Provider Process API UID10001、Binder调用者UID10129，0.902秒／1项。没有改引擎或权限，本轮原生和APK传递均有意跳过；CI40仍按固定cf4e2822运行，其旧消费者没有新增query检查，不能替代ARM64 query证明。

## 最新原生失败与手机导入简化候选

[CI40实际原生结果](research/container-api33/evidence/native-pid-set-and-owned-stage-results.json)已按固定cf4e2822核对四归档和9份原生源码。系统资格通过，PID663稳定，VM及自有包清理；两个普通原包通过，普通抖音持续304.677秒。容器微信登录页通过；抖音已看到隐私页，但5730ms后同名PID集合9665→9665/10333，严格集合相等检查失败。原PID仍在，新增PID角色未知，crash buffer无匹配Fatal，不能断言主进程退出或JIT崩溃。抖音PNG已查看；微信本轮无PNG。整轮3969.035秒不是启动耗时，目标定位保持关闭。

候选改按Core注册的主进程角色与真实ActivityManager PID／UID持续观察300秒，保留同名集合变化并复查最后隐私页；主进程消失、UID不符或界面失败仍失败。自有定位阶段改为普通原包对照及同一合格VM通过后独立执行，身份、已有文件／包保护、清理及总结果门槛不变，不再因目标UI失败跳过整项定位。

[手机导入入口候选](research/container-api33/evidence/installed-original-import-preparation.json)用一个页面选择应用、一次导入、填坐标和打开。新增“导入手机已装版本”，只读所选应用公开的完整APK，不复制账号或私有数据；版本不符／分包时提示选择固定官方APK。原应用保持不变。固定微信8.0.79和抖音40.8.0仅在主ABI为原生ARM64时准入；大小、摘要、包名、版本及原证书核验保留，不允许任意版本。13秒构建、宿主／消费者静态检查及25项驱动检查通过；新增云端真实按钮导入、原应用保留及清理检查仍待CI。没有新手机包或复测任务。

## 定位及默认检查的实际范围

定位功能的既有实测仍为Android13 **x86_64**：[独立自有Guest](research/container-api33/evidence/owned-guest-location-results.json)六条GPS／NETWORK路径A→B、停止稳定及取消零回调；[官方GPSTest](research/container-api33/evidence/official-gpstest-location-results.json)实际界面采用A→B、GNSS零卫星；[普通入口](research/container-api33/evidence/cloud-ordinary-entry-pass.json)经系统文件选择、原包导入、精确授权与同进程更新。**ARM64真实自有Guest定位尚无已核对结果；当前匿名启动不能填补它。**

[最新默认CI41](research/container-api33/evidence/default-ci-state.json)：Linux／Mac与四项case通过；框架0.902秒、计算器6.258秒、普通入口11.998秒、九项受控无线0.106秒，每case57份源码，变化UI59份，447项及lint通过，两归档已核对。本轮没有同轮APK传递；先前[CI40三件／59份源码／三签名及每包11份材料](research/container-api33/evidence/provider-attribution-results.json)单列保留，不混作CI41新产物。自有框架确认真实Binder身份、新鲜nonce、Context归属、旧HTTP和两类PendingIntent创建，未验证发送／后台投递。

九项检查使用受控服务、实际框架包装和合成输入；[网络数组修正](research/container-api33/evidence/connectivity-array-contract-results.json)保留关闭模式数组、Network句柄与连接状态，开启时只清除NetworkInfo框架副本的extraInfo；两项异步CellInfo请求完成空列表回调。没有真实无线查询，不证明Guest无线Hook、持续订阅或腾讯／字节SDK采用。样本保留mock标记；Guest与宿主共享UID，不是独立UID安全沙箱。详见[审计](research/container-api33/AUDIT.md)。

同分支另准备[简短默认反馈](docs/simple-support-flow.md)：当前状态与缓存优先，旧工作流只列索引，14,000字符上限；[编译、447项Debug检查及lint通过](research/container-api33/evidence/compact-feedback-preparation.json)，没有发布手机更新。定位引擎与#59日常候选保持原版本。

## 用户失败与继续操作

用户10月23日VPN开／关日志中，导出时GPS／NETWORK／GMS缓存新鲜且接近模拟目标，但微信截图及反馈失败；导出快照不是微信前台连续采样，10月13日旧实验也不是本轮结果。#59保留脱敏失败验收，实际地址截图未上传公共仓库；更新#59只改标记的后续研发段，保留此前正文。

1. CI40／cf4e2822原生作业121318547685已经失败，已核对四档及9份原生输入；自有定位被同名PID集合门槛跳过。继续同分支验证新的主进程身份检查、独立自有定位阶段与简化导入，保留CI38通过及CI39／40失败，不重复手机实验。
2. 核对新三件自有传递与原生自有Guest A→B／更新／停止／取消结果，再推进目标SDK和业务。账号与业务未验证的阶段不能写成成功，不接受条款、登录或发送消息，不发新手机实验任务。
3. 原生作业同锁且不取消运行中的前轮；push须带齐`[cuttlefish-boot] [cuttlefish-container]`，检查实际job，不能凭绿色基础CI推断原生运行。工作区、代理／CA及SDK安装串行约束保持。
4. 构建使用仓库外CONTAINER_RESEARCH_WORKDIR与tools/run-build.sh，生成物不入库；回执按固定Git提交的源码核对，新输出保留旧文件。许可见[记录](research/container-api33/THIRD_PARTY_NOTICES.md)。
