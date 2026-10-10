package com.zcshou.gogogo;

import org.junit.Test;

import java.util.Collections;

import static com.zcshou.gogogo.LabEvidenceTimeWindow.Relation.AFTER_START;
import static com.zcshou.gogogo.LabEvidenceTimeWindow.Relation.BEFORE_START;
import static com.zcshou.gogogo.LabEvidenceTimeWindow.Relation.UNKNOWN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LabEvidenceTimeWindowTest {
    private static final long START = 1_000_000L;
    private static final long NOW = 1_100_000L;

    @Test public void oldEventIsRecognizedAsPriorRecord() {
        assertEquals(BEFORE_START, LabEvidenceTimeWindow.classify(START, START - 1, NOW));
    }

    @Test public void eventAtServiceStartIsInWindow() {
        assertEquals(AFTER_START, LabEvidenceTimeWindow.classify(START, START, NOW));
    }

    @Test public void laterEventIsInTimeWindowOnly() {
        assertEquals(AFTER_START, LabEvidenceTimeWindow.classify(START, START + 500, NOW));
        assertTrue(LabEvidenceTimeWindow.label(AFTER_START).contains("时间在最近一次服务启动之后"));
    }

    @Test public void noServiceStartMakesAgeUnverifiable() {
        assertEquals(UNKNOWN, LabEvidenceTimeWindow.classify(0L, START + 1, NOW));
    }

    @Test public void noEventTimestampMakesAgeUnverifiable() {
        assertEquals(UNKNOWN, LabEvidenceTimeWindow.classify(START, 0L, NOW));
    }

    @Test public void clockMovedBackwardsDoesNotForgeFreshEvidence() {
        assertEquals(UNKNOWN, LabEvidenceTimeWindow.classify(START, NOW + 1, NOW));
        assertEquals(UNKNOWN, LabEvidenceTimeWindow.classify(NOW + 1, START, NOW));
    }

    @Test public void previousStartPendingDoesNotEscalateCurrentRecommendation() {
        LabRecoveryTriage.Snapshot recovery = snapshot("PENDING", BEFORE_START,
                "DISABLE_SUCCEEDED_STOP", BEFORE_START);
        Lab33DiagnosticBundle.Priority priority =
                Lab33DiagnosticBundle.priority(ready(), recovery);
        assertEquals(Lab33DiagnosticBundle.Priority.VERIFY_CONSUMERS, priority);
        String rendered = LabRecoveryTriage.render(recovery);
        assertTrue(rendered.contains("早于最近一次服务启动（旧记录）"));
        assertTrue(rendered.contains("不能作为最近一轮清理失败的证据"));
        assertFalse(rendered.contains("存在未完成或失败的清理 API 记录"));
    }

    @Test public void oldGmsFailureDoesNotEscalateCurrentRecommendation() {
        assertEquals(Lab33DiagnosticBundle.Priority.VERIFY_CONSUMERS,
                Lab33DiagnosticBundle.priority(ready(), snapshot(
                        "REMOVE_RETURNED", AFTER_START,
                        "DISABLE_FAILED_RuntimeException", BEFORE_START)));
        String rendered = LabRecoveryTriage.render(snapshot(
                "REMOVE_RETURNED", AFTER_START,
                "DISABLE_REQUESTED_STOP", BEFORE_START));
        assertFalse(rendered.contains("GMS 关闭没有成功回调证据"));
        assertTrue(rendered.contains("早于最近一次服务启动（旧记录）"));
    }

    @Test public void inWindowFailuresStillNeedInvestigation() {
        assertEquals(Lab33DiagnosticBundle.Priority.REVIEW_RECOVERY,
                Lab33DiagnosticBundle.priority(ready(), snapshot(
                        "SECURITY_DENIED", AFTER_START, "ENABLE_SUCCEEDED", AFTER_START)));
        assertEquals(Lab33DiagnosticBundle.Priority.REVIEW_RECOVERY,
                Lab33DiagnosticBundle.priority(ready(), snapshot(
                        "ALREADY_ABSENT", AFTER_START, "DISABLE_REQUESTED_STOP", AFTER_START)));
    }

    @Test public void uncertainTimesCannotBeTreatedAsOldEvidence() {
        assertEquals(Lab33DiagnosticBundle.Priority.REVIEW_RECOVERY,
                Lab33DiagnosticBundle.priority(ready(), snapshot(
                        "PENDING", UNKNOWN, "DISABLE_REQUESTED_STOP", UNKNOWN)));
    }

    @Test public void priorGmsSuccessCannotProveCurrentSuccess() {
        String rendered = LabRecoveryTriage.render(snapshot(
                "ALREADY_ABSENT", BEFORE_START, "DISABLE_SUCCEEDED_STOP", BEFORE_START));
        assertTrue(rendered.contains("历史异步回调"));
        assertTrue(rendered.contains("旧记录"));
        assertTrue(rendered.contains("不能证明微信"));
    }

    private static LabQuickCheckReport.Snapshot ready() {
        return new LabQuickCheckReport.Snapshot(
                33, LabQuickCheckReport.Signal.YES, LabQuickCheckReport.Signal.YES,
                LabQuickCheckReport.Signal.YES, LabQuickCheckReport.Signal.YES,
                LabQuickCheckReport.Signal.NO, false);
    }

    private static LabRecoveryTriage.Snapshot snapshot(
            String outcome, LabEvidenceTimeWindow.Relation providerWindow,
            String gms, LabEvidenceTimeWindow.Relation gmsWindow) {
        return new LabRecoveryTriage.Snapshot(LabRecoveryTriage.Lifecycle.NORMAL_STOP, false,
                Collections.singletonList(new LabRecoveryTriage.ProviderRow(
                        "GPS", "服务清理", outcome, null, providerWindow)), gms, gmsWindow);
    }
}
