# 原版应用预览的手机流程

本说明随候选安装包交付使用。当前研发状态见[PROJECT_STATUS.md](../PROJECT_STATUS.md)；[2.4.34微信兼容预览版已发布](https://github.com/soozooq/gogogo/releases/tag/preview-2.4.34-container-a26d14a5)，微信发送／实时位置、抖音无线及海外业务仍未完成真机验收。

面向一部Android13／vivo手机，无Root、无电脑。预览页接收选点和已装原版导入已经完成云端界面检查；主程序首页发送端和vivo真机仍待实际结果。

先安装[主程序APK](https://github.com/soozooq/gogogo/releases/download/preview-2.4.34-container-a26d14a5/Tianwaitian-2.4.34-container-preview-arm64.apk)和[原版预览APK](https://github.com/soozooq/gogogo/releases/download/preview-2.4.34-container-a26d14a5/Tianwaitian-Original-Preview-a26d14a5.apk)，不需要电脑。已有2.4.33主程序时只更新原版预览APK；首次使用或仍为2.4.31时安装两份。

## 三步使用

1. 在天外天地图选择地点，点击“将选点带入微信 / 抖音预览”，选择应用。此按钮在原生ARM64且预览入口已安装时显示，坐标会带入预览页。
2. 首次点击“导入手机已装版本”。当前只接受固定微信8.0.79／3200、抖音40.8.0／400801的完整原版APK；版本不符或分包时按提示选择对应官方完整APK。导入只需一次。
3. 确认地点，点击“应用坐标并打开”。以后直接改选地点再打开；结束时点击“停止预览”。收到地图选点本身不会启动应用或启用模拟。

预览环境需要自行登录，已有账号、聊天记录和其他私有数据不会自动迁入。系统原应用保留；本功能作用于从预览入口打开的应用。

## 正常使用后的一次反馈

不需要单独跑实验、遍历所有入口或反复切换VPN。有问题时，回到原版预览页点“一键复制反馈”，选择刚才使用的入口和页面结果（“选点地区”“真实地区”“找不到位置”或“还没查看”），直接粘贴发送即可。复制不读坐标、不重新定位、不启停模拟；未使用的入口保留为未验证。

地图地点、附近内容与IP属地分别判定；普通模拟、自有检查通过或写入海外坐标，均不能替代当前目标页面结果。历史实验不要求重做。

<details>
<summary>维护者证据，无需手机操作</summary>

- [普通入口与已装导入](../research/container-api33/evidence/map-point-prefill-results.json)：接收端预填、原版导入、原应用保留及GPSTest实际界面。
- [当前默认定位／19项受控无线回归](../research/container-api33/evidence/wechat-compatibility-presentation-default-results.json)：CI52固定a26源码，六方法含受控微信包上下文，通用Guest六路径与四阶段保留。
- [当前原生完整回执](../research/container-api33/evidence/native-wechat-compatibility-presentation-pass-results.json)：同源ARM64两款匿名双模式新主PID、自有框架／六路径和新增6方法受控契约通过；目标SDK、账号和实际位置业务仍未验证。
- [实际安装包发布回执](../research/container-api33/evidence/phone-a26d14a5-published-delivery-results.json)：Phone CI5、版本／签名、公开下载和旧版本未变已核对。

这些记录供维护者核对，不是用户操作清单。

</details>
