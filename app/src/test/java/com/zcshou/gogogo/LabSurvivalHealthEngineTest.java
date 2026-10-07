package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LabSurvivalHealthEngineTest {

    @Test
    public void stableExemptSessionScoresHighly() {
        LabCompatibilitySessionRecorder recorder =
                new LabCompatibilitySessionRecorder();

        recorder.start(1000L);
        for (int i = 0; i < 80; i++) {
            long t = 1000L + i * 500L;
            recorder.addHeartbeatMain(t, i, 125, false, true, false, true);
            recorder.addHeartbeatBackground(t, i);
            recorder.addLocation(t, "GPS", 35.0, 139.0, 1f, 0f, 0f, true);
            recorder.addLocation(t + 10, "NETWORK", 35.0, 139.0, 2f, 0f, 0f, true);
            recorder.addLocation(t + 20, "GMS_UPDATES", 35.0, 139.0, 1f, 0f, 0f, true);
        }
        recorder.stop(42000L);

        LabSurvivalHealthEngine.Report report =
                LabSurvivalHealthEngine.evaluate(
                        true,
                        false,
                        recorder.summarize(),
                        null);

        assertTrue(report.score >= 90);
        assertTrue(
                "EXCELLENT".equals(report.level)
                        || "STABLE_WITH_MINOR_JITTER".equals(report.level));
    }

    @Test
    public void batteryNotExemptCreatesHighRiskPenalty() {
        LabSurvivalHealthEngine.Report report =
                LabSurvivalHealthEngine.evaluate(
                        false,
                        false,
                        null,
                        null);

        assertTrue(report.score <= 60);
        assertTrue(report.findings.stream()
                .anyMatch(v -> v.contains("电池优化未豁免")));
    }

    @Test
    public void persistedMinorJitterIsNotTreatedLikeSevereFreeze() {
        LabAutoExperimentStore.Result wechat =
                new LabAutoExperimentStore.Result(
                        LabAutoExperimentStore.TYPE_WECHAT,
                        true,
                        1L,
                        60000L,
                        1218L,
                        815L,
                        "NO_LONG_GAP",
                        "STABLE",
                        true,
                        0);
        LabAutoExperimentStore.Result control =
                new LabAutoExperimentStore.Result(
                        LabAutoExperimentStore.TYPE_CONTROL,
                        true,
                        2L,
                        60000L,
                        1620L,
                        1198L,
                        "NO_LONG_GAP",
                        "PARTIAL",
                        true,
                        0);

        LabSurvivalHealthEngine.Report report =
                LabSurvivalHealthEngine.evaluate(
                        true,
                        false,
                        null,
                        LabAutoExperimentStore.compare(wechat, control));

        assertTrue(report.score >= 85);
        assertTrue(
                "STABLE_WITH_MINOR_JITTER".equals(report.level)
                        || "EXCELLENT".equals(report.level));
    }

    @Test
    public void sixtySecondFreezeScoresSevere() {
        LabCompatibilitySessionRecorder recorder =
                new LabCompatibilitySessionRecorder();

        recorder.start(1000L);
        recorder.addHeartbeatMain(1000L, 1L, 100, false, true, false, false);
        recorder.addHeartbeatBackground(1000L, 1L);
        recorder.addLocation(1000L, "GPS", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addLocation(1000L, "NETWORK", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addLocation(1000L, "GMS_UPDATES", 35.0, 139.0, 1f, 0f, 0f, true);

        recorder.addHeartbeatMain(62000L, 2L, 100, false, true, false, false);
        recorder.addHeartbeatBackground(62000L, 2L);
        recorder.addLocation(62000L, "GPS", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addLocation(62000L, "NETWORK", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addLocation(62000L, "GMS_UPDATES", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.stop(63000L);

        LabSurvivalHealthEngine.Report report =
                LabSurvivalHealthEngine.evaluate(
                        false,
                        false,
                        recorder.summarize(),
                        null);

        assertTrue(report.score < 50);
        assertEquals("SEVERE", report.level);
    }
}
