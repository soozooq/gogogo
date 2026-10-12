# GoGoGo · 抖音/高德/微信/腾讯地图定位来源差异实验（2026-10-12）

## 用户提供的关键新观察

- GoGoGo 已持续模拟美国，独立高德地图 App 显示美国。
- **抖音「发送位置」蓝色定位点及附近 POI 列表均准确显示国内实际地区**，不是只有 POI 列表没刷新。
- 微信「发送位置」先前也曾回到福建国内准确附近；腾讯地图 App 曾没成功进入美国。
- 不能因此声称抖音使用的就是高德定位 SDK，或证明抖音“检测出了真实国家”，更不能推导为某 SDK 具有反 mock 绕过：**地图渲染 SDK ≠ 位置数据实际来源**；蓝点和 POI 可能由同一国内坐标中心驱动。

## 官方技术边界

1. Android `LocationManager.getLastKnownLocation` 只读旧缓存、绝不启动传感器求新位置；返回位置可能很老，必须使用 elapsedRealtime 计算 fixAge。
   https://developer.android.com/reference/android/location/LocationManager#getLastKnownLocation(java.lang.String)
2. GMS `FusedLocationProviderClient.getLastLocation` 也是读缓存，不保证与 SDK 的 fresh 请求或别的进程一致，也可能返回 null/任意年代。
   https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient#getLastLocation()
3. Android framework mock 注入提供 `isMock` 标记。检测标记≠某个具体应用确实检查/拒绝。
   https://developer.android.com/reference/android/location/Location#isMock()
4. 高德定位 SDK 可返回 GPS=1、前次=2、缓存=4、Wi-Fi=5、基站=6、最后位置=9、系统网络=12 等 `getLocationType()`，并可由开发者配置是否使用 SDK 缓存、是否允许 mock GPS。
   https://lbs.amap.com/api/android-location-sdk/guide/utilities/location-type
   https://developer.amap.com/api/location-sdk-for-android/guide/android-location/getlocation
5. **所有这些 SDK 内部接口只可能在应用自愿提供回调时观察到**。我们没有抖音/微信内部 SDK 的可信回调、没有它们的源码，也不应将第三方私有数据转成自家日志。

## 可重复的新实验（固定设置，先求证而非修改注入）

### 第一轮：同环境基线，按应用分别做

1. 确认 GoGoGo **模拟服务仍运行**、固定美国目标没有变化。记录 VPN 开/关、Wi-Fi 开/关、移动网络开/关、系统定位开/关作为当轮人工备注（无需泄露真实经纬度）。**整个第一轮保持全部设置不变**，不更换 VPN 节点、不重开模拟、不清缓存。
2. GoGoGo → 实验与诊断 → **跨应用定位来源对照** → 选择「高德地图 App」，点「开始 BEFORE」。系统只取 GPS/NETWORK 最后缓存和 GMS getLastLocation（不会主动发定位），所有结果记录 age、isMock、若 mock 则与模拟目标的安全距离桶。
3. 点「打开目标应用」，观察高德 App 的「当前位置」，再返回 GoGoGo；`onResume` 自动获取 AFTER 只读快照（如没回来，可手动补拍）。选择真实观察类别，保存带时间戳总日志。
4. **在不变更设置的前提下**，重复对照「抖音发送位置」：确保真正打开「发送位置」，观察**蓝点以及下方附近 POI 是否都为国内**，别把手动拖动地图后的选点当作自动定位。
5. 需要的话再单独对照「微信发送位置」和「腾讯地图 App」。最后复制完整多应用报告（各个应用分别包含 BEFORE/AFTER 与人工观察）。
6. 可再对抖音做一次相隔约 2–3 分钟的同条件对照，以排除单次页面启动/异步延迟；等待时不必重复强停微信或抖音。

### 第二轮：只在第一轮仍无法区分时，单变量实验

- 对照抖音 2–3 分钟后仍一直在国内，不能单凭“时间长”排除它反复从 Wi-Fi、基站/SDK 新请求更新。只有固定条件的前后快照才说明系统缓存是否也变了。
- 若用户愿意进一步测试，可**单独**改变 Wi-Fi 是否连接并记录（切换前确保 VPN 不会断线/泄露真实网络出口），保持其他设置稳定，再重复相同对照。结果变化可能是 Wi-Fi 或网络状态的相关性，不直接证明“走 Wi-Fi 定位”。
- **不要**清除抖音/微信的数据、强制停止主微信及 vivo 分身、改手机真实时间、关闭隐私保护，作为常规步骤。这些会混淆缓存/登录会话且可能导致无法恢复。
- 若 SDK 数据源或结果仍未知，诚实报告 `UNKNOWN` 并继续查证；不建议任何反检测参数修改或无证据的 provider 注入叠加。

## 结果解释矩阵

| Android/GMS 被动缓存证据 | 抖音 UI 结果 | 解释（只能是候选） |
| --- | --- | --- |
| GPS mock=YES，NETWORK/GMS 也为模拟目标 | 国内蓝点 + 国内 POI | 抖音内部缓存、独立 SDK/网络来源、服务端数据、过滤模拟位置都仍可能；不能按此认定实际机制 |
| GPS mock=YES，NETWORK 的缓存为非 mock，且 age 较新 | 国内蓝点 + POI | 有不同定位源候选，但未读取非 mock 具体坐标；不能把非 mock 直接当作国内位置 |
| GPS/NETWORK=mock，GMS 为非 mock 或 NULL | 国内 UI | GMS 路径与系统不同是候选；NULL 只表示缓存无值，不等于 GMS 被绕过 |
| AFTER 缓存/age 变化而 BEFORE 未变 | 国内 UI | 可能其他应用在两次快照间主动请求更新了系统缓存，不能归因一定是抖音 |
| 仅 POI 国内，蓝点仍模拟目标 | POI 与定位分离 | 更优先研究搜索范围/服务端 POI 或独立历史中心 |
| BEFORE/AFTER 都无权限或 GMS 超时 | 国内 UI | 技术源仍是 UNKNOWN；应补齐权限/采样，不推导平台防御策略 |

## 已增加的复用工具

新增 `LabCrossAppLocationActivity`（挂在「实验与诊断」），一轮一应用，具有：
- 前置/返回的被动 GPS/NETWORK/GMS 最后缓存，不刷新传感器，也不订阅实时数据。
- 进入/离开应用及实际返回的时间戳、模拟目标的距离**分桶**，无 GPS 原始经纬度。
- 用户自主选择蓝点和 POI 的观察类别，手写可选模糊备注；电话号码/精确小数坐标/邮箱做基础脱敏，但仍**不要输入真实地址**。
- 反复累积多应用轮次的本地总日志，一键复制；清空需确认。
- 进程被系统杀死时可能无法自动获得 AFTER，须人工补拍并把不完整性保留在记录中。
- 第三方 App 的内部定位来源与 SDK type、真实国家推断、是否主动过滤模拟等均标记为未验证。
- 无第三方 app 数据读取、无 Shizuku 强停、无绕过模拟标记。

## Android/vivo 实机验收

- [ ] 同一设置下高德/抖音各一轮，GPS、NETWORK、GMS 均显示类型/age/mock/异常，无真实坐标写入日志。
- [ ] 用户从 GoGoGo 打开抖音再返回，AFTER 快照自动出现；系统打不开抖音时允许用户手动切换、返回或明确手动补拍。
- [ ] 无定位权限时只报 SKIPPED，不触发权限弹窗、不误写成功。
- [ ] GMS 异步回调不会写入新的不相干轮次；过程切换不制造坐标误判。
- [ ] 蓝点 + POI 两项均国内能作为人工观察记录，显示 `USER_REPORTED_NOT_SDK_PROVEN`。
- [ ] 复制完整报告安全、历史轮次顺序稳定，源码构建/单元测试/Lint 通过。
- [ ] 任何结果都不会声称“抖音定位绕过成功”或“定位失败已被特定反检测机制造成”。

版本 `2.4.0-lab47.10-crossapp-evidence` (code `2400064`)，基于上次 PR #41，独立 PR，待审查不合并。
