package com.zcshou.gogogo;

/**
 * Wall-clock ordering for persisted cleanup/GMS audit events relative to the
 * most recently recorded ServiceGo start. No clock-based conclusion here
 * establishes process identity, provider presence, or external app behavior.
 */
public final class LabEvidenceTimeWindow {
    public enum Relation { AFTER_START, BEFORE_START, UNKNOWN }

    private LabEvidenceTimeWindow() {}

    public static Relation classify(long lastStartWall, long eventWall, long nowWall) {
        if (lastStartWall <= 0L || eventWall <= 0L || nowWall <= 0L) {
            return Relation.UNKNOWN;
        }
        if (lastStartWall > nowWall || eventWall > nowWall) {
            // Device clock may have been set backward; don't trust ordering.
            return Relation.UNKNOWN;
        }
        if (eventWall < lastStartWall) return Relation.BEFORE_START;
        return Relation.AFTER_START;
    }

    public static String label(Relation relation) {
        if (relation == Relation.AFTER_START) return "时间在最近一次服务启动之后";
        if (relation == Relation.BEFORE_START) return "早于最近一次服务启动（旧记录）";
        return "时间关联无法确认";
    }

    public static boolean isFromEarlierStart(Relation relation) {
        return relation == Relation.BEFORE_START;
    }
}
