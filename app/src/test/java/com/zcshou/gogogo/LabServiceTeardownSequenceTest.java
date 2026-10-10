package com.zcshou.gogogo;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LabServiceTeardownSequenceTest {
    @Test public void allCleanupsRunInOrder() {
        List<String> calls = new ArrayList<>();
        int failures = LabServiceTeardownSequence.run(
                () -> calls.add("NETWORK"),
                () -> calls.add("GPS"),
                () -> calls.add("GMS"));
        assertEquals(0, failures);
        assertEquals(Arrays.asList("NETWORK", "GPS", "GMS"), calls);
    }

    @Test public void gpsFailureDoesNotSkipFusedOrGmsReset() {
        List<String> calls = new ArrayList<>();
        int failures = LabServiceTeardownSequence.run(
                () -> calls.add("NETWORK"),
                () -> { calls.add("GPS"); throw new IllegalStateException("broken"); },
                () -> calls.add("FUSED"),
                () -> calls.add("GMS"));
        assertEquals(1, failures);
        assertEquals(Arrays.asList("NETWORK", "GPS", "FUSED", "GMS"), calls);
    }

    @Test public void multipleFailuresAreCountedAndFinalizerStillRuns() {
        List<String> calls = new ArrayList<>();
        int failed = LabServiceTeardownSequence.run(
                () -> { calls.add("provider"); throw new SecurityException("denied"); },
                () -> { calls.add("gms"); throw new IllegalArgumentException("bad"); },
                () -> calls.add("journal"));
        assertEquals(2, failed);
        assertEquals(Arrays.asList("provider", "gms", "journal"), calls);
    }

    @Test public void nullStepsAreSkipped() {
        assertEquals(0, LabServiceTeardownSequence.run(
                null, () -> {}, null));
    }

    @Test public void emptyOrMissingStagesAreSafe() {
        assertEquals(0, LabServiceTeardownSequence.run());
        assertEquals(0, LabServiceTeardownSequence.run((Runnable[]) null));
    }

    @Test public void aSingleStageExceptionIsVisibleInFailureCount() {
        assertEquals(1, LabServiceTeardownSequence.run(
                () -> { throw new RuntimeException("fail"); }));
    }
}
