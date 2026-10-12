package com.zcshou.gogogo;

import org.junit.Test;
import static org.junit.Assert.*;

public class LabCrossAppEvidenceTest {
    @Test public void labelsDoNotProveSdkRoute() {
        String s=LabCrossAppEvidence.describe(0,0,true,"");
        assertTrue(s.contains("抖音发送位置"));
        assertTrue(s.contains("蓝点和附近地点均为国内实际地区"));
        assertTrue(s.contains("USER_REPORTED_NOT_SDK_PROVEN"));
        assertFalse(s.contains("AMAP_SDK_CONFIRMED"));
    }

    @Test public void unknownAndLongNotesBounded() {
        assertTrue(LabCrossAppEvidence.describe(-1,10,false,null).contains("UNKNOWN"));
        StringBuilder input=new StringBuilder();
        for(int i=0;i<700;i++)input.append('x');
        String out=LabCrossAppEvidence.describe(0,0,true,input.toString());
        assertTrue(out.length()<650);
        assertTrue(out.contains("no_raw_coordinates=true"));
    }

    @Test public void redactCommonSecretsFromNotes() {
        String s = LabCrossAppEvidence.describe(0, 0, true,
                "定位 38.0177934,-121.8162185 手机 13800138000 abc@example.com");
        assertFalse(s.contains("38.0177934"));
        assertFalse(s.contains("13800138000"));
        assertFalse(s.contains("abc@example.com"));
        assertTrue(s.contains("[COORD_REDACTED]"));
    }

    @Test public void noMisattributionOfPoiAndDot() {
        assertNotEquals(LabCrossAppEvidence.OBSERVATIONS[0],
                LabCrossAppEvidence.OBSERVATIONS[2]);
    }
}
