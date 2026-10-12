package com.zcshou.gogogo;

/** Fixed labels for human-observed cross-app positioning, not SDK attribution. */
public final class LabCrossAppEvidence {
    private LabCrossAppEvidence() {}

    public static final String[] APPLICATIONS = {
            "抖音发送位置", "高德地图 App", "微信发送位置", "腾讯地图 App"
    };

    public static final String[] OBSERVATIONS = {
            "蓝点和附近地点均为国内实际地区",
            "蓝点和附近地点均为 GoGoGo 模拟目标地区",
            "蓝点国内、附近地点为模拟目标地区",
            "蓝点为模拟目标地区、附近地点国内",
            "蓝点/附近地点显示加载失败或无结果",
            "暂时无法判断"
    };

    public static String describe(int targetIndex, int observedIndex, boolean appOpened,
                                  String note) {
        String app = validIndex(targetIndex, APPLICATIONS.length)
                ? APPLICATIONS[targetIndex] : "未核实应用";
        String observed = validIndex(observedIndex, OBSERVATIONS.length)
                ? OBSERVATIONS[observedIndex] : "UNKNOWN";
        String cleaned = note == null ? "" : note.trim()
                .replace('\n', ' ').replace('\r', ' ');
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
