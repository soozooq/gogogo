package com.zcshou.gogogo;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ProviderOperationQueueTest {
    private static final class ManualExecutor implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable operation) { tasks.addLast(operation); }
        void drain() { while (!tasks.isEmpty()) tasks.removeFirst().run(); }
    }

    @Test public void registrationPrecedesLatestFrameWithoutAnUnboundedBacklog() {
        ManualExecutor executor = new ManualExecutor();
        List<String> events = new ArrayList<>();
        ProviderOperationQueue<Integer> queue = new ProviderOperationQueue<>(executor,
                frame -> events.add("frame:" + frame));
        assertTrue(queue.runWhenOpen(() -> events.add("register")));
        for (int i = 0; i < 1000; i++) assertTrue(queue.submit(i));
        assertEquals(2, executor.tasks.size());
        executor.drain();
        assertEquals(Arrays.asList("register", "frame:999"), events);
        assertEquals(999, queue.coalescedFrames());
    }

    @Test public void closeDropsPendingFramesAndObserverRetriesButStillCleans() {
        ManualExecutor executor = new ManualExecutor();
        List<String> events = new ArrayList<>();
        ProviderOperationQueue<Integer> queue = new ProviderOperationQueue<>(executor,
                frame -> events.add("publish"));
        queue.submit(1);
        queue.runWhenOpen(() -> events.add("permission retry"));
        assertTrue(queue.close(() -> events.add("cleanup")));
        assertFalse(queue.submit(2));
        assertFalse(queue.runWhenOpen(() -> events.add("late observer")));
        executor.drain();
        assertEquals(Arrays.asList("cleanup"), events);
    }

    @Test public void stopDoesNotWaitForBlockedIoAndOldCleanupPrecedesNewRegistration() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean providerExists = new AtomicBoolean();
        List<String> events = new ArrayList<>();
        ProviderOperationQueue<Integer> oldSession = new ProviderOperationQueue<>(executor, frame -> {
            entered.countDown();
            try {
                if (!release.await(3, TimeUnit.SECONDS)) throw new AssertionError("I/O not released");
            } catch (InterruptedException error) {
                throw new AssertionError(error);
            }
            // Reproduce an old publish failure registering a provider after STOP.
            providerExists.set(true);
            events.add("old repair returned");
        });
        ProviderOperationQueue<Integer> nextSession = new ProviderOperationQueue<>(executor,
                frame -> events.add("new publish"));
        try {
            oldSession.submit(1);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(oldSession.close(() -> {
                providerExists.set(false);
                events.add("old cleanup");
            }));
            // close returned while the previous provider call is still blocked.
            assertEquals(1L, release.getCount());
            nextSession.runWhenOpen(() -> {
                assertFalse(providerExists.get());
                providerExists.set(true);
                events.add("new register");
            });
            nextSession.submit(2);
            release.countDown();
            executor.submit(() -> {}).get(3, TimeUnit.SECONDS);
            assertEquals(Arrays.asList("old repair returned", "old cleanup", "new register", "new publish"), events);
            assertTrue(providerExists.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test public void slowPublisherKeepsOnlyOnePendingLatestFrame() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<Integer> frames = new ArrayList<>();
        ProviderOperationQueue<Integer> queue = new ProviderOperationQueue<>(executor, frame -> {
            if (frame == 0) {
                entered.countDown();
                try {
                    if (!release.await(3, TimeUnit.SECONDS)) throw new AssertionError("I/O not released");
                } catch (InterruptedException error) { throw new AssertionError(error); }
            }
            frames.add(frame);
        });
        try {
            queue.submit(0);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            for (int i = 1; i <= 1000; i++) queue.submit(i);
            release.countDown();
            executor.submit(() -> {}).get(3, TimeUnit.SECONDS);
            assertEquals(Arrays.asList(0, 1000), frames);
            assertEquals(999, queue.coalescedFrames());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test public void failingPublishAndCleanupCannotSuppressTheNextService() {
        ManualExecutor executor = new ManualExecutor();
        AtomicInteger next = new AtomicInteger();
        ProviderOperationQueue<Integer> queue = new ProviderOperationQueue<>(executor,
                frame -> { throw new IllegalStateException("provider failed"); });
        queue.submit(1);
        executor.drain();
        queue.close(() -> { throw new IllegalStateException("cleanup failed"); });
        ProviderOperationQueue<Integer> following = new ProviderOperationQueue<>(executor, next::set);
        following.runWhenOpen(() -> next.set(2));
        following.submit(3);
        executor.drain();
        assertEquals(2, queue.failures());
        assertEquals(3, next.get());
    }

    @Test public void closeIsFinalAndQueuesOnlyOneCleanup() {
        ManualExecutor executor = new ManualExecutor();
        AtomicInteger cleanups = new AtomicInteger();
        ProviderOperationQueue<Integer> queue = new ProviderOperationQueue<>(executor, ignored -> {});
        assertTrue(queue.close(cleanups::incrementAndGet));
        assertFalse(queue.close(cleanups::incrementAndGet));
        assertTrue(queue.isClosed());
        executor.drain();
        assertEquals(1, cleanups.get());
    }

    @Test public void executorRejectionIsReportedRatherThanClaimedAsCleanup() {
        Executor reject = operation -> { throw new RejectedExecutionException(); };
        ProviderOperationQueue<Integer> queue = new ProviderOperationQueue<>(reject, ignored -> {});
        assertFalse(queue.submit(1));
        assertFalse(queue.runWhenOpen(() -> {}));
        assertFalse(queue.close(() -> {}));
        assertTrue(queue.isClosed());
        assertEquals(3, queue.failures());
    }
}
