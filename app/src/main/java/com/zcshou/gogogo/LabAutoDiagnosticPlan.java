package com.zcshou.gogogo;

/** Fixed safe diagnostic schedule and privacy-limited location evidence labels. */
public final class LabAutoDiagnosticPlan {
    public static final int[] PASSIVE_SECONDS = {0, 10, 30, 60};
    private LabAutoDiagnosticPlan() {}

    public static String cacheLine(String source, boolean hasFix, boolean isMock,
                                   long fixAgeMs, String mockTargetBucket) {
        String safeSource = "GPS".equals(source) || "NETWORK".equals(source)
                || "GMS_LAST".equals(source) ? source : "UNKNOWN";
        if (!hasFix) return safeSource + "：NULL/无缓存";
        String bucket = mockTargetBucket == null ? "UNKNOWN" : mockTargetBucket;
        return safeSource + "：fixAge=" + (fixAgeMs < 0 ? "UNKNOWN" : fixAgeMs + "ms")
                + " mock=" + (isMock ? "YES" : "NO")
                + (isMock ? " mock_target_distance=" + bucket : "")
                + " (坐标不记录)";
    }

    public static String taskTitle(boolean passive) {
        return passive ? "仅缓存时序观察（0/10/30/60 秒）" : "一键只读基础排障";
    }
}
