package com.zcshou.gogogo;

/**
 * Pure-Java sequencing policy for GMS Task audit callbacks.
 * This is only about which audit event is current; it never proves mock mode
 * has been reset in Google Play Services or another consumer.
 */
public final class LabGmsRequestOrder {
    private LabGmsRequestOrder() {}

    public static long next(long previous) {
        // GMS requests cannot realistically hit the sequence ceiling.
        // Treat corrupt/legacy values conservatively by starting at 1.
        return previous <= 0L || previous == Long.MAX_VALUE ? 1L : previous + 1L;
    }

    /**
     * A stopped ServiceGo may send one extra best-effort disable after a late
     * enable success ONLY while its last request still owns the audit slot.
     * This prevents obsolete instances from initiating a new cleanup request
     * when a newer service has already started. This is not a physical GMS lock.
     */
    public static boolean mayRetryLateEnable(long owningRequestId,
                                              long latestRequestId) {
        return isCurrent(owningRequestId, latestRequestId);
    }

    public static boolean isCurrent(long callbackRequestId, long latestRequestId) {
        return callbackRequestId > 0L && latestRequestId > 0L
                && callbackRequestId == latestRequestId;
    }
}
