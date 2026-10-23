package top.niunaijun.bcore.fake.service;

import static org.junit.Assert.*;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import top.niunaijun.bcore.app.BActivityThread;
import top.niunaijun.bcore.entity.location.BLocation;
import top.niunaijun.bcore.fake.frameworks.BLocationManager;

/** Controlled contract tests, not third-party adoption or guest hook-installation evidence.
 * Public API33 LocationManager creates its real framework callback/listener transports.
 * A test proxy routes only four methods through the exact patched hook entry points.
 * Continuous samples cross the engine's actual location-service Binder connection.
 */
@RunWith(AndroidJUnit4.class)
public final class LocationContractInstrumentedTest {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<LocationListener> listeners = new ArrayList<>();
    private LocationManager manager;
    private int user;
    private String pkg;

    @Before public void setup() throws Exception {
        assertEquals(33, Build.VERSION.SDK_INT);
        user = BActivityThread.getUserId();
        pkg = BActivityThread.getAppPackageName();
        BLocationManager.get().setLocation(user, pkg, new BLocation(38.0, -121.0));
        BLocationManager.get().setPattern(user, pkg, BLocationManager.OWN_MODE);
        assertTrue(BLocationManager.isFakeLocationEnable());
        Class<?> type = Class.forName("android.location.ILocationManager");
        Object service = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getLastLocation":
                            return new ILocationManagerProxy.GetLastLocation().hook(null, method, args);
                        case "getCurrentLocation":
                            return new ILocationManagerProxy.GetCurrentLocation().hook(null, method, args);
                        case "registerLocationListener":
                            return new ILocationManagerProxy.RegisterLocationListener().hook(null, method, args);
                        case "unregisterLocationListener":
                            return new ILocationManagerProxy.UnregisterLocationListener().hook(null, method, args);
                        default:
                            throw new AssertionError("Unexpected real-service call: " + method.getName());
                    }
                });
        Constructor<LocationManager> constructor = LocationManager.class.getDeclaredConstructor(
                Context.class, type);
        constructor.setAccessible(true);
        manager = constructor.newInstance(InstrumentationRegistry.getInstrumentation()
                .getTargetContext(), service);
    }

    @After public void cleanup() {
        if (manager != null) for (LocationListener listener : listeners) manager.removeUpdates(listener);
        BLocationManager.disableFakeLocation(user, pkg);
        executor.shutdownNow();
    }

    private static void sample(Location value, String provider) {
        assertNotNull(value);
        assertEquals(provider, value.getProvider());
        assertEquals(38.0, value.getLatitude(), 0.000001);
        assertEquals(-121.0, value.getLongitude(), 0.000001);
        assertTrue("Synthetic sample must remain marked", value.isMock());
        assertTrue(value.hasAccuracy());
        long age = SystemClock.elapsedRealtimeNanos() - value.getElapsedRealtimeNanos();
        assertTrue("Valid monotonic sample age: " + age, age >= 0 && age < 30_000_000_000L);
    }

    @Test public void lastAndCurrentUseFrameworkCallbackAndFreshMarkedSamples() throws Exception {
        sample(manager.getLastKnownLocation("gps"), "gps");
        sample(manager.getLastKnownLocation("network"), "network");
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Location> result = new AtomicReference<>();
        AtomicInteger count = new AtomicInteger();
        CancellationSignal cancellation = new CancellationSignal();
        manager.getCurrentLocation("network", cancellation, executor, value -> {
            result.set(value);
            count.incrementAndGet();
            done.countDown();
        });
        assertTrue("Current location callback", done.await(30, TimeUnit.SECONDS));
        sample(result.get(), "network");
        cancellation.cancel();
        SystemClock.sleep(250);
        assertEquals("Exactly one completion", 1, count.get());

        BLocationManager.get().setLocation(user, pkg, null);
        assertNull(manager.getLastKnownLocation("gps"));
        CountDownLatch empty = new CountDownLatch(1);
        manager.getCurrentLocation("gps", null, executor, value -> {
            result.set(value);
            empty.countDown();
        });
        assertTrue("Missing target completes with null", empty.await(30, TimeUnit.SECONDS));
        assertNull(result.get());
    }

    @Test public void cancellationBeforeQueuedDeliverySuppressesCallback() throws Exception {
        Handler main = new Handler(Looper.getMainLooper());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger callbacks = new AtomicInteger();
        assertTrue(main.post(() -> {
            entered.countDown();
            try { release.await(10, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }));
        try {
            assertTrue(entered.await(30, TimeUnit.SECONDS));
            CancellationSignal cancellation = new CancellationSignal();
            manager.getCurrentLocation("gps", cancellation, executor, ignored -> callbacks.incrementAndGet());
            cancellation.cancel();
        } finally { release.countDown(); }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { });
        executor.submit(() -> { }).get(30, TimeUnit.SECONDS);
        SystemClock.sleep(250);
        assertEquals(0, callbacks.get());
    }

    @Test public void continuousFrameworkBatchCallbacksStopAtMaxUpdatesAndUnregister() throws Exception {
        Samples limited = new Samples(2);
        request(limited, new LocationRequest.Builder(150).setMaxUpdates(2).build());
        assertTrue("Two continuous callbacks", limited.done.await(30, TimeUnit.SECONDS));
        SystemClock.sleep(500);
        assertEquals(2, limited.count.get());
        assertEquals(2, limited.batches.get());
        sample(limited.first.get(), "network");
        sample(limited.last.get(), "network");
        assertTrue(limited.last.get().getElapsedRealtimeNanos()
                > limited.first.get().getElapsedRealtimeNanos());

        Samples removed = new Samples(1);
        request(removed, new LocationRequest.Builder(150).build());
        assertTrue(removed.done.await(30, TimeUnit.SECONDS));
        manager.removeUpdates(removed);
        SystemClock.sleep(350); // drain any delivery already in flight at unregister
        int stable = removed.count.get();
        SystemClock.sleep(500);
        assertEquals("No further subscription delivery", stable, removed.count.get());
    }

    @Test public void durationAndDisablingStopFutureDeliveries() throws Exception {
        BLocationManager.get().setLocation(user, pkg, null);
        Samples expired = new Samples(1);
        request(expired, new LocationRequest.Builder(100).setDurationMillis(150).build());
        SystemClock.sleep(500);
        BLocationManager.get().setLocation(user, pkg, new BLocation(38.0, -121.0));
        SystemClock.sleep(500);
        assertEquals("Expired request cannot resume when a target appears", 0, expired.count.get());
        Samples disabled = new Samples(1);
        request(disabled, new LocationRequest.Builder(150).build());
        assertTrue(disabled.done.await(30, TimeUnit.SECONDS));
        BLocationManager.disableFakeLocation(user, pkg);
        SystemClock.sleep(350);
        int stable = disabled.count.get();
        // Keep virtual dispatch enabled for cleanup, but do not re-register this listener.
        BLocationManager.get().setPattern(user, pkg, BLocationManager.OWN_MODE);
        SystemClock.sleep(500);
        assertEquals("Disabled subscriptions remain removed", stable, disabled.count.get());
    }

    private void request(Samples listener, LocationRequest request) {
        listeners.add(listener);
        manager.requestLocationUpdates("network", request, executor, listener);
    }

    private static final class Samples implements LocationListener {
        final CountDownLatch done;
        final AtomicInteger count = new AtomicInteger();
        final AtomicInteger batches = new AtomicInteger();
        final AtomicReference<Location> first = new AtomicReference<>();
        final AtomicReference<Location> last = new AtomicReference<>();

        Samples(int count) { done = new CountDownLatch(count); }
        @Override public void onLocationChanged(Location value) {
            first.compareAndSet(null, value);
            last.set(value);
            count.incrementAndGet();
            done.countDown();
        }
        @Override public void onLocationChanged(List<Location> values) {
            batches.incrementAndGet();
            for (Location value : values) onLocationChanged(value);
        }
    }
}
