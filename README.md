> **soozooq / GoGoGo 集成测试版说明（2026-10-09）**：本 Fork 已整合历史 Lab 1–25 的实验开发成果，开发代码已通过 GitHub Actions 自动构建，但**尚未完成所有目标手机、腾讯真实密钥与第三方定位消费者的实机验收**。此仓库当前 APK 属于 **Preview / 测试版**，并非官方上游稳定发行版。
>
> - 本 Fork 源码： https://github.com/soozooq/gogogo
> - 已验证的 `main` 完整 CI： [Build Check](https://github.com/soozooq/gogogo/actions/runs/37855976504) · [Preview APK（在 Artifacts 中下载 ZIP 并解压）](https://github.com/soozooq/gogogo/actions/runs/37855976542)
> - 手机实测步骤： [安卓手机验收清单](docs/main-preview-phone-acceptance-20261009.md)
> - Lab 25 发布阻断清单： [docs/lab25-release-readiness.md](docs/lab25-release-readiness.md)
> - 后续自动构建： https://github.com/soozooq/gogogo/actions
> - 备份旧 main： [backup/main-before-labs-20261009](https://github.com/soozooq/gogogo/tree/backup/main-before-labs-20261009)
> - **重要**：仓库中已有公开的测试签名密钥和密码，不能把它当作私密的正式发行签名；更换签名可能需要重新安装，务必先保留应用数据。
>
> 以下为上游项目说明，版权和 GPL 协议要求继续适用。

---

<p align="center">
<img src="./docs/images/LOGO.png" height="80"/>
</p>

<div align="center">

[![GitHub stars](https://img.shields.io/github/stars/ZCShou/GoGoGo?logo=github)](https://github.com/ZCShou/GoGoGo/stargazers)
[![GitHub forks](https://img.shields.io/github/forks/ZCShou/GoGoGo?logo=github)](https://github.com/ZCShou/GoGoGo/network)
[![license](https://img.shields.io/github/license/ZCShou/GoGoGo)](https://github.com/ZCShou/GoGoGo/blob/master/LICENSE)
[![GitHub Release](https://img.shields.io/github/v/release/ZCShou/GoGoGo?label=Release)](https://github.com/ZCShou/GoGoGo/releases)
[![standard-readme compliant](https://img.shields.io/badge/readme%20style-standard-brightgreen.svg?style=flat-square)](https://github.com/RichardLitt/standard-readme)
</div>
<div align="center">

[![Build Check](https://github.com/ZCShou/GoGoGo/actions/workflows/build-check.yml/badge.svg)](https://github.com/ZCShou/GoGoGo/actions/workflows/build-check.yml)
[![CodeQL](https://github.com/ZCShou/GoGoGo/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/ZCShou/GoGoGo/actions/workflows/codeql-analysis.yml)
</div>

<div align="center">
影梭 - 用于 Android 8.0+ 的无需 ROOT 权限的虚拟定位 APP
</div>

## 简介
&emsp;&emsp;影梭是一个基于 Android 调试 API + 腾讯地图 SDK 实现的安卓定位修改工具，并且同时实现了一个可以自由控制移动的摇杆。使用影梭，不需要 ROOT 权限就可以随意修改自己的当前位置以及模拟移动。

> 历史版本基于百度地图 + 百度定位 SDK，详见 [腾讯地图 SDK 迁移说明](#腾讯地图-sdk-迁移说明-v1123)。新版与微信小程序、腾讯地图、高德地图同坐标系 (GCJ02)，无需额外坐标转换。

1. 源码仓库：[Github](https://github.com/ZCShou/GoGoGo)（推荐）、[Gitee](https://gitee.com/itexp/gogogo)（镜像）
2. 下载地址：[Github](https://github.com/ZCShou/GoGoGo/releases)（推荐）、[Gitee](https://gitee.com/itexp/gogogo/releases)（镜像）

## 警告一
&emsp;&emsp;**最近，有网友直接白嫖影梭后改名为标枪定位，然后添加广告（除了加广告，功能没有任何改变），但是，没有按照 GPLv3 协议的要求进行开源（我已经联系过该网友进了提醒，但并没有收到回复），在此提醒：**
1. **开源 ≠ 白嫖，请遵循开源协议**
2. **GPL 的法律效力在国内相关诉讼案例很多，请自行搜索，权衡利弊。影梭保留追究相关侵权人员法律责任的所有权利！**
3. **开源不易，且行且珍惜**

## 警告二
&emsp;&emsp;**最近，有很多人将影梭用在校园运动类 APP（包括但不限于闪动校园、TakeTwo、运动世界校园等）中作弊，开发者也收到了很多人提问为何影梭定位并不起作用或者寻求对影梭的改进，在此提醒：**
1. **影梭不支持任何校园运动类 APP 的作弊行为**
2. **影梭开发者也不赞同采用任何形式在校园运动中作弊**

## 背景
&emsp;&emsp;之前在玩一款 VR 游戏：一起来捉妖。为了省事，就想有没有可以更改位置的 APP。经过一番摸索发现确实有不少可以修改位置的 APP。但是，绝大多数这种 APP 都是收费的，而且贼贵！

&emsp;&emsp;我比较感兴趣的是这样的技术是如何实现的，因此，决定研究研究自己写一个！现在游戏已经弃坑了，但是技术不能丢。因此，将研究结果开源出来方便大家一起学习！但是请注意（重要的事情说三遍！否则后果自负）：

1. 该 APP 仅仅是为了学习 Android + 百度地图的实现方法，请勿用于游戏作弊！
2. 该 APP 仅仅是为了学习 Android + 百度地图的实现方法，请勿用于游戏作弊！
3. 该 APP 仅仅是为了学习 Android + 百度地图的实现方法，请勿用于游戏作弊！

## 功能
1. 定位修改
2. 摇杆控制移动
3. 历史记录
4. 位置搜索
5. 直接输入坐标

## 截图
![joystick.jpg](./docs/images/joystick.jpg)
![search_history.jpg](./docs/images/search_history.jpg)
![map.jpg](./docs/images/map.jpg)

## 用法
1. 下载 APK 直接安装
2. 启动影梭，赋予相关权限
3. 单击地图位置，然后点击启动按钮

## 文档
&emsp;&emsp;由于本人并不是做移动开发的，很多功能代码写的都比较差。我也第一次写  Android APP，目前还处在学习中。。。此外，就一个简单的 APP，应该也不需要啥文档，开发过程中遇到的一些问题，我一般都会记录在个人博客中，具体参见：https://blog.csdn.net/zcshoucsdn/category_10559121.html

&emsp;&emsp;如果有疑问可以直接搜索 ISSUE 或者 在上面直接提交问题。

## 参考
&emsp;&emsp;由于本人也是个新手，纯属业余瞎搞，因此，在写影梭的过程中，参考了很多网友分享的技术文章、示例代码等。包括但不限于以下列出的几个：
1. https://github.com/Hilaver/MockGPS
2. https://github.com/bxxfighting/together-go
3. https://github.com/P72B/Mocklation

&emsp;&emsp;还有些 CSDN 上的文章，目前不记得地址了，如果您发现其中有直接引用或借鉴您的地方，请与我联系，我会再第一时间进行处理，谢谢！

## FAQ
Q：为何不支持 Android 8.0 以下版本？

A：因为手里没有机器无法进行适配。。。

Q：为何定位不是很稳定，偶尔会飘回真实位置？

A：这是是由于实现原理导致的，Android 调试 API 固有的问题。确切的说，应该是由于手机本身还开启了其他定位方式（例如，基站定位、wifi定位等）导致的

Q：是否支持鸿蒙系统？

A：经过测试，影梭可以在鸿蒙系统上正常运行。

Q：为何在微信等腾讯系应用上定位不起作用？

A：v1.12.3 之前（百度 SDK 版本）依赖百度 LocationClient 在 `:remote` 进程持续订阅 Provider 间接保活，迁移到腾讯 SDK 后该机制丢失，导致 Android 12+ 上 Provider 进入低功耗状态，微信小程序通过 `getLastKnownLocation` 读不到 mock 数据。**新版本已通过下面三条独立路径修复**：(1) ServiceGo 中持久 LocationListener 保活，(2) 加 `LocationManager.FUSED_PROVIDER` 的 test provider (Android 12+)，(3) 调 `FusedLocationProviderClient.setMockMode + setMockLocation` 覆盖 GMS 路径。详见 [腾讯地图 SDK 迁移说明](#腾讯地图-sdk-迁移说明-v1123) 章节。仍然失效的少数 app（部分打车/银行/校园运动类）会主动检查 `Location.isMock()` 标记，**非 ROOT 设备无法绕过**。

Q：编译时 java 报错？

A：Gradle 使用的 java 版本与 Android Studio 使用的不一致。Gradle 默认会在环境变量中搜索 JAVA_HOME 来确定 Java 位置。

## 腾讯地图 SDK 迁移说明 (v1.12.3+)

&emsp;&emsp;v1.12.3 之前基于 **百度地图 + 百度定位 SDK**，新版迁移到 **腾讯地图 SDK 6.11.0**，使坐标系与微信小程序、腾讯地图、高德地图原生对齐（均为 GCJ02）。本节记录迁移涉及的所有技术细节，供后续维护参考。

### 一、改动概览

| 项 | 旧 (百度) | 新 (腾讯) |
|---|---|---|
| 地图 SDK | `BaiduLBS_Android.jar` + `libBaiduMapSDK_*.so` | `com.tencent.map:tencent-map-vector-sdk:6.11.0.260507.ab2310c2.209828299` |
| 基础类型 (LatLng/MapPoi) | 同主包 | `com.tencent.openmap:foundation:0.9.0.e5ea763-pro` ⚠️ **必须显式声明**，主包 pom 没传递依赖 |
| 工具 | — | `com.tencent.map:sdk-utilities:1.0.10` |
| 定位 SDK | `LocationClient` (Baidu) | Android 原生 `LocationManager` |
| 反向地理编码 | `api.map.baidu.com/reverse_geocoding/v3` | `apis.map.qq.com/ws/geocoder/v1` |
| 搜索 | `SuggestionSearch` (Baidu SDK) | `apis.map.qq.com/ws/place/v1/search` (Web API) |
| Mock 通路 | LocationManager 单通路 | LocationManager + FusedLocationProviderClient 双通路 |
| Mini-program 对照 | — | 新增 `MiniProgramTestActivity`，并排显示腾讯/百度反向编码结果 |

### 二、坐标系流转

&emsp;&emsp;最容易踩坑的就是坐标系，按层记忆：

```
腾讯地图 UI 上的点
       │  GCJ02
       ▼
mMarkLatLngMap (Java 字段)
       │  gcj02towgs84()
       ▼
ServiceGo 接收的 lat/lng
       │  WGS-84（Android 标准）
       ▼
LocationManager.setTestProviderLocation()
FusedLocationProviderClient.setMockLocation()
       │  WGS-84
       ▼
其他 app 通过 wx.getLocation / LocationManager 读到
       │  type:'wgs84' → WGS-84；type:'gcj02' → 由 app 自己转
       ▼
WeChat / 高德 / 百度地图显示的点
```

| 层 | 坐标系 |
|---|---|
| 腾讯地图 UI / 点位 / 摇杆迷你地图 / 历史回显 | **GCJ02** |
| 输入对话框 (BD09 单选项进来后) | 进来后 `bd09togcj02()` 转 GCJ02 |
| 数据库 `LONGITUDE_CUSTOM` / `LATITUDE_CUSTOM` | **GCJ02** (新数据)、~~BD09~~ (旧数据，见 [六、已知限制](#六已知限制)) |
| 数据库 `LONGITUDE_WGS84` / `LATITUDE_WGS84` | WGS-84 |
| `ServiceGo` 内部 / 注入系统 | **WGS-84** |
| 搜索 API / 反向编码 API 输入输出 | **GCJ02** |

&emsp;&emsp;唯一转换函数：`MapUtils.gcj02towgs84()` 一步逆变换，全国境内误差 < 1m，街道级无感。

### 三、Mock 注入路径（多通路）

&emsp;&emsp;不同应用读位置的入口不一样，新版同时注入四条路径，最大化兼容性：

| 路径 | 谁会读到 | 实现 |
|---|---|---|
| `LocationManager.GPS_PROVIDER` test provider | 高德、百度地图、滴滴等绝大多数原生 app | `addTestProvider` + `setTestProviderLocation` |
| `LocationManager.NETWORK_PROVIDER` test provider | 没 GPS 时的退路 | 同上 |
| `LocationManager.FUSED_PROVIDER` test provider (Android 12+) | 部分新 app 直接读系统 fused | 能注就注，系统拒就跳过 |
| `FusedLocationProviderClient` GMS API | **WeChat 小程序在 Android 12+ 走的就是这条** | `setMockMode(true)` + 每 tick `setMockLocation()`，没 GMS 静默跳过 |

&emsp;&emsp;`ServiceGo` 内 `HandlerThread` 以 **33ms (~30Hz)** 频率向上述所有通路 push 一次（旧版是 100ms）。tick 加密的目的是缩短"mock 过期"窗口，避免某些应用在两次 push 之间读到真实位置后漂移。

### 四、关键修复：让微信小程序看到 mock

&emsp;&emsp;迁移完成后初次测试出现"高德/百度 app 能 mock，但微信腾讯地图小程序不行"。根因排查：

> **百度版的 LocationClient 在 `:remote` 进程里以 `scanSpan=1000` 持续 `requestLocationUpdates`，让 Android 系统认为 GPS/NETWORK Provider 一直"有活跃订阅者"，保持高功耗状态。腾讯版没了这个 LocationClient，虽然 ServiceGo 每 33ms `setTestProviderLocation`，但 Android 12+ 没有活跃 listener 时 Provider 会进入低功耗/缓存退化状态，微信 `getLastKnownLocation` 读不到刚 push 的 mock 数据。**

补回的三件事:

1. `ServiceGo.initPersistentLocationListener()`: 注册一个空回调的 `LocationListener` 持续订阅 GPS_PROVIDER + NETWORK_PROVIDER，让 Provider 始终活跃。
2. AndroidManifest 加 `FOREGROUND_SERVICE_LOCATION` 权限，`ServiceGo` 标 `foregroundServiceType="location"`，否则 Android 14+ 调 `requestLocationUpdates` 会 SecurityException。
3. `startForeground` 在 API ≥ 34 时传 `FOREGROUND_SERVICE_TYPE_LOCATION`。

加上 GMS Fused 路径，目前实测：微信、腾讯地图小程序、高德、百度 app 全部能正常 mock。

### 五、构建配置

&emsp;&emsp;`local.properties` 必填：

```properties
TENCENT_MAP_KEY=<腾讯地图 SDK Key>
MAPS_API_KEY=<可选，Baidu Key，仅 MiniProgramTestActivity 对照用>
```

&emsp;&emsp;申请 Key (https://lbs.qq.com/dev/console/key/manage) 时勾选两个产品：
- ☑ **Android 地图 SDK** (地图渲染)
- ☑ **WebService API** (搜索、反向编码)

&emsp;&emsp;同时绑定:
- PackageName: `com.zcshou.gogogo`
- SHA1: 用 `keystore/GoGoGo.jks` 的 keytool 输出 (alias `GoGoKey`)

### 六、已知限制

1. **旧历史记录漂移 ~500m**：百度版在 `LONGITUDE_CUSTOM/LATITUDE_CUSTOM` 列存的是 BD09，腾讯版按 GCJ02 解读，对老条目会有 ~500m 偏移。新存的记录无问题。建议升级后清一次数据 (设置 → 应用 → 影梭 → 清除数据)。
2. **抗 mock 应用**：部分应用 (打车、银行、校园运动) 主动检查 `Location.isMock()` 标记。该标记由 Android 内核强制打上，**非 ROOT 设备无法擦除**。
3. **网络相关 API 仍需配额**：`apis.map.qq.com/ws/place/v1/search` 与 `geocoder/v1` 各自有每日 10000 次免费配额。共享演示 key 容易刷爆，正式使用请填自己的 key 到设置里。

### 七、验证方法

&emsp;&emsp;`docs/miniapp-test/` 是个微信小程序，能并排显示:

- `wx.getLocation({type:'wgs84'})` 返回值 (即我们注入的原始 WGS-84)
- `wx.getLocation({type:'gcj02'})` 返回值 (微信内部 GCJ02 转换结果)
- `isMock` 标记
- 百度 / 腾讯 反向地理编码对照

&emsp;&emsp;在影梭里点一个具体点 (如北京西站 GCJ02 ≈ 116.32212,39.89491) 启动模拟，微信里运行该测试小程序刷新定位:

- 期望: `gcj02` 返回 ≈ 116.32212,39.89491 (±1e-5 即 ~1m)
- 期望: `wgs84` 返回 ≈ 116.31570,39.89345 (即 `gcj02towgs84()` 的计算值)
- 期望: `isMock == true` (微信知道是模拟，但选择透传)

### 八、文件改动清单

```
build.gradle                                         # AGP 8.12.1 + Kotlin 1.9.10
app/build.gradle                                     # 腾讯 SDK + GMS location 依赖
app/proguard-rules.pro                               # 腾讯 SDK keep 规则
app/src/main/AndroidManifest.xml                     # 删 baidu :remote service / 加 fg-service-location
app/src/main/java/com/zcshou/gogogo/GoApplication.java   # TencentMapInitializer.start
app/src/main/java/com/zcshou/gogogo/MainActivity.java    # 坐标转换 + Tencent suggestion / geocoder
app/src/main/java/com/zcshou/gogogo/HistoryActivity.java # 字段重命名
app/src/main/java/com/zcshou/gogogo/FragmentSettings.java # 加 setting_tencent_key
app/src/main/java/com/zcshou/gogogo/MiniProgramTestActivity.java  # 新增对照页
app/src/main/java/com/zcshou/joystick/JoyStick.java      # 摇杆迷你地图改腾讯
app/src/main/java/com/zcshou/service/ServiceGo.java      # Fused + 持久 listener + 33ms tick
app/src/main/java/com/zcshou/utils/MapUtils.java         # 加 wgs2gcj02/bd09togcj02/gcj02tobd09
app/src/main/res/...                                 # 布局/字符串/preferences
app/libs/                                            # 删除 (Baidu jar + so)
```


## 如何贡献
1. FORK -> PR
2. 加入影梭开发，共同完善

## 许可证
GPL-3.0-only © ZCShou

[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FZCShou%2FGoGoGo.svg?type=large&issueType=license)](https://app.fossa.com/projects/git%2Bgithub.com%2FZCShou%2FGoGoGo?ref=badge_large&issueType=license)
