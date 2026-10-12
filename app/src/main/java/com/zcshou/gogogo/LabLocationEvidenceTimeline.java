package com.zcshou.gogogo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * A local, bounded, privacy-minimal event timeline used by our own location
 * consumers. All event times use elapsedRealtime() from the SAME device boot.
 * Android fix ages are monotonic; Tencent SDK wall-clock ages are estimates.
 *
 * Never store coordinates, POI names, SDK messages, identifiers, or raw fixes.
 * Instances are process/page local: this is NOT a cross-process causal trace.
 */
public final class LabLocationEvidenceTimeline {
    public enum Source {
        GPS, NETWORK, FRAMEWORK_FUSED, GMS_LAST, GMS_CURRENT,
        GMS_UPDATES, TENCENT, PASSIVE_GPS, PASSIVE_NETWORK
    }

    public enum Stage {
        REQUEST, REQUEST_ACCEPTED, REQUEST_REJECTED, TASK_RETURNED, CALLBACK_FIX, CALLBACK_EMPTY,
        CALLBACK_FAILED, TIMEOUT, STOP
    }

    public enum AgeBasis {
        ANDROID_MONOTONIC, TENCENT_WALL_ESTIMATE, UNKNOWN
    }

    public enum MockState {
        YES, NO, UNKNOWN
    }

    public static final long UNKNOWN = -1L;
    private static final int MAX_EVENTS = 48;
    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private long startElapsedMs = UNKNOWN;

    public synchronized void add(Source source, Stage stage, long elapsedMs) {
        add(source, stage, elapsedMs, UNKNOWN, AgeBasis.UNKNOWN, MockState.UNKNOWN);
    }

    public synchronized void add(Source source, Stage stage, long elapsedMs,
                                 long fixAgeMs, AgeBasis ageBasis, MockState mock) {
        if (source == null || stage == null) return;
        long effectiveElapsedMs = elapsedMs < 0L ? UNKNOWN : elapsedMs;
        if (startElapsedMs == UNKNOWN && effectiveElapsedMs >= 0L) {
            startElapsedMs = effectiveElapsedMs;
        }
        long relativeMs = startElapsedMs < 0L || effectiveElapsedMs < startElapsedMs
                ? UNKNOWN : effectiveElapsedMs - startElapsedMs;
        long safeAge = fixAgeMs < 0L ? UNKNOWN : fixAgeMs;
        events.addLast(new Event(source, stage, relativeMs, safeAge,
                ageBasis == null ? AgeBasis.UNKNOWN : ageBasis,
                mock == null ? MockState.UNKNOWN : mock));
        while (events.size() > MAX_EVENTS) events.removeFirst();
    }

    public synchronized String renderRecent(int maxRows) {
        if (events.isEmpty()) return "暂无事件；未进行跨进程采集。";
        List<Event> snapshot = new ArrayList<>(events);
        int count = Math.max(1, Math.min(MAX_EVENTS, maxRows));
        StringBuilder out = new StringBuilder();
        out.append("事件时间轴（本页面/本进程；+ms 为单调时钟相对时间）\n");
        for (int i = Math.max(0, snapshot.size() - count); i < snapshot.size(); i++) {
            Event event = snapshot.get(i);
            out.append(event.relativeMs < 0L ? "+UNKNOWN" : "+" + event.relativeMs)
                    .append("ms  ").append(event.source.name())
                    .append("  ").append(event.stage.name());
            if (event.stage == Stage.CALLBACK_FIX) {
                out.append("  fixAge=")
                        .append(event.fixAgeMs < 0L ? "UNKNOWN" : event.fixAgeMs + "ms")
                        .append("  basis=").append(event.ageBasis.name())
                        .append("  isMock=").append(event.mock.name());
            }
            if (i + 1 < snapshot.size()) out.append('\n');
        }
        out.append("\n注意：Task 返回≠成功；SDK 回调≠第三方应用采用。");
        return out.toString();
    }

    public synchronized int size() {
        return events.size();
    }

    public synchronized void clear() {
        events.clear();
        startElapsedMs = UNKNOWN;
    }

    private static final class Event {
        final Source source;
        final Stage stage;
        final long relativeMs;
        final long fixAgeMs;
        final AgeBasis ageBasis;
        final MockState mock;

        Event(Source source, Stage stage, long relativeMs, long fixAgeMs,
              AgeBasis ageBasis, MockState mock) {
            this.source = source;
            this.stage = stage;
            this.relativeMs = relativeMs;
            this.fixAgeMs = fixAgeMs;
            this.ageBasis = ageBasis;
            this.mock = mock;
        }
    }

    /** Clamps unknown/future timestamp values instead of pretending they are new. */
    public static long estimateWallAgeMs(long nowWallMs, long fixWallMs) {
        if (nowWallMs <= 0L || fixWallMs <= 0L || fixWallMs > nowWallMs) {
            return UNKNOWN;
        }
        return nowWallMs - fixWallMs;
    }
}
