package com.zcshou.gogogo;

/**
 * Lab 30: independently measure the age of a location fix and the age of its
 * callback.  Both elapsed clocks are monotonic only within one device boot.
 *
 * -1 means UNKNOWN/INVALID; never turn an invalid or future clock into "fresh".
 * No Android dependencies: the conversions can be regression-tested on the JVM.
 */
public final class LabLocationAge {
    public static final long UNKNOWN = -1L;
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private LabLocationAge() {}

    /** Android Location.getElapsedRealtimeNanos() against SystemClock.elapsedRealtimeNanos(). */
    public static long fixAgeMs(long nowElapsedNanos, long fixElapsedNanos) {
        if (nowElapsedNanos <= 0L || fixElapsedNanos <= 0L
                || fixElapsedNanos > nowElapsedNanos) {
            return UNKNOWN;
        }
        return (nowElapsedNanos - fixElapsedNanos) / NANOS_PER_MILLI;
    }

    /** Age of the callback delivery, NOT the age of the Location fix. */
    public static long callbackAgeMs(long nowElapsedMs, long receivedElapsedMs) {
        if (nowElapsedMs < 0L || receivedElapsedMs < 0L
                || receivedElapsedMs > nowElapsedMs) {
            return UNKNOWN;
        }
        return nowElapsedMs - receivedElapsedMs;
    }

    public static boolean isRecent(long ageMs, long thresholdMs) {
        return ageMs >= 0L && ageMs <= thresholdMs;
    }
}
