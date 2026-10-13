package com.zcshou.gogogo;

import org.junit.Test;
import static org.junit.Assert.*;

public class LabAutoDiagnosticPlanTest {
    @Test public void passiveScheduleMatchesPromisedDelays() {
        assertArrayEquals(new int[]{0,10,30,60}, LabAutoDiagnosticPlan.PASSIVE_SECONDS);
    }

    @Test public void reportsFixAgeButRedactsRealLocationAndDistances() {
        String line = LabAutoDiagnosticPlan.cacheLine("GPS", true, false, 1200, "VERY_CLOSE");
        assertTrue(line.contains("fixAge=1200ms"));
        assertTrue(line.contains("mock=NO"));
        assertFalse(line.contains("VERY_CLOSE"));
        assertTrue(line.contains("坐标不记录"));
    }

    @Test public void onlyMockFixCanShowDistanceBucket() {
        String line = LabAutoDiagnosticPlan.cacheLine("GMS_LAST", true, true, 20, "WITHIN_100M");
        assertTrue(line.contains("mock_target_distance=WITHIN_100M"));
    }

    @Test public void unknownOrMissingNeverClaimsSuccess() {
        assertTrue(LabAutoDiagnosticPlan.cacheLine("NETWORK", false, false, -1, null)
                .contains("NULL"));
        assertTrue(LabAutoDiagnosticPlan.cacheLine("OTHER", true, true, -1, null)
                .startsWith("UNKNOWN"));
    }
}
