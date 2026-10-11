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

    @Test public void boundEntryCannotInjectNewLines() {
        String result = LabSupportLogText.appendBounded("", "ok\nextra", 5);
        assertEquals("ok extra\n", result);
    }
}
