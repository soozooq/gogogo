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
