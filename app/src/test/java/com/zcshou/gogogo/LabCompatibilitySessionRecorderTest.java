package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LabCompatibilitySessionRecorderTest {

    @Test
    public void stableThreeStreamSessionGradesStable() {
        LabCompatibilitySessionRecorder recorder =
                new LabCompatibilitySessionRecorder();

        recorder.start(1000L);

        for (int i = 0; i < 6; i++) {
            long t = 1000L + i * 250L;
            recorder.addLocation(
                    t, "GPS", 35.0, 139.0, 1f, 0f, 90f, true);
            recorder.addLocation(
                    t + 10L, "NETWORK", 35.000001, 139.0, 2f, 0f, 90f, true);
            recorder.addLocation(
                    t + 20L, "GMS_UPDATES", 35.0, 139.000001, 1f, 0f, 90f, true);
        }

        recorder.addMarker(1800L, "UI_BACKGROUND");
        recorder.addMarker(2200L, "UI_FOREGROUND");
        recorder.stop(2500L);

        LabCompatibilitySessionRecorder.Summary summary = recorder.summarize();

        assertEquals("STABLE", summary.grade());
        assertEquals(6, summary.gps.count);
        assertEquals(6, summary.network.count);
        assertEquals(6, summary.gms.count);
        assertTrue(summary.gps.maxGapMs <= 300L);
        assertEquals(1, summary.backgroundMarkers);
        assertEquals(1, summary.foregroundMarkers);
        assertEquals(18, summary.mockMarkedLocations);
    }

    @Test
    public void longGapPreventsStableGrade() {
        LabCompatibilitySessionRecorder recorder =
                new LabCompatibilitySessionRecorder();

        recorder.start(1000L);
        recorder.addLocation(1000L, "GPS", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addLocation(1000L, "NETWORK", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addLocation(1000L, "GMS_UPDATES", 35.0, 139.0, 1f, 0f, 0f, true);

        recorder.addLocation(4000L, "GPS", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addLocation(4000L, "NETWORK", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addLocation(4000L, "GMS_UPDATES", 35.0, 139.0, 1f, 0f, 0f, true);

        LabCompatibilitySessionRecorder.Summary summary = recorder.summarize();

        assertEquals("PARTIAL", summary.grade());
        assertEquals(3000L, summary.gps.maxGapMs);
    }

    @Test
    public void errorPreventsStableGrade() {
        LabCompatibilitySessionRecorder recorder =
                new LabCompatibilitySessionRecorder();

        recorder.start(1000L);
        recorder.addLocation(1000L, "GPS", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addLocation(1000L, "NETWORK", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addLocation(1000L, "GMS_UPDATES", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addError(1100L, "GMS_UPDATES", "TEST_ERROR");

        assertEquals(1, recorder.summarize().errorCount);
        assertEquals("PARTIAL", recorder.summarize().grade());
    }

    @Test
    public void heartbeatGapMatchingLocationGapLooksLikeProcessFreeze() {
        LabCompatibilitySessionRecorder recorder =
                new LabCompatibilitySessionRecorder();

        recorder.start(1000L);

        recorder.addHeartbeatMain(1000L, 10L, 125, false, true, false, false);
        recorder.addHeartbeatBackground(1000L, 10L);
        recorder.addLocation(1000L, "GPS", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addLocation(1000L, "NETWORK", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addLocation(1000L, "GMS_UPDATES", 35.0, 139.0, 1f, 0f, 0f, true);

        recorder.addHeartbeatMain(52000L, 20L, 125, false, true, false, false);
        recorder.addHeartbeatBackground(52000L, 20L);
        recorder.addLocation(52000L, "GPS", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addLocation(52000L, "NETWORK", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addLocation(52000L, "GMS_UPDATES", 35.0, 139.0, 1f, 0f, 0f, true);

        LabCompatibilitySessionRecorder.Summary summary = recorder.summarize();

        assertEquals("PROCESS_OR_SCHEDULER_FREEZE", summary.freezeDiagnosis());
        assertEquals(51000L, summary.heartbeat.maxGapMs);
    }

    @Test
    public void healthyHeartbeatWithLocationGapLooksLikeLocationThrottle() {
        LabCompatibilitySessionRecorder recorder =
                new LabCompatibilitySessionRecorder();

        recorder.start(1000L);
        recorder.addLocation(1000L, "GPS", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addLocation(1000L, "NETWORK", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addLocation(1000L, "GMS_UPDATES", 35.0, 139.0, 1f, 0f, 0f, true);

        for (int i = 0; i <= 10; i++) {
            long t = 1000L + i * 500L;
            recorder.addHeartbeatMain(
                    t,
                    10L + i,
                    125,
                    false,
                    true,
                    false,
                    false);
            recorder.addHeartbeatBackground(t, 10L + i);
        }

        recorder.addLocation(6000L, "GPS", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addLocation(6000L, "NETWORK", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addLocation(6000L, "GMS_UPDATES", 35.0, 139.0, 1f, 0f, 0f, true);

        LabCompatibilitySessionRecorder.Summary summary = recorder.summarize();

        assertEquals("LOCATION_CALLBACK_THROTTLE", summary.freezeDiagnosis());
        assertTrue(summary.heartbeat.maxGapMs <= 500L);
    }

    @Test
    public void mainThreadOnlyGapIsClassifiedSeparately() {
        LabCompatibilitySessionRecorder recorder =
                new LabCompatibilitySessionRecorder();

        recorder.start(1000L);
        recorder.addLocation(1000L, "GPS", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addLocation(1000L, "NETWORK", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addLocation(1000L, "GMS_UPDATES", 35.0, 139.0, 1f, 0f, 0f, true);

        recorder.addHeartbeatMain(1000L, 10L, 100, false, true, false, true);
        recorder.addHeartbeatBackground(1000L, 10L);
        for (int i = 1; i <= 10; i++) {
            recorder.addHeartbeatBackground(1000L + i * 500L, 10L + i);
        }
        recorder.addHeartbeatMain(6000L, 20L, 100, false, true, false, true);

        recorder.addLocation(6000L, "GPS", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addLocation(6000L, "NETWORK", 35.0, 139.0, 1f, 0f, 0f, true);
        recorder.addLocation(6000L, "GMS_UPDATES", 35.0, 139.0, 1f, 0f, 0f, true);

        LabCompatibilitySessionRecorder.Summary summary = recorder.summarize();

        assertEquals("MAIN_THREAD_STALL", summary.freezeDiagnosis());
        assertTrue(summary.heartbeat.maxGapMs >= 5000L);
        assertTrue(summary.backgroundHeartbeat.maxGapMs <= 500L);
    }

    @Test
    public void gapProfilesExposeTailLatency() {
        LabCompatibilitySessionRecorder recorder =
                new LabCompatibilitySessionRecorder();

        recorder.start(1000L);
        long t = 1000L;
        for (int i = 0; i < 20; i++) {
            recorder.addHeartbeatMain(t, i, 100, false, true, false, true);
            recorder.addHeartbeatBackground(t, i);
            t += 500L;
        }
        recorder.addHeartbeatMain(t + 4000L, 50L, 100, false, true, false, true);
        recorder.addHeartbeatBackground(t + 4000L, 50L);

        LabCompatibilitySessionRecorder.Summary summary = recorder.summarize();

        assertTrue(summary.heartbeat.gaps.p95Ms >= 500L);
        assertEquals(1, summary.heartbeat.gaps.over3000);
        assertEquals(1, summary.backgroundHeartbeat.gaps.over3000);
    }

    @Test
    public void survivalModeAndWakeLockEvidenceRemainAfterStop() {
        LabCompatibilitySessionRecorder recorder =
                new LabCompatibilitySessionRecorder();

        recorder.start(1000L);
        recorder.addMarker(1010L, "SURVIVAL_MODE_WAKELOCK");
        recorder.addMarker(1020L, "WAKELOCK_ACQUIRED");
        recorder.addHeartbeatMain(1100L, 10L, 100, false, true, false, false);
        recorder.addHeartbeatBackground(1100L, 10L);
        recorder.addMarker(1200L, "WAKELOCK_NOT_HELD");
        recorder.stop(1300L);

        LabCompatibilitySessionRecorder.Summary summary = recorder.summarize();

        assertEquals("WAKELOCK", summary.sessionMode);
        assertTrue(summary.wakeLockAcquired);
        assertEquals(1, summary.wakeLockLossCount);
        assertEquals(false, summary.wakeLockAcquireFailed);
    }

    @Test
    public void csvContainsMarkersAndLocations() {
        LabCompatibilitySessionRecorder recorder =
                new LabCompatibilitySessionRecorder();

        recorder.start(1000L);
        recorder.addMarker(1100L, "UI_BACKGROUND");
        recorder.addLocation(1200L, "GPS", 35.0, 139.0, 1f, 1f, 45f, true);
        recorder.stop(1300L);

        String[] rows = recorder.toCsvRows();

        assertTrue(rows.length >= 5);
        assertTrue(rows[0].startsWith("relative_ms"));
        assertTrue(java.util.Arrays.stream(rows)
                .anyMatch(row -> row.contains("UI_BACKGROUND")));
        assertTrue(java.util.Arrays.stream(rows)
                .anyMatch(row -> row.contains("GPS")));
    }
}
