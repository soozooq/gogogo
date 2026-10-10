package com.zcshou.gogogo;

/**
 * Lab 33: combine already-redacted Lab 31/32 snapshots, and prioritize the next
 * human-visible check. No location coordinates, device IDs, or raw logs.
 *
 * Important: this is not an automated test of a third-party app or proof that
 * previously installed test providers have been removed.
 */
public final class Lab33DiagnosticBundle {
    public enum Priority { FIX_PREREQUISITES, REVIEW_RECOVERY, VERIFY_CONSUMERS }

    private Lab33DiagnosticBundle() {}

    public static Priority priority(LabQuickCheckReport.Snapshot quick,
                                    LabRecoveryTriage.Snapshot recovery) {
        if (quick == null) return Priority.FIX_PREREQUISITES;

        if (quick.systemLocation != LabQuickCheckReport.Signal.YES
                || quick.finePermission != LabQuickCheckReport.Signal.YES
                || quick.mockAppOp != LabQuickCheckReport.Signal.YES) {
            return Priority.FIX_PREREQUISITES;
        }

        if (recovery == null
                || recovery.lifecycle == LabRecoveryTriage.Lifecycle.INTERRUPTED_CANDIDATE
                || recovery.lifecycle == LabRecoveryTriage.Lifecycle.REBOOT_UNCERTAIN
                || recovery.gms == LabRecoveryTriage.Gms.DISABLE_FAILED
                || recovery.gms == LabRecoveryTriage.Gms.DISABLE_REQUESTED) {
            return Priority.REVIEW_RECOVERY;
        }

        for (LabRecoveryTriage.ProviderRow row : recovery.providers) {
            if (row == null) continue;
            if (row.outcome == LabRecoveryTriage.Cleanup.PENDING
                    || row.outcome == LabRecoveryTriage.Cleanup.SECURITY_DENIED
                    || row.outcome == LabRecoveryTriage.Cleanup.OTHER_FAILURE
                    || row.outcome == LabRecoveryTriage.Cleanup.UNRECOGNIZED) {
                return Priority.REVIEW_RECOVERY;
            }
        }
        return Priority.VERIFY_CONSUMERS;
    }

    private static String guidance(Priority priority) {
        switch (priority) {
            case FIX_PREREQUISITES:
                return "优先检查系统定位开关、本应用精确定位授权和开发者选项中的模拟位置应用。"
                        + "UNKNOWN 表示没有足够证据，不等于授权已成功。";
            case REVIEW_RECOVERY:
                return "优先打开异常退出与恢复取证，核对未完成的清理 API 或 GMS 异步回调。"
                        + "请勿在服务运行时强行移除 Provider。";
            default:
                return "基础条件没有发现明确阻断项；请用 Consumer Matrix 检查实时定位流。"
                        + "之后才能按需对照其他应用的实际 UI。";
        }
    }

    public static String render(LabQuickCheckReport.Snapshot quick,
                                LabRecoveryTriage.Snapshot recovery) {
        Priority p = priority(quick, recovery);
        StringBuilder result = new StringBuilder();
        result.append("GoGoGo Lab 33 · 一键排障摘要（只读）\n");
        result.append("下一步：").append(guidance(p)).append("\n\n");
        result.append("========== 基础条件（Lab 31） ==========\n");
        result.append(LabQuickCheckReport.render(quick));
        result.append("\n\n========== 退出及清理历史（Lab 32） ==========\n");
        result.append(LabRecoveryTriage.render(recovery));
        result.append("\n\n========== 验证边界 ==========\n");
        result.append("本摘要仅包含授权/运行标记及本应用已保存的清理结果枚举。");
        result.append("没有实时读取或验证微信、腾讯 SDK、GMS 缓存、系统原生 GNSS。");
        result.append("历史 REMOVE_RETURNED、DISABLE_SUCCEEDED 不代表第三方已经更新位置。");
        result.append("不自动修改模拟位置、清理 Provider、申请 Shizuku 权限。");
        return result.toString();
    }

    public static String compare(String baseline, String latest) {
        return LabRecoveryTriage.compare(baseline, latest);
    }
}
