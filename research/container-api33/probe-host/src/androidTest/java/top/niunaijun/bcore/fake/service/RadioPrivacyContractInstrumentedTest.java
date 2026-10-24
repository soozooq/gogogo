package top.niunaijun.bcore.fake.service;

import static org.junit.Assert.*;

import android.os.Build;
import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
 * A fake base service records invocations so no real cell identity is requested.
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
