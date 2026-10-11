package com.zcshou.gogogo;

/** Pure checkpoint model for the user-owned diagnostic workflow, no Android side effects. */
public final class LabDiagnosticWorkflow {
    public static final String NONE = "NONE";
    public static final String BASELINE = "BASELINE";
    public static final String PASSIVE = "PASSIVE";
    public static final String WECHAT_UI = "WECHAT_UI";
    public static final String BACKGROUND_AB = "BACKGROUND_AB";
    public static final String DONE = "DONE";
    public static final int MAX_TOTAL_CHARS = 125000;

    private LabDiagnosticWorkflow() {}

    public static boolean isActive(String phase) {
        return BASELINE.equals(phase) || PASSIVE.equals(phase)
                || WECHAT_UI.equals(phase) || BACKGROUND_AB.equals(phase);
    }

    public static String next(String phase) {
        if (BASELINE.equals(phase)) return PASSIVE;
        if (PASSIVE.equals(phase)) return WECHAT_UI;
        if (WECHAT_UI.equals(phase)) return BACKGROUND_AB;
        if (BACKGROUND_AB.equals(phase)) return DONE;
        return NONE;
    }

    public static String title(String phase) {
        if (BASELINE.equals(phase)) return "阶段 1/4 · 系统基础排障";
        if (PASSIVE.equals(phase)) return "阶段 2/4 · 被动缓存自动采样";
        if (WECHAT_UI.equals(phase)) return "阶段 3/4 · 需要你确认微信界面";
        if (BACKGROUND_AB.equals(phase)) return "阶段 4/4 · 微信后台 A/B 对照";
        if (DONE.equals(phase)) return "全流程已结束 · 综合报告已生成";
        return "未创建自动诊断任务";
    }

    public static boolean bothAbResultsFromThisRun(long runStartedAtMs,
                                                    long wechatSavedAtMs,
                                                    long controlSavedAtMs) {
        return runStartedAtMs > 0 && wechatSavedAtMs >= runStartedAtMs
                && controlSavedAtMs >= runStartedAtMs;
    }

    public static String appendSection(String aggregate, String label, String body) {
        String existing = aggregate == null ? "" : aggregate;
        if (existing.length() >= MAX_TOTAL_CHARS) return existing;
        String safeLabel = label == null ? "UNKNOWN" : label.replace('\n', ' ');
        String safeBody = body == null ? "NO_DATA" : body;
        String addition = "\n========== " + safeLabel + " ==========\n"
                + safeBody + "\n";
        if (existing.length() + addition.length() > MAX_TOTAL_CHARS) {
            int available = Math.max(0, MAX_TOTAL_CHARS - existing.length() - 30);
            addition = addition.substring(0, Math.min(available, addition.length()))
                    + "\n[TRUNCATED]\n";
        }
        return existing + addition;
    }
}
