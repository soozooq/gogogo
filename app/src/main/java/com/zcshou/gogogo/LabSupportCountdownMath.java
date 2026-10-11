package com.zcshou.gogogo;

/** Pure elapsed-realtime countdown math; ticking never accumulates timing drift. */
public final class LabSupportCountdownMath {
    private LabSupportCountdownMath() {}

    public static int remainingSeconds(long deadlineElapsed, long nowElapsed) {
        if (deadlineElapsed <= nowElapsed) return 0;
        long difference = deadlineElapsed - nowElapsed;
        return (int) Math.min(Integer.MAX_VALUE, (difference - 1L) / 1000L + 1L);
    }
}
