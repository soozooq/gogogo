package com.soozooq.tianwaitian.containerresearch;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.util.AtomicFile;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import top.niunaijun.bcore.BlackBoxCore;
import top.niunaijun.bcore.core.env.BEnvironment;
import top.niunaijun.bcore.entity.location.BLocation;
import top.niunaijun.bcore.entity.pm.InstallResult;
import top.niunaijun.bcore.fake.frameworks.BLocationManager;

/** Actual owned Guest process and public APIs; no test proxy or direct hook invocation. */
@RunWith(AndroidJUnit4.class)
public final class GuestLocationInstrumentedTest {
    private static final String GUEST = "com.soozooq.tianwaitian.locationconsumer";
    private final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private final JSONArray stages = new JSONArray();
    private String lastText = "NO_GUEST_REPORT";
    private File stageEvidence;
    private File guestReceipt;
    private String expectedNonce;
    private long lastReportSequence;

    private interface Condition { boolean matches(JSONObject report) throws Exception; }

    private String shell(String command) throws Exception {
        try (InputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(
                InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command))) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }

    @Test public void publicLocationApisInUninstalledGuestFollowTargetAndStop() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File evidence = new File(context.getFilesDir(), "guest-location-evidence");
        assertTrue(evidence.isDirectory() || evidence.mkdirs());
        stageEvidence = new File(evidence, "stages.json");
        File apk = new File(context.getFilesDir(), "owned-location-consumer.apk");
        long oldIdleTimeout = Configurator.getInstance().getWaitForIdleTimeout();
        // Streaming text deliberately keeps changing. Waiting for global UI idleness is invalid here.
        Configurator.getInstance().setWaitForIdleTimeout(0);
        String expected = InstrumentationRegistry.getArguments().getString("consumer_sha256", "");
        assertTrue("Runner must pin this independently built APK", expected.matches("[0-9a-f]{64}"));
        assertEquals("Guest must not be system-installed", "", shell("pm path " + GUEST));
        if (device.hasObject(By.text("System UI isn't responding"))
                || device.hasObject(By.text("System UI keeps stopping"))) {
            UiObject2 close = device.findObject(By.res("android", "aerr_close"));
            if (close != null) close.click();
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation()
                            .executeShellCommand("cat /data/local/tmp/gogogo-location-consumer.apk"));
                 FileOutputStream output = new FileOutputStream(apk)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int n;
                while ((n = input.read(buffer)) != -1) {
                    total += n;
                    assertTrue("Owned consumer size cap", total <= 2 * 1024 * 1024);
                    digest.update(buffer, 0, n);
                    output.write(buffer, 0, n);
                }
                output.getFD().sync();
            }
            StringBuilder actual = new StringBuilder();
            for (byte value : digest.digest()) actual.append(String.format(Locale.US, "%02x", value & 255));
            assertEquals(expected, actual.toString());
            PackageInfo archive = context.getPackageManager().getPackageArchiveInfo(apk.getPath(), 0);
            assertNotNull(archive);
            assertEquals(GUEST, archive.packageName);
            assertTrue(apk.setReadOnly());
            InstallResult imported = BlackBoxCore.get().installPackageAsUser(apk, 0);
            assertTrue("Import failed: " + imported.msg, imported.success);
            assertEquals(GUEST, imported.packageName);
            BLocationManager.get().setLocation(0, GUEST, new BLocation(38.0, -121.0));
            BLocationManager.get().setPattern(0, GUEST, BLocationManager.OWN_MODE);
            String nonce = UUID.randomUUID().toString();
            expectedNonce = nonce;
            guestReceipt = new File(BEnvironment.getDataDir(GUEST, 0), "files/location-status.json");
            new AtomicFile(guestReceipt).delete();
            assertFalse("Do not accept a previous own Guest report", guestReceipt.exists());
            Intent launch = BlackBoxCore.getBPackageManager().getLaunchIntentForPackage(GUEST, 0);
            assertNotNull(launch);
            launch.putExtra("test_nonce", nonce); // correlation only; no expected coordinates
            BlackBoxCore.get().startActivity(launch, 0);
            JSONObject ready = await(report -> nonce.equals(report.optString("nonce")));
            assertEquals(GUEST, ready.getString("package"));
            int guestPid = ready.getInt("pid");
            assertTrue(guestPid > 0);
            assertNotEquals("Real separate process required", Process.myPid(), guestPid);
            String kernelProcess = shell("ps -p " + guestPid + " -o PID,UID,NAME");
            // VirtualRuntime changes argv[0] to the Guest name. Check the kernel PID/UID too.
            String[] rows = kernelProcess.split("\\n");
            String[] identity = rows[rows.length - 1].trim().split("\\s+", 3);
            assertEquals("Kernel process record: " + kernelProcess, 3, identity.length);
            assertEquals(guestPid, Integer.parseInt(identity[0]));
            assertEquals("Guest shares the real host UID", Process.myUid(), Integer.parseInt(identity[1]));
            assertEquals(GUEST, identity[2]);
            tap("start");
            JSONObject first = await(report -> allSamples(report, 38.0, -121.0)
                    && report.optInt("gps_count") >= 2 && report.optInt("network_count") >= 2
                    && report.optInt("gnss_started") == 1 && report.optInt("gnss_status_count") >= 1);
            assertEquals("Virtual GNSS never invents satellite observations", 0, first.getInt("gnss_satellite_count"));
            assertEquals("No claimed physical first fix", 0, first.getInt("gnss_first_fix_count"));
            saveStage("TARGET_A", first);

            // Only the host config knows B. The Guest refreshes through public Android APIs.
            BLocationManager.get().setLocation(0, GUEST, new BLocation(-33.86, 151.20));
            tap("refresh");
            JSONObject moved = await(report -> report.optInt("read_sequence") > first.optInt("read_sequence")
                    && allSamples(report, -33.86, 151.20));
            assertEquals(guestPid, moved.getInt("pid"));
            saveStage("TARGET_B", moved);
            tap("stop");
            await(report -> !report.optBoolean("listening", true));
            SystemClock.sleep(350); // allow a delivery already in flight to settle
            JSONObject stopped = await(report -> nonce.equals(report.optString("nonce"))
                    && !report.optBoolean("listening", true));
            SystemClock.sleep(800);
            JSONObject stable = await(report -> nonce.equals(report.optString("nonce"))
                    && !report.optBoolean("listening", true));
            assertEquals(stopped.getInt("gps_count"), stable.getInt("gps_count"));
            assertEquals(stopped.getInt("network_count"), stable.getInt("network_count"));
            assertEquals(stopped.getInt("gnss_status_count"), stable.getInt("gnss_status_count"));
            saveStage("UNREGISTERED_STABLE", stable);
            tap("cancel");
            await(report -> report.optInt("cancel_attempts") == 1);
            SystemClock.sleep(500);
            JSONObject cancelled = await(report -> nonce.equals(report.optString("nonce"))
                    && report.optInt("cancel_attempts") == 1);
            assertEquals(0, cancelled.getInt("cancelled_callbacks"));
            saveStage("CURRENT_CANCELLED", cancelled);
            assertEquals("Guest remains absent from system", "", shell("pm path " + GUEST));
            JSONObject summary = new JSONObject();
            summary.put("scope", "OWNED_GUEST_REAL_PROCESS_NOT_THIRD_PARTY_OR_WECHAT");
            summary.put("host_pid", Process.myPid());
            summary.put("guest_pid", guestPid);
            summary.put("kernel_process", kernelProcess);
            summary.put("consumer_sha256", expected);
            summary.put("nonce", nonce);
            summary.put("stages", stages);
            summary.put("report_source", "OWN_GUEST_ATOMIC_PRIVATE_FILE");
            summary.put("passed", true);
            write(new File(evidence, "result.json"), summary.toString(2));
        } finally {
            try {
                write(new File(evidence, "last-guest-report.json"), lastText);
                device.dumpWindowHierarchy(new File(evidence, "window.xml"));
                device.takeScreenshot(new File(evidence, "screen.png"));
            } finally {
                try { BLocationManager.disableFakeLocation(0, GUEST); }
                finally {
                    try { BlackBoxCore.get().stopPackage(GUEST, 0); }
                    finally { apk.delete(); Configurator.getInstance().setWaitForIdleTimeout(oldIdleTimeout); }
                }
            }
        }
    }

    private void saveStage(String name, JSONObject report) throws Exception {
        stages.put(new JSONObject().put("name", name).put("guest_report", report));
        write(stageEvidence, new JSONObject().put("scope", "PARTIAL_OWNED_GUEST_STAGES_NOT_FULL_PASS")
                .put("stages", stages).put("report_source", "OWN_GUEST_ATOMIC_PRIVATE_FILE")
                .put("passed", false).toString(2));
    }

    private static void write(File path, String value) throws Exception {
        try (FileOutputStream output = new FileOutputStream(path)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
    }

    private void tap(String id) {
        UiObject2 button = device.wait(Until.findObject(By.res(GUEST, id)), 120000);
        assertNotNull("Guest button " + id, button);
        button.click();
    }

    private JSONObject current() throws Exception {
        if (!guestReceipt.isFile()) return new JSONObject();
        assertTrue("Own report size bound", guestReceipt.length() <= 32768);
        lastText = new String(Files.readAllBytes(guestReceipt.toPath()), StandardCharsets.UTF_8);
        JSONObject report = new JSONObject(lastText);
        assertEquals("Own report must belong to this launch", expectedNonce, report.getString("nonce"));
        long sequence = report.getLong("report_sequence");
        assertTrue("Own report sequence cannot go backwards", sequence > 0 && sequence >= lastReportSequence);
        lastReportSequence = sequence;
        long reportAge = SystemClock.elapsedRealtimeNanos() - report.getLong("report_elapsed_ns");
        assertTrue("Own report cannot come from the future", reportAge >= 0);
        assertFalse("Guest API failure: " + lastText, report.has("error"));
        if (reportAge > 30_000_000_000L) return new JSONObject();
        return report;
    }

    private JSONObject await(Condition condition) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 120000;
        while (SystemClock.elapsedRealtime() < deadline) {
            JSONObject report = current();
            if (condition.matches(report)) return report;
            SystemClock.sleep(100);
        }
        fail("Guest condition timed out: " + lastText);
        return null;
    }

    private static boolean allSamples(JSONObject report, double lat, double lon) throws Exception {
        for (String key : new String[] {"last_gps", "last_network", "current_gps", "current_network",
                "stream_gps", "stream_network"}) {
            JSONObject sample = report.optJSONObject(key);
            String provider = key.endsWith("network") ? "network" : "gps";
            if (sample == null || !sample.optBoolean("present") || !sample.optBoolean("mock")
                    || sample.optInt("extra_satellites", -1) != 0
                    || !provider.equals(sample.optString("provider"))
                    || !sample.has("lat") || !sample.has("lon")
                    || !Double.isFinite(sample.getDouble("lat")) || !Double.isFinite(sample.getDouble("lon"))
                    || Math.abs(sample.getDouble("lat") - lat) > 0.000001
                    || Math.abs(sample.getDouble("lon") - lon) > 0.000001
                    || sample.optLong("elapsed_ns") <= 0 || sample.optLong("age_ms", -1) < 0
                    || sample.getLong("age_ms") > 30000
                    || SystemClock.elapsedRealtimeNanos() - sample.getLong("elapsed_ns") > 30_000_000_000L)
                return false;
        }
        return true;
    }
}
