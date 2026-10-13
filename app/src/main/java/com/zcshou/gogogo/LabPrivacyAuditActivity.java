package com.zcshou.gogogo;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * User-triggered privacy *audit*, not fingerprint evasion.
 * Local snapshot is passive. Optional external checks use only GoGoGo's
 * default VPN network and never keep, display, log or upload the returned IP.
 */
public final class LabPrivacyAuditActivity extends AppCompatActivity {
    private static final String IPV4_CHECK = "https://api4.ipify.org?format=json";
    private static final String IPV6_CHECK = "https://api6.ipify.org?format=json";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView status;
    private TextView local;
    private TextView external;
    private boolean running;
    private boolean closing;
    private String report = "";
    private String latestExternal = "尚未运行外部检测：IPv4、IPv6 出口和公网国家均未验证。";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createUi();
        refreshLocal();
    }

    private void createUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        GoGoUi.applyScreenBackground(scroll);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(GoGoUi.dp(this, 18), GoGoUi.dp(this, 22),
                GoGoUi.dp(this, 18), GoGoUi.dp(this, 32));
        scroll.addView(root);

        root.addView(GoGoUi.eyebrow(this, "PRIVACY  /  NETWORK AUDIT"), GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));
        root.addView(GoGoUi.heroTitle(this, "网络与定位隐私体检"), GoGoUi.matchWrap());
        root.addView(GoGoUi.subtitle(this,
                "只读检查 VPN、网络协议、DNS、时区 · 可选联网出口检测"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 14));

        com.google.android.material.card.MaterialCardView card = GoGoUi.card(this);
        LinearLayout body = GoGoUi.cardContent(this);
        card.addView(body);
        body.addView(GoGoUi.sectionTitle(this, "本机只读快照"), GoGoUi.matchWrap());
        body.addView(GoGoUi.muted(this, "仅检查 GoGoGo 自己的默认网络；"
                + "不调用定位 API、不修改系统设置、不读取 SIM 号码。"), GoGoUi.matchWrap());
        local = GoGoUi.muted(this, "等待检查…");
        local.setTextIsSelectable(true);
        body.addView(GoGoUi.gap(this, 10));
        body.addView(local, GoGoUi.matchWrap());
        body.addView(GoGoUi.secondaryButton(this, "重新采集本机状态",
                v -> refreshLocal()), GoGoUi.matchWrap());
        root.addView(card, GoGoUi.matchWrap());

        root.addView(GoGoUi.gap(this, 14));
        com.google.android.material.card.MaterialCardView probeCard = GoGoUi.card(this);
        LinearLayout probeBody = GoGoUi.cardContent(this);
        probeCard.addView(probeBody);
        probeBody.addView(GoGoUi.sectionTitle(this, "自愿联网验证"), GoGoUi.matchWrap());
        probeBody.addView(GoGoUi.muted(this,
                "点击后才向 api4.ipify.org、api6.ipify.org 和 ipwho.is"
                        + " 发起 HTTPS 请求，后者会核对两个公网出口 IP 的国家。"
                        + " 这些服务会看到请求的公网出口 IP，ipwho.is 会收到两个出口地址。"
                        + " 本应用只保留连通性及国家代码，不保存或复制 IP 地址。"
                        + " 仅测试 GoGoGo，不代表 Claude、微信或浏览器使用同一出口。"),
                GoGoUi.matchWrap());
        status = GoGoUi.muted(this, "尚未开始联网检测");
        probeBody.addView(GoGoUi.gap(this, 8));
        probeBody.addView(status, GoGoUi.matchWrap());
        external = GoGoUi.muted(this, latestExternal);
        external.setTextIsSelectable(true);
        probeBody.addView(external, GoGoUi.matchWrap());
        probeBody.addView(GoGoUi.secondaryButton(this,
                "确认联网检测 IPv4 / IPv6 / 出口国家",
                v -> new AlertDialog.Builder(this)
                        .setTitle("进行第三方出口测试？")
                        .setMessage("ipify 会看到请求出口 IP；ipwho.is 会收到"
                                + " IPv4/IPv6 出口地址用于归属国家查询。"
                                + "仅在 GoGoGo 默认网络确认 VPN 时执行，"
                                + "不保存 IP，也不能检查 Claude 或浏览器是否分流。")
                        .setNegativeButton("取消", (d, w) -> {})
                        .setPositiveButton("同意并检测", (d, w) -> startProbes())
                        .show()), GoGoUi.matchWrap());
        root.addView(probeCard, GoGoUi.matchWrap());

        root.addView(GoGoUi.gap(this, 14));
        com.google.android.material.card.MaterialCardView browserCard = GoGoUi.card(this);
        LinearLayout browserBody = GoGoUi.cardContent(this);
        browserCard.addView(browserBody);
        browserBody.addView(GoGoUi.sectionTitle(this, "浏览器专项检查"), GoGoUi.matchWrap());
        browserBody.addView(GoGoUi.muted(this,
                "GoGoGo 无法直接读取 Chrome、Claude App 或其它浏览器内部的"
                        + " WebRTC/DNS 结果。以下检查必须在你实际使用的浏览器里打开。"
                        + " 第三方测试页能看到浏览器的公网 IP；是否打开完全由你决定。"),
                GoGoUi.matchWrap());
        browserBody.addView(GoGoUi.secondaryButton(this, "打开 WebRTC 泄露检测",
                v -> confirmExternalPage("https://browserleaks.com/webrtc")), GoGoUi.matchWrap());
        browserBody.addView(GoGoUi.secondaryButton(this, "打开 DNS 泄露检测",
                v -> confirmExternalPage("https://www.dnsleaktest.com/")), GoGoUi.matchWrap());
        browserBody.addView(GoGoUi.secondaryButton(this, "打开 Android VPN 设置",
                v -> {
                    try { startActivity(new Intent(Settings.ACTION_VPN_SETTINGS)); }
                    catch (RuntimeException unavailable) {
                        Toast.makeText(this, "系统未提供标准 VPN 设置入口",
                                Toast.LENGTH_LONG).show();
                    }
                }), GoGoUi.matchWrap());
        browserBody.addView(GoGoUi.muted(this,
                "建议检查 VPN 是否启用「始终开启 / 无 VPN 时阻止连接」；"
                        + "以及浏览器、Claude 是否被 VPN 分流排除。"
                        + " 本应用无法验证其它应用的网络路径。"), GoGoUi.matchWrap());
        root.addView(browserCard, GoGoUi.matchWrap());

        root.addView(GoGoUi.gap(this, 14));
        root.addView(GoGoUi.primaryButton(this, "复制脱敏隐私体检总日志",
                v -> {
                    refreshReport();
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText(
                                    "GoGoGo privacy audit", report));
                    Toast.makeText(this, "已复制不含公网 IP / SIM / 真实 GPS 的报告",
                            Toast.LENGTH_SHORT).show();
                }), GoGoUi.matchWrap());
        TextView footer = GoGoUi.muted(this,
                "注意：单次检测无法证明「实际国家绝不会被识别」，"
                        + "也不能确认第三方账号、支付资料或服务端历史信号。"
                        + "检测结果不是服务地区资格或平台封禁规则的保证。");
        footer.setGravity(Gravity.CENTER);
        root.addView(GoGoUi.gap(this, 14));
        root.addView(footer, GoGoUi.matchWrap());
        setContentView(scroll);
        TianwaitianBottomNav.attach(this, TianwaitianBottomNav.Tab.AUDIT);
    }

    private void confirmExternalPage(String url) {
        new AlertDialog.Builder(this)
                .setTitle("打开第三方检测网站")
                .setMessage("网站会看到你在浏览器中的出口 IP，"
                        + "可能收集浏览器指纹。GoGoGo 不会读取或上传它的结果。")
                .setNegativeButton("取消", (d, w) -> {})
                .setPositiveButton("继续", (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (RuntimeException unavailable) {
                        Toast.makeText(this, "无法打开浏览器，请检查默认浏览器",
                                Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void refreshLocal() {
        StringBuilder sb = new StringBuilder();
        ConnectivityManager connectivity = (ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE);
        Network network = connectivity == null ? null : connectivity.getActiveNetwork();
        NetworkCapabilities caps = connectivity == null || network == null
                ? null : connectivity.getNetworkCapabilities(network);
        boolean vpn = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
        sb.append("GoGoGo 默认网络：")
                .append(LabPrivacyAuditRules.vpnState(network != null, vpn))
                .append("\n");

        if (network == null) {
            sb.append("链路 IP/DNS：UNKNOWN（默认网络不可用）\n");
        } else {
            LinkProperties link = connectivity.getLinkProperties(network);
            if (link == null) {
                sb.append("链路 IP/DNS：UNKNOWN（系统未提供 LinkProperties）\n");
            } else {
                int v4 = 0;
                int v6 = 0;
                for (LinkAddress address : link.getLinkAddresses()) {
                    InetAddress ip = address.getAddress();
                    if (ip instanceof Inet4Address) v4++;
                    if (ip instanceof Inet6Address) v6++;
                }
                sb.append("链路地址类型：IPv4=").append(v4 > 0 ? "存在" : "未见")
                        .append("，IPv6=").append(v6 > 0 ? "存在" : "未见")
                        .append("（不代表公网可达）\n");
                sb.append("系统报告 DNS 服务器数量：")
                        .append(link.getDnsServers().size()).append("\n");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    sb.append("Android Private DNS：")
                            .append(link.isPrivateDnsActive() ? "活动" : "未显示活动")
                            .append("\n");
                }
                sb.append("DNS 真正出口地区：UNVERIFIED（不能由 DNS 地址推断）\n");
            }
        }
        String expected = LabVpnExitSync.timezoneId(this);
        String actual = TimeZone.getDefault().getID();
        sb.append("手机系统时区：").append(actual).append("\n");
        sb.append("已保存 VPN 时区：")
                .append(LabVpnTimeZone.valid(expected) ? expected : "UNKNOWN")
                .append("\n");
        sb.append("时区比较：").append(LabPrivacyAuditRules.comparison(actual,
                expected, LabVpnTimeZone.valid(expected))).append("\n");
        sb.append("手机语言/地区设置：")
                .append(Locale.getDefault().toLanguageTag())
                .append("（不等于实际国家）\n");
        sb.append("浏览器 WebRTC：NOT_TESTED_IN_THIS_APP\n");
        sb.append("Claude/其它应用 VPN 分流：UNKNOWN\n");
        sb.append("VPN 断线阻断保护：UNKNOWN（请进入 VPN 设置核对）\n");

        local.setText(sb.toString());
        refreshReport();
    }

    private void startProbes() {
        if (running || closing) {
            Toast.makeText(this, "当前正在检测", Toast.LENGTH_SHORT).show();
            return;
        }
        ConnectivityManager manager = (ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE);
        Network current = manager == null ? null : manager.getActiveNetwork();
        NetworkCapabilities caps = current == null ? null
                : manager.getNetworkCapabilities(current);
        if (current == null || caps == null
                || !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            latestExternal = "未执行：GoGoGo 的默认网络未确认 VPN。"
                    + "建议先检查 VPN 分流/默认连接，防止测试意外使用非 VPN 出口。";
            external.setText(latestExternal);
            status.setText("联网测试已安全跳过");
            refreshReport();
            return;
        }
        running = true;
        status.setText("正在检查本应用 VPN 上的公网 IPv4 / IPv6 出口…");
        executor.execute(() -> {
            String[] v4 = readFamily(current, IPV4_CHECK, false);
            String[] v6 = readFamily(current, IPV6_CHECK, true);
            runOnUiThread(() -> {
                if (closing) return;
                running = false;
                Network latest = manager.getActiveNetwork();
                NetworkCapabilities currentCaps = latest == null ? null
                        : manager.getNetworkCapabilities(latest);
                if (!current.equals(latest) || currentCaps == null
                        || !currentCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    latestExternal = "VPN 在检测过程中变化，全部结果已作废；请重新检测。";
                } else {
                    latestExternal = "IPv4 HTTPS 出口：" + v4[0] + " · 国家=" + v4[1]
                            + "\nIPv6 HTTPS 出口：" + v6[0] + " · 国家=" + v6[1]
                            + "\n双栈国家对照：" + LabPrivacyAuditRules.familyCountryComparison(
                                    v4[1], v6[1])
                            + "\n仅针对本应用，其他应用/WebRTC/DNS泄露仍未验证。"
                            + "\n测试未存储任何 IP 地址。";
                }
                external.setText(latestExternal);
                status.setText("本轮联网检测结束；不是跨应用隐私认证");
                refreshLocal();
            });
        });
    }

    private static String[] readFamily(Network network, String endpoint, boolean ipv6) {
        try {
            JSONObject result = new JSONObject(fetch(network, endpoint));
            String ip = result.optString("ip", "").trim();
            boolean valid = ipv6
                    ? ip.contains(":") && ip.matches("[0-9a-fA-F:.]{3,45}")
                    : ip.matches("[0-9.]{7,15}") && ip.contains(".");
            if (!valid) return new String[]{"UNKNOWN/UNEXPECTED_FAMILY", "UNKNOWN"};
            return new String[]{
                    ipv6 ? "REACHABLE (IPv6)" : "REACHABLE (IPv4)",
                    readIpCountry(network, ip)
            };
        } catch (Exception unavailable) {
            return new String[]{"FAILED/UNREACHABLE/PROXY_BLOCKED (not proof of no leakage)",
                    "UNKNOWN"};
        }
    }

    private static String readIpCountry(Network network, String validatedIp) {
        try {
            // This explicit optional lookup discloses the exit IP to ipwho.is.
            // Never write the IP to logs, UI or the copied summary.
            JSONObject result = new JSONObject(fetch(network,
                    "https://ipwho.is/" + validatedIp + "?fields=success,country_code"));
            if (!result.optBoolean("success", false)) return "UNKNOWN";
            String code = result.optString("country_code", "");
            return code.matches("[A-Z]{2}") ? code : "UNKNOWN";
        } catch (Exception unavailable) {
            return "UNKNOWN";
        }
    }

    private static String fetch(Network network, String url) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) network.openConnection(new URL(url));
            connection.setConnectTimeout(6500);
            connection.setReadTimeout(6500);
            connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != 200) throw new IllegalStateException("HTTP");
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] block = new byte[512];
                int count;
                while ((count = input.read(block)) != -1) {
                    if (output.size() + count > 2048) throw new IllegalStateException("OVERSIZE");
                    output.write(block, 0, count);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void refreshReport() {
        String localText = local == null ? "UNKNOWN" : local.getText().toString();
        String sanitized = localText
                .replaceAll("手机系统时区：[^\\n]*", "手机系统时区：[已脱敏]")
                .replaceAll("已保存 VPN 时区：[^\\n]*", "已保存 VPN 时区：[已脱敏]")
                .replaceAll("手机语言/地区设置：[^\\n]*", "手机语言/地区设置：[已脱敏]");
        String egress = latestExternal == null ? "NOT_TESTED" : latestExternal;
        report = "GOGOGO_PRIVACY_AUDIT_V1\n"
                + "scope=GoGoGo-default-network-only\n"
                + "network_snapshot=READ_ONLY\n"
                + sanitized + "\nEXTERNAL_OPTIONAL\n" + egress
                + "\nWEBRTC=NOT_VERIFIED_FOR_EXTERNAL_BROWSER\n"
                + "DNS_LEAK=NOT_VERIFIED_FOR_EXTERNAL_BROWSER\n"
                + "THIRD_PARTY_APP_ROUTING=UNKNOWN\n"
                + "no_raw_ip_no_sim_identity_no_real_gps_coordinates=true\n"
                + "cannot_guarantee_anonymity_or_country_invisibility=true";
    }

    @Override protected void onDestroy() {
        closing = true;
        executor.shutdownNow();
        super.onDestroy();
    }
}
