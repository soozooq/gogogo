package com.zcshou.gogogo;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class LocationPublishGateTest {
    @Test public void createdServiceCannotCaptureItsDefaultCoordinates() {
        LocationPublishGate gate = new LocationPublishGate();
        AtomicInteger captures = new AtomicInteger();
        for (int i = 0; i < 500; i++) {
            assertNull(gate.captureIfReady(() -> captures.incrementAndGet()));
        }
        assertEquals(0, captures.get());
        assertFalse(gate.isReady());
    }

    @Test public void completeTargetOpensPublicationIncludingZeroAndBoundaryCoordinates() {
        LocationPublishGate gate = new LocationPublishGate();
        assertTrue(gate.initialize(0.0, 0.0, 0.0));
        assertEquals("selected target", gate.captureIfReady(() -> "selected target"));
        assertTrue(gate.initialize(-90.0, 180.0, -200.0));
        assertTrue(gate.initialize(90.0, -180.0, 1000.0));
    }

    @Test public void invalidTargetNeverAuthorizesAColdService() {
        double[][] invalid = {
                {Double.NaN, 120.0, 1.0}, {1.0, Double.NaN, 1.0},
                {Double.POSITIVE_INFINITY, 120.0, 1.0},
                {91.0, 120.0, 1.0}, {-91.0, 120.0, 1.0},
                {1.0, 181.0, 1.0}, {1.0, -181.0, 1.0},
                {1.0, 120.0, Double.NaN}, {1.0, 120.0, Double.NEGATIVE_INFINITY}
        };
        LocationPublishGate gate = new LocationPublishGate();
        for (double[] target : invalid) {
            assertFalse(gate.initialize(target[0], target[1], target[2]));
            assertNull(gate.captureIfReady(() -> "must not publish"));
        }
    }

    @Test public void invalidCommandDoesNotRevokeAnExistingValidSession() {
        LocationPublishGate gate = new LocationPublishGate();
        assertTrue(gate.initialize(39.9, 116.4, 50.0));
        assertFalse(gate.initialize(Double.NaN, 0.0, 50.0));
        assertTrue(gate.isReady());
        assertEquals("prior frame", gate.captureIfReady(() -> "prior frame"));
    }

    @Test public void captureCannotObserveHalfOfASwitchBetweenValidTargets() throws Exception {
        LocationPublishGate gate = new LocationPublishGate();
        assertTrue(gate.initialize(39.9, 116.4, 50.0));
        double[] target = {39.9, 116.4};
        CountDownLatch firstFieldWritten = new CountDownLatch(1);
        CountDownLatch completeTarget = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> writer = executor.submit(() -> {
                synchronized (gate) {
                    target[0] = 40.7;
                    firstFieldWritten.countDown();
                    try {
                        assertTrue(completeTarget.await(2, TimeUnit.SECONDS));
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(error);
                    }
                    target[1] = -74.0;
                    assertTrue(gate.initialize(target[0], target[1], 50.0));
                }
            });
            assertTrue(firstFieldWritten.await(2, TimeUnit.SECONDS));
            CountDownLatch readerStarted = new CountDownLatch(1);
            Future<double[]> reader = executor.submit(() -> {
                readerStarted.countDown();
                return gate.captureIfReady(() -> new double[] {target[0], target[1]});
            });
            assertTrue(readerStarted.await(2, TimeUnit.SECONDS));
            try {
                reader.get(50, TimeUnit.MILLISECONDS);
                fail("capture must wait while only half the target has been written");
            } catch (TimeoutException expected) {
                // The writer still owns the complete-target monitor.
            }
            completeTarget.countDown();
            writer.get(2, TimeUnit.SECONDS);
            double[] captured = reader.get(2, TimeUnit.SECONDS);
            assertArrayEquals(new double[] {40.7, -74.0}, captured, 0.0);
            // The returned frame owns its values even after the next command.
            target[0] = 48.8;
            target[1] = 2.3;
            assertArrayEquals(new double[] {40.7, -74.0}, captured, 0.0);
        } finally {
            completeTarget.countDown();
            executor.shutdownNow();
        }
    }

    @Test public void stoppedServiceCannotBeRevivedByALateTargetOrCallback() {
        LocationPublishGate gate = new LocationPublishGate();
        assertTrue(gate.initialize(39.9, 116.4, 50.0));
        gate.close();
        assertTrue(gate.isClosed());
        assertFalse(gate.isReady());
        assertFalse(gate.initialize(40.7, -74.0, 50.0));
        assertNull(gate.captureIfReady(() -> "late callback"));
    }

    @Test public void malformedRouteCannotTurnIntoAnImplicitDefaultTarget() {
        assertFalse(LocationPublishGate.validRoute(null, null));
        assertFalse(LocationPublishGate.validRoute(new double[] {1}, new double[] {2}));
        assertFalse(LocationPublishGate.validRoute(new double[] {1, 2}, new double[] {2}));
        assertFalse(LocationPublishGate.validRoute(new double[] {1, Double.NaN}, new double[] {2, 3}));
        assertFalse(LocationPublishGate.validRoute(new double[] {1, 2}, new double[] {2, 181}));
        assertFalse(LocationPublishGate.validRoute(new double[5001], new double[5001]));
        assertTrue(LocationPublishGate.validRoute(new double[] {0, 40.7}, new double[] {0, -74.0}));
    }
}
