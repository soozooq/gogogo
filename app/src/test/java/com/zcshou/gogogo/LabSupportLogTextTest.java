package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.*;

public final class LabSupportLogTextTest {
    @Test public void boundedRingKeepsNewestEntries() {
        String log = "";
        for (int i = 0; i < 40; i++) {
            log = LabSupportLogText.appendBounded(log, "event=" + i, 14);
        }
        assertEquals(14, log.split("\\n").length);
        assertFalse(log.contains("event=0\n"));
        assertTrue(log.contains("event=39\n"));
    }

    @Test public void sdkReasonAndPlacesAreNeverCopied() {
        assertEquals("CALLBACK SINGLE_FRESH error=0",
                LabSupportLogText.summarizeTencent(
                        "CALLBACK SINGLE_FRESH error=0 reason=token/secret/real_address"));
        assertNull(LabSupportLogText.summarizeTencent(
                "POI[0] name=private-address distance=100"));
        assertEquals("FIX_RECEIVED coordinates=REDACTED",
                LabSupportLogText.summarizeTencent("FIX coord(GCJ02 lon,lat)=116.31,39.99"));
        assertEquals("ERROR SecurityException",
                LabSupportLogText.summarizeTencent("ERROR SecurityException private-key-value"));
    }

    @Test public void targetIsStrictlyNumericAndWithinRanges() {
        assertEquals("116.397470", LabSupportLogText.finiteTarget("116.39747", true));
        assertEquals("INVALID", LabSupportLogText.finiteTarget("181", true));
        assertEquals("INVALID", LabSupportLogText.finiteTarget("-91", false));
        assertEquals("INVALID", LabSupportLogText.finiteTarget("NaN", false));
        assertEquals("INVALID", LabSupportLogText.finiteTarget("not-an-address", true));
    }

    @Test public void distanceOnlyReportsBroadBucket() {
        assertEquals("LT_1KM",
                LabSupportLogText.distanceBucket(116.39747, 39.90549,
                        116.39748, 39.90550));
        assertEquals("GE_1000KM",
                LabSupportLogText.distanceBucket(116.39747, 39.90549,
                        119.2, 26.0));
        assertEquals("UNKNOWN",
                LabSupportLogText.distanceBucket(999.0, 39.0, 119.0, 26.0));
    }

    @Test public void recentRowsKeepWholeEntriesAndDropOldExperiments() {
        String rows = "";
        for (int i = 0; i < 14; i++) {
            rows = LabSupportLogText.appendBounded(rows, "shot=" + i, 14);
        }
        String recent = LabSupportLogText.lastLines(rows, 3);
        assertEquals("shot=11\nshot=12\nshot=13\n", recent);
        assertEquals("NO_RECORD\n", LabSupportLogText.lastLines("NO_RECORD\n", 3));
    }

    @Test public void boundEntryCannotInjectNewLines() {
        String result = LabSupportLogText.appendBounded("", "ok\nextra", 5);
        assertEquals("ok extra\n", result);
    }
}
