package com.soozooq.tianwaitian.containerresearch;

import static org.junit.Assert.*;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.UUID;
import top.niunaijun.bcore.BlackBoxCore;
import top.niunaijun.bcore.core.env.BEnvironment;
import top.niunaijun.bcore.entity.pm.InstallResult;
import top.niunaijun.bcore.fake.frameworks.BLocationManager;

/** Tests the observed framework prerequisites using an own guest, without a third-party account. */
@RunWith(AndroidJUnit4.class)
public final class StartupFrameworkInstrumentedTest {
    @Test public void ownGuestResolvesLegacyHttpAndCreatesPendingIntents() throws Exception {
        String pkg = "com.soozooq.tianwaitian.locationconsumer";
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File apk = new File(context.getFilesDir(), "startup-contract-input.apk");
        String expected = InstrumentationRegistry.getArguments().getString("guest_sha256");
        assertNotNull("Own build SHA required", expected);
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try {
            try (InputStream source = new ParcelFileDescriptor.AutoCloseInputStream(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation()
                            .executeShellCommand("cat /data/local/tmp/gogogo-location-consumer.apk"));
                 FileOutputStream target = new FileOutputStream(apk)) {
                byte[] buffer = new byte[65536]; int count; long total = 0;
                while ((count = source.read(buffer)) != -1) {
                    total += count; assertTrue("Own test input bound", total < 1024 * 1024);
                    hash.update(buffer, 0, count); target.write(buffer, 0, count);
                }
                target.getFD().sync();
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : hash.digest()) hex.append(String.format(java.util.Locale.US, "%02x", value & 255));
            assertEquals(expected, hex.toString());
            assertTrue(apk.setReadOnly());
            InstallResult imported = BlackBoxCore.get().installPackageAsUser(apk, 0);
            assertTrue(imported.success); assertEquals(pkg, imported.packageName);
            BLocationManager.disableFakeLocation(0, pkg);
            Intent launch = new Intent().setComponent(new ComponentName(pkg, pkg + ".StartupContractActivity"));
            int hostUid = context.getApplicationInfo().uid;
            assertEquals("Capture the system UID before entering the virtual guest", android.os.Process.myUid(), hostUid);
            assertEquals("Cached host UID must agree with the real host process", BlackBoxCore.getHostUid(), hostUid);
            launch.putExtra("expected_host_uid", hostUid);
            String runId = UUID.randomUUID().toString();
            launch.putExtra("startup_contract_run_id", runId);
            File receipt = new File(BEnvironment.getDataDir(pkg, 0), "files/startup-contract.json");
            assertTrue("Do not accept a previous guest receipt", !receipt.exists() || receipt.delete());
            BlackBoxCore.get().startActivity(launch, 0);
            long deadline = SystemClock.elapsedRealtime() + 60000;
            while (!receipt.isFile() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(300);
            assertTrue("Own guest must return its framework receipt", receipt.isFile());
            JSONObject observed = new JSONObject(new String(Files.readAllBytes(receipt.toPath()), StandardCharsets.UTF_8));
            File folder = new File(context.getFilesDir(), "startup-framework-evidence");
            assertTrue(folder.isDirectory() || folder.mkdirs());
            try (FileOutputStream output = new FileOutputStream(new File(folder, "result.json"))) {
                output.write(observed.toString(2).getBytes(StandardCharsets.UTF_8)); output.getFD().sync();
            }
            assertEquals("The receipt must belong to this launch", runId, observed.getString("startup_contract_run_id"));
            assertTrue(observed.optBoolean("framework_http_legacy_class_resolved"));
            assertTrue(observed.optBoolean("pending_activity_created"));
            assertTrue(observed.optBoolean("pending_service_created"));
            assertEquals(hostUid, observed.getInt("expected_real_host_uid"));
            assertEquals("Public Binder caller UID outside a transaction is the system UID",
                    hostUid, observed.getInt("binder_self_uid"));
            assertTrue(observed.getInt("binder_self_pid") > 0);
            assertEquals(observed.getInt("process_api_pid"), observed.getInt("binder_self_pid"));
            assertTrue(observed.optBoolean("binder_self_uid_matches_real_host"));
            assertEquals(hostUid, observed.getInt("activity_attribution_uid"));
            assertEquals(hostUid, observed.getInt("application_attribution_uid"));
            assertTrue(observed.optBoolean("activity_attribution_uid_matches_real_host"));
            assertTrue(observed.optBoolean("application_attribution_uid_matches_real_host"));
            assertEquals(context.getPackageName(), observed.getString("activity_attribution_package"));
            assertFalse(observed.optBoolean("pending_intents_sent"));
            assertFalse(observed.optBoolean("location_requested"));
            assertTrue(observed.optBoolean("passed"));
        } finally {
            BlackBoxCore.get().stopPackage(pkg, 0);
            BLocationManager.disableFakeLocation(0, pkg);
            BlackBoxCore.get().uninstallPackageAsUser(pkg, 0);
            apk.delete();
        }
    }
}
