package com.zcshou.gogogo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LabHeadingTraceRecorderTest {

    @Test
    public void recorderRateLimitsAndExportsCsv() {
        LabHeadingTraceRecorder recorder = new LabHeadingTraceRecorder();

        recorder.add(1000L, 10f, 11f, 12f, 13f,
                1f, 2f, "FLAT", 40.0, 0.9, "MAG_LOCKED", "ABS+GAME");
        recorder.add(1050L, 20f, 21f, 22f, 23f,
                2f, 3f, "FLAT", 41.0, 0.8, "MAG_LOCKED", "ABS+GAME");
        recorder.add(1110L, 30f, 31f, 32f, 33f,
                3f, 4f, "TILTED", 42.0, 0.7, "INERTIAL_HOLD", "GAME");

        assertEquals(2, recorder.size());

        String[] rows = recorder.toCsvRows();
        assertEquals(3, rows.length);
        assertTrue(rows[0].startsWith("elapsed_ms"));
        assertTrue(rows[1].contains("MAG_LOCKED"));
        assertTrue(rows[2].contains("INERTIAL_HOLD"));
    }

    @Test
    public void clearRemovesSamples() {
        LabHeadingTraceRecorder recorder = new LabHeadingTraceRecorder();
        recorder.add(1000L, 0f, 0f, 0f, 0f,
                0f, 0f, "FLAT", 35.0, 1.0, "MAG_LOCKED", "ABS");

        recorder.clear();

        assertEquals(0, recorder.size());
        assertEquals(1, recorder.toCsvRows().length);
    }
}
