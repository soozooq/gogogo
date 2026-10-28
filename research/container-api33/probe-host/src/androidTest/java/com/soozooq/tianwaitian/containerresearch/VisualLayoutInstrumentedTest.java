package com.soozooq.tianwaitian.containerresearch;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
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
    private File evidence;

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
                shell("wm size " + (large ? "640x1280" : "720x1600"));
                shell("wm density 320");
                shell("settings put system font_scale " + (large ? "1.5" : "1.0"));
                shell("cmd uimode night " + (profile.startsWith("dark") ? "yes" : "no"));
                SystemClock.sleep(500);
                context.startActivity(new Intent().setClassName(MAIN, "com.zcshou.gogogo.TianwaitianHomeActivity")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                UiObject2 search = required(MAIN, "twt_home_search");
                required(MAIN, "twt_home_target");
                assertTapTarget(search);
                assertTapTarget(required(MAIN, "twt_bottom_navigation"));
                capture(profile + "-map");
                search.click();
                UiObject2 query = required(MAIN, "twt_search_query");
                assertTapTarget(query);
                query.setText("北京");
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
                assertTapTarget(required(HOST, "open_guest"));
                assertTapTarget(required(HOST, "copy_preview_feedback"));
                capture(profile + "-keyboard");
                assertEquals("Opening a UI must not enable location", BLocationManager.CLOSE_MODE,
                        BLocationManager.get().getPattern(0, GUEST));
                assertFalse(BlackBoxCore.get().isInstalled(GUEST, 0));
                assertEquals("No location permission request in this flow", PackageManager.PERMISSION_DENIED,
                        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION));
                shell("am force-stop " + MAIN);
            }
            passed = true;
        } finally {
            Configurator.getInstance().setWaitForIdleTimeout(idle);
            JSONObject result = new JSONObject().put("scope", "SELF_OWNED_VISUAL_UI_ONLY_NOT_TARGET_LOCATION")
                    .put("passed", passed).put("pages", pages).put("expected_page_count", 16)
                    .put("guest_imported", false).put("guest_launched", false)
                    .put("location_permission_requested", false).put("target_sdk_adoption_verified", false);
            try (FileOutputStream output = new FileOutputStream(new File(evidence, "result.json"))) {
                output.write(result.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private UiObject2 required(String pkg, String id) {
        UiObject2 node = device.wait(Until.findObject(By.res(pkg, id)), 30000);
        assertNotNull("Visible own UI: " + pkg + "/" + id, node);
        return node;
    }

    private UiObject2 reveal(String id) {
        for (int n = 0; n < 8; n++) {
            UiObject2 node = device.findObject(By.res(HOST, id));
            if (node != null && node.getVisibleBounds().height() >= 48 * 2) return node;
            UiObject2 form = required(HOST, "preview_form");
            if (!form.scroll(Direction.DOWN, 0.5f)) form.scroll(Direction.UP, 1f);
        }
        fail("Coordinate field unreachable: " + id);
        return null;
    }

    private void assertTapTarget(UiObject2 node) {
        Rect bounds = node.getVisibleBounds();
        assertTrue("48dp minimum visible height: " + node.getResourceName(), bounds.height() >= 96);
        assertTrue("Control within viewport", bounds.left >= 0 && bounds.top >= 0
                && bounds.right <= device.getDisplayWidth() && bounds.bottom <= device.getDisplayHeight());
    }

    private void capture(String name) throws Exception {
        assertTrue(device.takeScreenshot(new File(evidence, name + ".png")));
        device.dumpWindowHierarchy(new File(evidence, name + ".xml"));
        pages.put(new JSONObject().put("name", name).put("screen_width_px", device.getDisplayWidth())
                .put("screen_height_px", device.getDisplayHeight()));
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
