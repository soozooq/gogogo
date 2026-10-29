package com.soozooq.tianwaitian.containerresearch;

import static org.junit.Assert.*;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import top.niunaijun.bcore.BlackBoxCore;
import top.niunaijun.bcore.entity.pm.InstallResult;
import top.niunaijun.bcore.fake.frameworks.BLocationManager;
import top.niunaijun.bcore.fake.frameworks.BActivityManager;
import top.niunaijun.bcore.entity.am.RunningAppProcessInfo;
import top.niunaijun.bcore.entity.location.BLocation;

/** Cloud-only anonymous startup; CVD also restarts with synthetic virtual input.
 * Never accepts terms, logs in, sends messages or claims target location adoption.
 */
@RunWith(AndroidJUnit4.class)
public final class OfficialTargetStartupInstrumentedTest {
    private final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private String pkg, sha, cert;
    private long size;
    private List<String> lastText = new ArrayList<>();

    private String shell(String command) throws Exception {
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(
                InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command))) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int n;
            while ((n = input.read(buffer)) != -1) bytes.write(buffer, 0, n);
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8).trim();
        }
    }
    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format(Locale.US, "%02x", value & 255));
        return result.toString();
    }
    private static void write(File file, JSONObject value) throws Exception {
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(value.toString(2).getBytes(StandardCharsets.UTF_8)); output.getFD().sync();
        }
        if (!temporary.renameTo(file)) throw new IllegalStateException("Startup receipt commit failed");
    }
    @Test public void pinnedOfficialTargetColdStartup() throws Exception {
        String key = InstrumentationRegistry.getArguments().getString("target");
        if ("wechat".equals(key)) {
            pkg = "com.tencent.mm"; size = 285284760L;
            sha = "f2a725bc04302ad218a563533ae79356e66b64aa711b275bc919a8d432f7e15d";
            cert = "0fe4ff85c215918396dadc7cd8ce6963339af33d37751a56e54c7206b63a3c7c";
        } else if ("douyin".equals(key)) {
            pkg = "com.ss.android.ugc.aweme"; size = 360503779L;
            sha = "9fc10c40b81b8244e515f9588d381f43e6ed870e6d9bebf14754a5e309ba380a";
            cert = "5182ae3b1b85337bb182cf24882449f84447ded18e296a747a9f6a0a2622512e";
        } else { fail("Only the two pinned original cloud inputs are admitted"); return; }
        assertEquals("2000", shell("id -u"));
        String api = InstrumentationRegistry.getArguments().getString("api");
        String runtime = InstrumentationRegistry.getArguments().getString("runtime", "official-translation");
        boolean cuttlefish = "cuttlefish-arm64".equals(runtime);
        boolean fullArm64 = cuttlefish || "full-arm64".equals(runtime);
        assertTrue("Explicit qualified cloud image and runtime",
                cuttlefish ? "33".equals(api) : fullArm64 ? ("32".equals(api) || "33".equals(api)) : "official-translation".equals(runtime)
                        && ("30".equals(api) || "32".equals(api)));
        if (cuttlefish) {
            assertEquals("cutf_cvm", shell("getprop ro.hardware"));
            assertEquals("aosp_cf_arm64_phone", shell("getprop ro.product.name"));
            assertEquals("16596636", shell("getprop ro.build.version.incremental"));
            assertEquals("50", shell("getprop ro.hw_timeout_multiplier"));
        } else {
            assertEquals("1", shell("getprop ro.kernel.qemu"));
        }
        assertEquals(api, shell("getprop ro.build.version.sdk"));
        assertEquals("1", shell("getprop sys.boot_completed"));
        assertEquals("Enforcing", shell("getenforce"));
        assertEquals("", shell("pm path " + pkg));
        assertTrue("Actual image must advertise ARM64 app support",
                shell("getprop ro.product.cpu.abilist").contains("arm64-v8a"));
        String bridge = shell("getprop ro.dalvik.vm.native.bridge");
        if (fullArm64) {
            assertEquals("arm64-v8a", shell("getprop ro.product.cpu.abi"));
            assertEquals("aarch64", shell("uname -m"));
            assertFalse(shell("getprop ro.product.cpu.abilist").contains("x86"));
            assertTrue("Full ARM64 must not use translation", bridge.isEmpty() || "0".equals(bridge));
        } else {
            assertTrue("Native Bridge must be enabled", !bridge.isEmpty() && !"0".equals(bridge));
        }
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File folder = new File(context.getFilesDir(), "target-startup-" + key);
        assertTrue(folder.isDirectory() || folder.mkdirs());
        File apk = new File(context.getFilesDir(), "pinned-target.apk");
        JSONObject report = new JSONObject().put("scope", cuttlefish
                ? "CVD_ARM64_ANONYMOUS_CLOSE_AND_VIRTUAL_STARTUP_NOT_TARGET_LOCATION_BUSINESS_OR_VIVO" : fullArm64
                ? "FULL_ARM64_EMULATOR_STARTUP_ONLY_NOT_PHYSICAL_VIVO_OR_LOCATION_BUSINESS"
                : "CLOUD_TRANSLATED_STARTUP_ONLY_NOT_API33_OR_VIVO").put("runtime", runtime)
                .put("target", key).put("package", pkg).put("sha256", sha).put("api", api)
                .put("location_enabled", false).put("terms_accepted", false).put("login_attempted", false)
                .put("target_location_business_tested", false)
                .put("passed", false);
        long idle = Configurator.getInstance().getWaitForIdleTimeout();
        Configurator.getInstance().setWaitForIdleTimeout(0);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation()
                            .executeShellCommand("cat /data/local/tmp/gogogo-official-target.apk"));
                 FileOutputStream output = new FileOutputStream(apk)) {
                long total = 0; int n; byte[] buffer = new byte[65536];
                while ((n = input.read(buffer)) != -1) {
                    total += n; assertTrue(total <= size); digest.update(buffer, 0, n); output.write(buffer, 0, n);
                }
                assertEquals(size, total); output.getFD().sync();
            }
            assertEquals(sha, hex(digest.digest())); assertTrue(apk.setReadOnly());
            report.put("last_completed_stage", "PINNED_INPUT_VERIFIED");
            write(new File(folder, "result.json"), report);
            InstallResult imported = BlackBoxCore.get().installPackageAsUser(apk, 0);
            report.put("import_success", imported.success);
            assertTrue("Pinned virtual import must succeed", imported.success);
            assertEquals(pkg, imported.packageName);
            report.put("last_completed_stage", "VIRTUAL_IMPORT_CONFIRMED");
            write(new File(folder, "result.json"), report);
            BLocationManager.disableFakeLocation(0, pkg);
            assertEquals(BLocationManager.CLOSE_MODE, BLocationManager.get().getPattern(0, pkg));
            PackageInfo identity = BlackBoxCore.getBPackageManager().getPackageInfo(pkg,
                    PackageManager.GET_SIGNING_CERTIFICATES, 0);
            report.put("package_info_present", identity != null);
            report.put("signing_info_present", identity != null && identity.signingInfo != null);
            assertNotNull("Imported virtual PackageInfo must be available", identity);
            assertNotNull("Original APK SigningInfo must be available", identity.signingInfo);
            assertNull("Internal legacy certificate flag must not change the requested fields", identity.signatures);
            assertEquals(1, identity.signingInfo.getApkContentsSigners().length);
            assertEquals(cert, hex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.signingInfo.getApkContentsSigners()[0].toByteArray())));
            report.put("original_signer", true);
            report.put("last_completed_stage", "ORIGINAL_SIGNER_CONFIRMED");
            write(new File(folder, "result.json"), report);
            Intent launch = BlackBoxCore.getBPackageManager().getLaunchIntentForPackage(pkg, 0);
            assertNotNull(launch); BlackBoxCore.get().startActivity(launch, 0);
            report.put("last_completed_stage", "TARGET_LAUNCH_REQUESTED");
            write(new File(folder, "result.json"), report);
            String stage = waitForAnonymousStage(key, cuttlefish ? 300000 : 90000);
            report.put("visible_text", new JSONArray(lastText));
            String pid = shell("pidof " + pkg);
            boolean ready = stage != null && !pid.isEmpty();
            report.put("guest_pid", pid);
            report.put("stage", stage == null ? "NOT_REACHED" : stage);
            if (ready && cuttlefish && "douyin".equals(key)) {
                report.put("last_completed_stage", "ANONYMOUS_UI_SEEN_BEFORE_HOLD");
                write(new File(folder, "result.json"), report);
                long holdStarted = SystemClock.elapsedRealtime();
                long holdDeadline = holdStarted + 300000;
                boolean stable = true;
                String current = pid;
                int hostUid = context.getApplicationInfo().uid;
                JSONArray initialProcesses = registeredProcesses();
                int mainPid = mainProcess(initialProcesses, hostUid);
                assertTrue("The registered main process must have a live system record and host UID", mainPid > 0);
                JSONArray currentProcesses = initialProcesses;
                boolean sameNamePidSetChanged = false;
                while (SystemClock.elapsedRealtime() < holdDeadline) {
                    SystemClock.sleep(1500);
                    current = shell("pidof " + pkg);
                    sameNamePidSetChanged |= !pid.equals(current);
                    currentProcesses = registeredProcesses();
                    if (mainProcess(currentProcesses, hostUid) != mainPid) { stable = false; break; }
                }
                report.put("post_ui_observation", new JSONObject().put("requested_seconds", 300)
                        .put("observed_ms", SystemClock.elapsedRealtime() - holdStarted)
                        .put("initial_target_pid", pid).put("last_target_pid", current)
                        .put("identity_basis", "CORE_REGISTERED_MAIN_PROCESS_AND_ACTUAL_ACTIVITY_MANAGER_PID_UID")
                        .put("main_pid", mainPid).put("main_uid", hostUid)
                        .put("initial_registered_processes", initialProcesses)
                        .put("last_registered_processes", currentProcesses)
                        .put("same_name_pid_set_changed", sameNamePidSetChanged).put("passed", stable));
                ready = stable;
                if (ready) {
                    List<String> text = new ArrayList<>();
                    collect(InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow(), text);
                    lastText = text;
                    String joined = String.join("\n", text).toLowerCase(Locale.ROOT);
                    ready = (joined.contains("隐私") || joined.contains("privacy"))
                            && (joined.contains("同意") || joined.contains("agree"));
                    report.put("post_hold_privacy_ui_visible", ready);
                }
            }
            assertTrue("Expected anonymous startup UI was not reached: " + lastText, ready);
            report.put("closed_startup_confirmed", true);
            report.put("closed_startup_observation", new JSONObject().put("passed", true)
                    .put("stage", stage).put("guest_pid", pid).put("input_pattern", BLocationManager.CLOSE_MODE));
            if (cuttlefish) {
                verifyVirtualStartup(key, launch, context, folder, report);
            }
            report.put("passed", true);
            report.put("last_completed_stage", cuttlefish
                    ? "CLOSED_AND_VIRTUAL_ANONYMOUS_UI_CONFIRMED" : "ANONYMOUS_UI_CONFIRMED");
        } catch (Exception | AssertionError error) {
            report.put("error_class", error.getClass().getName());
            throw error;
        } finally {
            try {
                captureBeforeCleanup(context, report);
                report.put("visible_text", new JSONArray(lastText));
                write(new File(folder, "result.json"), report);
                device.dumpWindowHierarchy(new File(folder, "window.xml"));
                device.takeScreenshot(new File(folder, "screen.png"));
            } finally {
                BlackBoxCore.get().stopPackage(pkg, 0);
                BLocationManager.disableFakeLocation(0, pkg);
                BlackBoxCore.get().uninstallPackageAsUser(pkg, 0);
                apk.delete(); Configurator.getInstance().setWaitForIdleTimeout(idle);
            }
        }
    }

    private void captureBeforeCleanup(Context context, JSONObject report) throws Exception {
        JSONObject snapshot = new JSONObject().put("scope", "READ_ONLY_BEFORE_STOP_OR_UNINSTALL_NOT_LOCATION_ADOPTION")
                .put("elapsed_ms", SystemClock.elapsedRealtime());
        report.put("before_cleanup_process_snapshot", snapshot);
        try {
            snapshot.put("registered_guest_processes", registeredProcesses());
            snapshot.put("target_pidof_auxiliary_only", shell("pidof " + pkg));
            int uid = context.getApplicationInfo().uid;
            JSONArray actual = new JSONArray();
            List<ActivityManager.RunningAppProcessInfo> processes = ((ActivityManager)
                    context.getSystemService(Context.ACTIVITY_SERVICE)).getRunningAppProcesses();
            if (processes != null) for (ActivityManager.RunningAppProcessInfo process : processes) {
                if (process.uid == uid && actual.length() < 16) actual.put(new JSONObject()
                        .put("pid", process.pid).put("uid", process.uid).put("process_name", process.processName));
            }
            snapshot.put("actual_host_uid_processes_max16", actual).put("actual_host_uid", uid);
            snapshot.put("foreground_package", device.getCurrentPackageName());
        } catch (Exception | AssertionError error) {
            snapshot.put("unavailable_error_class", error.getClass().getName());
        }
    }

    private String waitForAnonymousStage(String key, long waitMs) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + waitMs;
        while (SystemClock.elapsedRealtime() < deadline) {
            List<String> text = new ArrayList<>();
            collect(InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow(), text);
            lastText = text;
            String joined = String.join("\n", text).toLowerCase(Locale.ROOT);
            boolean privacy = (joined.contains("隐私") || joined.contains("privacy"))
                    && (joined.contains("同意") || joined.contains("agree"));
            if ("wechat".equals(key) && (joined.contains("log in") || joined.contains("登录"))
                    && (joined.contains("sign up") || joined.contains("注册"))) return "WELCOME_WITHOUT_LOGIN";
            if (privacy && (!"wechat".equals(key) || joined.contains("微信") || joined.contains("wechat")))
                return "PRIVACY_WITHOUT_ACCEPTANCE";
            SystemClock.sleep(300);
        }
        return null;
    }

    private void verifyVirtualStartup(String key, Intent launch, Context context, File folder, JSONObject report)
            throws Exception {
        int oldMain = mainProcess(registeredProcesses(), context.getApplicationInfo().uid);
        assertTrue("Closed-mode main must be known before restart", oldMain > 0);
        JSONObject own = new JSONObject().put("passed", false).put("synthetic_input_only", true)
                .put("target_location_business_tested", false).put("previous_main_pid", oldMain);
        report.put("virtual_startup_observation", own).put("last_completed_stage", "STOPPING_CLOSED_TARGET");
        write(new File(folder, "result.json"), report);
        // Match the phone flow: the stop control is on our own preview page.
        // Return there before stopping, so the platform need not restart a killed
        // foreground Activity. No target UI or consent control is clicked.
        context.startActivity(new Intent(context, ProbeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        long returnBy = SystemClock.elapsedRealtime() + 15000;
        while (!context.getPackageName().equals(device.getCurrentPackageName())
                && SystemClock.elapsedRealtime() < returnBy) SystemClock.sleep(300);
        assertEquals("Own preview must be foreground before its stop operation",
                context.getPackageName(), device.getCurrentPackageName());
        own.put("returned_to_host_before_stop", true);
        BlackBoxCore.get().stopPackage(pkg, 0);
        long stoppedBy = SystemClock.elapsedRealtime() + 30000;
        boolean stopped = false;
        while (SystemClock.elapsedRealtime() < stoppedBy) {
            if (shell("pidof " + pkg).isEmpty() && registeredProcesses().length() == 0) { stopped = true; break; }
            SystemClock.sleep(300);
        }
        own.put("previous_main_absent", stopped);
        assertTrue("Closed-mode processes must actually stop before virtual restart", stopped);
        BLocationManager.get().setLocation(0, pkg, new BLocation(38.0, -121.0));
        BLocationManager.get().setPattern(0, pkg, BLocationManager.OWN_MODE);
        int pattern = BLocationManager.get().getPattern(0, pkg);
        own.put("input_pattern", pattern); assertEquals(BLocationManager.OWN_MODE, pattern);
        report.put("location_enabled", true).put("last_completed_stage", "VIRTUAL_TARGET_LAUNCH_REQUESTED");
        write(new File(folder, "result.json"), report);
        BlackBoxCore.get().startActivity(launch, 0);
        String stage = waitForAnonymousStage(key, 300000);
        int newMain = mainProcess(registeredProcesses(), context.getApplicationInfo().uid);
        own.put("stage", stage == null ? "NOT_REACHED" : stage).put("main_pid", newMain)
                .put("main_uid", context.getApplicationInfo().uid).put("visible_text", new JSONArray(lastText));
        assertNotNull("Anonymous UI must also work with synthetic input; not SDK location adoption", stage);
        assertTrue("Virtual-mode main must be a new live process with the real host UID", newMain > 0 && newMain != oldMain);
        assertEquals(BLocationManager.OWN_MODE, BLocationManager.get().getPattern(0, pkg));
        own.put("passed", true);
        report.put("guest_pid", shell("pidof " + pkg)).put("stage", stage);
    }
    private JSONArray registeredProcesses() throws Exception {
        RunningAppProcessInfo running = BActivityManager.get().getRunningAppProcesses(pkg, 0);
        assertNotNull("Actual ActivityManager and registered Guest process snapshot", running);
        JSONArray snapshot = new JSONArray();
        for (ActivityManager.RunningAppProcessInfo process : running.mAppProcessInfoList) {
            snapshot.put(new JSONObject().put("pid", process.pid).put("uid", process.uid)
                    .put("process_name", process.processName));
        }
        return snapshot;
    }
    private int mainProcess(JSONArray processes, int expectedUid) throws Exception {
        int result = 0;
        for (int i = 0; i < processes.length(); i++) {
            JSONObject process = processes.getJSONObject(i);
            if (!pkg.equals(process.getString("process_name"))) continue;
            if (result != 0 || process.getInt("uid") != expectedUid) return 0;
            result = process.getInt("pid");
        }
        return result;
    }
    private void collect(AccessibilityNodeInfo node, List<String> text) {
        if (node == null) return;
        CharSequence owner = node.getPackageName();
        if (owner != null && (pkg.contentEquals(owner)
                || "com.soozooq.tianwaitian.containerresearch".contentEquals(owner)) && node.getText() != null) {
            text.add(node.getText().toString());
        }
        for (int i = 0; i < node.getChildCount(); i++) collect(node.getChild(i), text);
    }
}
