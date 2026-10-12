package com.zcshou.gogogo;

import org.junit.Test;
import static org.junit.Assert.*;

public class LabSimAttributionTest {
    @Test public void onlySevenDigitChinaMobileSegmentIsAccepted() {
        assertTrue(LabSimAttribution.validSegment("1380013"));
        assertTrue(LabSimAttribution.validSegment("1991234"));
        assertFalse(LabSimAttribution.validSegment("13800138000"));
        assertFalse(LabSimAttribution.validSegment("138001"));
        assertFalse(LabSimAttribution.validSegment("1280013"));
        assertFalse(LabSimAttribution.validSegment("138001X"));
        assertFalse(LabSimAttribution.validSegment(""));
        assertFalse(LabSimAttribution.validSegment(null));
    }

    @Test public void simCountryIsDistinctFromPhoneNumberRegistration() {
        assertTrue(LabSimAttribution.country("cn").contains("中国"));
        assertTrue(LabSimAttribution.country("us").contains("美国"));
        assertEquals("未知", LabSimAttribution.country(""));
        String report = LabSimAttribution.displaySegmentResult(
                "广东", "深圳", "中国移动", "202502");
        assertTrue(report.contains("广东"));
        assertTrue(report.contains("深圳"));
        assertTrue(report.contains("非实时位置"));
        assertTrue(report.contains("携号转网"));
        assertFalse(report.contains("13800138000"));
    }
}
