package com.zcshou.gogogo;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LabConsumerMatrixEvaluatorTest {

    @Test
    public void consistentFreshStreamsScoreConsistent() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        new LabConsumerMatrixEvaluator.Sample("gps", true, 35.0, 139.0, 50, 0L, true, true),
                        new LabConsumerMatrixEvaluator.Sample("network", true, 35.00001, 139.0, 70, 0L, true, true),
                        new LabConsumerMatrixEvaluator.Sample("last", true, 35.0, 139.00001, 6000, 0L, true, false),
                        new LabConsumerMatrixEvaluator.Sample("current", true, 35.0, 139.0, 5800, 0L, true, false),
                        new LabConsumerMatrixEvaluator.Sample("updates", true, 35.00001, 139.00001, 30, 0L, true, true)
                ));

        assertEquals("CONSISTENT", result.grade);
        assertEquals(5, result.availableChannels);
        assertEquals(3, result.availableStreams);
        assertEquals(3, result.freshStreams);
        assertEquals(2, result.availableSnapshots);
        assertEquals(2, result.freshSnapshots);
        assertTrue(result.maxSeparationMeters < 10.0);
    }

    @Test
    public void staleStreamIsReported() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        new LabConsumerMatrixEvaluator.Sample("gps", true, 35.0, 139.0, 5000, 0L, true, true),
                        new LabConsumerMatrixEvaluator.Sample("network", true, 35.0, 139.0, 50, 0L, true, true),
                        new LabConsumerMatrixEvaluator.Sample("updates", true, 35.0, 139.0, 60, 0L, true, true)
                ));

        assertEquals("PARTIAL", result.grade);
        assertEquals(2, result.freshStreams);
        assertTrue(result.staleStreams.contains("gps"));
    }

    @Test
    public void staleSnapshotDoesNotDowngradeHealthyStreams() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        new LabConsumerMatrixEvaluator.Sample("gps", true, 35.0, 139.0, 50, 0L, true, true),
                        new LabConsumerMatrixEvaluator.Sample("network", true, 35.0, 139.0, 50, 0L, true, true),
                        new LabConsumerMatrixEvaluator.Sample("updates", true, 35.0, 139.0, 50, 0L, true, true),
                        new LabConsumerMatrixEvaluator.Sample("last", true, 35.0, 139.0, 60000, 0L, true, false)
                ));

        assertEquals("CONSISTENT", result.grade);
        assertEquals(3, result.freshStreams);
        assertEquals(0, result.freshSnapshots);
        assertTrue(result.staleSnapshots.contains("last"));
    }

    @Test
    public void separatedChannelsDoNotScoreConsistent() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        new LabConsumerMatrixEvaluator.Sample("gps", true, 35.0, 139.0, 50, 0L, true, true),
                        new LabConsumerMatrixEvaluator.Sample("network", true, 36.0, 139.0, 50, 0L, true, true),
                        new LabConsumerMatrixEvaluator.Sample("updates", true, 35.0, 139.0, 50, 0L, true, true)
                ));

        assertEquals("DEGRADED", result.grade);
        assertTrue(result.maxSeparationMeters > 1000.0);
    }

    @Test
    public void emptyInputIsSafe() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Collections.emptyList());

        assertEquals("DEGRADED", result.grade);
        assertEquals(0, result.availableChannels);
        assertEquals(0, result.freshStreams);
    }
    @Test
    public void newlyDeliveredOldStreamFixIsNotFresh() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        sample("gps", 600000, 0, true),
                        sample("network", 20, 0, true),
                        sample("updates", 20, 0, true)));
        assertEquals("PARTIAL", result.grade);
        assertEquals(2, result.freshStreams);
        assertTrue(result.staleStreams.contains("gps"));
    }

    @Test
    public void newlyDeliveredOldSnapshotCannotMasqueradeAsFresh() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        sample("gps", 50, 50, true),
                        sample("network", 50, 50, true),
                        sample("updates", 50, 50, true),
                        sample("last", 600000, 0, false)));
        assertEquals("CONSISTENT", result.grade);
        assertEquals(0, result.freshSnapshots);
        assertTrue(result.staleSnapshots.contains("last"));
    }

    @Test
    public void staleCallbackDoesNotCountAsLiveStream() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        sample("gps", 20, 2000, true),
                        sample("network", 20, 0, true),
                        sample("updates", 20, 0, true)));
        assertEquals("PARTIAL", result.grade);
        assertTrue(result.staleStreams.contains("gps"));
    }

    @Test
    public void unknownFixAgeIsNotFreshEvenWithRecentCallback() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        sample("gps", -1, 0, true),
                        sample("network", 25, 0, true),
                        sample("updates", 25, 0, true),
                        sample("last", -1, 0, false)));
        assertEquals("PARTIAL", result.grade);
        assertTrue(result.staleStreams.contains("gps"));
        assertTrue(result.staleSnapshots.contains("last"));
    }

    @Test
    public void oldRemoteSnapshotDoesNotPoisonFreshStreamDistance() {
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        sample("gps", 50, 0, true),
                        sample("network", 50, 0, true),
                        sample("updates", 50, 0, true),
                        new LabConsumerMatrixEvaluator.Sample(
                                "old far away", true, 40.0, 130.0, 600000, 0, true, false)));
        assertEquals("CONSISTENT", result.grade);
        assertTrue(result.maxSeparationMeters > 1000.0);
        assertTrue(result.maxFreshStreamSeparationMeters < 10.0);
    }


    @Test
    public void symmetricFreshStreamDisagreementIsNotMaskedByFirstChannel() {
        // Both endpoints are about 7.8 m from the middle; they are >15 m apart.
        // A reference-only measurement previously reported CONSISTENT.
        LabConsumerMatrixEvaluator.Sample middle =
                new LabConsumerMatrixEvaluator.Sample(
                        "middle", true, 35.0, 139.0, 10, 10, true, true);
        LabConsumerMatrixEvaluator.Sample north =
                new LabConsumerMatrixEvaluator.Sample(
                        "north", true, 35.00007, 139.0, 10, 10, true, true);
        LabConsumerMatrixEvaluator.Sample south =
                new LabConsumerMatrixEvaluator.Sample(
                        "south", true, 34.99993, 139.0, 10, 10, true, true);

        LabConsumerMatrixEvaluator.Result firstMiddle =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(middle, north, south));
        LabConsumerMatrixEvaluator.Result firstNorth =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(north, middle, south));
        assertEquals("PARTIAL", firstMiddle.grade);
        assertEquals("PARTIAL", firstNorth.grade);
        assertTrue(firstMiddle.maxFreshStreamSeparationMeters > 10.0);
        assertEquals(firstMiddle.maxFreshStreamSeparationMeters,
                firstNorth.maxFreshStreamSeparationMeters, 0.001);
        assertEquals(firstMiddle.maxFreshStreamSeparationMeters,
                firstMiddle.maxSeparationMeters, 0.001);
    }

    @Test
    public void allChannelSeparationAlsoUsesTruePairwiseMaximum() {
        // The two old snapshots are on opposite sides of the fresh stream.
        // They affect the information-only metric, never the live grade.
        LabConsumerMatrixEvaluator.Result result =
                LabConsumerMatrixEvaluator.evaluate(Arrays.asList(
                        sample("gps", 20, 0, true),
                        sample("network", 20, 0, true),
                        sample("updates", 20, 0, true),
                        new LabConsumerMatrixEvaluator.Sample(
                                "cached north", true, 35.00007, 139.0, 60000, 0, true, false),
                        new LabConsumerMatrixEvaluator.Sample(
                                "cached south", true, 34.99993, 139.0, 60000, 0, true, false)));
        assertEquals("CONSISTENT", result.grade);
        assertTrue(result.maxSeparationMeters > 10.0);
        assertEquals(0.0, result.maxFreshStreamSeparationMeters, 0.001);
    }

    private static LabConsumerMatrixEvaluator.Sample sample(
            String name, long fixAge, long callbackAge, boolean stream) {
        return new LabConsumerMatrixEvaluator.Sample(
                name, true, 35.0, 139.0, fixAge, callbackAge, true, stream);
    }

}
