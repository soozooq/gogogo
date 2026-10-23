package com.soozooq.tianwaitian.containerresearch;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import top.niunaijun.bcore.BlackBoxCore;
import top.niunaijun.bcore.entity.location.BLocation;
import top.niunaijun.bcore.entity.pm.InstallResult;
import top.niunaijun.bcore.fake.frameworks.BLocationManager;

/** Unmodified official third-party APK; verify its own visible coordinate values. */
@RunWith(AndroidJUnit4.class)
public final class ThirdPartyLocationInstrumentedTest {
    static final String GUEST = "com.android.gpstest.osmdroid";
    static final String SHA = "897b91fe6a1ea0c3a7f6e2b041d5ace0d582941f67cf454fb7778f031d89ce41";
    private final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private final JSONArray stages = new JSONArray();
    private final JSONArray permissionActions = new JSONArray();
    private List<String> lastTexts = new ArrayList<>();

    private String shell(String command) throws Exception {
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(
                InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }

    @Test public void officialGpsTestReceivesChangedTargetThroughItsNormalUi() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File evidence = new File(context.getFilesDir(), "third-party-location-evidence");
        assertTrue(evidence.isDirectory() || evidence.mkdirs());
        File apk = new File(context.getFilesDir(), "official-gpstest.apk");
        long idle = Configurator.getInstance().getWaitForIdleTimeout();
        Configurator.getInstance().setWaitForIdleTimeout(0);
        assertEquals("Third-party APK must remain absent from the system", "", shell("pm path " + GUEST));
        assertEquals("Runner must start with normal permission denied", PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION));
        try {
            closeOnlySystemUiFailureDialog();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation()
                            .executeShellCommand("cat /data/local/tmp/gogogo-official-gpstest.apk"));
                 FileOutputStream output = new FileOutputStream(apk)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int n;
                while ((n = input.read(buffer)) != -1) {
                    total += n;
                    assertTrue(total <= 5 * 1024 * 1024);
                    digest.update(buffer, 0, n);
                    output.write(buffer, 0, n);
                }
                assertEquals(3457918, total);
                output.getFD().sync();
            }
            StringBuilder hash = new StringBuilder();
            for (byte value : digest.digest()) hash.append(String.format(Locale.US, "%02x", value & 255));
            assertEquals(SHA, hash.toString());
            assertTrue(apk.setReadOnly());
            InstallResult installed = BlackBoxCore.get().installPackageAsUser(apk, 0);
            assertTrue("Virtual import failed: " + installed.msg, installed.success);
            assertEquals(GUEST, installed.packageName);
            BLocationManager.get().setLocation(0, GUEST, new BLocation(38.125, -121.875));
            BLocationManager.get().setPattern(0, GUEST, BLocationManager.OWN_MODE);
            Intent launch = BlackBoxCore.getBPackageManager().getLaunchIntentForPackage(GUEST, 0);
            assertNotNull(launch);
            BlackBoxCore.get().startActivity(launch, 0); // no target coordinates in Intent
            JSONArray first = awaitCoordinates(38.125, -121.875);
            stages.put(new JSONObject().put("stage", "TARGET_A").put("visible_text", first));
            assertEquals(PackageManager.PERMISSION_GRANTED,
                    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION));
            assertEquals(PackageManager.PERMISSION_GRANTED,
                    context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION));
            BLocationManager.get().setLocation(0, GUEST, new BLocation(-33.86, 151.20));
            JSONArray second = awaitCoordinates(-33.86, 151.20);
            stages.put(new JSONObject().put("stage", "TARGET_B").put("visible_text", second));
            assertEquals("Third-party APK is not installed by the system", "", shell("pm path " + GUEST));
            JSONObject result = new JSONObject();
            result.put("scope", "OFFICIAL_UNMODIFIED_GPSTEST_UI_NOT_WECHAT_OR_GMS");
            result.put("package", GUEST);
            result.put("apk_sha256", SHA);
            result.put("permission_actions", permissionActions);
            result.put("stages", stages);
            result.put("passed", true);
            write(new File(evidence, "result.json"), result.toString(2));
        } finally {
            write(new File(evidence, "last-visible-text.json"), new JSONArray(lastTexts).toString(2));
            device.dumpWindowHierarchy(new File(evidence, "window.xml"));
            device.takeScreenshot(new File(evidence, "screen.png"));
            BLocationManager.disableFakeLocation(0, GUEST);
            BlackBoxCore.get().stopPackage(GUEST, 0);
            Configurator.getInstance().setWaitForIdleTimeout(idle);
            apk.delete();
        }
    }

    private JSONArray awaitCoordinates(double lat, double lon) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 120000;
        while (SystemClock.elapsedRealtime() < deadline) {
            handleNormalPermissionDialog();
            AccessibilityNodeInfo root = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().getRootInActiveWindow();
            List<String> text = new ArrayList<>();
            // Android sanitizes accessibility packageName to the physical window owner's host UID.
            // First require this unmodified Guest's resource IDs, then inspect its active tree.
            if (hasGuestResource(root)) collect(root, text);
            lastTexts = text;
            boolean latitude = false, longitude = false;
            for (String value : text) {
                Matcher m = Pattern.compile("[-+]?\\d{1,3}[.,]\\d{4,}").matcher(value.replace('\u2212', '-'));
                while (m.find()) {
                    double number = Double.parseDouble(m.group().replace(',', '.'));
                    if (Math.abs(number - lat) < 0.00001) latitude = true;
                    if (Math.abs(number - lon) < 0.00001) longitude = true;
                }
            }
            if (latitude && longitude) return new JSONArray(text);
            dismissKnownWhatsNewDialog();
            SystemClock.sleep(150);
        }
        fail("Third-party coordinate UI did not reach target: " + lastTexts);
        return null;
    }

    private void collect(AccessibilityNodeInfo node, List<String> result) {
        if (node == null) return;
        if (node.getPackageName() != null && (GUEST.contentEquals(node.getPackageName())
                || "com.soozooq.tianwaitian.containerresearch".contentEquals(node.getPackageName()))
                && node.getText() != null) result.add(node.getText().toString());
        for (int i = 0; i < node.getChildCount(); i++) collect(node.getChild(i), result);
    }

    private boolean hasGuestResource(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.getViewIdResourceName() != null
                && node.getViewIdResourceName().startsWith(GUEST + ":id/")) return true;
        for (int i = 0; i < node.getChildCount(); i++) if (hasGuestResource(node.getChild(i))) return true;
        return false;
    }

    private void handleNormalPermissionDialog() throws Exception {
        for (String pkg : new String[] {"com.android.permissioncontroller", "com.google.android.permissioncontroller"}) {
            UiObject2 precise = device.findObject(By.res(pkg, "permission_location_accuracy_radio_fine"));
            if (precise != null && !precise.isChecked()) precise.click();
            UiObject2 allow = device.findObject(By.res(pkg, "permission_allow_foreground_only_button"));
            if (allow != null) {
                permissionActions.put(new JSONObject().put("system_package", pkg)
                        .put("action", "ALLOW_WHILE_IN_USE").put("elapsed_ms", SystemClock.elapsedRealtime()));
                allow.click();
            }
        }
    }

    private void dismissKnownWhatsNewDialog() {
        if (device.hasObject(By.textContains("What's new")) || device.hasObject(By.textContains("What's New"))) {
            UiObject2 ok = device.findObject(By.res("android", "button1"));
            if (ok != null && ("OK".equals(ok.getText()) || "Close".equals(ok.getText()))) ok.click();
        }
    }

    private void closeOnlySystemUiFailureDialog() {
        if (device.hasObject(By.text("System UI isn't responding"))
                || device.hasObject(By.text("System UI keeps stopping"))) {
            UiObject2 close = device.findObject(By.res("android", "aerr_close"));
            if (close != null) close.click();
        }
    }

    private static void write(File path, String data) throws Exception {
        try (FileOutputStream output = new FileOutputStream(path)) {
            output.write(data.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
    }
}
