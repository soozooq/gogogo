package top.niunaijun.bcore.fake.service;

import static org.junit.Assert.*;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TransportInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.WorkSource;
import android.telephony.CellInfo;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyCallback;
import android.telephony.ServiceState;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.niunaijun.bcore.app.BActivityThread;
import top.niunaijun.bcore.entity.location.BLocation;
import top.niunaijun.bcore.fake.frameworks.BLocationManager;

/** Controlled framework-interface/proxy contracts, not Guest or WeChat adoption.
 * Controlled base services avoid all real cellular/Wi-Fi queries.
 */
@RunWith(AndroidJUnit4.class)
public final class RadioPrivacyContractInstrumentedTest {
    private Class<?> telephony;
    private Object base;
    private int user, previousPattern;
    private String pkg;
    private final AtomicInteger baseCalls = new AtomicInteger();
    private final List<Object> baseCells = new ArrayList<>(Arrays.asList(new Object()));

    @Before public void setup() throws Exception {
        assertEquals(33, Build.VERSION.SDK_INT);
        user = BActivityThread.getUserId();
        pkg = BActivityThread.getAppPackageName();
        previousPattern = BLocationManager.get().getPattern(user, pkg);
        BLocationManager.get().setLocation(user, pkg, new BLocation(38.0, -121.0));
        BLocationManager.get().setPattern(user, pkg, BLocationManager.OWN_MODE);
        assertTrue(BLocationManager.isFakeLocationEnable());
        assertNull("Missing virtual cell is the privacy regression case",
                BLocationManager.get().getCell(user, pkg));
        telephony = Class.forName("com.android.internal.telephony.ITelephony");
        base = Proxy.newProxyInstance(telephony.getClassLoader(), new Class<?>[] {telephony},
                (proxy, method, args) -> {
                    baseCalls.incrementAndGet();
                    if ("getAllCellInfo".equals(method.getName())) return baseCells;
                    if ("getCellLocation".equals(method.getName())) return null;
                    throw new AssertionError("Unexpected base method: " + method.getName());
                });
    }

    @After public void cleanup() {
        BLocationManager.get().setPattern(user, pkg, previousPattern);
    }

    private Method method(String name) {
        for (Method method : telephony.getMethods()) if (name.equals(method.getName())) return method;
        throw new AssertionError("Missing Android13 telephony method: " + name);
    }

    private Object[] arguments(Method method) {
        Class<?>[] types = method.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i] == String.class) args[i] = pkg;
            else if (types[i] == int.class) args[i] = 0;
            else if (types[i] == long.class) args[i] = 0L;
            else if (types[i] == boolean.class) args[i] = false;
        }
        return args;
    }

    public static final class LegacyBase {
        int calls;
        final Bundle cell = new Bundle();
        final List<Object> neighbors = new ArrayList<>(Arrays.asList(new Object()));
        public Bundle getCellLocation() { calls++; return cell; }
        public List<Object> getNeighboringCellInfo() { calls++; return neighbors; }
    }

    private Method wifiMethod(String name) throws Exception {
        Class<?> wifi = Class.forName("android.net.wifi.IWifiManager");
        for (Method method : wifi.getMethods()) if (name.equals(method.getName())) return method;
        throw new AssertionError("Missing Android13 Wi-Fi method: " + name);
    }

    private Object wifiBase(WifiInfo info, List<ScanResult> scans) throws Exception {
        Class<?> wifi = Class.forName("android.net.wifi.IWifiManager");
        return Proxy.newProxyInstance(wifi.getClassLoader(), new Class<?>[] {wifi},
                (proxy, method, args) -> {
                    baseCalls.incrementAndGet();
                    if ("getConnectionInfo".equals(method.getName())) return info;
                    if ("getScanResults".equals(method.getName())) return scans;
                    throw new AssertionError("Unexpected controlled Wi-Fi method: " + method.getName());
                });
    }

    @Test public void virtualWifiUsesFrameworkRedactedCopyWithoutMutatingBase() throws Throwable {
        WifiInfo original = new WifiInfo.Builder()
                .setSsid("synthetic-radio-fixture".getBytes(StandardCharsets.UTF_8))
                .setBssid("02:11:22:33:44:55").setNetworkId(41).setRssi(-55).build();
        String originalSsid = original.getSSID();
        Method method = wifiMethod("getConnectionInfo");
        assertEquals(WifiInfo.class, method.getReturnType());
        IWifiManagerProxy.GetConnectionInfo hook = new IWifiManagerProxy.GetConnectionInfo();
        Object controlled = wifiBase(original, new ArrayList<ScanResult>());
        WifiInfo redacted = (WifiInfo) hook.hook(controlled, method, arguments(method));
        assertNotNull(redacted);
        assertNotSame(original, redacted);
        assertEquals(WifiManager.UNKNOWN_SSID, redacted.getSSID());
        assertTrue(redacted.getBSSID() == null || "02:00:00:00:00:00".equals(redacted.getBSSID()));
        assertEquals(-1, redacted.getNetworkId());
        assertEquals(-55, redacted.getRssi());
        assertEquals(originalSsid, original.getSSID());
        assertEquals("02:11:22:33:44:55", original.getBSSID());
        assertEquals(41, original.getNetworkId());
        assertEquals(1, baseCalls.get());

        BLocationManager.disableFakeLocation(user, pkg);
        assertSame(original, hook.hook(controlled, method, arguments(method)));
        assertEquals(2, baseCalls.get());
    }

    @Test public void absentWifiConnectionRemainsNullInVirtualAndClosedModes() throws Throwable {
        Method method = wifiMethod("getConnectionInfo");
        Object controlled = wifiBase(null, new ArrayList<ScanResult>());
        IWifiManagerProxy.GetConnectionInfo hook = new IWifiManagerProxy.GetConnectionInfo();
        assertNull(hook.hook(controlled, method, arguments(method)));
        BLocationManager.disableFakeLocation(user, pkg);
        assertNull(hook.hook(controlled, method, arguments(method)));
        assertEquals(2, baseCalls.get());
    }

    @Test public void wifiScansAreSuppressedOnlyInVirtualMode() throws Throwable {
        List<ScanResult> scans = new ArrayList<>(Arrays.asList(new ScanResult()));
        Method method = wifiMethod("getScanResults");
        assertEquals(List.class, method.getReturnType());
        Object controlled = wifiBase(null, scans);
        IWifiManagerProxy.GetScanResults hook = new IWifiManagerProxy.GetScanResults();
        Object result = hook.hook(controlled, method, arguments(method));
        assertTrue(result instanceof List);
        assertTrue(((List<?>) result).isEmpty());
        assertEquals(0, baseCalls.get());
        BLocationManager.disableFakeLocation(user, pkg);
        assertSame(scans, hook.hook(controlled, method, arguments(method)));
        assertEquals(1, baseCalls.get());
    }

    @Test public void missingVirtualCellNeverInvokesRealBaseAndClosedModePassesThrough() throws Throwable {
        Method method = method("getCellLocation");
        assertEquals("android.telephony.CellIdentity", method.getReturnType().getName());
        ITelephonyManagerProxy.GetCellLocation hook = new ITelephonyManagerProxy.GetCellLocation();
        assertNull(hook.hook(base, method, arguments(method)));
        assertEquals("Virtual mode must not ask the real service for a cell", 0, baseCalls.get());

        LegacyBase legacy = new LegacyBase();
        Method legacyMethod = LegacyBase.class.getMethod("getCellLocation");
        Object empty = hook.hook(legacy, legacyMethod, new Object[0]);
        assertTrue("Retain the older Bundle return shape", empty instanceof Bundle);
        assertTrue(((Bundle) empty).isEmpty());
        assertEquals(0, legacy.calls);

        BLocationManager.disableFakeLocation(user, pkg);
        assertFalse(BLocationManager.isFakeLocationEnable());
        assertNull(hook.hook(base, method, arguments(method)));
        assertEquals(1, baseCalls.get());
        assertSame(legacy.cell, hook.hook(legacy, legacyMethod, new Object[0]));
        assertEquals(1, legacy.calls);
    }

    @Test public void virtualCellCollectionsAreEmptyFrameworkListsAndClosedModePassesThrough() throws Throwable {
        Method method = method("getAllCellInfo");
        assertEquals(List.class, method.getReturnType());
        ITelephonyManagerProxy.GetAllCellInfo hook = new ITelephonyManagerProxy.GetAllCellInfo();
        Object empty = hook.hook(base, method, arguments(method));
        assertTrue("Do not expose internal BCell collections", empty instanceof List);
        assertTrue(((List<?>) empty).isEmpty());
        assertEquals(0, baseCalls.get());

        LegacyBase legacy = new LegacyBase();
        Method neighborMethod = LegacyBase.class.getMethod("getNeighboringCellInfo");
        ITelephonyManagerProxy.GetNeighboringCellInfo neighbors = new ITelephonyManagerProxy.GetNeighboringCellInfo();
        Object neighborResult = neighbors.hook(legacy, neighborMethod, new Object[0]);
        assertTrue(neighborResult instanceof List);
        assertTrue(((List<?>) neighborResult).isEmpty());
        assertEquals(0, legacy.calls);

        BLocationManager.disableFakeLocation(user, pkg);
        assertSame(baseCells, hook.hook(base, method, arguments(method)));
        assertEquals(1, baseCalls.get());
        assertSame(legacy.neighbors, neighbors.hook(legacy, neighborMethod, new Object[0]));
        assertEquals(1, legacy.calls);
    }

    /** Exercise the actual public framework wrapper, including its private Binder callback
     * and the caller's executor. The process-local service cache is replaced with a controlled
     * service for the entire call, then restored; neither mode queries real radio hardware.
     */
    private void asyncFrameworkRequest(boolean workSourceForm, boolean virtual) throws Throwable {
        Field cache = TelephonyManager.class.getDeclaredField("sITelephony");
        Field caching = TelephonyManager.class.getDeclaredField("sServiceHandleCacheEnabled");
        cache.setAccessible(true); caching.setAccessible(true);
        Object previousService = cache.get(null);
        boolean previousCaching = caching.getBoolean(null);
        AtomicReference<Object[]> incoming = new AtomicReference<>();
        AtomicReference<Object[]> delegated = new AtomicReference<>();
        AtomicReference<String> calledMethod = new AtomicReference<>();
        List<CellInfo> synthetic = new ArrayList<>();
        synthetic.add((CellInfo) Class.forName("android.telephony.CellInfoGsm")
                .getDeclaredConstructor().newInstance());
        Object controlled = Proxy.newProxyInstance(telephony.getClassLoader(), new Class<?>[] {telephony},
                (proxy, method, args) -> {
                    baseCalls.incrementAndGet();
                    delegated.set(args.clone());
                    Class<?> callbackType = method.getParameterTypes()[1];
                    callbackType.getMethod("onCellInfo", List.class).invoke(args[1], synthetic);
                    return null;
                });
        ITelephonyManagerProxy.RequestCellInfoUpdate hook = new ITelephonyManagerProxy.RequestCellInfoUpdate();
        Object intercepted = Proxy.newProxyInstance(telephony.getClassLoader(), new Class<?>[] {telephony},
                (proxy, method, args) -> {
                    if ("asBinder".equals(method.getName())) return new Binder();
                    incoming.set(args.clone()); calledMethod.set(method.getName());
                    try { return hook.hook(controlled, method, args); }
                    catch (InvocationTargetException error) { throw error.getCause(); }
                });
        ExecutorService executor = Executors.newSingleThreadExecutor(
                task -> new Thread(task, "gogogo-cell-contract-executor"));
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<List<CellInfo>> received = new AtomicReference<>();
        AtomicReference<String> callbackThread = new AtomicReference<>();
        AtomicInteger callbacks = new AtomicInteger(), errors = new AtomicInteger();
        TelephonyManager.CellInfoCallback callback = new TelephonyManager.CellInfoCallback() {
            @Override public void onCellInfo(List<CellInfo> cells) {
                received.set(cells); callbackThread.set(Thread.currentThread().getName());
                callbacks.incrementAndGet(); completed.countDown();
            }
            @Override public void onError(int code, Throwable error) {
                errors.incrementAndGet(); completed.countDown();
            }
        };
        WorkSource workSource = new WorkSource();
        try {
            caching.setBoolean(null, true); cache.set(null, intercepted);
            TelephonyManager manager = InstrumentationRegistry.getInstrumentation().getTargetContext()
                    .getSystemService(TelephonyManager.class).createForSubscriptionId(1);
            if (workSourceForm) {
                Method withWorkSource = TelephonyManager.class.getDeclaredMethod("requestCellInfoUpdate",
                        WorkSource.class, Executor.class, TelephonyManager.CellInfoCallback.class);
                withWorkSource.setAccessible(true);
                withWorkSource.invoke(manager, workSource, executor, callback);
            } else {
                manager.requestCellInfoUpdate(executor, callback);
            }
            assertTrue("Framework callback must complete instead of hanging", completed.await(5, TimeUnit.SECONDS));
            assertEquals(0, errors.get()); assertEquals(1, callbacks.get());
            assertEquals("gogogo-cell-contract-executor", callbackThread.get());
            assertEquals(workSourceForm ? "requestCellInfoUpdateWithWorkSource" : "requestCellInfoUpdate",
                    calledMethod.get());
            assertNotNull(received.get());
            assertEquals(1, incoming.get()[0]);
            if (workSourceForm) assertSame(workSource, incoming.get()[4]);
            if (virtual) {
                assertTrue(received.get().isEmpty()); assertNull(delegated.get());
            } else {
                assertSame(synthetic, received.get());
                assertNotNull(delegated.get());
                for (int i = 0; i < incoming.get().length; i++) {
                    if (i == 0) assertEquals(incoming.get()[i], delegated.get()[i]);
                    else assertSame("Ordinary mode must retain callback and attribution arguments",
                                incoming.get()[i], delegated.get()[i]);
                }
            }
        } finally {
            cache.set(null, previousService); caching.setBoolean(null, previousCaching);
            executor.shutdownNow();
        }
    }

    @Test public void virtualAsyncCellRequestsCompleteOnTheFrameworkExecutorWithoutModemCalls() throws Throwable {
        asyncFrameworkRequest(false, true);
        asyncFrameworkRequest(true, true);
        assertEquals(0, baseCalls.get());
    }

    @Test public void closedAsyncCellRequestsPreserveCallbacksAndWorkAttribution() throws Throwable {
        BLocationManager.disableFakeLocation(user, pkg);
        asyncFrameworkRequest(false, false);
        asyncFrameworkRequest(true, false);
        assertEquals(2, baseCalls.get());
    }

    private interface NetworkProbe { void run(ConnectivityManager manager) throws Throwable; }

    /** Public framework getters use only this controlled IConnectivityManager. */
    private void withNetworkManager(Network[] networks, NetworkInfo[] infos, NetworkProbe probe) throws Throwable {
        Class<?> service = Class.forName("android.net.IConnectivityManager");
        Object controlled = Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[] {service},
                (proxy, method, args) -> {
                    baseCalls.incrementAndGet();
                    if ("getAllNetworks".equals(method.getName())) return networks;
                    if ("getAllNetworkInfo".equals(method.getName())) return infos;
                    throw new AssertionError("Unexpected controlled connectivity method: " + method.getName());
                });
        IConnectivityManagerProxy.GetAllNetworkInfo hook = new IConnectivityManagerProxy.GetAllNetworkInfo();
        Object intercepted = Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[] {service},
                (proxy, method, args) -> {
                    if ("asBinder".equals(method.getName())) return new Binder();
                    try {
                        return "getAllNetworkInfo".equals(method.getName())
                                ? hook.hook(controlled, method, args) : method.invoke(controlled, args);
                    } catch (InvocationTargetException error) { throw error.getCause(); }
                });
        Field instance = ConnectivityManager.class.getDeclaredField("sInstance");
        instance.setAccessible(true); Object previous = instance.get(null);
        try {
            java.lang.reflect.Constructor<?> constructor = ConnectivityManager.class
                    .getDeclaredConstructor(Context.class, service);
            constructor.setAccessible(true);
            ConnectivityManager manager = (ConnectivityManager) constructor.newInstance(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), intercepted);
            probe.run(manager);
        } finally { instance.set(null, previous); }
    }

    private NetworkInfo syntheticConnectedWifi() throws Exception {
        java.lang.reflect.Constructor<NetworkInfo> constructor = NetworkInfo.class
                .getDeclaredConstructor(int.class, int.class, String.class, String.class);
        constructor.setAccessible(true);
        NetworkInfo info = constructor.newInstance(ConnectivityManager.TYPE_WIFI, 0, "WIFI", "");
        Method state = NetworkInfo.class.getDeclaredMethod("setDetailedState",
                NetworkInfo.DetailedState.class, String.class, String.class);
        state.setAccessible(true);
        state.invoke(info, NetworkInfo.DetailedState.CONNECTED, "synthetic-reason", "synthetic-ssid");
        return info;
    }

    private Network[] syntheticNetworks() throws Exception {
        java.lang.reflect.Constructor<Network> constructor = Network.class.getDeclaredConstructor(int.class);
        constructor.setAccessible(true);
        return new Network[] {constructor.newInstance(42)};
    }

    @Test public void closedConnectivityKeepsOrdinaryPublicNetworkArrays() throws Throwable {
        BLocationManager.disableFakeLocation(user, pkg);
        Network[] networks = syntheticNetworks(); NetworkInfo[] infos = {syntheticConnectedWifi()};
        withNetworkManager(networks, infos, manager -> {
            assertSame(networks, manager.getAllNetworks());
            assertSame(infos, manager.getAllNetworkInfo());
        });
        assertEquals(2, baseCalls.get());
    }

    @Test public void virtualConnectivityKeepsRoutesAndRedactsOnlyCopiedExtraInfo() throws Throwable {
        Network[] networks = syntheticNetworks(); NetworkInfo original = syntheticConnectedWifi();
        NetworkInfo[] infos = {original};
        withNetworkManager(networks, infos, manager -> {
            assertSame("Opaque handles must remain available to normal routing code", networks, manager.getAllNetworks());
            NetworkInfo[] copies = manager.getAllNetworkInfo();
            assertNotNull(copies); assertEquals(1, copies.length);
            assertNotSame(infos, copies); assertNotSame(original, copies[0]);
            assertTrue(copies[0].isConnected());
            assertEquals(original.getType(), copies[0].getType());
            assertEquals(original.getDetailedState(), copies[0].getDetailedState());
            assertEquals("synthetic-reason", copies[0].getReason());
            assertNull(copies[0].getExtraInfo());
            assertEquals("synthetic-ssid", original.getExtraInfo());
        });
        assertEquals(2, baseCalls.get());
    }

    private void exercisePublicNetworkCallback(boolean listen, boolean reject) throws Throwable {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Class<?> service = Class.forName("android.net.IConnectivityManager");
        AtomicReference<Messenger> delivery = new AtomicReference<>();
        AtomicReference<IBinder> originalDeathToken = new AtomicReference<>();
        NetworkRequest request = new NetworkRequest.Builder().build();
        Field requestId = NetworkRequest.class.getDeclaredField("requestId");
        requestId.setAccessible(true); requestId.setInt(request, 4242);
        AtomicInteger registered = new AtomicInteger(), released = new AtomicInteger();
        String expectedMethod = listen ? "listenForNetwork" : "requestNetwork";
        Object controlled = Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[] {service},
                (proxy, method, args) -> {
                    if (expectedMethod.equals(method.getName())) {
                        registered.incrementAndGet();
                        assertSame("Original system death token must be preserved", originalDeathToken.get(), args[listen ? 2 : 5]);
                        assertEquals(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO, args[listen ? 3 : 7]);
                        assertEquals(context.getOpPackageName(), args[listen ? 4 : 8]);
                        if (reject) throw new SecurityException("CONTROLLED_FRAMEWORK_REJECTION");
                        delivery.set((Messenger) args[listen ? 1 : 3]); return request;
                    }
                    if ("releaseNetworkRequest".equals(method.getName())) {
                        assertEquals(request, args[0]); released.incrementAndGet(); return null;
                    }
                    throw new AssertionError("Unexpected controlled network callback method: " + method.getName());
                });
        IConnectivityManagerProxy.NetworkCallbacks hook = new IConnectivityManagerProxy.NetworkCallbacks();
        Object intercepted = Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[] {service},
                (proxy, method, args) -> {
                    if ("asBinder".equals(method.getName())) return new Binder();
                    try {
                        if (expectedMethod.equals(method.getName())) {
                            originalDeathToken.set((IBinder) args[listen ? 2 : 5]);
                            return hook.hook(controlled, method, args);
                        }
                        return method.invoke(controlled, args);
                    } catch (InvocationTargetException error) { throw error.getCause(); }
                });
        Field instance = ConnectivityManager.class.getDeclaredField("sInstance");
        instance.setAccessible(true); Object previous = instance.get(null);
        HandlerThread destination = new HandlerThread("controlled-network-callback"); destination.start();
        BlockingQueue<NetworkCapabilities> received = new LinkedBlockingQueue<>();
        BlockingQueue<Network> lost = new LinkedBlockingQueue<>();
        Network network = syntheticNetworks()[0];
        ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback(
                ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
            @Override public void onCapabilitiesChanged(Network value, NetworkCapabilities caps) {
                assertEquals(network, value); received.add(caps);
            }
            @Override public void onLost(Network value) { lost.add(value); }
        };
        ConnectivityManager manager = null; boolean active = false;
        try {
            java.lang.reflect.Constructor<?> constructor = ConnectivityManager.class.getDeclaredConstructor(Context.class, service);
            constructor.setAccessible(true); manager = (ConnectivityManager) constructor.newInstance(context, intercepted);
            BLocationManager.disableFakeLocation(user, pkg);
            try {
                if (listen) manager.registerNetworkCallback(request, callback, new Handler(destination.getLooper()));
                else manager.requestNetwork(request, callback, new Handler(destination.getLooper()));
                active = true;
                assertFalse("A platform rejection must propagate", reject);
            } catch (SecurityException denied) {
                assertTrue(reject); assertEquals("CONTROLLED_FRAMEWORK_REJECTION", denied.getMessage());
                assertEquals(1, registered.get()); assertEquals(0, released.get()); return;
            }
            assertNotNull(delivery.get());
            WifiInfo wifi = new WifiInfo.Builder().setSsid("synthetic-callback-fixture".getBytes(StandardCharsets.UTF_8))
                    .setBssid("02:11:22:33:44:55").setNetworkId(41).setRssi(-55).build();
            NetworkCapabilities original = new NetworkCapabilities();
            Method addTransport = NetworkCapabilities.class.getDeclaredMethod("addTransportType", int.class);
            Method setInfo = NetworkCapabilities.class.getDeclaredMethod("setTransportInfo", TransportInfo.class);
            addTransport.setAccessible(true); setInfo.setAccessible(true);
            addTransport.invoke(original, NetworkCapabilities.TRANSPORT_WIFI); setInfo.invoke(original, wifi);
            for (boolean virtual : new boolean[] {false, true, false}) {
                BLocationManager.get().setPattern(user, pkg, virtual ? BLocationManager.OWN_MODE : BLocationManager.CLOSE_MODE);
                Message update = Message.obtain(); update.what = 6; // fixed API33 CALLBACK_CAP_CHANGED
                update.getData().putParcelable(NetworkRequest.class.getSimpleName(), request);
                update.getData().putParcelable(Network.class.getSimpleName(), network);
                update.getData().putParcelable(NetworkCapabilities.class.getSimpleName(), original);
                delivery.get().send(update);
                NetworkCapabilities result = received.poll(5, TimeUnit.SECONDS);
                assertNotNull("Public NetworkCallback must receive the relayed message", result);
                assertTrue(result.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
                assertArrayEquals(original.getCapabilities(), result.getCapabilities());
                WifiInfo delivered = (WifiInfo) result.getTransportInfo(); assertNotNull(delivered);
                if (virtual) {
                    assertNotSame(wifi, delivered); assertEquals(WifiManager.UNKNOWN_SSID, delivered.getSSID());
                    assertTrue(delivered.getBSSID() == null || "02:00:00:00:00:00".equals(delivered.getBSSID()));
                    assertEquals(-1, delivered.getNetworkId());
                } else {
                    assertEquals(wifi.getSSID(), delivered.getSSID()); assertEquals(wifi.getBSSID(), delivered.getBSSID());
                    assertEquals(wifi.getNetworkId(), delivered.getNetworkId());
                }
                assertEquals(-55, delivered.getRssi());
                assertEquals("02:11:22:33:44:55", wifi.getBSSID()); assertEquals(41, wifi.getNetworkId());
            }
            BLocationManager.get().setPattern(user, pkg, BLocationManager.OWN_MODE);
            NetworkCapabilities cellular = new NetworkCapabilities();
            addTransport.invoke(cellular, NetworkCapabilities.TRANSPORT_CELLULAR);
            Message cellUpdate = Message.obtain(); cellUpdate.what = 6;
            cellUpdate.getData().putParcelable(NetworkRequest.class.getSimpleName(), request);
            cellUpdate.getData().putParcelable(Network.class.getSimpleName(), network);
            cellUpdate.getData().putParcelable(NetworkCapabilities.class.getSimpleName(), cellular);
            delivery.get().send(cellUpdate);
            NetworkCapabilities cellResult = received.poll(5, TimeUnit.SECONDS);
            assertNotNull(cellResult); assertTrue(cellResult.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
            assertNull("No fabricated Wi-Fi info on a cellular callback", cellResult.getTransportInfo());
            Message removed = Message.obtain(); removed.what = 4; // fixed API33 CALLBACK_LOST
            removed.getData().putParcelable(NetworkRequest.class.getSimpleName(), request);
            removed.getData().putParcelable(Network.class.getSimpleName(), network); delivery.get().send(removed);
            assertEquals(network, lost.poll(5, TimeUnit.SECONDS));
        } finally {
            try { if (active) manager.unregisterNetworkCallback(callback); }
            finally { instance.set(null, previous); destination.quitSafely(); destination.join(5000); }
        }
        assertEquals(1, registered.get()); assertEquals(1, released.get());
    }

    @Test public void publicRequestedNetworkCallbackChecksModeAtDeliveryAndKeepsDeathAndRelease() throws Throwable {
        exercisePublicNetworkCallback(false, false);
    }

    @Test public void publicListenedNetworkCallbackChecksModeAtDeliveryAndKeepsDeathAndRelease() throws Throwable {
        exercisePublicNetworkCallback(true, false);
    }

    @Test public void publicNetworkCallbackKeepsPlatformPermissionRejection() throws Throwable {
        exercisePublicNetworkCallback(true, true);
    }

    private static final String PHONE_LISTENER = "com.android.internal.telephony.IPhoneStateListener";
    private static final String[] LOCATION_PHONE_METHODS = {"onCellLocationChanged", "onCellInfoChanged",
            "onServiceStateChanged", "onBarringInfoChanged", "onPhysicalChannelConfigChanged", "onRegistrationFailed"};

    private static Method named(Class<?> type, String name) {
        for (Method method : type.getMethods()) if (method.getName().equals(name)) return method;
        throw new AssertionError("Missing framework method: " + name);
    }

    private static void sendSyntheticPhoneEvent(Object listener, String name) throws Exception {
        Method event = named(Class.forName(PHONE_LISTENER), name);
        Class<?>[] types = event.getParameterTypes(); Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i] == int.class) args[i] = 17;
            else if (types[i] == String.class) args[i] = "synthetic-only";
            else if (types[i] == List.class) args[i] = new ArrayList<>();
        }
        event.invoke(listener, args); // The actual framework AIDL Proxy encodes each transaction.
    }

    @Test public void persistentPhoneBinderGatesAllSixPayloadsAtDeliveryAndKeepsIdentity() throws Throwable {
        Class<?> registry = Class.forName("com.android.internal.telephony.ITelephonyRegistry");
        Method listen = named(registry, "listenWithEventList");
        AtomicReference<Object[]> delegated = new AtomicReference<>();
        AtomicInteger registrations = new AtomicInteger();
        Object controlled = Proxy.newProxyInstance(registry.getClassLoader(), new Class<?>[] {registry},
                (proxy, method, args) -> { registrations.incrementAndGet(); delegated.set(args.clone()); return null; });
        List<Integer> codes = new ArrayList<>(); List<byte[]> bytes = new ArrayList<>();
        Binder destination = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                assertEquals(IBinder.FLAG_ONEWAY, flags);
                assertEquals(android.os.Process.myUid(), Binder.getCallingUid());
                assertEquals(android.os.Process.myPid(), Binder.getCallingPid());
                codes.add(code); bytes.add(data.marshall()); return true;
            }
        };
        Class<?> stub = Class.forName(PHONE_LISTENER + "$Stub");
        Object original = stub.getDeclaredMethod("asInterface", IBinder.class).invoke(null, destination);
        Object[] incoming = {true, false, 3, InstrumentationRegistry.getInstrumentation().getTargetContext().getOpPackageName(),
                "synthetic-feature", original, new int[] {1, 5, 11}, true};
        ITelephonyRegistryProxy.ListenWithEventList hook = new ITelephonyRegistryProxy.ListenWithEventList();
        BLocationManager.disableFakeLocation(user, pkg);
        hook.hook(controlled, listen, incoming);
        Object delivered = delegated.get()[5]; IBinder relay = ((IInterface) delivered).asBinder();
        assertNotSame(destination, relay); assertTrue(relay instanceof Binder);
        for (int i = 0; i < incoming.length; i++) if (i != 5) assertSame(incoming[i], delegated.get()[i]);
        for (String name : LOCATION_PHONE_METHODS) sendSyntheticPhoneEvent(delivered, name);
        assertEquals(6, codes.size()); assertEquals(6, new java.util.HashSet<>(codes).size());
        List<byte[]> baseline = new ArrayList<>(bytes);
        codes.clear(); bytes.clear();
        BLocationManager.get().setPattern(user, pkg, BLocationManager.OWN_MODE);
        for (String name : LOCATION_PHONE_METHODS) sendSyntheticPhoneEvent(delivered, name);
        assertTrue("No location-bearing transaction reaches the delegate in virtual mode", codes.isEmpty());
        sendSyntheticPhoneEvent(delivered, "onDataActivity"); assertEquals(1, codes.size());
        codes.clear(); bytes.clear();
        BLocationManager.disableFakeLocation(user, pkg);
        for (String name : LOCATION_PHONE_METHODS) sendSyntheticPhoneEvent(delivered, name);
        assertEquals(6, bytes.size());
        for (int i = 0; i < 6; i++) assertArrayEquals("Closed mode must retain original AIDL bytes", baseline.get(i), bytes.get(i));
        // A failed mode lookup cannot fall back to a raw cell/location-bearing payload.
        Field cache = Class.forName("top.niunaijun.bcore.fake.frameworks.BlackManager").getDeclaredField("mService");
        cache.setAccessible(true); Object previous = cache.get(BLocationManager.get());
        Class<?> modes = Class.forName("top.niunaijun.bcore.core.system.location.IBLocationManagerService");
        Binder live = new Binder();
        Object unavailable = Proxy.newProxyInstance(modes.getClassLoader(), new Class<?>[] {modes}, (proxy, method, args) -> {
            if (method.getName().equals("asBinder")) return live;
            throw new RemoteException("CONTROLLED_MODE_UNAVAILABLE");
        });
        try {
            cache.set(BLocationManager.get(), unavailable); codes.clear(); bytes.clear();
            for (String name : LOCATION_PHONE_METHODS) sendSyntheticPhoneEvent(delivered, name);
            assertTrue(codes.isEmpty());
            sendSyntheticPhoneEvent(delivered, "onDataActivity"); assertEquals(1, codes.size());
        } finally { cache.set(BLocationManager.get(), previous); }
        Object[] removal = incoming.clone(); removal[6] = new int[0];
        hook.hook(controlled, listen, removal);
        assertSame("Unregistration must use the registered Binder", relay, ((IInterface) delegated.get()[5]).asBinder());
        assertEquals(0, ((int[]) delegated.get()[6]).length); assertEquals(2, registrations.get());
        assertSame("Caller arguments remain untouched", original, incoming[5]);
        // Only public framework local Stubs are supported. Reject a nonlocal-shaped
        // Binder facade rather than change a remote delegate's caller/death identity.
        IBinder nonlocal = (IBinder) Proxy.newProxyInstance(IBinder.class.getClassLoader(), new Class<?>[] {IBinder.class},
                (proxy, method, args) -> method.invoke(destination, args));
        Object[] unsupported = incoming.clone();
        unsupported[5] = stub.getDeclaredMethod("asInterface", IBinder.class).invoke(null, nonlocal);
        try { hook.hook(controlled, listen, unsupported); fail("Unsupported callback must not reach the registry"); }
        catch (IllegalArgumentException expected) { assertEquals(2, registrations.get()); }
    }

    private void exercisePublicRegistryCallback(boolean reject) throws Throwable {
        Class<?> registry = Class.forName("com.android.internal.telephony.ITelephonyRegistry");
        Class<?> managerType = Class.forName("android.telephony.TelephonyRegistryManager");
        Field registryCache = managerType.getDeclaredField("sRegistry"); registryCache.setAccessible(true);
        Field phoneCache = TelephonyManager.class.getDeclaredField("sITelephony"); phoneCache.setAccessible(true);
        Field caching = TelephonyManager.class.getDeclaredField("sServiceHandleCacheEnabled"); caching.setAccessible(true);
        Object oldRegistry = registryCache.get(null), oldPhone = phoneCache.get(null);
        boolean oldCaching = caching.getBoolean(null);
        AtomicReference<Object[]> incoming = new AtomicReference<>(), delegated = new AtomicReference<>();
        AtomicInteger registered = new AtomicInteger(), removed = new AtomicInteger();
        AtomicReference<IBinder> firstRelay = new AtomicReference<>();
        Object controlled = Proxy.newProxyInstance(registry.getClassLoader(), new Class<?>[] {registry}, (proxy, method, args) -> {
            assertEquals("listenWithEventList", method.getName());
            if (reject) throw new SecurityException("CONTROLLED_REGISTRY_REJECTION");
            delegated.set(args.clone());
            for (int i = 0; i < args.length; i++) if (i != 5) assertSame(incoming.get()[i], args[i]);
            IBinder relay = ((IInterface) args[5]).asBinder();
            assertNotSame(((IInterface) incoming.get()[5]).asBinder(), relay);
            if (((int[]) args[6]).length == 0) { removed.incrementAndGet(); assertSame(firstRelay.get(), relay); }
            else { registered.incrementAndGet(); firstRelay.set(relay); }
            return null;
        });
        ITelephonyRegistryProxy.ListenWithEventList hook = new ITelephonyRegistryProxy.ListenWithEventList();
        Object intercepted = Proxy.newProxyInstance(registry.getClassLoader(), new Class<?>[] {registry}, (proxy, method, args) -> {
            if (method.getName().equals("asBinder")) return new Binder();
            incoming.set(args.clone()); return hook.hook(controlled, method, args);
        });
        Object noModem = Proxy.newProxyInstance(telephony.getClassLoader(), new Class<?>[] {telephony}, (proxy, method, args) -> {
            if (method.getName().equals("asBinder")) return new Binder();
            throw new AssertionError("No modem query is allowed in the controlled registry check");
        });
        ExecutorService executor = Executors.newSingleThreadExecutor(task -> new Thread(task, "controlled-phone-callback"));
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        class Callback extends TelephonyCallback implements TelephonyCallback.ServiceStateListener,
                TelephonyCallback.CellInfoListener, TelephonyCallback.DataActivityListener {
            @Override public void onServiceStateChanged(ServiceState value) { received.add("service:" + value.getOperatorAlphaLong()); }
            @Override public void onCellInfoChanged(List<CellInfo> cells) { received.add("cell:" + cells.size()); }
            @Override public void onDataActivity(int direction) { received.add("data:" + direction); }
        }
        Callback callback = new Callback(); TelephonyManager manager = null; boolean active = false;
        try {
            registryCache.set(null, controlled); // Deliberately preload a non-null stale service.
            ITelephonyRegistryProxy.replaceCachedRegistry(intercepted);
            assertSame(intercepted, registryCache.get(null));
            phoneCache.set(null, noModem); caching.setBoolean(null, true);
            manager = InstrumentationRegistry.getInstrumentation().getTargetContext()
                    .getSystemService(TelephonyManager.class).createForSubscriptionId(1);
            BLocationManager.disableFakeLocation(user, pkg);
            try {
                manager.registerTelephonyCallback(executor, callback); active = true;
                assertFalse("A registry permission rejection must propagate", reject);
            } catch (SecurityException denied) {
                assertTrue(reject); assertEquals("CONTROLLED_REGISTRY_REJECTION", denied.getMessage()); return;
            }
            ServiceState state = new ServiceState(); state.setOperatorName("synthetic-operator", "synthetic", "00101");
            List<CellInfo> cells = new ArrayList<>();
            cells.add((CellInfo) Class.forName("android.telephony.CellInfoGsm").getDeclaredConstructor().newInstance());
            Class<?> listener = Class.forName(PHONE_LISTENER);
            for (boolean virtual : new boolean[] {false, true, false}) {
                BLocationManager.get().setPattern(user, pkg, virtual ? BLocationManager.OWN_MODE : BLocationManager.CLOSE_MODE);
                Object destination = delegated.get()[5];
                listener.getMethod("onServiceStateChanged", ServiceState.class).invoke(destination, state);
                listener.getMethod("onCellInfoChanged", List.class).invoke(destination, cells);
                listener.getMethod("onDataActivity", int.class).invoke(destination, 2);
                executor.submit(() -> {}).get(5, TimeUnit.SECONDS); // barrier after all local Binder deliveries
                assertEquals(virtual ? 1 : 3, received.size());
                if (!virtual) { assertEquals("service:synthetic-operator", received.remove()); assertEquals("cell:1", received.remove()); }
                assertEquals("data:2", received.remove());
                assertEquals("synthetic-operator", state.getOperatorAlphaLong()); assertEquals(1, cells.size());
            }
            manager.unregisterTelephonyCallback(callback); active = false;
            assertEquals(1, registered.get()); assertEquals(1, removed.get());
        } finally {
            try { if (active) manager.unregisterTelephonyCallback(callback); }
            finally {
                registryCache.set(null, oldRegistry); phoneCache.set(null, oldPhone); caching.setBoolean(null, oldCaching);
                executor.shutdownNow();
            }
        }
    }

    @Test public void publicTelephonyCallbackChecksModeAtDeliveryAndUnregistersSameBinder() throws Throwable {
        exercisePublicRegistryCallback(false);
    }

    @Test public void publicTelephonyCallbackKeepsRegistryPermissionRejection() throws Throwable {
        exercisePublicRegistryCallback(true);
    }
}
