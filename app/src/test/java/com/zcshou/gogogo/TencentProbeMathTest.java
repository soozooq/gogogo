package com.zcshou.gogogo;

import org.junit.Test;
import static org.junit.Assert.*;

public class TencentProbeMathTest {
    @Test public void samePointHasZeroDistance() {
        assertEquals(0.0, TencentProbeMath.distanceMeters(39.9075,116.3913,39.9075,116.3913),0.001);
    }
    @Test public void beijingToFujianIsAboutFifteenHundredKm() {
        double m=TencentProbeMath.distanceMeters(39.9075,116.3913,26.03,119.20);
        assertTrue(m>1500000 && m<1650000);
    }
    @Test public void zeroPairIsSuspiciousNotInvalid() {
        assertTrue(TencentProbeMath.validCoordinate(0,0));
        assertTrue(TencentProbeMath.suspiciousZeroPair(0,0));
        assertTrue(TencentProbeMath.describeCoordinate(0,0).contains("ZERO_PAIR_SUSPECT"));
    }
    @Test public void rejectsNanInfinityAndImpossibleLatitudes() {
        assertFalse(TencentProbeMath.validCoordinate(Double.NaN,1));
        assertFalse(TencentProbeMath.validCoordinate(Double.POSITIVE_INFINITY,1));
        assertFalse(TencentProbeMath.validCoordinate(91,1));
        assertFalse(TencentProbeMath.validCoordinate(1,181));
        assertTrue(Double.isNaN(TencentProbeMath.distanceMeters(100,0,0,0)));
    }
    @Test public void displaysStaleAgeAndFutureTimeClearly() {
        assertEquals("UNKNOWN",TencentProbeMath.displayAge(0,50000));
        assertEquals("5.0s",TencentProbeMath.displayAge(10000,15000));
        assertEquals("FUTURE_TIMESTAMP",TencentProbeMath.displayAge(200000,1000));
    }
}
