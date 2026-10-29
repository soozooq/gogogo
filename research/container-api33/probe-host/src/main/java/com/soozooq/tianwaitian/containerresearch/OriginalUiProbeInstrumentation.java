package com.soozooq.tianwaitian.containerresearch;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Xml;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlSerializer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Targets the own driver's plain Application. No engine, input events, idle wait or app launch. */
public final class OriginalUiProbeInstrumentation extends Instrumentation {
    private Bundle arguments;
    private String expectedPackage;
    private int nodes;
    private List<String> text;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = new Bundle(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle output = new Bundle();
        JSONObject report = new JSONObject();
        int resultCode = Activity.RESULT_CANCELED;
        try {
            String target = arguments.getString("target", "");
            boolean fixture = "owned-fixture".equals(target);
            if ("wechat".equals(target)) expectedPackage = "com.tencent.mm";
            else if ("douyin".equals(target)) expectedPackage = "com.ss.android.ugc.aweme";
            else if (fixture) expectedPackage = "com.soozooq.tianwaitian.locationconsumer";
            else throw new IllegalArgumentException("Only pinned originals or the own fixture are admitted");
            if (Build.VERSION.SDK_INT != 33
                    || !getTargetContext().getPackageName().equals("com.soozooq.tianwaitian.containerresearch.test")
                    || getTargetContext().getApplicationContext().getClass() != Application.class) {
                throw new IllegalStateException("API33 own driver with a plain Application required");
            }
            report.put("scope", fixture ? "SELF_OWNED_UI_CAPTURE_ONLY_NOT_TARGET_STARTUP"
                    : "ORIGINAL_INSTALLED_READ_ONLY_UI_NOT_CONTAINER_OR_LOCATION");
            report.put("target", target).put("package", expectedPackage).put("api", Build.VERSION.SDK_INT)
                    .put("capture_method", "UI_AUTOMATION_WITHOUT_IDLE_WAIT")
                    .put("own_driver_targeting", true).put("plain_application", true)
                    .put("passed", false).put("terms_accepted", false)
                    .put("login_attempted", false).put("messages_sent", false).put("input_events_sent", false);
            long started = SystemClock.elapsedRealtime();
            long deadline = started + (fixture ? 10000 : 300000);
            String stage = null, xml = "", rootPackage = "";
            List<String> lastText = new ArrayList<>();
            do {
                nodes = 0;
                text = new ArrayList<>();
                AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
                if (root != null) {
                    try {
                        rootPackage = root.getPackageName() == null ? "" : root.getPackageName().toString();
                        StringWriter buffer = new StringWriter();
                        XmlSerializer serializer = Xml.newSerializer();
                        serializer.setOutput(buffer);
                        serializer.startDocument("UTF-8", true);
                        serializer.startTag(null, "hierarchy");
                        collect(root, serializer, 0);
                        serializer.endTag(null, "hierarchy");
                        serializer.endDocument();
                        xml = buffer.toString();
                        lastText = text;
                        if (expectedPackage.equals(rootPackage)) stage = classify(target, lastText);
                    } finally { root.recycle(); }
                }
                if (stage != null) break;
                SystemClock.sleep(500);
            } while (SystemClock.elapsedRealtime() < deadline);
            report.put("root_package", rootPackage).put("visible_text", new JSONArray(lastText))
                    .put("stage", stage == null ? "NOT_REACHED" : stage)
                    .put("elapsed_ms", SystemClock.elapsedRealtime() - started).put("passed", stage != null);
            File folder = new File(getTargetContext().getFilesDir(), "original-ui-" + target);
            if (!folder.isDirectory() && !folder.mkdirs()) throw new IllegalStateException("Capture directory unavailable");
            write(new File(folder, "window.xml"), xml);
            Bitmap screenshot = getUiAutomation().takeScreenshot();
            if (screenshot != null) {
                try (FileOutputStream stream = new FileOutputStream(new File(folder, "screen.png"))) {
                    if (!screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        throw new IllegalStateException("Screenshot encoding failed");
                    }
                } finally { screenshot.recycle(); }
            }
            write(new File(folder, "result.json"), report.toString(2));
            if (stage != null) resultCode = Activity.RESULT_OK;
        } catch (Exception error) {
            output.putString("error_class", error.getClass().getName());
        }
        output.putString("capture_report", report.toString());
        finish(resultCode, output);
    }

    private void collect(AccessibilityNodeInfo node, XmlSerializer serializer, int depth) throws Exception {
        if (++nodes > 4096 || depth > 60) return;
        String owner = node.getPackageName() == null ? "" : node.getPackageName().toString();
        String value = node.getText() == null ? "" : node.getText().toString();
        if (value.length() > 12000) value = value.substring(0, 12000);
        serializer.startTag(null, "node");
        serializer.attribute(null, "package", owner);
        serializer.attribute(null, "text", value);
        serializer.attribute(null, "visible", Boolean.toString(node.isVisibleToUser()));
        if (expectedPackage.equals(owner) && node.isVisibleToUser() && !value.isEmpty()) text.add(value);
        for (int i = 0; i < node.getChildCount() && nodes < 4096; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                try { collect(child, serializer, depth + 1); }
                finally { child.recycle(); }
            }
        }
        serializer.endTag(null, "node");
    }

    private static String classify(String target, List<String> values) {
        String value = String.join("\n", values).toLowerCase(Locale.ROOT);
        if ("owned-fixture".equals(target) && value.contains("read-only moving fixture")) return "OWN_UI_VISIBLE";
        boolean privacy = (value.contains("隐私") || value.contains("privacy"))
                && (value.contains("同意") || value.contains("agree"));
        if ("wechat".equals(target)) {
            if ((value.contains("登录") || value.contains("log in"))
                    && (value.contains("注册") || value.contains("sign up"))) return "WELCOME_WITHOUT_LOGIN";
            if (privacy && (value.contains("微信") || value.contains("wechat"))) return "PRIVACY_WITHOUT_ACCEPTANCE";
        } else if ("douyin".equals(target) && privacy) return "PRIVACY_WITHOUT_ACCEPTANCE";
        return null;
    }

    private static void write(File file, String value) throws Exception {
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(value.getBytes(StandardCharsets.UTF_8));
            stream.getFD().sync();
        }
    }
}
