package com.zcshou.gogogo;

/** Pure evidence labels for privacy diagnostics; never claims another app was tested. */
public final class LabPrivacyAuditRules {
    private LabPrivacyAuditRules() {}

    public static String vpnState(boolean networkPresent, boolean vpnTransport) {
        if (!networkPresent) return "UNKNOWN: 当前无可观察的默认网络";
        if (!vpnTransport) return "WARNING: GoGoGo 当前默认网络未显示 VPN 传输";
        return "OBSERVED: GoGoGo 当前默认网络包含 VPN 传输";
    }

    public static String familyState(boolean tried, boolean reachable) {
        if (!tried) return "NOT_TESTED";
        return reachable ? "REACHABLE_VIA_APP_DEFAULT_NETWORK"
                : "NOT_REACHABLE_OR_BLOCKED_OR_FAILED";
    }

    public static String comparison(String systemZone, String vpnZone, boolean validVpnZone) {
        if (!validVpnZone || systemZone == null || systemZone.isEmpty())
            return "UNKNOWN: 缺少可核对的 VPN IANA 时区";
        return vpnZone.equals(systemZone)
                ? "MATCH: 系统时区与已保存 VPN 时区一致"
                : "DIFFERENT: 系统时区与已保存 VPN 时区不同（不等于真实国家泄露）";
    }

    public static String conclusion(boolean vpnObserved, boolean externalChecksCompleted) {
        if (!vpnObserved)
            return "需要检查 VPN 是否覆盖此应用；无法推断其他应用的网络路由";
        if (!externalChecksCompleted)
            return "仅有本地网络证据；公网出口、DNS 实际解析器和浏览器 WebRTC 尚未验证";
        return "已测试 GoGoGo 的网络出口连通性；其他应用的 DNS/WebRTC 和真实地区仍未验证";
    }
}
