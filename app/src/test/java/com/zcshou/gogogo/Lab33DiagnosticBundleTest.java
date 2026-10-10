package com.zcshou.gogogo;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static com.zcshou.gogogo.LabQuickCheckReport.Signal.NO;
import static com.zcshou.gogogo.LabQuickCheckReport.Signal.UNKNOWN;
import static com.zcshou.gogogo.LabQuickCheckReport.Signal.YES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Lab33DiagnosticBundleTest {
    private LabQuickCheckReport.Snapshot quick(
            LabQuickCheckReport.Signal location, LabQuickCheckReport.Signal fine,
            LabQuickCheckReport.Signal coarse, LabQuickCheckReport.Signal appOps,
            boolean service) {
        return new LabQuickCheckReport.Snapshot(
                33, location, fine, coarse, appOps, NO, service);
    }

    private LabRecoveryTriage.Snapshot recovered(
            LabRecoveryTriage.Lifecycle lifecycle, String outcome, String gms) {
        return new LabRecoveryTriage.Snapshot(lifecycle, false,
                Collections.singletonList(new LabRecoveryTriage.ProviderRow(
                        "GPS", "服务清理", outcome, null)), gms);
    }

    @Test public void locationOffTakesPrecedenceOverHistoricalSuccess() {
        assertEquals(Lab33DiagnosticBundle.Priority.FIX_PREREQUISITES,
                Lab33DiagnosticBundle.priority(quick(NO, YES, YES, YES, false),
                        recovered(LabRecoveryTriage.Lifecycle.NORMAL_STOP,
                                "REMOVE_RETURNED", "DISABLE_SUCCEEDED_STOP")));
    }

    @Test public void approximateOnlyCannotClaimPrerequisitesReady() {
        assertEquals(Lab33DiagnosticBundle.Priority.FIX_PREREQUISITES,
                Lab33DiagnosticBundle.priority(quick(YES, NO, YES, YES, false),
                        recovered(LabRecoveryTriage.Lifecycle.NORMAL_STOP,
                                "ALREADY_ABSENT", "DISABLE_SUCCEEDED_STOP")));
    }

    @Test public void unknownMockAuthorizationRemainsUnverified() {
        assertEquals(Lab33DiagnosticBundle.Priority.FIX_PREREQUISITES,
                Lab33DiagnosticBundle.priority(quick(YES, YES, YES, UNKNOWN, false),
                        recovered(LabRecoveryTriage.Lifecycle.NORMAL_STOP,
                                "REMOVE_RETURNED", null)));
    }

    @Test public void pendingProviderPrioritizesRecovery() {
        assertEquals(Lab33DiagnosticBundle.Priority.REVIEW_RECOVERY,
                Lab33DiagnosticBundle.priority(quick(YES, YES, YES, YES, false),
                        recovered(LabRecoveryTriage.Lifecycle.NORMAL_STOP,
                                "PENDING", null)));
    }

    @Test public void interruptedSessionIsNotAutomaticallyHealthy() {
        assertEquals(Lab33DiagnosticBundle.Priority.REVIEW_RECOVERY,
                Lab33DiagnosticBundle.priority(quick(YES, YES, YES, YES, false),
                        recovered(LabRecoveryTriage.Lifecycle.INTERRUPTED_CANDIDATE,
                                "REMOVE_RETURNED", null)));
    }

    @Test public void requestedGmsShutdownDoesNotCountAsConfirmation() {
        assertEquals(Lab33DiagnosticBundle.Priority.REVIEW_RECOVERY,
                Lab33DiagnosticBundle.priority(quick(YES, YES, YES, YES, false),
                        recovered(LabRecoveryTriage.Lifecycle.NORMAL_STOP,
                                "ALREADY_ABSENT", "DISABLE_REQUESTED_STOP")));
    }

    @Test public void prerequisitesClearMeansVerifyConsumersNotCompleteSuccess() {
        LabQuickCheckReport.Snapshot q = quick(YES, YES, YES, YES, true);
        LabRecoveryTriage.Snapshot r = recovered(
                LabRecoveryTriage.Lifecycle.RUNNING, "ALREADY_ABSENT", null);
        assertEquals(Lab33DiagnosticBundle.Priority.VERIFY_CONSUMERS,
                Lab33DiagnosticBundle.priority(q, r));
        String report = Lab33DiagnosticBundle.render(q, r);
        assertTrue(report.contains("Consumer Matrix"));
        assertTrue(report.contains("没有实时读取或验证微信"));
        assertFalse(report.contains("微信兼容已验证"));
    }

    @Test public void missingRecoveryDoesNotClaimThatProviderWasRemoved() {
        LabQuickCheckReport.Snapshot q = quick(YES, YES, YES, YES, false);
        assertEquals(Lab33DiagnosticBundle.Priority.REVIEW_RECOVERY,
                Lab33DiagnosticBundle.priority(q, null));
        String report = Lab33DiagnosticBundle.render(q, null);
        assertTrue(report.contains("暂时没有可供判断"));
        assertTrue(report.contains("历史 REMOVE_RETURNED"));
    }

    @Test public void reportUsesOnlyStructuredStatesAndNoDynamicLocation() {
        String report = Lab33DiagnosticBundle.render(
                quick(YES, YES, YES, YES, false),
                new LabRecoveryTriage.Snapshot(
                        LabRecoveryTriage.Lifecycle.NORMAL_STOP, false,
                        Arrays.asList(
                                new LabRecoveryTriage.ProviderRow("GPS", "启动扫尾",
                                        "REMOVE_RETURNED", "PENDING"),
                                new LabRecoveryTriage.ProviderRow("NETWORK", "服务清理",
                                        "ALREADY_ABSENT", null)),
                        "DISABLE_SUCCEEDED_STOP"));
        assertTrue(report.contains("基础条件（Lab 31）"));
        assertTrue(report.contains("退出及清理历史（Lab 32）"));
        assertFalse(report.contains("经度："));
        assertFalse(report.contains("纬度："));
        assertFalse(report.contains("SSID:"));
        assertFalse(report.contains("PID:"));
    }

    @Test public void compareOnlyClaimsAChangedSummary() {
        assertTrue(Lab33DiagnosticBundle.compare("状态A\n状态B", "状态A\n状态C")
                .contains("发现 1 行摘要差异"));
        assertTrue(Lab33DiagnosticBundle.compare("same", "same")
                .contains("并不代表实际系统状态全部正常"));
    }
}
