package com.zcshou.gogogo;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.zcshou.service.ServiceGo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class ScenarioLabActivity extends AppCompatActivity {
    private static final int REQ_EXPORT_SCENARIO = 7101;
    private static final int REQ_IMPORT_SCENARIO = 7102;
    private static final int REQ_EXPORT_REPORT = 7103;

    private static final long TICK_INTERVAL_MS = 33L;

    private LabScenarioEngine scenarioEngine;
    private LabPolicyEngine policyEngine;

    private TextView stateView;
    private TextView liveView;
    private TextView previewView;

    private EditText nameInput;
    private EditText seedInput;
    private EditText noiseInput;
    private EditText batteryInput;
    private EditText headingInput;
    private EditText headingNoiseInput;
    private EditText virtualStartInput;

    private ServiceGo.ServiceGoBinder serviceBinder;
    private boolean serviceBound;

    private JSONObject pendingReport;

    private final android.os.Handler handler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            refreshViews();
            handler.postDelayed(this, 500L);
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (service instanceof ServiceGo.ServiceGoBinder) {
                serviceBinder = (ServiceGo.ServiceGoBinder) service;
                serviceBound = true;
                refreshViews();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBinder = null;
            serviceBound = false;
            refreshViews();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scenarioEngine = new LabScenarioEngine(this);
        policyEngine = new LabPolicyEngine(this);
        buildUi();
        loadInputsFromEngine();
        refreshViews();
    }

    private void buildUi() {
        int pad = dp(14);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("🎛 GoGoGo Deterministic Scenario · Lab 11");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView intro = body();
        intro.setText(
                "同一个场景配置 + 同一个 Seed + 同一个 Tick，会得到完全相同的实验影子。\n\n"
                        + "位置噪声会真正作用在 Resource Broker 的 Published Mock 坐标上；"
                        + "虚拟时间 / 电量 /方向目前是 GoGoGo Lab 的可重复 shadow resource，"
                        + "不会修改手机真实系统时钟、电池状态或物理传感器。");
        root.addView(intro, matchWrap());

        stateView = body();
        root.addView(stateView, matchWrap());

        root.addView(sectionTitle("场景配置"));

        nameInput = field("场景名", "Lab11");
        root.addView(nameInput, matchWrap());

        seedInput = field("Seed（long）", "114514");
        root.addView(seedInput, matchWrap());

        noiseInput = field("位置噪声半径 m（0-5000）", "80");
        root.addView(noiseInput, matchWrap());

        batteryInput = field("电量影子基准 %（0-100）", "37");
        root.addView(batteryInput, matchWrap());

        headingInput = field("方向影子基准 °", "90");
        root.addView(headingInput, matchWrap());

        headingNoiseInput = field("方向影子扰动 ±°（0-180）", "12");
        root.addView(headingNoiseInput, matchWrap());

        virtualStartInput = field(
                "虚拟时间起点 UTC（yyyy-MM-dd HH:mm:ss）",
                "2026-01-01 00:00:00");
        root.addView(virtualStartInput, matchWrap());

        root.addView(buttonRow(
                button("🔥 保存并启用", v -> saveAndEnable()),
                button("⏸ 停用场景", v -> disableScenario()),
                button("⏮ 重播 Tick 0", v -> resetReplay()),
                button("🎲 新 Seed", v -> randomizeSeed())
        ));

        root.addView(sectionTitle("可重复预览"));
        previewView = body();
        root.addView(previewView, matchWrap());

        root.addView(buttonRow(
                button("📦 导出场景 JSON", v -> exportScenario()),
                button("📂 导入并重播", v -> importScenario()),
                button("🧾 导出实验报告", v -> exportReport())
        ));

        root.addView(sectionTitle("实时运行状态"));
        liveView = body();
        root.addView(liveView, matchWrap());

        TextView note = body();
        note.setText(
                "复现实验时建议：先导入同一个场景 JSON → 重置 Tick 0 → "
                        + "加载同一路线 / 同一 Policy → 再开始记录。\n\n"
                        + "实验报告会写入场景配置、当前 Raw/Published、Policy、Tick、"
                        + "虚拟资源和最近 provenance，并附一个 SHA-256 digest；"
                        + "这个 digest 是完整性校验，不是数字签名。");
        root.addView(note, matchWrap());

        Button back = button("← 返回 Sandbox Lab", v -> finish());
        root.addView(back, matchWrap());

        setContentView(scroll);
    }

    private TextView sectionTitle(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(18);
        v.setPadding(0, dp(14), 0, dp(4));
        return v;
    }

    private TextView body() {
        TextView v = new TextView(this);
        v.setTextSize(14);
        v.setTextIsSelectable(true);
        v.setPadding(dp(8), dp(8), dp(8), dp(8));
        return v;
    }

    private EditText field(String hint, String value) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        return input;
    }

    private Button button(String text, android.view.View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        return b;
    }

    private android.widget.HorizontalScrollView buttonRow(Button... buttons) {
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (Button b : buttons) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(6), 0);
            row.addView(b, lp);
        }
        scroll.addView(row);
        return scroll;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void loadInputsFromEngine() {
        scenarioEngine.reload();
        nameInput.setText(scenarioEngine.getName());
        seedInput.setText(Long.toString(scenarioEngine.getSeed()));
        noiseInput.setText(String.format(Locale.US, "%.2f",
                scenarioEngine.getLocationNoiseMeters()));
        batteryInput.setText(Integer.toString(scenarioEngine.getBaseBatteryPercent()));
        headingInput.setText(String.format(Locale.US, "%.2f",
                scenarioEngine.getBaseHeading()));
        headingNoiseInput.setText(String.format(Locale.US, "%.2f",
                scenarioEngine.getHeadingNoise()));
        virtualStartInput.setText(formatUtc(scenarioEngine.getBaseEpochMillis()));
    }

    private void saveAndEnable() {
        try {
            scenarioEngine.saveConfig(
                    nameInput.getText().toString(),
                    parseLong(seedInput, LabScenarioEngine.DEFAULT_SEED),
                    parseDouble(noiseInput, 80.0),
                    parseInt(batteryInput, 37),
                    (float) parseDouble(headingInput, 90.0),
                    (float) parseDouble(headingNoiseInput, 12.0),
                    parseUtc(virtualStartInput.getText().toString()),
                    true);

            resetReplay();
            refreshViews();
            Toast.makeText(this,
                    "场景已启用 · ID " + scenarioEngine.scenarioId(),
                    Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this,
                    "保存场景失败：" + t.getClass().getSimpleName()
                            + "\n" + String.valueOf(t.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void disableScenario() {
        scenarioEngine.setEnabled(false);
        refreshViews();
        Toast.makeText(this, "确定性场景已停用", Toast.LENGTH_SHORT).show();
    }

    private void resetReplay() {
        if (serviceBinder != null) {
            try {
                serviceBinder.resetScenarioStep();
                refreshViews();
                return;
            } catch (Throwable ignored) {
            }
        }

        if (ServiceGo.sRunning) {
            try {
                Intent intent = new Intent(this, ServiceGo.class);
                intent.setAction(ServiceGo.ACTION_SCENARIO_RESET);
                startService(intent);
            } catch (Throwable ignored) {
            }
        }
        refreshViews();
    }

    private void randomizeSeed() {
        long seed = System.nanoTime() ^ System.currentTimeMillis();
        seedInput.setText(Long.toString(seed));
        seedInput.selectAll();
    }

    private void refreshViews() {
        if (scenarioEngine == null || policyEngine == null) return;

        scenarioEngine.reload();
        policyEngine.reload();

        stateView.setText(
                "Scenario: " + scenarioEngine.summary()
                        + "\nPolicy: " + policyEngine.summary());

        refreshPreview();

        ServiceGo.ServiceGoBinder binder = serviceBinder;
        if (binder == null) {
            liveView.setText(ServiceGo.sRunning
                    ? "ServiceGo 正在运行，但 Scenario 控制台还没有绑定上。"
                    : "ServiceGo 未运行。预览仍可使用；启动 Mock 后这里会显示实时 Tick。");
            if (!serviceBound && ServiceGo.sRunning) bindIfRunning();
            return;
        }

        try {
            liveView.setText(String.format(Locale.US,
                    "Tick: %d\n"
                            + "Scenario: %s\n"
                            + "Virtual time: %s UTC\n"
                            + "Battery shadow: %d%%\n"
                            + "Heading shadow: %.1f°\n"
                            + "Noise N/E: %.2f / %.2f m\n"
                            + "Raw: %.6f, %.6f\n"
                            + "Published: %s\n"
                            + "Policy: %s",
                    binder.getScenarioStep(),
                    binder.getScenarioSummary(),
                    formatUtc(binder.getScenarioVirtualTimeMillis()),
                    binder.getScenarioBatteryPercent(),
                    binder.getScenarioHeadingDegrees(),
                    binder.getScenarioNoiseNorthMeters(),
                    binder.getScenarioNoiseEastMeters(),
                    binder.getLongitude(),
                    binder.getLatitude(),
                    binder.isPolicyPublishing()
                            ? String.format(Locale.US, "%.6f, %.6f · acc %.0fm",
                            binder.getPublishedLongitude(),
                            binder.getPublishedLatitude(),
                            binder.getPublishedAccuracyMeters())
                            : "PAUSED",
                    binder.getPolicySummary()));
        } catch (Throwable t) {
            liveView.setText("实时场景状态读取失败：" + t.getClass().getSimpleName());
        }
    }

    private void refreshPreview() {
        double rawLat = ServiceGo.DEFAULT_LAT;
        double rawLng = ServiceGo.DEFAULT_LNG;
        double rawAlt = ServiceGo.DEFAULT_ALT;
        double rawSpeed = 0.0;
        float rawBearing = 0.0f;

        if (serviceBinder != null) {
            try {
                rawLat = serviceBinder.getLatitude();
                rawLng = serviceBinder.getLongitude();
                rawAlt = serviceBinder.getAltitude();
                rawSpeed = serviceBinder.getSpeedMps();
                rawBearing = serviceBinder.getBearingDegrees();
            } catch (Throwable ignored) {
            }
        }

        LabPolicyEngine.LocationDecision base = policyEngine.decideLocation(
                rawLat, rawLng, rawAlt, rawSpeed, rawBearing);

        long[] steps = {0L, 30L, 60L, 90L, 120L};
        StringBuilder out = new StringBuilder();
        out.append("Scenario ID: ").append(scenarioEngine.scenarioId()).append("\n");
        out.append(String.format(Locale.US,
                "Preview raw: %.6f, %.6f\n\n", rawLng, rawLat));

        for (long step : steps) {
            LabScenarioEngine.ScenarioLocation loc = scenarioEngine.applyLocation(
                    base.publish,
                    base.latitude,
                    base.longitude,
                    base.altitude,
                    base.speedMps,
                    base.bearingDegrees,
                    base.accuracyMeters,
                    base.includeMotion,
                    step);

            out.append(String.format(Locale.US,
                    "Tick %-3d  %s  %s  batt=%d%%  head=%.1f°\n",
                    step,
                    loc.publish
                            ? String.format(Locale.US, "%.6f, %.6f",
                            loc.longitude, loc.latitude)
                            : "PAUSED",
                    formatUtc(scenarioEngine.virtualTimeMillis(step, TICK_INTERVAL_MS)),
                    scenarioEngine.batteryPercent(step),
                    scenarioEngine.headingDegrees(step)));
        }

        previewView.setText(out.toString().trim());
    }

    private void exportScenario() {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date());

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE,
                "GoGoGo_scenario_" + scenarioEngine.scenarioId() + "_" + stamp + ".json");
        startActivityForResult(intent, REQ_EXPORT_SCENARIO);
    }

    private void importScenario() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQ_IMPORT_SCENARIO);
    }

    private void exportReport() {
        try {
            pendingReport = buildReport();

            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date());

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE,
                    "GoGoGo_experiment_" + scenarioEngine.scenarioId()
                            + "_" + stamp + ".json");
            startActivityForResult(intent, REQ_EXPORT_REPORT);
        } catch (Throwable t) {
            Toast.makeText(this,
                    "生成报告失败：" + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private JSONObject buildReport() throws Exception {
        JSONObject report = new JSONObject();
        report.put("schema", 1);
        report.put("type", "GoGoGo deterministic experiment report");
        report.put("generated_utc", formatUtcMillis(System.currentTimeMillis()));
        report.put("scenario", scenarioEngine.toJson());
        report.put("policy", policyEngine.summary());

        ServiceGo.ServiceGoBinder binder = serviceBinder;
        if (binder != null) {
            JSONObject live = new JSONObject();
            live.put("scenario_step", binder.getScenarioStep());
            live.put("scenario_summary", binder.getScenarioSummary());
            live.put("virtual_time_ms", binder.getScenarioVirtualTimeMillis());
            live.put("battery_shadow_percent", binder.getScenarioBatteryPercent());
            live.put("heading_shadow_deg", binder.getScenarioHeadingDegrees());
            live.put("noise_north_m", binder.getScenarioNoiseNorthMeters());
            live.put("noise_east_m", binder.getScenarioNoiseEastMeters());

            live.put("raw_lng", binder.getLongitude());
            live.put("raw_lat", binder.getLatitude());
            live.put("raw_alt", binder.getAltitude());
            live.put("raw_speed_mps", binder.getSpeedMps());
            live.put("raw_bearing_deg", binder.getBearingDegrees());

            live.put("published_enabled", binder.isPolicyPublishing());
            live.put("published_lng", binder.getPublishedLongitude());
            live.put("published_lat", binder.getPublishedLatitude());
            live.put("published_alt", binder.getPublishedAltitude());
            live.put("published_speed_mps", binder.getPublishedSpeedMps());
            live.put("published_bearing_deg", binder.getPublishedBearingDegrees());
            live.put("published_accuracy_m", binder.getPublishedAccuracyMeters());
            live.put("provenance_source", binder.getProvenanceSource());
            live.put("service_policy", binder.getPolicySummary());
            report.put("live", live);

            JSONArray provenance = new JSONArray();
            String[] events = binder.getProvenanceEvents();
            int start = Math.max(0, events.length - 100);
            for (int i = start; i < events.length; i++) {
                provenance.put(events[i]);
            }
            report.put("provenance_tail", provenance);
        } else {
            report.put("live", JSONObject.NULL);
        }

        String digest = sha256(report.toString());
        report.put("sha256_integrity", digest);
        return report;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT_SCENARIO) {
            writeJson(uri, scenarioEngine.toJson(), "场景 JSON 已导出");
        } else if (requestCode == REQ_IMPORT_SCENARIO) {
            readScenario(uri);
        } else if (requestCode == REQ_EXPORT_REPORT) {
            JSONObject report = pendingReport;
            pendingReport = null;
            if (report != null) writeJson(uri, report, "实验报告已导出");
        }
    }

    private void writeJson(Uri uri, JSONObject json, String success) {
        try (OutputStream output = getContentResolver().openOutputStream(uri);
             BufferedWriter writer = output == null ? null
                     : new BufferedWriter(new OutputStreamWriter(
                     output, StandardCharsets.UTF_8))) {
            if (writer == null) throw new IllegalStateException("output");
            writer.write(json.toString(2));
            writer.flush();
            Toast.makeText(this, success + " ✅", Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this,
                    "写入失败：" + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void readScenario(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri);
             BufferedReader reader = input == null ? null
                     : new BufferedReader(new InputStreamReader(
                     input, StandardCharsets.UTF_8))) {
            if (reader == null) throw new IllegalStateException("input");

            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (text.length() > 128 * 1024) {
                    throw new IllegalArgumentException("scenario too large");
                }
                text.append(line).append('\n');
            }

            JSONObject json = new JSONObject(text.toString());
            scenarioEngine.applyJson(json, true);
            loadInputsFromEngine();
            resetReplay();
            refreshViews();

            Toast.makeText(this,
                    "场景已导入并从 Tick 0 重播 · ID "
                            + scenarioEngine.scenarioId(),
                    Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this,
                    "场景导入失败：" + t.getClass().getSimpleName()
                            + "\n" + String.valueOf(t.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void bindIfRunning() {
        if (serviceBound || !ServiceGo.sRunning) return;
        try {
            bindService(new Intent(this, ServiceGo.class), connection, 0);
        } catch (Throwable ignored) {
        }
    }

    private void unbindIfNeeded() {
        if (!serviceBound) return;
        try {
            unbindService(connection);
        } catch (Throwable ignored) {
        }
        serviceBound = false;
        serviceBinder = null;
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindIfRunning();
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(refreshTask);
        unbindIfNeeded();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        unbindIfNeeded();
        if (scenarioEngine != null) scenarioEngine.close();
        if (policyEngine != null) policyEngine.close();
        super.onDestroy();
    }

    private static long parseLong(EditText input, long fallback) {
        try {
            return Long.parseLong(input.getText().toString().trim());
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static int parseInt(EditText input, int fallback) {
        try {
            return Integer.parseInt(input.getText().toString().trim());
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static double parseDouble(EditText input, double fallback) {
        try {
            return Double.parseDouble(input.getText().toString().trim());
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static long parseUtc(String value) throws Exception {
        SimpleDateFormat format =
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        format.setLenient(false);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date = format.parse(value.trim());
        if (date == null) throw new IllegalArgumentException("invalid UTC time");
        return date.getTime();
    }

    private static String formatUtc(long time) {
        SimpleDateFormat format =
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(time));
    }

    private static String formatUtcMillis(long time) {
        SimpleDateFormat format =
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(time));
    }

    private static String sha256(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (byte b : hash) {
            out.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return out.toString();
    }
}
