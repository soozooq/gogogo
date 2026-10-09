package com.zcshou.gogogo;

import org.junit.Test;

import static com.zcshou.gogogo.LabQuickCheckReport.Signal.NO;
import static com.zcshou.gogogo.LabQuickCheckReport.Signal.UNKNOWN;
import static com.zcshou.gogogo.LabQuickCheckReport.Signal.YES;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LabQuickCheckReportTest {
    private static LabQuickCheckReport.Snapshot signals(
            LabQuickCheckReport.Signal location,
            LabQuickCheckReport.Signal fine,
            LabQuickCheckReport.Signal coarse,
            LabQuickCheckReport.Signal mock,
            LabQuickCheckReport.Signal shizuku, boolean running) {
        return new LabQuickCheckReport.Snapshot(
                33, location, fine, coarse, mock, shizuku, running);
    }

    @Test public void missingSystemLocationIsFirstAction() {
        String s = LabQuickCheckReport.render(signals(NO, NO, NO, NO, NO, false));
        assertTrue(s.contains("先在系统设置里打开定位"));
    }

    @Test public void approximateOnlyDoesNotClaimPrecisePermission() {
        String s = LabQuickCheckReport.render(signals(YES, NO, YES, YES, NO, false));
        assertTrue(s.contains("只有近似定位权限"));
        assertFalse(s.contains("精确定位已授权"));
        assertTrue(s.contains("检查定位权限"));
    }

    @Test public void appOpsDenialIsNotReportedAsReadiness() {
        String s = LabQuickCheckReport.render(signals(YES, YES, YES, NO, NO, true));
        assertTrue(s.contains("模拟位置 AppOps 授权：未授权"));
        assertTrue(s.contains("选择模拟位置信息应用"));
    }

    @Test public void unknownSignalsRemainUnknown() {
        String s = LabQuickCheckReport.render(signals(UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN,
                UNKNOWN, false));
        assertTrue(s.contains("无法确认"));
        assertTrue(s.contains("有基础状态无法确认"));
    }

    @Test public void activeFlagCannotCertifyThirdPartyConsumer() {
        String s = LabQuickCheckReport.render(signals(YES, YES, YES, YES, NO, true));
        assertTrue(s.contains("可打开 Consumer Matrix"));
        assertTrue(s.contains("本进程的标记"));
        assertTrue(s.contains("不验证第三方应用"));
    }

    @Test public void optionalShizukuDoesNotBlockReadiness() {
        String s = LabQuickCheckReport.render(signals(YES, YES, YES, YES, NO, false));
        assertTrue(s.contains("基础条件具备"));
        assertTrue(s.contains("Shizuku Binder（可选）：未连接"));
    }

    @Test public void reportContainsNoDynamicIdentifiersOrPositions() {
        String s = LabQuickCheckReport.render(signals(YES, YES, YES, YES, YES, false));
        assertFalse(s.contains("经度："));
        assertFalse(s.contains("纬度："));
        assertFalse(s.contains("SSID:"));
        assertFalse(s.contains("IP:"));
        assertFalse(s.contains("PID:"));
        assertTrue(s.contains("不包含坐标、IP"));
    }
}
