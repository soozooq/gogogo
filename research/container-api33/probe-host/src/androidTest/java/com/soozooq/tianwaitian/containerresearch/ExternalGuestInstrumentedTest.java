package com.soozooq.tianwaitian.containerresearch;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.niunaijun.bcore.fake.frameworks.BLocationManager;
import top.niunaijun.bcore.BlackBoxCore;

/** Real device-side UI assertions against an unmodified third-party release. */
@RunWith(AndroidJUnit4.class)
public final class ExternalGuestInstrumentedTest {
    private final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    @Before public void clearOnlyOldSoftwareEmulatorSystemUiDialog() {
        UiObject2 title = device.findObject(By.res("android", "alertTitle"));
        if (title != null && "System UI isn't responding".equals(title.getText())) {
            UiObject2 close = device.findObject(By.res("android", "aerr_close"));
            if (close != null) {
                System.out.println("SETUP: restarting stalled software-emulator System UI through its own ANR dialog; guest errors remain failures");
                close.click();
            }
        }
    }

    private String shell(String command) throws Exception {
        try (InputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(
                InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command))) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }

    @Test public void installedCalculatorControl() throws Exception {
        assertEquals(33, Build.VERSION.SDK_INT);
        assertTrue("Installed-app control requires the real system package", !shell("pm path org.fossify.math").isEmpty());
        startInstalledCalculator();
        calculateThree();
        device.pressHome();
        startInstalledCalculator();
        assertResultIsThree();
    }

    private void startInstalledCalculator() throws Exception {
        String resolved = shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p org.fossify.math");
        String[] lines = resolved.split("\\n");
        String component = lines[lines.length - 1].trim();
        assertTrue("No explicit launcher component: " + resolved,
                component.matches("org\\.fossify\\.math/[A-Za-z0-9_.$]+"));
        // UiAutomation tokenizes argv directly; shell quoting would become literal.
        String outcome = shell("am start -n " + component);
        assertFalse(outcome, outcome.contains("Error:"));
    }

    @Test public void calculatorRunsUninstalledAndRetainsResultAfterHome() throws Exception {
        assertEquals("API 33 runtime required", 33, Build.VERSION.SDK_INT);
        assertTrue("Host must execute as an application UID", Process.myUid() >= 10000);
        assertEquals("Cloud setup must use ordinary adb shell", "2000", shell("id -u"));
        assertEquals("Guest must not be installed in the system", "", shell("pm path org.fossify.math"));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File apk = new File(context.getFilesDir(), "external-calculator.apk");
        try {
            // Test-only shell FD from this dedicated cloud emulator, never a phone export/API.
            try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation()
                            .executeShellCommand("cat /data/local/tmp/gogogo-research-guest.apk"));
                 FileOutputStream output = new FileOutputStream(apk)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    assertTrue("Guest APK too large", total <= GuestImport.MAX_BYTES);
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
            GuestImport.verify(apk);
            assertTrue(apk.setReadOnly());
            GuestImport.installVerified(apk);
            assertOriginalGuestSignatures();
            BLocationManager.disableFakeLocation(0, GuestImport.PACKAGE);
            assertTrue("Launch must be requested", GuestImport.requestLaunch());
            calculateThree();
            device.pressHome();
            assertTrue(GuestImport.requestLaunch());
            assertResultIsThree();
            assertEquals("Guest must remain uninstalled in the system", "", shell("pm path org.fossify.math"));
        } finally { apk.delete(); }
    }

    private void assertOriginalGuestSignatures() throws Exception {
        PackageInfo legacy = BlackBoxCore.getBPackageManager().getPackageInfo(GuestImport.PACKAGE,
                PackageManager.GET_SIGNATURES, 0);
        PackageInfo modern = BlackBoxCore.getBPackageManager().getPackageInfo(GuestImport.PACKAGE,
                PackageManager.GET_SIGNING_CERTIFICATES, 0);
        assertNotNull("Virtual legacy package metadata", legacy);
        assertNotNull("Virtual modern package metadata", modern);
        assertNotNull("Original SigningInfo", modern.signingInfo);
        Signature[] signers = modern.signingInfo.getApkContentsSigners();
        assertNotNull(legacy.signatures);
        assertEquals(1, legacy.signatures.length);
        assertEquals(1, signers.length);
        String expected = "affdb124d3f4720c2f98dbca9eacba0514fba4306e20a2786c861c3c0d6ff292";
        assertEquals(expected, certificateSha256(legacy.signatures[0]));
        assertEquals(expected, certificateSha256(signers[0]));
        System.out.println("ORIGINAL_GUEST_SIGNER_SHA256=" + expected
                + "; legacy_and_modern_metadata=PASS; system_guest_installed=false");
    }

    private static String certificateSha256(Signature signature) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
        StringBuilder hex = new StringBuilder();
        for (byte value : hash) hex.append(String.format(Locale.US, "%02x", value & 255));
        return hex.toString();
    }

    private void calculateThree() {
        // The actual portrait UI resets through a long press on its visible C key.
        button("btn_clear").longClick();
        button("btn_1").click();
        button("btn_plus").click();
        button("btn_2").click();
        button("btn_equals").click();
        assertResultIsThree();
    }

    private UiObject2 button(String id) {
        UiObject2 view = device.wait(Until.findObject(By.res(GuestImport.PACKAGE, id)), 120000);
        assertNotNull("Third-party view missing: " + id, view);
        return view;
    }

    private void assertResultIsThree() {
        long end = SystemClock.elapsedRealtime() + 15000;
        String last = "MISSING";
        while (SystemClock.elapsedRealtime() < end) {
            UiObject2 result = device.findObject(By.res(GuestImport.PACKAGE, "result"));
            if (result != null) {
                last = result.getText();
                try { if (new BigDecimal(last.trim()).compareTo(BigDecimal.valueOf(3)) == 0) return; }
                catch (RuntimeException invalid) { /* Wait for the real UI result. */ }
            }
            SystemClock.sleep(100);
        }
        fail("Actual calculator result was " + last);
    }

    @After public void saveActualUiEvidence() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(context.getFilesDir(), "runtime-evidence");
        if (!directory.isDirectory()) assertTrue(directory.mkdirs());
        device.dumpWindowHierarchy(new File(directory, "window.xml"));
        assertTrue(device.takeScreenshot(new File(directory, "screen.png")));
    }
}
