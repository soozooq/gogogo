package com.soozooq.tianwaitian.containerresearch;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityWindowInfo;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.StaleObjectException;
import androidx.test.uiautomator.Until;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import top.niunaijun.bcore.BlackBoxCore;
import top.niunaijun.bcore.fake.frameworks.BLocationManager;

/** Own UI only: real rendering and reachable controls, without starting a Guest. */
@RunWith(AndroidJUnit4.class)
public final class VisualLayoutInstrumentedTest {
    private static final String MAIN = "com.soozooq.gogogo.test";
    private static final String HOST = "com.soozooq.tianwaitian.containerresearch";
    private static final String GUEST = "com.android.gpstest.osmdroid";
    private final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private final JSONArray pages = new JSONArray();
    private final JSONArray issues = new JSONArray();
    private File evidence;
    private int profileWidthDp = 360;
    private String currentProfile;
    private JSONObject lastControl;

    @Test public void ownScreensRemainUsableAcrossThemesAndFontSizes() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        evidence = new File(context.getFilesDir(), "visual-layout-evidence");
        assertTrue(evidence.isDirectory() || evidence.mkdirs());
        long idle = Configurator.getInstance().getWaitForIdleTimeout();
        Configurator.getInstance().setWaitForIdleTimeout(0);
        boolean passed = false;
        try {
            assertFalse("No imported Guest for visual checks", BlackBoxCore.get().isInstalled(GUEST, 0));
            assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION));
            for (String profile : new String[] {"light-360", "dark-360", "light-large-320", "dark-large-320"}) {
                boolean large = profile.contains("large");
                profileWidthDp = large ? 320 : 360;
                currentProfile = profile;
                lastControl = null;
                try {
                shell("wm size " + (large ? "640x1280" : "720x1600"));
                shell("wm density 320");
                shell("settings put system font_scale " + (large ? "1.5" : "1.0"));
                shell("cmd uimode night " + (profile.startsWith("dark") ? "yes" : "no"));
                SystemClock.sleep(500);
                context.startActivity(new Intent().setClassName(MAIN, "com.zcshou.gogogo.TianwaitianHomeActivity")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                UiObject2 search = required(MAIN, "twt_home_search");
                required(MAIN, "twt_home_target");
                capture(profile + "-map");
                assertTapTarget(required(MAIN, "twt_home_search"));
                assertTapTarget(required(MAIN, "twt_bottom_navigation"));
                assertTapTarget(required(MAIN, "twt_home_controls"));
                required(MAIN, "twt_home_search").click();
                UiObject2 query = required(MAIN, "twt_search_query");
                assertTapTarget(query);
                query.setText("北京");
                assertTrue("Search input applied before capture", device.wait(Until.hasObject(
                        By.res(MAIN, "twt_search_query").text("北京")), 10000));
                capture(profile + "-search");

                context.startActivity(new Intent(context, ProbeActivity.class).setAction(ProbeActivity.PREFILL_ACTION)
                        .putExtra("coordinate_system", "WGS84").putExtra("guest_package", GUEST)
                        .putExtra("latitude", 38.125).putExtra("longitude", -121.875)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                UiObject2 open = required(HOST, "open_guest");
                assertFalse("Unimported app cannot be opened", open.isEnabled());
                assertTapTarget(open);
                assertTapTarget(required(HOST, "stop_guest"));
                assertTapTarget(required(HOST, "copy_preview_feedback"));
                capture(profile + "-preview");
                UiObject2 latitude = reveal("target_latitude");
                assertEquals("38.125", latitude.getText());
                assertEquals("-121.875", reveal("target_longitude").getText());
                latitude = reveal("target_latitude");
                latitude.click();
                latitude.setText("-33.86");
                assertTrue("Coordinate edit applied before capture", device.wait(Until.hasObject(
                        By.res(HOST, "target_latitude").text("-33.86")), 10000));
                long imeDeadline = SystemClock.elapsedRealtime() + 10000;
                while (!imeVisible() && SystemClock.elapsedRealtime() < imeDeadline) SystemClock.sleep(50);
                assertTrue("Actual input method visible for keyboard layout", imeVisible());
                assertTapTarget(reveal("target_latitude"));
                assertTapTarget(required(HOST, "open_guest"));
                assertTapTarget(required(HOST, "copy_preview_feedback"));
                capture(profile + "-keyboard");
                assertEquals("Opening a UI must not enable location", BLocationManager.CLOSE_MODE,
                        BLocationManager.get().getPattern(0, GUEST));
                assertFalse(BlackBoxCore.get().isInstalled(GUEST, 0));
                assertEquals("No location permission request in this flow", PackageManager.PERMISSION_DENIED,
                        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION));
                shell("am force-stop " + MAIN);
                } catch (Exception | AssertionError problem) {
                    JSONObject issue = new JSONObject().put("profile", profile)
                            .put("error_class", problem.getClass().getSimpleName())
                            .put("message", String.valueOf(problem.getMessage()));
                    if (lastControl != null) issue.put("control", lastControl);
                    issues.put(issue);
                    try { capture(profile + "-failure"); }
                    catch (Exception captureFailure) { issue.put("capture_error", captureFailure.getClass().getSimpleName()); }
                }
            }
            passed = issues.length() == 0;
            assertTrue("Every visual profile must pass: " + issues, passed);
        } finally {
            Configurator.getInstance().setWaitForIdleTimeout(idle);
            JSONObject result = new JSONObject().put("scope", "SELF_OWNED_VISUAL_UI_ONLY_NOT_TARGET_LOCATION")
                    .put("passed", passed).put("pages", pages).put("expected_page_count", 16)
                    .put("issues", issues)
                    .put("guest_imported", false).put("guest_launched", false)
                    .put("location_permission_requested", false).put("target_sdk_adoption_verified", false);
            if (lastControl != null) result.put("last_control", lastControl);
            try (FileOutputStream output = new FileOutputStream(new File(evidence, "result.json"))) {
                output.write(result.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private UiObject2 required(String pkg, String id) {
        long deadline = SystemClock.elapsedRealtime() + 30000;
        Rect previous = null; int stable = 0;
        while (SystemClock.elapsedRealtime() < deadline) {
            UiObject2 node = device.findObject(By.res(pkg, id));
            if (node != null) {
                try {
                    Rect bounds = node.getVisibleBounds();
                    if (bounds.width() > 0 && bounds.height() > 0 && bounds.equals(previous)) {
                        if (++stable >= 3) return node;
                    } else stable = 0;
                    previous = bounds;
                } catch (StaleObjectException recreated) { stable = 0; previous = null; }
            }
            SystemClock.sleep(50);
        }
        fail("Stable visible own UI: " + pkg + "/" + id);
        return null;
    }

    private UiObject2 reveal(String id) {
        for (int n = 0; n < 11; n++) {
            UiObject2 node = device.findObject(By.res(HOST, id));
            if (node != null && node.getVisibleBounds().height() >= unitsForDp(48)) return node;
            if (n == 10) break;
            UiObject2 form = required(HOST, "preview_form");
            // Re-query after every real swipe. Some ScrollViews move but do not
            // acknowledge scrolling: don't undo that movement on a false return.
            form.scroll(n < 5 ? Direction.DOWN : Direction.UP, 0.6f);
        }
        fail("Coordinate field unreachable: " + id);
        return null;
    }

    private boolean imeVisible() {
        for (AccessibilityWindowInfo window : InstrumentationRegistry.getInstrumentation().getUiAutomation().getWindows()) {
            if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) return true;
        }
        return false;
    }

    private int unitsForDp(int dp) {
        // UiAutomation can expose compatibility coordinates after display changes.
        // Convert the unchanged 48dp requirement within that same viewport space.
        return Math.round(dp * device.getDisplayWidth() / (float) profileWidthDp);
    }

    private void assertTapTarget(UiObject2 node) throws Exception {
        Rect bounds = node.getVisibleBounds();
        lastControl = new JSONObject().put("resource", node.getResourceName())
                .put("profile", currentProfile)
                .put("bounds", bounds.toShortString()).put("profile_width_dp", profileWidthDp)
                .put("automation_viewport_width", device.getDisplayWidth())
                .put("automation_viewport_height", device.getDisplayHeight())
                .put("minimum_height_dp", 48).put("minimum_height_in_viewport", unitsForDp(48));
        boolean largeEnough = bounds.height() >= unitsForDp(48);
        boolean inside = bounds.left >= 0 && bounds.top >= 0
                && bounds.right <= device.getDisplayWidth() && bounds.bottom <= device.getDisplayHeight();
        if (!largeEnough || !inside) {
            // Keep this a failing check, but collect the remaining screenshots
            // so one bad target does not hide every other page/theme.
            issues.put(lastControl.put("criterion", "48DP_VISIBLE_AND_INSIDE_VIEWPORT")
                    .put("height_passed", largeEnough).put("viewport_passed", inside));
        }
    }

    private void capture(String name) throws Exception {
        assertTrue(device.takeScreenshot(new File(evidence, name + ".png")));
        device.dumpWindowHierarchy(new File(evidence, name + ".xml"));
        pages.put(new JSONObject().put("name", name).put("automation_viewport_width", device.getDisplayWidth())
                .put("automation_viewport_height", device.getDisplayHeight()).put("profile_width_dp", profileWidthDp)
                .put("input_method_window_visible", imeVisible()));
    }

    private String shell(String command) throws Exception {
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(
                InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command))) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) {
                assertTrue(output.size() + count <= 65536);
                output.write(buffer, 0, count);
            }
            return output.toString("UTF-8").trim();
        }
    }
}
