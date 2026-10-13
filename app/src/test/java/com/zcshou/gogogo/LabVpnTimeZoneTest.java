package com.zcshou.gogogo;

import org.junit.Test;
import static org.junit.Assert.*;

public class LabVpnTimeZoneTest {
    @Test public void acceptsKnownIanaZonesNotOffsetsOrGuesses() {
        assertTrue(LabVpnTimeZone.valid("America/Los_Angeles"));
        assertTrue(LabVpnTimeZone.valid("Asia/Shanghai"));
        assertTrue(LabVpnTimeZone.valid("Europe/London"));
        assertFalse(LabVpnTimeZone.valid("PST"));
        assertFalse(LabVpnTimeZone.valid("GMT+8"));
        assertFalse(LabVpnTimeZone.valid("Etc/GMT+8"));
        assertFalse(LabVpnTimeZone.valid("../../../../"));
        assertFalse(LabVpnTimeZone.valid(""));
    }

    @Test public void DSTChangesUSOffsetWithoutChangingZoneId() {
        // UTC noon in January vs July 2026
        String winter = LabVpnTimeZone.display("America/Los_Angeles", 1767268800000L);
        String summer = LabVpnTimeZone.display("America/Los_Angeles", 1782907200000L);
        assertTrue(winter.contains("UTC-08:00"));
        assertTrue(summer.contains("UTC-07:00"));
        assertTrue(summer.contains("夏令时"));
        assertFalse(winter.contains("夏令时"));
    }

    @Test public void invalidTimeZoneNotPresentedAsValid() {
        assertTrue(LabVpnTimeZone.display("UNKNOWN", 0L).contains("尚未识别"));
    }
}
