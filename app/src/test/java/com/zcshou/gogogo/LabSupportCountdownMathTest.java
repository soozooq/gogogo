package com.zcshou.gogogo;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class LabSupportCountdownMathTest {
    @Test public void countdownRoundsUpAndEndsAtDeadline() {
        long start = 100_000L;
        long deadline = start + 10_000L;
        assertEquals(10, LabSupportCountdownMath.remainingSeconds(deadline, start));
        assertEquals(10, LabSupportCountdownMath.remainingSeconds(deadline, start + 1));
        assertEquals(2, LabSupportCountdownMath.remainingSeconds(deadline, start + 8_001));
        assertEquals(1, LabSupportCountdownMath.remainingSeconds(deadline, deadline - 1));
        assertEquals(0, LabSupportCountdownMath.remainingSeconds(deadline, deadline));
        assertEquals(0, LabSupportCountdownMath.remainingSeconds(deadline, deadline + 4_000));
    }

    @Test public void delayedCallbackDoesNotAccumulateDrift() {
        assertEquals(3, LabSupportCountdownMath.remainingSeconds(30_000, 27_055));
        assertEquals(0, LabSupportCountdownMath.remainingSeconds(30_000, 31_500));
    }
}
