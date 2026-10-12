package com.zcshou.gogogo;

import java.util.Locale;

/**
 * Per-ServiceGo, bounded O(1) in-memory timing counters. No location,
 * app identifiers, phone state, background polling or scheduler changes.
 * elapsedMs must be Android SystemClock.elapsedRealtime() (one boot).
 */
public final class LabPublishPerformanceEvidence {
    public static final long UNKNOWN = -1L;
    private long ticks;
    private long lastTickElapsedMs = UNKNOWN;
    private long validTickIntervals;
    private long sumTickIntervalMs;
    private long maxTickIntervalMs;
    private long intervalsOver50Ms;
    private long intervalsOver100Ms;

    private long taskReturned;
    private long taskFinished;
    private long taskFailed;
    private long taskObserverUnknown;
    private long pendingTasks;
    private long peakPendingTasks;
    private long completedWithValidLatency;
    private long sumTaskLatencyMs;
    private long maxTaskLatencyMs;
    private long taskLatenciesOver100Ms;
    private long invalidLatencySamples;
    private long unmatchedTerminalCallbacks;

    public synchronized void onTick(long elapsedMs) {
        ticks++;
        if (elapsedMs < 0L) {
            lastTickElapsedMs = UNKNOWN;
            return;
        }
        if (lastTickElapsedMs >= 0L) {
            if (elapsedMs > lastTickElapsedMs) {
                long delta = elapsedMs - lastTickElapsedMs;
                validTickIntervals++;
                // Avoid overflow for extremely long running sessions.
                sumTickIntervalMs = saturatingAdd(sumTickIntervalMs, delta);
                maxTickIntervalMs = Math.max(maxTickIntervalMs, delta);
                if (delta > 50L) intervalsOver50Ms++;
                if (delta > 100L) intervalsOver100Ms++;
            } else {
                invalidLatencySamples++;
            }
        }
        lastTickElapsedMs = elapsedMs;
    }

    public synchronized void onTaskReturned() {
        taskReturned++;
        pendingTasks++;
        peakPendingTasks = Math.max(peakPendingTasks, pendingTasks);
    }

    public synchronized void onTaskFinished(boolean successful, long returnedAtMs,
                                            long finishedAtMs) {
        if (pendingTasks == 0L) {
            unmatchedTerminalCallbacks++;
            return;
        }
        pendingTasks--;
        taskFinished++;
        if (!successful) taskFailed++;
        if (returnedAtMs < 0L || finishedAtMs < returnedAtMs) {
            invalidLatencySamples++;
            return;
        }
        long latency = finishedAtMs - returnedAtMs;
        completedWithValidLatency++;
        sumTaskLatencyMs = saturatingAdd(sumTaskLatencyMs, latency);
        maxTaskLatencyMs = Math.max(maxTaskLatencyMs, latency);
        if (latency > 100L) taskLatenciesOver100Ms++;
    }

    public synchronized void onCallbackUnavailable() {
        if (pendingTasks == 0L) {
            unmatchedTerminalCallbacks++;
            return;
        }
        pendingTasks--;
        taskObserverUnknown++;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(ticks, validTickIntervals, sumTickIntervalMs,
                maxTickIntervalMs, intervalsOver50Ms, intervalsOver100Ms,
                taskReturned, taskFinished, taskFailed, taskObserverUnknown,
                pendingTasks, peakPendingTasks, completedWithValidLatency,
                sumTaskLatencyMs, maxTaskLatencyMs, taskLatenciesOver100Ms,
                invalidLatencySamples, unmatchedTerminalCallbacks);
    }

    private static long saturatingAdd(long left, long right) {
        if (right < 0L || left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    public static final class Snapshot {
        public final long ticks, tickIntervals, tickTotalMs, maxTickIntervalMs;
        public final long intervalsOver50Ms, intervalsOver100Ms;
        public final long taskReturned, taskFinished, taskFailed, taskObserverUnknown;
        public final long pendingTasks, peakPendingTasks;
        public final long validTaskLatencies, taskTotalLatencyMs, maxTaskLatencyMs;
        public final long taskLatenciesOver100Ms, invalidClockSamples, unmatchedCallbacks;

        private Snapshot(long ticks, long intervals, long totalMs,
                         long maxTick, long over50, long over100,
                         long returned, long finished, long failed, long unknown,
                         long pending, long peak, long validLatency, long totalLatency,
                         long maxLatency, long slowLatency, long invalid,
                         long unmatched) {
            this.ticks = ticks;
            tickIntervals = intervals;
            tickTotalMs = totalMs;
            maxTickIntervalMs = maxTick;
            intervalsOver50Ms = over50;
            intervalsOver100Ms = over100;
            taskReturned = returned;
            taskFinished = finished;
            taskFailed = failed;
            taskObserverUnknown = unknown;
            pendingTasks = pending;
            peakPendingTasks = peak;
            validTaskLatencies = validLatency;
            taskTotalLatencyMs = totalLatency;
            maxTaskLatencyMs = maxLatency;
            taskLatenciesOver100Ms = slowLatency;
            invalidClockSamples = invalid;
            unmatchedCallbacks = unmatched;
        }

        /** Observed loop rate, not mock API throughput or location delivery rate. */
        public double measuredTickHz() {
            return tickIntervals <= 0L || tickTotalMs <= 0L
                    ? Double.NaN : tickIntervals * 1000.0 / tickTotalMs;
        }

        public double meanTaskLatencyMs() {
            return validTaskLatencies == 0L
                    ? Double.NaN : taskTotalLatencyMs / (double) validTaskLatencies;
        }

        public String render() {
            return String.format(Locale.US,
                    "Lab 52 · 只读性能观察（本次 ServiceGo 实例）\n"
                    + "计划休眠间隔=33ms；实际循环间隔（含处理时间）=%s ms；实测 tick≈%s Hz\n"
                    + "tick=%d · 有效间隔=%d · 最大间隔=%dms · >50ms=%d · >100ms=%d\n"
                    + "GMS Task returned=%d · finished=%d · failed=%d · unknown=%d\n"
                    + "pending=%d · peakPending=%d\n"
                    + "Task 返回→完成回调耗时平均=%s ms · 最大=%dms · >100ms=%d\n"
                    + "CLOCK_INVALID=%d · UNMATCHED_CALLBACK=%d\n"
                    + "注意：仅为本服务内部定时数据，不能证明第三方消费者收到位置；"
                    + "也不能据此声称 1Hz/5Hz/30Hz 哪种更兼容。",
                    avg(tickIntervals, tickTotalMs), format(measuredTickHz()),
                    ticks, tickIntervals, maxTickIntervalMs,
                    intervalsOver50Ms, intervalsOver100Ms,
                    taskReturned, taskFinished, taskFailed, taskObserverUnknown,
                    pendingTasks, peakPendingTasks,
                    format(meanTaskLatencyMs()), maxTaskLatencyMs,
                    taskLatenciesOver100Ms, invalidClockSamples, unmatchedCallbacks);
        }

        private static String avg(long count, long total) {
            return count <= 0L ? "UNKNOWN"
                    : String.format(Locale.US, "%.1f", total / (double) count);
        }

        private static String format(double value) {
            return Double.isFinite(value)
                    ? String.format(Locale.US, "%.1f", value) : "UNKNOWN";
        }
    }
}
