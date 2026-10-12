package com.zcshou.gogogo;

import java.util.Locale;

/**
 * Privacy-minimal, zero-new-request acceptance summary for a voluntarily
 * shared RC preview. Deliberately does not contain coordinates, user IDs,
 * IP addresses, SDK keys, exception messages or third-party app contents.
 */
public final class LabReleaseAcceptanceSummary {
    public final boolean bound;
    public final boolean policyPublishing;
    public final long gpsSubmitted, gpsFailed, networkSubmitted, networkFailed;
    public final long frameworkFusedSubmitted, frameworkFusedFailed;
    public final boolean gmsMockEnabled;
    public final long gmsTaskReturned, gmsTaskOk, gmsTaskFailed, gmsTaskPending;
    public final long gmsTaskObserverUnknown, gmsTaskSyncFailed;
    public final long loopIntervals, loopIntervalsOver100Ms, tickCount;
    public final double measuredTickHz, meanGmsTaskLatencyMs;
    public final long peakGmsPending;

    private LabReleaseAcceptanceSummary(boolean bound, boolean publishing,
                                        long gps, long gpsFail,
                                        long network, long networkFail,
                                        long fused, long fusedFail, boolean gmsEnabled,
                                        long taskReturned, long taskOk, long taskFailed,
                                        long taskPending, long taskUnknown, long syncFailed,
                                        long loopIntervals, long slowLoops,
                                        long ticks, double tickHz,
                                        double taskLatency, long peakPending) {
        this.bound = bound;
        policyPublishing = publishing;
        gpsSubmitted = gps;
        gpsFailed = gpsFail;
        networkSubmitted = network;
        networkFailed = networkFail;
        frameworkFusedSubmitted = fused;
        frameworkFusedFailed = fusedFail;
        gmsMockEnabled = gmsEnabled;
        gmsTaskReturned = taskReturned;
        gmsTaskOk = taskOk;
        gmsTaskFailed = taskFailed;
        gmsTaskPending = taskPending;
        gmsTaskObserverUnknown = taskUnknown;
        gmsTaskSyncFailed = syncFailed;
        this.loopIntervals = loopIntervals;
        loopIntervalsOver100Ms = slowLoops;
        tickCount = ticks;
        measuredTickHz = tickHz;
        meanGmsTaskLatencyMs = taskLatency;
        peakGmsPending = peakPending;
    }

    /** A missing service is UNKNOWN, not a failed mock injection. */
    public static LabReleaseAcceptanceSummary unavailable() {
        return new LabReleaseAcceptanceSummary(false, false,
                0, 0, 0, 0, 0, 0, false,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                Double.NaN, Double.NaN, 0);
    }

    public static LabReleaseAcceptanceSummary observed(boolean publishing,
            long gps, long gpsFail, long network, long networkFail,
            long fused, long fusedFail, boolean gmsEnabled,
            LabGmsPublishEvidence.Snapshot gms,
            LabPublishPerformanceEvidence.Snapshot perf) {
        if (gms == null || perf == null) return unavailable();
        return new LabReleaseAcceptanceSummary(true, publishing,
                gps, gpsFail, network, networkFail, fused, fusedFail,
                gmsEnabled, gms.submitted, gms.taskSucceeded, gms.taskFailed,
                gms.pending, gms.callbackUnavailable, gms.synchronousFailure,
                perf.tickIntervals, perf.intervalsOver100Ms, perf.ticks,
                perf.measuredTickHz(), perf.meanTaskLatencyMs(),
                perf.peakPendingTasks);
    }

    public String render() {
        StringBuilder out = new StringBuilder();
        out.append("GoGoGo RC1 · 匿名验收摘要\n");
        out.append("SCHEMA=RC1_COUNTERS_NO_COORDINATES\n");
        out.append("SERVICE=").append(bound ? "BOUND" : "UNKNOWN").append('\n');
        if (!bound) {
            out.append("MOCK_POLICY=UNKNOWN\nFRAMEWORK_GPS=UNKNOWN\n");
            out.append("FRAMEWORK_NETWORK=UNKNOWN\nFRAMEWORK_FUSED=UNKNOWN\n");
            out.append("GMS_TASK=UNKNOWN\nACTUAL_TICK_HZ=UNKNOWN\n");
        } else {
            out.append("MOCK_POLICY=")
                    .append(policyPublishing ? "PUBLISHING" : "PAUSED").append('\n');
            out.append("FRAMEWORK_GPS=submitted:").append(gpsSubmitted)
                    .append(" failed:").append(gpsFailed).append('\n');
            out.append("FRAMEWORK_NETWORK=submitted:").append(networkSubmitted)
                    .append(" failed:").append(networkFailed).append('\n');
            out.append("FRAMEWORK_FUSED=submitted:").append(frameworkFusedSubmitted)
                    .append(" failed:").append(frameworkFusedFailed).append('\n');
            out.append("GMS_MOCK_FLAG=").append(gmsMockEnabled
                    ? "ENABLED_IN_SERVICE" : "UNAVAILABLE_OR_DISABLED").append('\n');
            out.append("GMS_TASK=returned:").append(gmsTaskReturned)
                    .append(" completed_ok:").append(gmsTaskOk)
                    .append(" failed:").append(gmsTaskFailed)
                    .append(" pending:").append(gmsTaskPending)
                    .append(" observer_unknown:").append(gmsTaskObserverUnknown)
                    .append(" sync_failed:").append(gmsTaskSyncFailed).append('\n');
            out.append("ACTUAL_TICK_HZ=")
                    .append(number(measuredTickHz)).append('\n');
            out.append("TICK_INTERVALS=").append(loopIntervals)
                    .append(" over100ms:").append(loopIntervalsOver100Ms)
                    .append(" ticks:").append(tickCount).append('\n');
            out.append("GMS_CALLBACK_MEAN_MS=")
                    .append(number(meanGmsTaskLatencyMs))
                    .append(" peak_pending:").append(peakGmsPending).append('\n');
        }
        out.append("E1_GMS_TASK_RETURNED=").append(bound
                ? (gmsTaskReturned > 0L ? "OBSERVED" : "NOT_OBSERVED")
                : "UNKNOWN").append('\n');
        out.append("E2_GMS_TASK_OK=").append(bound
                ? (gmsTaskOk > 0L ? "OBSERVED" : "NOT_OBSERVED")
                : "UNKNOWN").append('\n');
        out.append("E3_OWN_CONSUMER_FIX=NOT_CHECKED\n");
        out.append("E4_TENCENT_SDK_FIX=NOT_CHECKED\n");
        out.append("E5_WECHAT_DISPLAY=NOT_CHECKED\n");
        out.append("REAL_DEVICE_RELEASE_ACCEPTANCE=PENDING\n");
        out.append("注：此摘要不主动请求定位，也不读取微信/分身或设备敏感标识；"
                + "框架提交与 GMS Task OK 不能证明第三方采用模拟位置。"
                + "仅在你主动点击分享后交由系统选择发送目标。");
        return out.toString();
    }

    private static String number(double value) {
        return Double.isFinite(value) && value >= 0.0
                ? String.format(Locale.US, "%.1f", value) : "UNKNOWN";
    }
}
