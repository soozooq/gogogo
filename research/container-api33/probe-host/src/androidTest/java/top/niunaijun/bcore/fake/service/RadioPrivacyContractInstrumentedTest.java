package top.niunaijun.bcore.fake.service;

import static org.junit.Assert.*;

import android.os.Build;
import android.os.Bundle;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
}
