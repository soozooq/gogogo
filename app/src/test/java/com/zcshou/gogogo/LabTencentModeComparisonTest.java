package com.zcshou.gogogo;

import org.junit.Test;
import static org.junit.Assert.*;

public class LabTencentModeComparisonTest {
    @Test public void fourProfilesInitiallyNotRun() {
        LabTencentModeComparison c = new LabTencentModeComparison();
        for (boolean poi : new boolean[]{false,true}) {
            for (boolean cache : new boolean[]{false,true}) {
                assertEquals(LabTencentModeComparison.State.NOT_RUN,c.getState(poi,cache));
            }
        }
        assertTrue(c.render(false).contains("SDK_KEY=UNAVAILABLE"));
    }

    @Test public void requestReturnIsNotAValidLocation() {
        LabTencentModeComparison c = new LabTencentModeComparison();
        c.begin(false, false);
        c.requestReturn(false,false,0);
        assertEquals(LabTencentModeComparison.State.ACCEPTED,c.getState(false,false));
        assertFalse(c.render(true).contains("GEO / OFF | FIX_VALID"));
    }

    @Test public void rejectedRequestCannotBeCalledFix() {
        LabTencentModeComparison c = new LabTencentModeComparison();
        c.begin(false, true);
        c.requestReturn(false,true,7);
        assertEquals(LabTencentModeComparison.State.REJECTED,c.getState(false,true));
        assertTrue(c.render(true).contains("GEO / ON | REJECTED"));
    }

    @Test public void validFixOnlyRecordedOnExplicitCallback() {
        LabTencentModeComparison c = new LabTencentModeComparison();
        c.begin(true,false);
        c.requestReturn(true,false,0);
        c.callbackFix(true,false,true,false,4300L,260L,3);
        assertEquals(LabTencentModeComparison.State.FIX_VALID,c.getState(true,false));
        assertTrue(c.render(true).contains("POI / OFF | FIX_VALID | 260ms | WALL_EST_LE_30S | 3"));
        assertEquals(LabTencentModeComparison.State.NOT_RUN,c.getState(true,true));
    }

    @Test public void zeroCoordinateAndBadCoordinateAreSuspect() {
        LabTencentModeComparison c = new LabTencentModeComparison();
        c.begin(false,false);c.callbackFix(false,false,true,true,0,100,-1);
        c.begin(false,true);c.callbackFix(false,true,false,false,0,100,-1);
        assertEquals(LabTencentModeComparison.State.FIX_SUSPECT,c.getState(false,false));
        assertEquals(LabTencentModeComparison.State.FIX_SUSPECT,c.getState(false,true));
    }

    @Test public void futureOrUnknownWallAgeIsNotFresh() {
        LabTencentModeComparison c = new LabTencentModeComparison();
        c.begin(true,true);c.callbackFix(true,true,true,false,-1,77,-1);
        assertTrue(c.render(true).contains("POI / ON | FIX_VALID | 77ms | WALL_UNKNOWN | UNKNOWN"));
    }

    @Test public void staleAgeRemainsOnlyEstimate() {
        LabTencentModeComparison c = new LabTencentModeComparison();
        c.begin(true,true);c.callbackFix(true,true,true,false,30001,100,0);
        assertTrue(c.render(true).contains("WALL_EST_GT_30S | 0"));
        assertFalse(c.render(true).contains("CACHE_HIT"));
    }

    @Test public void noDirectIncompatibleCoordinateComparison() {
        LabTencentModeComparison c = new LabTencentModeComparison();
        c.begin(false,false);c.callbackFix(false,false,true,false,220,100,-1);
        c.begin(false,true);c.callbackFix(false,true,true,false,250,110,-1);
        String summary=c.render(true);
        assertTrue(summary.contains("SAME_ESTIMATED_AGE_BUCKET_NOT_CACHE_PROOF"));
        assertFalse(summary.contains("WGS84="));
        assertFalse(summary.contains("GCJ02="));
    }

    @Test public void errorEmptyAndTimeoutStayDistinct() {
        LabTencentModeComparison c = new LabTencentModeComparison();
        c.begin(false,false);c.requestReturn(false,false,0);c.timeout(false,false);
        c.begin(false,true);c.callbackEmpty(false,true,150);
        c.begin(true,true);c.callbackError(true,true,10,200);
        assertEquals(LabTencentModeComparison.State.TIMEOUT,c.getState(false,false));
        assertEquals(LabTencentModeComparison.State.EMPTY,c.getState(false,true));
        assertEquals(LabTencentModeComparison.State.ERROR,c.getState(true,true));
    }

    @Test public void userCanResetOnlyLocalEvidence() {
        LabTencentModeComparison c = new LabTencentModeComparison();
        c.begin(true,false);c.callbackFix(true,false,true,false,12,30,1);
        c.clear();
        assertEquals(LabTencentModeComparison.State.NOT_RUN,c.getState(true,false));
        assertTrue(c.render(false).contains("GEO / OFF | NOT_RUN"));
    }

    @Test public void lastObservationReplacesPreviousInEachSlot() {
        LabTencentModeComparison c = new LabTencentModeComparison();
        c.begin(true,false);c.callbackFix(true,false,true,false,1,100,100);
        c.begin(true,false);
        assertEquals(LabTencentModeComparison.State.REQUESTING,c.getState(true,false));
        assertTrue(c.render(true).contains("POI / OFF | REQUESTING | UNKNOWN | WALL_UNKNOWN | UNKNOWN"));
    }

    @Test public void noRawLocationOrPoiNamesInMatrix() {
        LabTencentModeComparison c = new LabTencentModeComparison();
        c.begin(true,true);c.callbackFix(true,true,true,false,123,50,3);
        String summary=c.render(true);
        assertFalse(summary.contains("深圳"));
        assertFalse(summary.contains("ssid"));
        assertFalse(summary.contains("latitude"));
        assertFalse(summary.contains("longitude"));
        assertTrue(summary.contains("SDK_KEY=PRESENT_NOT_VALIDATED"));
    }
}
