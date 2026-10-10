package com.zcshou.gogogo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure-Java formatter for historical mock-service recovery evidence.
 * No provider access, location coordinates, device identity or external consumers.
 * Historical cleanup API results NEVER certify current provider or SDK state.
 */
public final class LabRecoveryTriage {
    public enum Lifecycle {
        NO_RECORD, NORMAL_STOP, RUNNING, INTERRUPTED_CANDIDATE, REBOOT_UNCERTAIN
    }

    public enum Cleanup {
        REMOVE_RETURNED, ALREADY_ABSENT, PENDING, SECURITY_DENIED, OTHER_FAILURE,
        NOT_RECORDED, UNRECOGNIZED
    }

    public enum Gms {
        DISABLE_SUCCEEDED, DISABLE_REQUESTED, DISABLE_FAILED,
        ENABLE_OR_OTHER, NOT_RECORDED
    }

    public static final class ProviderRow {
        public final String name;
        public final String phase;
        public final Cleanup outcome;
        public final Cleanup priorOutcome;
        public final LabEvidenceTimeWindow.Relation window;

        public ProviderRow(String name, String phase, String outcome, String previous) {
            this(name, phase, outcome, previous, LabEvidenceTimeWindow.Relation.UNKNOWN);
        }

        public ProviderRow(String name, String phase, String outcome, String previous,
                           LabEvidenceTimeWindow.Relation window) {
            this.name = name == null ? "UNKNOWN" : name;
            this.phase = phase == null ? "UNKNOWN" : phase;
            this.outcome = cleanup(outcome);
            this.priorOutcome = cleanup(previous);
            this.window = window == null ? LabEvidenceTimeWindow.Relation.UNKNOWN : window;
        }
    }

    public static final class Snapshot {
        public final Lifecycle lifecycle;
        public final boolean localServiceFlag;
        public final List<ProviderRow> providers;
        public final Gms gms;
        public final LabEvidenceTimeWindow.Relation gmsWindow;
        public final long ignoredGmsCallbacks;

        public Snapshot(Lifecycle lifecycle, boolean localServiceFlag,
                        List<ProviderRow> providers, String lastGmsEvent) {
            this(lifecycle, localServiceFlag, providers, lastGmsEvent,
                    LabEvidenceTimeWindow.Relation.UNKNOWN, 0L);
        }

        public Snapshot(Lifecycle lifecycle, boolean localServiceFlag,
                        List<ProviderRow> providers, String lastGmsEvent,
                        LabEvidenceTimeWindow.Relation gmsWindow) {
            this(lifecycle, localServiceFlag, providers, lastGmsEvent, gmsWindow, 0L);
        }

        public Snapshot(Lifecycle lifecycle, boolean localServiceFlag,
                        List<ProviderRow> providers, String lastGmsEvent,
                        LabEvidenceTimeWindow.Relation gmsWindow, long ignoredGmsCallbacks) {
            this.lifecycle = lifecycle == null ? Lifecycle.REBOOT_UNCERTAIN : lifecycle;
            this.localServiceFlag = localServiceFlag;
            this.providers = Collections.unmodifiableList(new ArrayList<>(
                    providers == null ? Collections.emptyList() : providers));
            this.gms = gms(lastGmsEvent);
            this.gmsWindow = gmsWindow == null
                    ? LabEvidenceTimeWindow.Relation.UNKNOWN : gmsWindow;
            this.ignoredGmsCallbacks = Math.max(0L, ignoredGmsCallbacks);
        }
    }

    private LabRecoveryTriage() {}

    public static Cleanup cleanup(String value) {
        if (value == null || value.isEmpty() || "NONE".equals(value)) {
            return Cleanup.NOT_RECORDED;
        }
        try {
            return Cleanup.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return Cleanup.UNRECOGNIZED;
        }
    }

    public static Gms gms(String event) {
        if (event == null || event.isEmpty() || "NOT_RECORDED".equals(event)) {
            return Gms.NOT_RECORDED;
        }
        if (event.startsWith("DISABLE_SUCCEEDED_")) return Gms.DISABLE_SUCCEEDED;
        if (event.startsWith("DISABLE_REQUESTED_")) return Gms.DISABLE_REQUESTED;
        if (event.startsWith("DISABLE_FAILED_")
                || event.startsWith("DISABLE_DENIED_")
                || event.startsWith("DISABLE_BLOCKED_")
                || event.startsWith("DISABLE_EXCEPTION_")) return Gms.DISABLE_FAILED;
        return Gms.ENABLE_OR_OTHER;
    }

    private static String lifecycleText(Lifecycle state) {
        switch (state) {
            case NO_RECORD: return "没有服务记录";
            case NORMAL_STOP: return "上次执行了正常停止回调（不等于完成系统清理）";
            case RUNNING: return "本进程运行标记与记录一致";
            case INTERRUPTED_CANDIDATE: return "疑似非正常结束／旧会话未关闭";
            default: return "可能经历重启或时钟变化，无法确定";
        }
    }

    private static String cleanupText(Cleanup state) {
        switch (state) {
            case REMOVE_RETURNED: return "REMOVE_RETURNED（移除 API 已返回）";
            case ALREADY_ABSENT: return "ALREADY_ABSENT（API 报告不存在）";
            case PENDING: return "PENDING（没有完成结果）";
            case SECURITY_DENIED: return "SECURITY_DENIED（权限拒绝）";
            case OTHER_FAILURE: return "OTHER_FAILURE（调用失败）";
            case NOT_RECORDED: return "无记录";
            default: return "未知结果";
        }
    }

    private static String gmsText(Gms state) {
        switch (state) {
            case DISABLE_SUCCEEDED: return "上一次记录 DISABLE_SUCCEEDED（历史异步回调）";
            case DISABLE_REQUESTED: return "仅记录 DISABLE_REQUESTED（尚未证实关闭成功）";
            case DISABLE_FAILED: return "上一次关闭失败／被拒／无法继续";
            case ENABLE_OR_OTHER: return "最近事件不是关闭成功回调，结果待核查";
            default: return "尚无 GMS 事件记录";
        }
    }

    public static String render(Snapshot snapshot) {
        if (snapshot == null) return "暂时没有可供判断的异常退出取证";
        StringBuilder result = new StringBuilder();
        result.append("Lab 32 · 异常退出取证（历史数据，只读）\n");
        result.append("当前进程服务标记：")
                .append(snapshot.localServiceFlag ? "RUNNING" : "STOPPED").append('\n');
        result.append("生命周期：").append(lifecycleText(snapshot.lifecycle)).append("\n\n");
        result.append("最近一次 Provider API 记录：\n");
        int currentPending = 0;
        int currentFailures = 0;
        int previousPending = 0;
        int olderWarnings = 0;
        int observed = 0;
        for (ProviderRow row : snapshot.providers) {
            if (row == null) continue;
            result.append(row.name).append(" · ").append(row.phase).append("：")
                    .append(cleanupText(row.outcome));
            if (row.outcome != Cleanup.NOT_RECORDED) {
                result.append(" [").append(LabEvidenceTimeWindow.label(row.window)).append("]");
            }
            if (row.priorOutcome == Cleanup.PENDING) {
                result.append("；更早一次曾 PENDING");
                previousPending++;
            }
            result.append('\n');
            boolean old = LabEvidenceTimeWindow.isFromEarlierStart(row.window);
            if (row.outcome == Cleanup.PENDING) {
                if (old) olderWarnings++; else currentPending++;
            }
            if (row.outcome == Cleanup.SECURITY_DENIED
                    || row.outcome == Cleanup.OTHER_FAILURE
                    || row.outcome == Cleanup.UNRECOGNIZED) {
                if (old) olderWarnings++; else currentFailures++;
            }
            if (row.outcome != Cleanup.NOT_RECORDED) observed++;
        }
        if (observed == 0) result.append("无任何 Provider 清理证据\n");
        result.append("\nGMS：").append(gmsText(snapshot.gms));
        if (snapshot.gms != Gms.NOT_RECORDED) {
            result.append(" [").append(LabEvidenceTimeWindow.label(snapshot.gmsWindow))
                    .append("]");
        }
        if (snapshot.ignoredGmsCallbacks > 0L) {
            result.append("\n已忽略 ").append(snapshot.ignoredGmsCallbacks)
                    .append(" 次过期 GMS 异步回调（审计顺序保护，不能证明系统状态）");
        }
        result.append("\n\n");

        result.append("建议：");
        if (snapshot.localServiceFlag || snapshot.lifecycle == Lifecycle.RUNNING) {
            result.append("模拟服务可能正在运行；不要在取证页强行移除 Provider。先在首页正常停止，再回来复查。");
        } else if (currentPending > 0 || currentFailures > 0) {
            result.append("存在未完成或失败的清理 API 记录；先保存摘要，在实验仪表盘核对详细结果。不要仅凭记录认定残留。");
        } else if (snapshot.lifecycle == Lifecycle.INTERRUPTED_CANDIDATE) {
            result.append("存在异常退出候选；请打开 Consumer Matrix 查看新鲜度，并核对启动扫尾记录。");
        } else if (snapshot.lifecycle == Lifecycle.REBOOT_UNCERTAIN) {
            result.append("旧会话和本次启动未必处于同一开机周期；建议重新做一次完整启动／停止对照。");
        } else if (observed == 0) {
            result.append("缺少清理取证数据；需要先用测试场景完成一次正常启动／停止后再对比。");
        } else {
            result.append("可在 Consumer Matrix 验证实时流，并到实验仪表盘查阅完整清理取证。");
        }

        if (olderWarnings > 0) {
            result.append("\n提醒：存在更早一次服务启动之前的异常记录；"
                    + "它们不能作为最近一轮清理失败的证据。");
        }
        if (previousPending > 0) {
            result.append("\n提醒：更早一次存在 PENDING，并不代表目前仍有 Provider 残留。");
        }
        if (!LabEvidenceTimeWindow.isFromEarlierStart(snapshot.gmsWindow)
                && (snapshot.gms == Gms.DISABLE_REQUESTED
                    || snapshot.gms == Gms.DISABLE_FAILED)) {
            result.append("\n注意：GMS 关闭没有成功回调证据，需要独立检查。");
        }
        result.append("\n\n所有结果均为本应用保存的历史记录；REMOVE_RETURNED 和 GMS 历史成功事件");
        result.append("都不能证明微信／GMS 缓存／系统当前状态。");
        result.append("事件时间与最近一次启动的前后关系只是墙上时钟排序，"
                + "不能证明它属于哪个进程或应用会话。");
        result.append("不包含经纬度、IP、SSID、PID 或完整日志。");
        return result.toString();
    }

    /** Compare already-redacted reports; no raw device reports are persisted here. */
    public static String compare(String baseline, String latest) {
        if (baseline == null || baseline.isEmpty()) return "没有基准摘要";
        if (latest == null || latest.isEmpty()) return "本次摘要不可用";
        if (baseline.equals(latest)) return "未发现摘要字段变化；并不代表实际系统状态全部正常。";
        String[] a = baseline.split("\\n", -1);
        String[] b = latest.split("\\n", -1);
        int changed = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            String before = i < a.length ? a[i] : "";
            String after = i < b.length ? b[i] : "";
            if (!before.equals(after)) {
                changed++;
                if (changed <= 12) {
                    sb.append("\n").append("第 ").append(i + 1).append(" 行：\n")
                            .append("前：").append(before).append("\n后：").append(after).append('\n');
                }
            }
        }
        if (changed > 12) sb.append("\n另有 ").append(changed - 12).append(" 行不同");
        return "发现 " + changed + " 行摘要差异：" + sb;
    }
}
