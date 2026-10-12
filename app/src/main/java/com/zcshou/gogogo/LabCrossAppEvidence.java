package com.zcshou.gogogo;

/** Fixed labels for human-observed cross-app positioning, not SDK attribution. */
public final class LabCrossAppEvidence {
    private LabCrossAppEvidence() {}

    public static final String[] APPLICATIONS = {
            "抖音发送位置", "高德地图 App", "微信发送位置", "腾讯地图 App",
            "美团首页 / 新增收货地址"
    };

    public static final String[] OBSERVATIONS = {
            "蓝点和附近地点均为国内实际地区",
            "蓝点和附近地点均为 GoGoGo 模拟目标地区",
            "蓝点国内、附近地点为模拟目标地区",
            "蓝点为模拟目标地区、附近地点国内",
            "蓝点/附近地点显示加载失败或无结果",
            "暂时无法判断"
    };

    public static final int MEITUAN_INDEX = 4;

    public static final String[] MEITUAN_OBSERVATIONS = {
            "首页和地址蓝点在模拟地区，城市栏仍是国内旧城市，地址列表提示网络不通畅",
            "首页和地址蓝点在模拟地区，城市栏仍是国内旧城市，地址列表正常加载",
            "首页和地址蓝点在模拟地区，城市栏也已更新，但地址列表加载失败",
            "首页和地址蓝点在模拟地区，地址列表正常加载",
            "首页或地址蓝点仍然在国内地区",
            "无法确认页面状态或出现其他结果"
    };

    public static final String[] MEITUAN_VPN_OUTCOMES = {
            "VPN 开启和关闭时，新增收货地址都报错",
            "VPN 关闭时失败，开启后正常",
            "VPN 开启时失败，关闭后正常",
            "VPN 状态只有一种 / 尚未做对照",
            "不同轮次网络条件变化，无法作对照结论"
    };

    public static String[] observationOptions(int appIndex) {
        return appIndex == MEITUAN_INDEX ? MEITUAN_OBSERVATIONS : OBSERVATIONS;
    }

    public static String vpnComparison(int choice) {
        return validIndex(choice, MEITUAN_VPN_OUTCOMES.length)
                ? MEITUAN_VPN_OUTCOMES[choice] + " (USER_REPORTED)"
                : "UNKNOWN_NO_CONTROLLED_COMPARISON";
    }

    public static String describe(int targetIndex, int observedIndex, boolean appOpened,
                                  String note) {
        String app = validIndex(targetIndex, APPLICATIONS.length)
                ? APPLICATIONS[targetIndex] : "未核实应用";
        String[] choices = observationOptions(targetIndex);
        String observed = validIndex(observedIndex, choices.length)
                ? choices[observedIndex] : "UNKNOWN";
        String cleaned = note == null ? "" : note.trim()
                .replace('\n', ' ').replace('\r', ' ');
        // Optional user note is not a location source. Redact likely phone
        // numbers, high-precision lat/lon and email before local aggregation.
        cleaned = cleaned.replaceAll("[-+]?\\d{1,3}\\.\\d{4,}", "[COORD_REDACTED]")
                .replaceAll("(?<!\\d)\\d{7,}(?!\\d)", "[NUMBER_REDACTED]")
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}",
                        "[EMAIL_REDACTED]");
        if (cleaned.length() > 240) cleaned = cleaned.substring(0, 240) + "…";
        return "app=" + app + "\nopened_by_user=" + appOpened
                + "\nvisual_observation=" + observed
                + "\nobservation_source=USER_REPORTED_NOT_SDK_PROVEN"
                + "\nmanual_note=" + (cleaned.isEmpty() ? "NONE" : cleaned)
                + "\nno_raw_coordinates=true";
    }

    public static boolean validIndex(int i, int count) {
        return i >= 0 && i < count;
    }
}
