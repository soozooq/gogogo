package com.zcshou.gogogo;

import org.junit.Test;
import static org.junit.Assert.*;

public class LabReleaseAcceptanceSummaryTest {
    @Test public void noServiceDoesNotBecomePass() {
        String r=LabReleaseAcceptanceSummary.unavailable().render();
        assertTrue(r.contains("SERVICE=UNKNOWN"));
        assertTrue(r.contains("FRAMEWORK_GPS=UNKNOWN"));
        assertTrue(r.contains("E2_GMS_TASK_OK=UNKNOWN"));
        assertTrue(r.contains("REAL_DEVICE_RELEASE_ACCEPTANCE=PENDING"));
    }

    @Test public void observedTasksNeverPromoteWeChatOrTencent() {
        LabGmsPublishEvidence gms=new LabGmsPublishEvidence();
        gms.onTaskReturned();gms.onTaskCompleted(true);
        LabPublishPerformanceEvidence perf=new LabPublishPerformanceEvidence();
        perf.onTick(1000);perf.onTick(1050);
        perf.onTaskReturned();perf.onTaskFinished(true,1000,1033);
        String r=LabReleaseAcceptanceSummary.observed(true,
                100,0,100,1,90,0,true,gms.snapshot(),perf.snapshot()).render();
        assertTrue(r.contains("E1_GMS_TASK_RETURNED=OBSERVED"));
        assertTrue(r.contains("E2_GMS_TASK_OK=OBSERVED"));
        assertTrue(r.contains("E3_OWN_CONSUMER_FIX=NOT_CHECKED"));
        assertTrue(r.contains("E4_TENCENT_SDK_FIX=NOT_CHECKED"));
        assertTrue(r.contains("E5_WECHAT_DISPLAY=NOT_CHECKED"));
        assertTrue(r.contains("REAL_DEVICE_RELEASE_ACCEPTANCE=PENDING"));
    }

    @Test public void failedOrPendingTaskIsNotOk() {
        LabGmsPublishEvidence gms=new LabGmsPublishEvidence();
        gms.onTaskReturned();gms.onTaskCompleted(false);
        gms.onTaskReturned();
        LabPublishPerformanceEvidence perf=new LabPublishPerformanceEvidence();
        String r=LabReleaseAcceptanceSummary.observed(false,
                0,2,0,0,0,0,false,gms.snapshot(),perf.snapshot()).render();
        assertTrue(r.contains("GMS_TASK=returned:2 completed_ok:0 failed:1 pending:1"));
        assertTrue(r.contains("E2_GMS_TASK_OK=NOT_OBSERVED"));
        assertTrue(r.contains("MOCK_POLICY=PAUSED"));
    }

    @Test public void unknownRateIsLabelledUnknown() {
        LabGmsPublishEvidence gms=new LabGmsPublishEvidence();
        LabPublishPerformanceEvidence perf=new LabPublishPerformanceEvidence();
        String r=LabReleaseAcceptanceSummary.observed(true,
                0,0,0,0,0,0,false,gms.snapshot(),perf.snapshot()).render();
        assertTrue(r.contains("ACTUAL_TICK_HZ=UNKNOWN"));
        assertTrue(r.contains("GMS_CALLBACK_MEAN_MS=UNKNOWN"));
        assertFalse(r.contains("NaN"));
    }

    @Test public void reportNeverContainsRawSensitiveSources() {
        LabGmsPublishEvidence gms=new LabGmsPublishEvidence();
        LabPublishPerformanceEvidence perf=new LabPublishPerformanceEvidence();
        String r=LabReleaseAcceptanceSummary.observed(true,
                999999L,12,43,0,32,0,true,gms.snapshot(),perf.snapshot()).render();
        assertFalse(r.contains("latitude="));
        assertFalse(r.contains("longitude="));
        assertFalse(r.contains("com.tencent.mm"));
        assertFalse(r.contains("UserInfo"));
        assertFalse(r.contains("SSID="));
        assertFalse(r.contains("TencentMapSDK="));
        assertTrue(r.contains("SCHEMA=RC1_COUNTERS_NO_COORDINATES"));
    }
}
