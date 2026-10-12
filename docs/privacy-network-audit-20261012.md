# GoGoGo · 网络与定位隐私体检（2026-10-12）

## 背景

用户看到短视频评论宣称 Claude 会检测手机时区、DNS、WebRTC、IPv6、浏览器指纹，要求增强隐私保护并减少实际位置/国家信息暴露。用户此前已实现 GPS 模拟、VPN 出口 IP 近似城市跟随、持久定位锁定、VPN 时区跟随、SIM 发卡国家查询，但这些功能**不等于对其它应用的地区识别进行匿名认证**。

Anthropic 隐私中心（2026-03-16）明确表示为安全/反滥用使用 IP 和其它信号来判断地区，该安全用途不可由用户关闭。Android 官方文档说明 VPN 可按应用分流，`ConnectivityManager` 观测的是 GoGoGo 本应用默认网络，不具备跨应用网络可见性。MDN 明确提示 WebRTC ICE 候选地址可能成为指纹与 IP 暴露信号，但是否暴露要在实际浏览器测试。

## 本轮功能

首页增加「网络与定位隐私体检」（独立非导出 Activity），避免继续向主界面堆叠定位和微信 Lab 控件。

**无需联网、打开页面即本地读取：**
- 此应用的 `ConnectivityManager.getActiveNetwork()` 当前是否报告 `TRANSPORT_VPN`；缺 VPN 标为警告而不是偷偷发请求。
- `LinkProperties` 的 IPv4/IPv6 接口地址家族是否存在（不记录/展示地址原文；此信息**不代表公网出口可达**）。
- 设备 DNS 服务器个数与 Android Private DNS 是否生效（不输出具体 DNS IP 或域名；此信息**无法证明实际 DNS 出口不泄漏**）。
- 本机时区与已存 VPN IANA 时区是否一致、设备语言地区设置（界面可见，但导出报告隐藏具体时区和语言地区值，仅保留比对结论）。即便不一致也不等于真实地区暴露。
- Chrome/Claude/其它应用的网络分流、WebRTC、DNS 实际解析器、VPN kill switch 均标为 **UNKNOWN / NOT_TESTED**，从不误写“完全安全”。

**用户主动同意后才联网：**
- 先确认**GoGoGo 默认网络当前包含 VPN**，没有确认则**拒绝连接外部出口检测站点**。
- 在此默认 `Network.openConnection` 内，向 `https://api4.ipify.org?format=json` 和 `https://api6.ipify.org?format=json` 查询出口 IPv4/IPv6，随后对获得的出口 IP 向 `https://ipwho.is/<ip>?fields=success,country_code` 核对**粗略国家代码**；只在临时内存保留 IP 字符串，不加入界面、日志、偏好设置、Clipboard。
- 对比两个出口是否都可达以及数据库国家是否一致。服务不可用/IPv6 阻断时标为 UNKNOWN/FAILED，不能推断“无 IPv6 泄漏”，也不能推断其它 App 的出口。
- 网络请求有 6.5s connect/read 超时、2KB 响应上限、关闭重定向，且异步执行。检测中更换默认网络时结果作废，防止跨 VPN 节点的错误结论。
- 提前明确提示：ipify 可以看到连接出口 IP；ipwho.is 会收到**需要地理归属的 IPv4/IPv6 出口地址**。无用户点击及二次明确同意，不发起这些请求。
- 受限于分流和设备权限，**GoGoGo 网络结果不代表 Claude App、浏览器或 vivo 双开中的网络结果**。

**第三方浏览器检查（人工打开）：**
- `https://browserleaks.com/webrtc`：WebRTC；`https://www.dnsleaktest.com/`：DNS；点击前提示网站可获取公网 IP/浏览器指纹。
- 标准 Android VPN 设置入口，引导用户自行检查 Always-on VPN 与「无 VPN 时阻止网络连接」；不擅自开启/关闭系统设置。
- 复制脱敏诊断总日志：无公网/本地 IP、SIM 身份、GPS 真实坐标、完整系统时区或设备语言地区；明确 UNKNOWN 的所有未验证数据来源。

## 设计与边界

这是**隐私体检和泄露风险提示**，不是反检测、反封禁或向第三方 App 注入环境参数。不会操控浏览器 WebRTC API、其它 App 的流量路由、IP 协议栈、系统 DNS 或日期时间，也不会清除或伪造设备指纹。检测结果不保证匿名，也不保证 Anthropic 支持未列入服务地区的使用。不会暗示通过这些方法可绕过服务政策。

## 真机验收

1. 无 VPN 时打开页面，不会发起网络检测；点联网检测明确显示“已安全跳过”。
2. VPN 默认网络可见时才允许自愿出口探测；IPv4/IPv6 按协议族分别判断，两个国家不一致时告警，未知时不猜测。
3. 关闭 Wi-Fi、VPN 节点切换、IPv6 无路由/屏蔽、VPN 意外断线时不崩溃，失效/超时准确说明。
4. 本机 DNS/Private DNS/时区/语言地区显示与 Android 设置相符；报告无真实 IP、SIM、GPS，时区和 locale 脱敏。
5. 浏览器打开的检测站点是**外部浏览器**，不冒充本 App 已自动验证 WebRTC。
6. 老版本模拟开始/停止、微信后台自动诊断、VPN 联动/锁定、SIM 归属地、时区同步完全不变。
7. GitHub Build Check、Debug APK、Lint、单位测试 PASS，真实 vivo Android 13 仍需用户验收。

版本 `2.4.0-lab47.9-privacy-audit` (`2400063`)。本 PR 堆叠在 #40 上，不自动合并 main。
