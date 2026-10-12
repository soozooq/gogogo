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

    @Test public void meituanScreenshotIsDifferentFromNetworkWideFailure() {
        assertEquals("美团首页 / 新增收货地址",
                LabCrossAppEvidence.APPLICATIONS[LabCrossAppEvidence.MEITUAN_INDEX]);
        String[] opts = LabCrossAppEvidence.observationOptions(
                LabCrossAppEvidence.MEITUAN_INDEX);
        assertTrue(opts[0].contains("地址列表提示网络不通畅"));
        assertTrue(opts[0].contains("蓝点在模拟地区"));
        String record = LabCrossAppEvidence.describe(
                LabCrossAppEvidence.MEITUAN_INDEX, 0, true, "");
        assertTrue(record.contains("美团"));
        assertTrue(record.contains("城市栏仍是国内旧城市"));
        assertTrue(record.contains("USER_REPORTED_NOT_SDK_PROVEN"));
        assertTrue(LabCrossAppEvidence.vpnComparison(0).contains("VPN 开启和关闭"));
        assertTrue(LabCrossAppEvidence.vpnComparison(-1).contains("UNKNOWN"));
    }

    @Test public void noMisattributionOfPoiAndDot() {
        assertNotEquals(LabCrossAppEvidence.OBSERVATIONS[0],
                LabCrossAppEvidence.OBSERVATIONS[2]);
    }
}
