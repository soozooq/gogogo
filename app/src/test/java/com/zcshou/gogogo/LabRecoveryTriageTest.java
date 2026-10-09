package com.zcshou.gogogo;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LabRecoveryTriageTest {
    private static LabRecoveryTriage.ProviderRow row(
            String outcome, String previous) {
        return new LabRecoveryTriage.ProviderRow("GPS", "启动扫尾", outcome, previous);
    }

    private static LabRecoveryTriage.Snapshot sample(
            LabRecoveryTriage.Lifecycle lifecycle, boolean running,
            LabRecoveryTriage.ProviderRow evidence, String gms) {
        return new LabRecoveryTriage.Snapshot(lifecycle, running,
                evidence == null ? Collections.emptyList() :
                        Collections.singletonList(evidence), gms);
    }

    @Test public void noEvidenceDoesNotClaimHealthySystem() {
        String output = LabRecoveryTriage.render(sample(
                LabRecoveryTriage.Lifecycle.NO_RECORD, false, null, null));
        assertTrue(output.contains("无任何 Provider 清理证据"));
        assertTrue(output.contains("缺少清理取证数据"));
        assertFalse(output.contains("已完全修复"));
    }

    @Test public void pendingOutcomeIsNotReportedAsCleanupSuccess() {
        String output = LabRecoveryTriage.render(sample(
                LabRecoveryTriage.Lifecycle.NORMAL_STOP, false, row("PENDING", null), null));
        assertTrue(output.contains("PENDING（没有完成结果）"));
        assertTrue(output.contains("未完成或失败"));
    }

    @Test public void failedApiStillRequiresManualEvidence() {
        String output = LabRecoveryTriage.render(sample(
                LabRecoveryTriage.Lifecycle.NORMAL_STOP, false,
                row("SECURITY_DENIED", null), null));
        assertTrue(output.contains("权限拒绝"));
        assertTrue(output.contains("不要仅凭记录认定残留"));
    }

    @Test public void olderPendingIsNotCurrentFailure() {
        String output = LabRecoveryTriage.render(sample(
                LabRecoveryTriage.Lifecycle.NORMAL_STOP, false,
                row("REMOVE_RETURNED", "PENDING"), "DISABLE_SUCCEEDED_NORMAL"));
        assertTrue(output.contains("更早一次曾 PENDING"));
        assertTrue(output.contains("不代表目前仍有 Provider 残留"));
        assertFalse(output.contains("存在未完成或失败的清理 API 记录"));
    }

    @Test public void gmsDisableRequestedIsNotSuccess() {
        assertEquals(LabRecoveryTriage.Gms.DISABLE_REQUESTED,
                LabRecoveryTriage.gms("DISABLE_REQUESTED_NORMAL"));
        String output = LabRecoveryTriage.render(sample(
                LabRecoveryTriage.Lifecycle.INTERRUPTED_CANDIDATE,
                false, row("ALREADY_ABSENT", "NONE"), "DISABLE_REQUESTED_NORMAL"));
        assertTrue(output.contains("尚未证实关闭成功"));
        assertTrue(output.contains("GMS 关闭没有成功回调证据"));
    }

    @Test public void failedAndEnableEventsCannotMasqueradeAsDisable() {
        assertEquals(LabRecoveryTriage.Gms.DISABLE_FAILED,
                LabRecoveryTriage.gms("DISABLE_BLOCKED_NO_LOCATION_PERMISSION_SHUTDOWN"));
        assertEquals(LabRecoveryTriage.Gms.DISABLE_FAILED,
                LabRecoveryTriage.gms("DISABLE_EXCEPTION_RuntimeException"));
        assertEquals(LabRecoveryTriage.Gms.ENABLE_OR_OTHER,
                LabRecoveryTriage.gms("ENABLE_SUCCEEDED"));
        assertEquals(LabRecoveryTriage.Gms.ENABLE_OR_OTHER,
                LabRecoveryTriage.gms("LATE_ENABLE_AFTER_STOP"));
    }

    @Test public void runningServiceNeverSuggestsDestructiveCleanup() {
        String output = LabRecoveryTriage.render(sample(
                LabRecoveryTriage.Lifecycle.RUNNING, true,
                row("OTHER_FAILURE", null), null));
        assertTrue(output.contains("不要在取证页强行移除 Provider"));
    }

    @Test public void uncertainLifecycleIsNotCalledCrash() {
        String output = LabRecoveryTriage.render(sample(
                LabRecoveryTriage.Lifecycle.REBOOT_UNCERTAIN, false,
                row("ALREADY_ABSENT", null), null));
        assertTrue(output.contains("可能经历重启或时钟变化"));
        assertFalse(output.contains("确定发生崩溃"));
    }

    @Test public void reportDoesNotIncludeIdentifyingValues() {
        String output = LabRecoveryTriage.render(new LabRecoveryTriage.Snapshot(
                LabRecoveryTriage.Lifecycle.NORMAL_STOP, false,
                Arrays.asList(row("REMOVE_RETURNED", null),
                        new LabRecoveryTriage.ProviderRow("NETWORK", "服务清理",
                                "ALREADY_ABSENT", null)), "DISABLE_SUCCEEDED_SHUTDOWN"));
        assertFalse(output.contains("SSID:"));
        assertFalse(output.contains("PID:"));
        assertFalse(output.contains("经度："));
        assertFalse(output.contains("纬度："));
        assertTrue(output.contains("历史记录"));
    }

    @Test public void summaryCompareDoesNotClaimSystemCertainty() {
        assertTrue(LabRecoveryTriage.compare("a\nb", "a\nc")
                .contains("发现 1 行摘要差异"));
        assertTrue(LabRecoveryTriage.compare("a\nb", "a\nb")
                .contains("并不代表实际系统状态全部正常"));
        assertTrue(LabRecoveryTriage.compare(null, "a").contains("没有基准"));
    }

    @Test public void unknownProviderOutcomeIsNotSuccess() {
        assertEquals(LabRecoveryTriage.Cleanup.UNRECOGNIZED,
                LabRecoveryTriage.cleanup("NEW_OUTCOME"));
        String output = LabRecoveryTriage.render(sample(
                LabRecoveryTriage.Lifecycle.NORMAL_STOP, false,
                row("NEW_OUTCOME", null), null));
        assertTrue(output.contains("未知结果"));
        assertTrue(output.contains("未完成或失败"));
    }
}
