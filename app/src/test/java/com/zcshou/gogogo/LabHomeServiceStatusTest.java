package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LabHomeServiceStatusTest {
    @Test public void appLaunchWhenStoppedDoesNotClaimCleanupProved() {
        LabHomeServiceStatus.Summary s = LabHomeServiceStatus.render(
                false, LabHomeServiceStatus.Request.NONE, false);
        assertEquals(LabHomeServiceStatus.Tone.NEUTRAL, s.tone);
        assertTrue(s.text.contains("STOPPED"));
        assertFalse(s.text.contains("清理完成"));
    }

    @Test public void appLaunchWhenRunningDoesNotClaimConsumersAcceptedMock() {
        LabHomeServiceStatus.Summary s = LabHomeServiceStatus.render(
                true, LabHomeServiceStatus.Request.NONE, false);
        assertEquals(LabHomeServiceStatus.Tone.INFO, s.tone);
        assertTrue(s.text.contains("RUNNING"));
        assertTrue(s.text.contains("不代表第三方应用"));
    }

    @Test public void pendingStartIsNotReportedAsSuccess() {
        LabHomeServiceStatus.Summary s = LabHomeServiceStatus.render(
                false, LabHomeServiceStatus.Request.START, false);
        assertEquals(LabHomeServiceStatus.Tone.INFO, s.tone);
        assertTrue(s.text.contains("尚未确认"));
        assertFalse(s.text.contains("已启动成功"));
    }

    @Test public void observedRunningOnlyReportsProcessFlag() {
        LabHomeServiceStatus.Summary s = LabHomeServiceStatus.render(
                true, LabHomeServiceStatus.Request.START, false);
        assertTrue(s.text.contains("RUNNING"));
        assertTrue(s.text.contains("仍需独立验证"));
        assertTrue(LabHomeServiceStatus.requestConfirmed(
                true, LabHomeServiceStatus.Request.START));
    }

    @Test public void startStillNotObservedAfterWindowShowsWarning() {
        LabHomeServiceStatus.Summary s = LabHomeServiceStatus.render(
                false, LabHomeServiceStatus.Request.START, true);
        assertEquals(LabHomeServiceStatus.Tone.WARNING, s.tone);
        assertTrue(s.text.contains("尚未观察到"));
        assertFalse(LabHomeServiceStatus.requestConfirmed(
                false, LabHomeServiceStatus.Request.START));
    }

    @Test public void pendingStopDoesNotClaimProviderReset() {
        LabHomeServiceStatus.Summary s = LabHomeServiceStatus.render(
                true, LabHomeServiceStatus.Request.STOP, false);
        assertEquals(LabHomeServiceStatus.Tone.INFO, s.tone);
        assertTrue(s.text.contains("RUNNING"));
        assertFalse(s.text.contains("成功关闭 GMS"));
    }

    @Test public void stopObservedDoesNotPromiseGmsReset() {
        LabHomeServiceStatus.Summary s = LabHomeServiceStatus.render(
                false, LabHomeServiceStatus.Request.STOP, true);
        assertEquals(LabHomeServiceStatus.Tone.NEUTRAL, s.tone);
        assertTrue(s.text.contains("Provider/GMS 清理结果仍需独立检查"));
        assertTrue(LabHomeServiceStatus.requestConfirmed(
                false, LabHomeServiceStatus.Request.STOP));
    }

    @Test public void stopStillRunningAfterWindowShowsWarning() {
        LabHomeServiceStatus.Summary s = LabHomeServiceStatus.render(
                true, LabHomeServiceStatus.Request.STOP, true);
        assertEquals(LabHomeServiceStatus.Tone.WARNING, s.tone);
        assertTrue(s.text.contains("仍显示 RUNNING"));
        assertFalse(LabHomeServiceStatus.requestConfirmed(
                true, LabHomeServiceStatus.Request.STOP));
    }

    @Test public void nullRequestUsesIdleObservationWithoutAssumingSuccess() {
        LabHomeServiceStatus.Summary s = LabHomeServiceStatus.render(
                false, null, true);
        assertEquals(LabHomeServiceStatus.Tone.NEUTRAL, s.tone);
        assertTrue(s.text.contains("STOPPED"));
        assertFalse(LabHomeServiceStatus.requestConfirmed(
                true, LabHomeServiceStatus.Request.NONE));
    }

    @Test public void sourceFlagDoesNotConfirmOppositeRequest() {
        assertFalse(LabHomeServiceStatus.requestConfirmed(
                true, LabHomeServiceStatus.Request.STOP));
        assertFalse(LabHomeServiceStatus.requestConfirmed(
                false, LabHomeServiceStatus.Request.START));
    }
}
