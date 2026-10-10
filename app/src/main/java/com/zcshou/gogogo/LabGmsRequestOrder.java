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

    public static boolean isCurrent(long callbackRequestId, long latestRequestId) {
        return callbackRequestId > 0L && latestRequestId > 0L
                && callbackRequestId == latestRequestId;
    }
}
