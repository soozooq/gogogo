package com.zcshou.gogogo;

import org.junit.Test;

import static com.zcshou.gogogo.LabQuickCheckReport.Signal.NO;
import static com.zcshou.gogogo.LabQuickCheckReport.Signal.UNKNOWN;
import static com.zcshou.gogogo.LabQuickCheckReport.Signal.YES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LabHubReadinessTest {
    private LabQuickCheckReport.Snapshot device(
            LabQuickCheckReport.Signal location, LabQuickCheckReport.Signal fine,
            LabQuickCheckReport.Signal appOp, boolean service) {
        return new LabQuickCheckReport.Snapshot(33, location, fine,
                YES, appOp, UNKNOWN, service);
    }

    @Test public void noDataIsUnknownNotReady() {
        assertEquals(LabHubReadiness.Attention.UNKNOWN,
                LabHubReadiness.summarize(null).attention);
    }

    @Test public void systemLocationOffTakesPriority() {
        LabHubReadiness.Summary result =
                LabHubReadiness.summarize(device(NO, NO, NO, false));
        assertEquals(LabHubReadiness.Attention.NEED_ACTION, result.attention);
        assertTrue(result.headline.contains("系统定位"));
    }

    @Test public void coarseOnlyCannotPassFineLocationPrerequisite() {
        LabHubReadiness.Summary result =
                LabHubReadiness.summarize(device(YES, NO, YES, false));
        assertEquals(LabHubReadiness.Attention.NEED_ACTION, result.attention);
        assertTrue(result.detail.contains("近似定位"));
    }

    @Test public void mockAppOpDenialHasOwnAction() {
        LabHubReadiness.Summary result =
                LabHubReadiness.summarize(device(YES, YES, NO, true));
        assertEquals(LabHubReadiness.Attention.NEED_ACTION, result.attention);
        assertTrue(result.detail.contains("开发者选项"));
    }

    @Test public void unknownAuthorizationNeverClaimedGranted() {
        LabHubReadiness.Summary result =
                LabHubReadiness.summarize(device(YES, YES, UNKNOWN, true));
        assertEquals(LabHubReadiness.Attention.UNKNOWN, result.attention);
        assertFalse(result.headline.contains("全部就绪"));
    }

    @Test public void unknownSystemLocationNeverClaimsReady() {
        assertEquals(LabHubReadiness.Attention.UNKNOWN,
                LabHubReadiness.summarize(device(UNKNOWN, YES, YES, false)).attention);
    }

    @Test public void runningIsOnlyAFlagAndConsumersNeedIndependentVerification() {
        LabHubReadiness.Summary result =
                LabHubReadiness.summarize(device(YES, YES, YES, true));
        assertEquals(LabHubReadiness.Attention.VERIFY_CONSUMERS, result.attention);
        assertTrue(result.detail.contains("Consumer Matrix"));
        assertTrue(result.detail.contains("运行标记"));
        assertFalse(result.detail.contains("微信兼容成功"));
    }

    @Test public void stoppedServiceNeverClaimsMockCurrentlyRunning() {
        LabHubReadiness.Summary result =
                LabHubReadiness.summarize(device(YES, YES, YES, false));
        assertEquals(LabHubReadiness.Attention.VERIFY_CONSUMERS, result.attention);
        assertTrue(result.detail.contains("STOPPED"));
        assertTrue(result.detail.contains("手动启动"));
    }

    @Test public void optionalShizukuDoesNotBlockBasicPrerequisites() {
        LabHubReadiness.Summary result =
                LabHubReadiness.summarize(device(YES, YES, YES, false));
        assertEquals(LabHubReadiness.Attention.VERIFY_CONSUMERS, result.attention);
        assertFalse(result.detail.contains("Shizuku"));
    }
}
