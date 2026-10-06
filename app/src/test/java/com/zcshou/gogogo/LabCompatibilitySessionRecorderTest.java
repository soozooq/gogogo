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
