# GoGoGo · WeChat / Tencent LBS 兼容性研究（第二轮）
> 日期：2026-10-08 ｜ 状态：已核对公开仓库源码，设备端根因尚未确认
>
> 本笔记保留可复现现象、开源源码入口与下一轮研发方向。不保存聊天截图、个人坐标、真实手机号、私密位置或账号标识。
> 研究边界：合法的 Android 模拟定位测试、可观察性、定位缓存与第三方 SDK 行为比较；不修改第三方应用进程，不隐藏 mock 标志，不规避平台风控。

## 1. 设备实验（仅保存非敏感、必要的信息）

在一台 Android 设备上的 GoGoGo Lab 22 测试，设置公开测试点 **北京天安门附近**（WGS84：经度 116.391300 / 纬度 39.907500）。

**已观察到（截图证据留在原聊天，不提交仓库）：**
- GoGoGo 自检：GPS / NETWORK / Framework Fused / GMS Fused 均返回该测试点，mock=true。
- 独立 `:consumer` 进程的 Consumer Matrix：GPS stream / NETWORK stream / GMS lastLocation / currentLocation / updates 五个通道均返回相同测试坐标。
- Consumer Matrix：`CONSISTENT`，available 5/5，stream fresh 3/3，mock-marked 5，最大跨通道距离 0.0m。
- 微信聊天「发送位置」地图不跟随测试点，仍显示之前出现过的另一个区域，附近 POI 列表与地图地区一致，POI 距离却持续在约 12,900 km 的异常量级。
- 关闭 Wi-Fi、改用蜂窝网络后，以上微信位置异常未明显变化。
- 发送位置卡片能够成功，但内容仍是地图显示的另一区域，**不等于模拟目标位置成功**。
- 设备端观察到 Shizuku 权限/状态调整与微信定位行为变化相关；目前仅为相关性，尚不能证明因果或 Shizuku 对位置的直接作用。
- SIM 配置实验与上述测试时间重叠；不能单独据此判定 SIM 国家或 Shizuku 为根因。

**目前可支持的结论：**
1. Android / GMS 标准模拟定位发布链以及独立消费者读取链在此设备、此时刻工作正常。
2. 未证明微信的聊天定位界面使用了相同 API/坐标，也未证明与微信小程序 `wx.getLocation` 路径相同。
3. 12,900km 是异常距离的观测值；`(0,0)` 只是待验证解释，**无微信侧原始坐标证据**。
4. 系统/腾讯 SDK 缓存、SDK 对模拟位置的处理、逆地理编码/POI 数据链、坐标变换，都保持为独立假设。

## 2. 本轮新挖出的代码与资料（通过“类名/函数名/调用特征”找到）

### A. 微信小程序 JSAPI 位置链分析（**最相关的缓存线索**）
- 仓库：**1kuzus/1kuzus.github.io**
- 入口：[wx.getLocation 代码分析](https://github.com/1kuzus/1kuzus.github.io/blob/93e0aa3a2b9adef0db8e84b58bbcd17a6f283d34/src/posts/26a/wx-jsapi/index.js#L1551-L1838)
- 针对作者观察的微信 Android **8.0.65**，追踪 `wx.getLocation` 从 JSAPI 到 Java 的调用。
- Java 侧追踪到 `DefaultTencentLocationManager`，可见 `useCache` 的传参与处理、`isHighAccuracy`、`highAccuracyExpireTime`、`requestSingleFreshLocation`。
- 源码片段有 `bundle.putBoolean("useCache", appState != FOREGROUND)` 等逻辑，仅说明**该版本小程序路径**存在缓存决策，不能直接推断聊天「发送位置」实现。

### B. Android + GMS + 腾讯 SDK 多源消费者（**优先用于自研探针**）
- 仓库：**RavioliWonton/InfoTest**
- 入口：[LocationUtils.kt](https://github.com/RavioliWonton/InfoTest/blob/main/app/src/main/java/com/example/infotest/utils/LocationUtils.kt)
- 同时调用 Android `LocationManager`、GMS FLP 与 `TencentLocationManager`。
- 可见 `TencentLocationRequest.setEnableAntiMock(true)` 和 `TencentLocationListener.onLocationChanged`。
- **只借鉴 SDK 消费和观察逻辑**，不是要让 GoGoGo 或第三方程序隐藏 mock 标志。

### C. 名字几乎看不出用途的 Shizuku 定位项目
- 仓库：**sgamiza/Apk_for_Fake_GPS**
- 入口：[项目说明](https://github.com/sgamiza/Apk_for_Fake_GPS) | [MockLocationProvider.kt](https://github.com/sgamiza/Apk_for_Fake_GPS/blob/main/MockGpsShizuku/app/src/main/java/com/mockgps/shizuku/MockLocationProvider.kt)
- 独立的 `MockGpsShizuku` / `AmiMockGps` 两个 App，使用 Shizuku shell UserService 授予 mock_location AppOps；实际仍是 Android `addTestProvider` + `setTestProviderLocation`。
- 对 GoGoGo 重点参考：**Shizuku 权限授予、撤销时服务状态/Provider 生命周期、错误日志、清理与重注册**。
- 注意仓库另有 VPN/DNS 处理等可能改变设备网络行为的实验代码，**不应直接安装运行或迁入**；不将其 mock 标志规避功能纳入范围。

### D. 统一采样 + 启动高频期 + 完整停止闭环
- 仓库：**AuroraNest/Modify_Positioning**
- 入口：[README](https://github.com/AuroraNest/Modify_Positioning)
- 使用同一 LocationSample 给 GPS/NETWORK/GMS Fused 发布；启动高频注入、状态聚合、停止清理和缓存残留状态说明。
- 对 GoGoGo 优先比较 `sample` 时间戳、GMS mock-mode 开/关确认、Provider 抢占、重复启动/停止，避免重复实现已覆盖的模拟层。
- README 所说第三方兼容性**属于项目主张**，不是本设备验证结果。

### E. “微信位置选择”功能的开源对照（不是微信官方源码）
- 仓库：**GitLqr/LQRWeChat**，入口：[MyLocationActivity.java](https://github.com/GitLqr/LQRWeChat/blob/master/app/src/main/java/com/lqr/wechat/ui/activity/MyLocationActivity.java)
- 仓库：**wildfirechat/android-chat**，入口：[MyLocationPageFragment.java](https://github.com/wildfirechat/android-chat/blob/master/uikit/src/main/java/cn/wildfire/chat/kit/third/location/ui/fragment/MyLocationPageFragment.java)
- 两者均有腾讯定位 SDK listener、地图 Marker、地图摄像机变化时更新、基于地图中心搜索 POI 的逻辑。
- **价值是理解一种聊天位置选择 UI 的架构可能怎样分离“定位原点/地图中心/附近 POI”，不能据此声称微信内部相同。**

### F. 真实客户端 SDK 单次定位回调样例
- 仓库：**tencentyun/iot-link-android**
- 入口：[LocationUtil.java](https://github.com/tencentyun/iot-link-android/blob/master/sdk/explorer-link-android/src/main/java/com/tencent/iot/explorer/link/core/utils/LocationUtil.java)
- 直接示例 `TencentLocationManager.requestSingleFreshLocation(null, listener, ...)`。
- 可用于构造自有腾讯 SDK 的**一次性 fresh request** 与持续回调对比，记录错误码及返回结果。

### G. SDK 开发测试的官方/通用参照
- **didi/DoKit**：`GPSTencentClassTransformer`、`TencentLocationListenerProxy`，仅供自有 App SDK 测试插桩架构参考。
- **TencentLBS/TencentWalkNavi_Android**：官方导航示例的 `MockLocationSource` 和 `GPSReplayEngine`，可用于受控 SDK 重放测试。
- **MarsGao/WeChatLocationBridge** 等 LSPosed/目标进程 hook 类仓库：说明有人在做微信 SDK 层桥接，但**依赖 root/hook、维护风险与使用范围不同**，仅记录分层思路，不用在无 root 的 GoGoGo 上直接照搬或绕过对抗检测。

## 3. 下一步实施优先级（应由 Codex 先评估差异）

### P0：Tencent Location Consumer Probe（自主测试 App 内）
- 与现有 Consumer Matrix 并排显示腾讯 SDK `requestLocationUpdates`、`requestSingleFreshLocation` 的结果。
- 记录：API / SDK 版本、error code、reason、provider、返回经纬度、坐标系、timestamp/elapsed age、accuracy、mock 相关公开属性与结果空值。
- 能够比较标准 Android/GMS 结果是否一致；不读取/改动微信内部对象。
- SDK 依赖与密钥先核对许可、版本、运行配置；拿不到时明确 `UNAVAILABLE`，不用伪结果。

### P1：受控缓存实验与完整事件时间线
- `t0` 开始模拟；`t1` 切换国内两个相距较远的公共测试点；`t2` 打开自有 SDK 测试界面；`t3` 分别请求 fresh / continuous。
- 对比不同窗口：前台/后台、已有/新建 SDK manager、网络变化、Provider 清理/恢复。
- 记录缓存位置是否随模拟点更新、是否复用旧值及其时间戳。与微信 UI 仅做视觉时间对照，不能声称拿到微信内部数据。

### P1：Shizuku 授权前后对照
- 分开记录 Shizuku 服务存活、GoGoGo 是否授权、GoGoGo UserService 是否绑定、模拟 ServiceGo 是否运行、mock_location AppOps 状态。
- 一次仅改变一个变量。撤销权限后观察 Provider/模拟服务是否继续发布以及残留状态，避免把“授权”与“服务执行”混为一谈。

### P2：POI / 坐标系 / 距离异常判定
- 在**自有测试界面**区分地图中心、定位 fix、POI 搜索中心、POI 实际坐标、距离计算使用的经纬度，输出每条链的来源和新鲜度。
- 使用已知公共点做 WGS84 / GCJ-02 转换单元测试，针对空值、0/0、重复位置等情况正确展示 INVALID/STALE；不根据距离数字反推出微信实际使用了哪个点。

## 4. 检索规则（项目命名不规范时）

优先代码搜索而非仅搜项目名字：
- 类名：`TencentLocationManager`, `TencentLocationRequest`, `TencentLocationListener`, `DefaultTencentLocationManager`
- 方法与字段：`requestSingleFreshLocation`, `useCache`, `highAccuracyExpireTime`, `setEnableAntiMock`, `onLocationChanged`
- Android：`LocationManager.addTestProvider`, `setTestProviderLocation`, `elapsedRealtimeNanos`, `isMock`, `AppOpsManager.OPSTR_MOCK_LOCATION`
- 交互：`MyLocationActivity`, `MyLocationPageFragment`, `reverseGeocoding`, `POI search`, `camera center`
- 分别区分：标准模拟 / SDK 消费探针 / SDK 插桩 / 逆向历史线索 / Root-Hook 项目 / 无关素材。
- 每项需核实 README+具体源码；文档所述兼容性不等于在本机证实，尤其区分微信聊天“发送位置”、位置共享与小程序 JSAPI。

## 5. 研究进度

- [x] 已证实标准定位发布和独立消费者 5/5 一致
- [x] 已确认微信发送位置与模拟目标不同且有异常距离
- [x] 已找到微信 8.0.65 小程序路径的缓存代码线索
- [x] 已找到可借鉴的三源定位消费者 / 腾讯 SDK 单次定位样例
- [ ] 自主 SDK Probe 在 Android 设备上编译运行
- [ ] 取得腾讯 SDK 错误码/缓存行为的独立数据
- [ ] 明确 Shizuku 开关变化是否对 ServiceGo/Provider 有因果影响
- [ ] 分辨微信 UI 的旧位置是缓存、定位失败回退还是其他路径
- [ ] 对前述结果建立可重复的自动化测试


## 6. 继续搜索：按“POI/聊天位置页/定位层级”反查的新发现

### 腾讯定位 SDK 原生 Demo（命名没有微信、mock 关键词）
- **tencentlocation/TencentLocationDemo**：[DemoLevelActivity.java](https://github.com/tencentlocation/TencentLocationDemo/blob/master/src/com/tencent/example/location/DemoLevelActivity.java)
- **tencentlocation/TencentLocationDemoAs**：[DemoLevelActivity.java](https://github.com/tencentlocation/TencentLocationDemoAs/blob/master/app/src/main/java/com/tencent/example/location/DemoLevelActivity.java)
- 与“地图能显示但地点距离奇怪”高度相关：示例显式区分 `TencentLocationRequest.REQUEST_LEVEL_GEO / NAME / ADMIN_AREA / POI`；当请求包含 POI 时读取 `TencentPoi.getDistance()`。
- 这提醒我们：POI 的 distance 可能来自 SDK 返回值，不一定由地图 UI 自己根据绿色点临时计算。
- **限制**：属于较早 SDK 示例；请求级别、坐标与 distance 的语义必须以实际 SDK 版本验证。

### 开源聊天 UI 的坐标/POI 分离
- **GitLqr/LQRWeChat**：[MyLocationActivity.java](https://github.com/GitLqr/LQRWeChat/blob/56d419d7e7c0709b509e7b3727b38123246d5602/app/src/main/java/com/lqr/wechat/ui/activity/MyLocationActivity.java)
- **wildfirechat/android-chat**：[MyLocationPageFragment.java](https://github.com/wildfirechat/android-chat/blob/1ca2c0c7dde6d475e0a20d42690ac64b511ab07e/uikit/src/main/java/cn/wildfire/chat/kit/third/location/ui/fragment/MyLocationPageFragment.java)
- 源码中可见 `TencentLocationListener` 的坐标用于初始 Marker 和移动地图；`OnMapCameraChangeListener` 在地图拖动后又按 `getMapCenter()` 触发附近 POI 逆地理查询。
- 因而建议自研探针分别记录“定位回调坐标”“地图中心”“POI 查询输入坐标”“POI 返回中心与距离”，避免以地图渲染点等同于距离计算坐标。
- **这两个是第三方聊天应用，不是微信本体**，只验证一种常见 UI 分层架构。

### 微信/腾讯 SDK 版本及接口差异观察
- 旧版公开源码可见 `TencentLocationManager.getLastKnownLocation`，`requestSingleFreshLocation` 等接口；新版/其他命名空间存在 `com.tencent.map.geolocation.sapp`。不应把不同版本的返回策略当作同一行为。
- 小程序 `wx.getLocation` 的已知分析不覆盖聊天「发送位置」，必须继续独立求证。
- 测试日志应包含 SDK version、request level、是否允许缓存、是否一次性请求、error/reason、响应坐标系和有效时间戳。

**更新后的 P0 建议**：先用受控腾讯 SDK Demo 做 GEO/NAME/POI 等请求级别对照，同时通过 Consumer Matrix 比较 fresh 与 cached；此后才决定是否继续 Shizuku 或 Wi-Fi 变量实验。


## 7. 第三轮源码搜索：腾讯 POI 距离的真实语义、缓存 API 与非直观适配器

### 7.1 SDK 旧版官方风格 Javadoc：getDistance 的单位和参考点
- 仓库：**tencentlocation/tencentlocation.github.io**
- [TencentPoi Javadoc](https://github.com/tencentlocation/tencentlocation.github.io/blob/master/doc/com/tencent/map/geolocation/TencentPoi.html)
- 文档标明 `TencentPoi.getDistance()` 返回**当前 POI 与当前位置（定位中心点）的距离，单位米**。
- [TencentLocationRequest Javadoc](https://github.com/tencentlocation/tencentlocation.github.io/blob/master/doc/com/tencent/map/geolocation/TencentLocationRequest.html) 描述 `REQUEST_LEVEL_GEO / NAME / ADMIN_AREA / POI`。
- `TencentLocationDemo/DemoLevelActivity.java` 在 `REQUEST_LEVEL_POI` 模式下直接输出 `poi.getDistance()` 和 POI 坐标。
- **含义**：一个 SDK 内部计算的 POI 距离完全可能与地图 Marker/地图中心的经纬度来自不同的状态；应明确区分“SDK POI 返回的距离”与“UI 自行计算的距离”。
- **不应推断**：微信聊天位置发送 UI 必定使用 `TencentPoi.getDistance()`，或 12,900km 必定源自 (0,0)。该文档生成于 2015 年，需做版本检查。

### 7.2 SDK 缓存不是单纯 UI 猜测
- 同份 [TencentLocationRequest Javadoc](https://github.com/tencentlocation/tencentlocation.github.io/blob/master/doc/com/tencent/map/geolocation/TencentLocationRequest.html) 有 `setAllowCache(boolean)` 和 `isAllowCache()`。
- 文档原意：**长时间连续定位可启用缓存，单次定位更建议不使用缓存**，以减少网络请求、节省流量。
- 与已找到的微信 Android 8.0.65 小程序 `DefaultTencentLocationManager/useCache` 分支形成**两个独立层面的缓存线索**。仍无法直接证明聊天「发送位置」使用哪种缓存策略。
- **探针要求**：以 SDK 支持的 API 显式设置允许/不允许缓存进行 A/B，分别记录 首次结果来源、位置时间、elapsed-age、请求耗时、错误码以及重新请求结果；前提是该 SDK 版本公开提供该参数。

### 7.3 意料之外的代码来源：uni-app 腾讯定位实现
- 仓库：**dcloudio/uni-app**
- 源码：[uni-location-tencent/utssdk/app-android/index.uts](https://github.com/dcloudio/uni-app/blob/fbb161d0118dda57d7205f4f6f427e3b40109500/src/uni_modules/uni-location-tencent/utssdk/app-android/index.uts)（分支若变更可改查代码搜索）。
- 使用原生 `TencentLocationManager`；一次定位通过 `requestSingleFreshLocation()`；`geocode=true` 时选 `REQUEST_LEVEL_NAME`，否则选 `REQUEST_LEVEL_GEO`。
- 其适配器明确限制 GCJ02 输出；这属于**适配器自身的限制**，不代表所有腾讯 SDK 版本都只允许 GCJ02。
- **潜在实现弱点**：其 `onLocationChanged(location,error,reason)` 包装逻辑直接使用 `location.latitude/longitude`，没有先按 `error` 判成功，也没有对 `location` 做空值防护；这是对该仓库代码的静态观察，尚未实测。
- **对我们最重要**：GoGoGo 的 Tencent Consumer Probe 必须将“回调到达”与“坐标有效”分开，error / reason / null / 坐标系 / source / age 都单独显示，失败不得以 0/0 冒充有效定位。

### 7.4 较少人会从定位项目名称搜索到的腾讯 LBS 服务端流程
- 仓库 **tencentyun/iot-link-android** 中的 `LocationUtil.java` 也调用 `requestSingleFreshLocation`，仅用来比较一次请求接口的调用方式。
- **GitLqr/LQRWeChat** 与 **wildfirechat/android-chat** 的位置发送 UI 源码表明，在第三方聊天 App 中，腾讯 SDK 坐标、地图中心、逆地理解析/附近 POI 处理可完全独立；本机微信有相似表现但不能以此证明代码相同。

### 7.5 下一轮检验顺序
1. 确定可合法调用的腾讯 SDK 依赖、Key/权限和当前版本；无法初始化时输出 `SDK_UNAVAILABLE`。
2. 先在**自有 App** 进行 GEO 与 POI 两种 request level、cache on/off、single/stream 的全量状态对照，记录定位原点和 POI 返回距离（米）。
3. 加入 SDK 回调失败和空值测试，保证 UI 不把 0/0、旧值、未初始化的距离显示成新鲜定位。
4. 再将观测到的错误类别与微信外部 UI 表现比较，保留“微信内部真实机制未知”的限定。


## 8. 第四轮源码搜索：Shizuku 撤销授权导致目标 App 被强制停止（重要因果混杂项）

**最重要的新发现（官方 Shizuku 源码）：**
- 仓库：**RikkaApps/Shizuku**
- 源码：[ShizukuService.java](https://github.com/RikkaApps/Shizuku/blob/b844bc491f1790c72328e1a8e5b2349f8978f0ea/server/src/main/java/rikka/shizuku/server/ShizukuService.java#L373-L418)
- 在 `updateFlagsForUid` 调整已连接客户端授权状态时，撤权分支执行：
  1. `record.allowed = false`
  2. `ActivityManagerApis.forceStopPackageNoThrow(record.packageName, ...)`
  3. `onPermissionRevoked(record.packageName)`
  4. 其内进一步 `removeUserServicesForPackage(packageName)`。
- Shizuku manager 的 [AuthorizationManager.kt](https://github.com/RikkaApps/Shizuku/blob/b844bc491f1790c72328e1a8e5b2349f8978f0ea/manager/src/main/java/moe/shizuku/manager/authorization/AuthorizationManager.kt) 使用 `Shizuku.updateFlagsForUid(uid, MASK_PERMISSION, 0)` 路径进行撤权（版本/权限路径可能有差异）。

**重新解读本机实验：**
此前观察：关掉影梭的 Shizuku 授权、Shizuku 服务仍运行，微信回复真实位置。这不构成“Shizuku 直接改变微信定位策略”的证明；**撤权动作本身可能使影梭进程被强制停止**，从而打断模拟服务或改变 test-provider 状态，并让微信重新获取真实位置。它是比“授予高权限改变微信定位”更直接的替代解释。应在本机确认 Shizuku 版本、撤权时 GoGoGo PID/进程/模拟 ServiceGo 状态，以及系统 Provider 留存情况。

**注意**：Shizuku 撤权并非所有安装版本都必然走同一路径；`forceStopPackageNoThrow` 的调用发生在授权管理路径上，需区分“App 获得授权”与“撤销授权强制停止 App”的因果方向。不得把此机制当成微信已采用模拟位置的证据。

**建议受控实验：**
- 先在 GoGoGo 的日志记录模拟服务活跃状态、会话 ID、进程 PID、状态变更时间、Provider 读取时间；不采集真实地理坐标。
- 独立对照：不执行模拟时的 Shizuku 授权开关 vs 模拟中撤权；记录进程是否重启/被系统强停，及 UserService 是否解绑。
- 撤权后主动提供恢复/清理提示；如果 test provider 留存在 system_server 而前台服务死亡，应标记 `ORPHANED/CLEANUP_PENDING`，不能误报“彻底停止”。
- 这类测试属于本 App 生命周期诊断，不修改第三方应用进程/行为。

## 9. 第四轮距离研究：0,0 只是候选，已与另一套“SDK 原点”假设区分

- 腾讯定位文档：[TencentPoi.getDistance()](https://github.com/tencentlocation/tencentlocation.github.io/blob/c2f1a8a5752af664b1689283d31789eb1ed66d98/doc/com/tencent/map/geolocation/TencentPoi.html#L236-L245)，其语义是 POI 到**定位中心点**的米数。
- 若以福州附近约 `26.03°N, 119.20°E` 作为**非个人**示例点，球面测地线计算与 `(0,0)` 距离约 **12,899 km**，与截图显示的约 12,914 km 属于同一量级。但显示距离与坐标不精确，不能证明输入坐标是 `(0,0)`。
- 从北京天安门附近 `39.9075°N, 116.3913°E` 到上述示例点仅约 **1,565 km**；如果 POI 实际在福建且距离显示 >12,000km，便不太可能使用北京作为距离计算起点。
- 候选解释 A：SDK 位置无效/未初始化、某处把无效坐标当作 0/0。
- 候选解释 B：SDK/服务端的距离计算起点与地图 UI 中心、发送卡片的选点中心不同。
- 候选解释 C：旧值、新值、POI 返回来源或字段单位/时间状态混合。
- **判断实验**：只在自有测试界面同时显示 Android fix / 腾讯定位 fix / 地图中心 / 腾讯 POI 坐标 / SDK `getDistance()` / 本地按 Haversine 计算的参考距离和各数据时间戳。若返回 `(0,0)` 或无效字段，须独立显示 `INVALID`，不得把猜测反馈成事实。

## 10. 新的下一步优先级

1. **P0：Shizuku 撤权是否 force-stop GoGoGo 的进程，先做不影响微信的自有 App 验证。** 若成立可解释以前的关键混淆现象。
2. **P0：Tencent SDK 独立消费者探针，区分 SDK 定位成功/失败/缓存，并按 GEO/POI 分类。**
3. **P1：POI 距离原点诊断，不再以地图 UI 的位置直接假定距离输入。**
4. 暂停堆叠定位器、DNS 阻断、第三方进程注入等高干扰变量，保留设备可恢复性。
