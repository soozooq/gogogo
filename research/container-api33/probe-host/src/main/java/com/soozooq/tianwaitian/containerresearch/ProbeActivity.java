package com.soozooq.tianwaitian.containerresearch;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import top.niunaijun.bcore.BlackBoxCore;
import top.niunaijun.bcore.entity.location.BLocation;
import top.niunaijun.bcore.fake.frameworks.BLocationManager;
import top.niunaijun.bcore.fake.frameworks.BActivityManager;
import top.niunaijun.bcore.core.system.location.IBLocationManagerService;
import top.niunaijun.bcore.entity.am.RunningAppProcessInfo;

/** Single ordinary flow: select pinned app, import once, set coordinates and open. */
public final class ProbeActivity extends Activity {
    public static final String PREFILL_ACTION = "com.soozooq.tianwaitian.containerresearch.action.PREFILL_TARGET_V1";
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private TextView status;
    private Button open, choose, installed, feedback;
    private EditText latitude, longitude;
    private LinearLayout coordinates;
    private Spinner apps;
    private List<GuestImport.Entry> entries;
    private SharedPreferences saved;
    private GuestImport.Entry pendingImport;
    private volatile boolean destroyed;
    private boolean busy, waitingPermission, feedbackPending;
    private int feedbackGeneration;
    private final Handler feedbackHandler = new Handler(Looper.getMainLooper());

    private static final class IncomingTarget {
        final String packageName;
        final double latitude, longitude;
        IncomingTarget(String packageName, double latitude, double longitude) {
            this.packageName = packageName; this.latitude = latitude; this.longitude = longitude;
        }
    }

    private IncomingTarget incomingTarget() {
        try { return parseIncomingTarget(getIntent()); }
        catch (RuntimeException malformedExternalInput) { return null; }
    }

    private IncomingTarget parseIncomingTarget(Intent intent) {
        if (intent == null || !PREFILL_ACTION.equals(intent.getAction())
                || !"WGS84".equals(intent.getStringExtra("coordinate_system"))) return null;
        double lat = intent.getDoubleExtra("latitude", Double.NaN);
        double lon = intent.getDoubleExtra("longitude", Double.NaN);
        if (Double.isNaN(lat) || Double.isInfinite(lat) || lat < -90 || lat > 90
                || Double.isNaN(lon) || Double.isInfinite(lon) || lon < -180 || lon > 180) return null;
        String pkg = intent.getStringExtra("guest_package");
        for (GuestImport.Entry entry : entries) {
            if (entry.packageName.equals(pkg) && entry.location && entry.supportsDevice())
                return new IncomingTarget(pkg, lat, lon);
        }
        return null;
    }

    private GuestImport.Entry selected() { return entries.get(apps.getSelectedItemPosition()); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        saved = getSharedPreferences("research-entry", MODE_PRIVATE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        setContentView(R.layout.preview_entry);
        if (Build.VERSION.SDK_INT >= 30) {
            View header = findViewById(R.id.preview_header);
            getWindow().getDecorView().setOnApplyWindowInsetsListener((view, insets) -> {
                int visible = insets.isVisible(WindowInsets.Type.ime()) ? View.GONE : View.VISIBLE;
                if (header.getVisibility() != visible) header.setVisibility(visible);
                return view.onApplyWindowInsets(insets);
            });
        }
        apps = findViewById(R.id.guest_choice);
        installed = findViewById(R.id.import_installed_guest);
        choose = findViewById(R.id.import_guest);
        coordinates = findViewById(R.id.coordinate_section);
        latitude = findViewById(R.id.target_latitude);
        longitude = findViewById(R.id.target_longitude);
        open = findViewById(R.id.open_guest);
        feedback = findViewById(R.id.copy_preview_feedback);
        status = findViewById(R.id.import_status);
        adaptActionRows();
        try { entries = GuestImport.catalog(this); }
        catch (Exception error) {
            showStatus("应用清单读取失败，请重新安装预览包。");
            open.setEnabled(false); choose.setEnabled(false); installed.setEnabled(false);
            feedback.setEnabled(false); return;
        }
        ArrayAdapter<GuestImport.Entry> adapter = new ArrayAdapter<>(this,
                R.layout.preview_spinner_item, entries);
        adapter.setDropDownViewResource(R.layout.preview_spinner_item);
        apps.setAdapter(adapter);
        IncomingTarget incoming = incomingTarget();
        String previous = saved.getString("selected", "com.android.gpstest.osmdroid");
        if (incoming != null) previous = incoming.packageName;
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).packageName.equals(previous)) apps.setSelection(i);
        Button download = findViewById(R.id.download_original);
        download.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(selected().url))); }
            catch (android.content.ActivityNotFoundException error) { showStatus("请使用浏览器打开官方发布页下载对应版本。"); }
        });
        installed.setOnClickListener(v -> importChosen(selected(), null, true));
        choose.setOnClickListener(v -> {
            pendingImport = selected();
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.setType("application/vnd.android.package-archive");
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents", "primary:Download"));
            startActivityForResult(pick, 1);
        });
        TextView hint = findViewById(R.id.coordinate_hint);
        hint.setText(incoming == null ? "目标坐标（WGS84）" : "已带入地图选点（WGS84），确认后应用");
        latitude.setText(incoming == null ? saved.getString("latitude", "38.125") : Double.toString(incoming.latitude));
        longitude.setText(incoming == null ? saved.getString("longitude", "-121.875") : Double.toString(incoming.longitude));
        open.setOnClickListener(v -> openSelected());
        Button stop = findViewById(R.id.stop_guest);
        stop.setOnClickListener(v -> {
            try { stopActive(); showStatus("已请求停止；可修改坐标后重新打开。"); }
            catch (RuntimeException error) { showStatus("停止请求失败，请稍后重试。"); }
        });
        feedback.setOnClickListener(v -> chooseFeedbackResult());
        apps.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saved.edit().putString("selected", selected().packageName).apply(); refresh();
            }
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        refresh();
    }

    private void adaptActionRows() {
        // Let labels grow at the user's font size. The main action and feedback
        // remain outside the scrolling form, including while editing coordinates.
        if (getResources().getConfiguration().fontScale <= 1.2f
                && getResources().getConfiguration().screenWidthDp >= 320) return;
        for (int id : new int[] {R.id.import_actions, R.id.coordinate_fields, R.id.preview_actions}) {
            if ((id == R.id.import_actions || id == R.id.preview_actions)
                    && getResources().getConfiguration().screenWidthDp >= 320
                    && getResources().getConfiguration().fontScale <= 1.6f) continue;
            LinearLayout row = findViewById(id);
            row.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < row.getChildCount(); i++) {
                View child = row.getChildAt(i);
                child.setLayoutParams(new LinearLayout.LayoutParams(
                        child instanceof android.widget.Space ? 1 : ViewGroup.LayoutParams.MATCH_PARENT,
                        child instanceof android.widget.Space ? dp(8) : ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
    }

    private void refresh() {
        if (entries == null || open == null) return;
        GuestImport.Entry entry = selected();
        boolean imported = BlackBoxCore.get().isInstalled(entry.packageName, 0);
        coordinates.setVisibility(entry.location ? View.VISIBLE : View.GONE);
        open.setText(entry.location ? "应用坐标并打开" : "打开计算器");
        boolean supported = entry.supportsDevice();
        open.setEnabled(imported && !busy && supported);
        choose.setEnabled(!busy && supported && !imported);
        installed.setEnabled(!busy && supported && !imported); apps.setEnabled(!busy);
        feedback.setEnabled(!busy && !feedbackPending);
        status.setText(!supported ? "所选原版仅支持原生 ARM64 设备。" : imported ? "已导入 " + entry
                + (entry.location ? "。确认坐标后应用并打开；以后可直接修改坐标。" : "。可直接打开。")
                : "先导入 " + entry + " 的官方原版 APK。");
        // Publish the whole batch after all controls and the status agree. A
        // repeated status string alone need not invalidate cached button nodes.
        AccessibilityEvent changed = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        changed.setContentChangeTypes(AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE);
        findViewById(R.id.preview_root).sendAccessibilityEventUnchecked(changed);
    }

    @Override protected void onResume() { super.onResume(); if (!busy) refresh(); }

    private void showStatus(String message) {
        status.setText(message);
        // Action results must be visible even when the form was scrolled to its
        // inputs. Passive refresh keeps its place; only explicit results reveal.
        status.post(() -> {
            if (!destroyed) status.requestRectangleOnScreen(
                    new Rect(0, 0, status.getWidth(), status.getHeight()), true);
        });
    }

    private double number(EditText edit, double max) {
        double value;
        try { value = Double.parseDouble(edit.getText().toString().trim()); }
        catch (NumberFormatException error) { edit.setError("请输入有效数字"); throw error; }
        if (!Double.isFinite(value) || Math.abs(value) > max) {
            edit.setError("超出有效范围"); throw new IllegalArgumentException("COORDINATE_RANGE");
        }
        edit.setError(null); return value;
    }

    private void openSelected() {
        GuestImport.Entry entry = selected();
        if (!entry.supportsDevice()) { showStatus("所选原版仅支持原生 ARM64 设备。"); return; }
        try {
            double lat = 0, lon = 0;
            if (entry.location) {
                lat = number(latitude, 90); lon = number(longitude, 180);
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    waitingPermission = true;
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 2);
                    return;
                }
            }
            String active = saved.getString("active", "");
            if (!active.isEmpty() && !active.equals(entry.packageName)) stopActive();
            if (entry.location) {
                BLocationManager.get().setLocation(0, entry.packageName, new BLocation(lat, lon));
                BLocationManager.get().setPattern(0, entry.packageName, BLocationManager.OWN_MODE);
                BLocation applied = BLocationManager.get().getLocation(0, entry.packageName);
                if (applied == null || applied.getLatitude() != lat || applied.getLongitude() != lon
                        || BLocationManager.get().getPattern(0, entry.packageName) != BLocationManager.OWN_MODE) {
                    throw new IllegalStateException("LOCATION_NOT_APPLIED");
                }
                saved.edit().putString("latitude", latitude.getText().toString().trim())
                        .putString("longitude", longitude.getText().toString().trim()).apply();
            } else BLocationManager.disableFakeLocation(0, entry.packageName);
            saved.edit().putString("active", entry.packageName).apply();
            if (!BlackBoxCore.get().launchApk(entry.packageName, 0)) {
                stopActive(); showStatus("启动请求失败，请重试。"); return;
            }
            showStatus("已应用设置并请求打开，请以应用实际显示为准。");
        } catch (IllegalArgumentException error) { showStatus("请检查纬度和经度。"); }
        catch (RuntimeException error) { showStatus("未能应用并打开，请重试。"); }
    }

    private void stopActive() {
        String active = saved.getString("active", "");
        if (active.isEmpty()) active = selected().packageName;
        // Stop Guest before disabling its virtual session; never force-stop a system-installed app.
        BlackBoxCore.get().stopPackage(active, 0);
        BLocationManager.disableFakeLocation(0, active);
        saved.edit().remove("active").apply();
    }

    private void chooseFeedbackResult() {
        if (destroyed || busy || feedbackPending) return;
        GuestImport.Entry entry = selected();
        boolean wechat = "com.tencent.mm".equals(entry.packageName);
        String[] choices = wechat ? new String[] {"发送位置：选点地区", "发送位置：真实地区", "发送位置：找不到位置",
                "实时共享：选点地区", "实时共享：真实地区", "实时共享：找不到位置", "还没查看位置页"}
                : new String[] {"位置页：选点地区", "位置页：真实地区", "位置页：找不到位置", "还没查看位置页"};
        new AlertDialog.Builder(this).setTitle("复制反馈：只选当前看到的结果")
                .setItems(choices, (dialog, index) -> {
                    String scene = wechat ? (index < 3 ? "WECHAT_SEND" : index < 6 ? "WECHAT_LIVE" : "UNSPECIFIED")
                            : "LOCATION_PAGE_NOT_IP_PROFILE";
                    String[] results = {"SELECTED_REGION", "REAL_REGION", "NO_LOCATION"};
                    String result = index == choices.length - 1 ? "NOT_VIEWED" : results[index % 3];
                    collectFeedback(entry, scene, result);
                }).setNegativeButton("取消", null).show();
    }

    private boolean feedbackInputValid(GuestImport.Entry entry) {
        if (!entry.location) return false;
        try {
            double lat = Double.parseDouble(latitude.getText().toString().trim());
            double lon = Double.parseDouble(longitude.getText().toString().trim());
            return Double.isFinite(lat) && Double.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180;
        } catch (NumberFormatException invalid) { return false; }
    }

    private static String feedbackField(String value) {
        if (value == null) return "UNKNOWN";
        String clean = value.replace('\n', ' ').replace('\r', ' ');
        return clean.length() > 80 ? clean.substring(0, 80) : clean;
    }

    private static String consumerFeedback(Bundle value) {
        if (value == null || !"CORE_MEMORY_VIRTUAL_PREPARATION_AND_BINDER_SEND_NOT_SDK_ADOPTION"
                .equals(value.getString("scope"))) return "consumer_delivery=UNKNOWN_READ_RESULT\n";
        if (!value.getBoolean("present")) return "consumer_delivery=NO_RECORD_IN_CURRENT_CORE_MEMORY\n";
        StringBuilder text = new StringBuilder("consumer_delivery=RECORDED_NOT_SDK_ADOPTION\n");
        for (String path : new String[] {"cache", "current", "stream_registered", "stream_returned"}) {
            String prefix = path.startsWith("stream") ? "stream" : path;
            String suffix = path.equals("stream_registered") ? "registered"
                    : path.equals("stream_returned") ? "transaction_returned" : "prepared";
            text.append("consumer_").append(path).append("_gps_network=")
                    .append(value.getLong(prefix + "_gps_" + suffix)).append('/')
                    .append(value.getLong(prefix + "_network_" + suffix)).append('\n');
        }
        text.append("consumer_passive_cache_current_registered_returned=")
                .append(value.getLong("cache_passive_prepared")).append('/')
                .append(value.getLong("current_passive_prepared")).append('/')
                .append(value.getLong("stream_passive_registered")).append('/')
                .append(value.getLong("stream_passive_transaction_returned")).append('\n');
        text.append("consumer_missing_stream_errors=").append(value.getLong("missing_sample_count"))
                .append('/').append(value.getLong("stream_failure_count")).append('\n')
                .append("consumer_last_event_age_ms=").append(value.getLong("last_event_age_ms")).append('\n')
                .append("consumer_stream_mock_synthetic=")
                .append(value.containsKey("last_stream_mock") ? value.getBoolean("last_stream_mock")
                        + "/" + value.getBoolean("last_stream_synthetic") : "NO_STREAM_SAMPLE").append('\n')
                .append("consumer_history=VOLATILE_BOUNDED_CORE_MEMORY_NOT_CURRENT_APP_SESSION\n");
        return text.toString();
    }

    private void collectFeedback(GuestImport.Entry entry, String scene, String result) {
        if (destroyed) return;
        long started = SystemClock.elapsedRealtime();
        String ownVersion = feedbackField(BuildConfig.VERSION_NAME) + "/" + BuildConfig.VERSION_CODE;
        final String base = "GOGOGO_PREVIEW_FEEDBACK_V1\n"
                + "scope=OWN_PREVIEW_COPY_TIME_SEQUENTIAL_SNAPSHOT_NOT_SDK_OR_EXTERNAL_UI_PROOF\n"
                + "host_build_version=" + ownVersion + "\nandroid_api=" + Build.VERSION.SDK_INT
                + "\nselected_app=" + feedbackField(entry.packageName)
                + "\nexpected_original=" + feedbackField(entry.versionName) + "/" + entry.versionCode
                + "\nvirtual_location_source=SYNTHETIC_NOT_PHYSICAL_GNSS"
                + "\nconfigured_virtual_presentation=" + (BLocation.exposesMockFlagForPackage(entry.packageName)
                        ? "DEFAULT_FRAMEWORK_MOCK_MARKED" : "WECHAT_COMPATIBILITY_NO_FRAMEWORK_MOCK_MARKER")
                + "\npresentation_scope=SELECTED_PROFILE_NOT_SDK_ADOPTION_PROOF"
                + "\ndevice_abi_supported=" + (entry.supportsDevice() ? "YES" : "NO")
                + "\nprecise_permission=" + (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED")
                + "\ntarget_input=" + (entry.location ? feedbackInputValid(entry) ? "VALID_NOT_RECORDED" : "INVALID" : "NOT_APPLICABLE")
                + "\nuser_entry=" + scene + "\nuser_visual_result=" + result
                + "\nobservation_source=USER_REPORTED_NOT_TIME_CORRELATED_WITH_SNAPSHOT\n"
                + "external_app_actually_opened=NOT_AUTO_VERIFIED\nsdk_location_adoption=UNKNOWN\n"
                + "real_consumer_coordinates=NOT_READ\nno_active_fix_or_mock_start_stop=true\n"
                + "no_ip_ssid_cell_ids_keys_phone_identifiers=true\nstarted_elapsed_ms=" + started + "\n";
        feedbackPending = true; feedback.setEnabled(false); showStatus("正在整理短反馈……");
        final int generation = ++feedbackGeneration;
        final long deadline = SystemClock.elapsedRealtime() + 1500;
        // A slow/dead Core cannot make copying wait indefinitely. Late results
        // cannot replace the user's clipboard after the fallback or a new request.
        feedbackHandler.postDelayed(() -> publishFeedback(generation, base
                + "core_snapshot=UNKNOWN_READ_TIMEOUT\nconsumer_delivery=UNKNOWN_READ_TIMEOUT\n"), 1500);
        io.execute(() -> {
            if (destroyed || SystemClock.elapsedRealtime() > deadline) return;
            StringBuilder core = new StringBuilder();
            try { core.append("virtual_import_present=").append(BlackBoxCore.get().isInstalled(entry.packageName, 0) ? "YES" : "NO").append('\n'); }
            catch (RuntimeException unavailable) { core.append("virtual_import_present=UNKNOWN\n"); }
            if (destroyed || SystemClock.elapsedRealtime() > deadline) return;
            try {
                IBLocationManagerService modes = BLocationManager.get().getService();
                if (modes == null) core.append("virtual_mode=UNKNOWN\nconsumer_delivery=UNKNOWN_READ_FAILED\n");
                else {
                    int pattern = modes.getPattern(0, entry.packageName);
                    core.append("virtual_mode=").append(pattern == BLocationManager.OWN_MODE ? "OWN" :
                            pattern == BLocationManager.CLOSE_MODE ? "CLOSE" : "OTHER").append('\n');
                    if (destroyed || SystemClock.elapsedRealtime() > deadline) return;
                    try { core.append(consumerFeedback(modes.getConsumerDeliverySummary(0, entry.packageName))); }
                    catch (Exception unavailable) { core.append("consumer_delivery=UNKNOWN_READ_FAILED\n"); }
                }
            } catch (Exception unavailable) { core.append("virtual_mode=UNKNOWN\nconsumer_delivery=UNKNOWN_READ_FAILED\n"); }
            if (destroyed || SystemClock.elapsedRealtime() > deadline) return;
            try {
                RunningAppProcessInfo snapshot = BActivityManager.get().getRunningAppProcesses(entry.packageName, 0);
                if (snapshot == null) core.append("registered_main_seen_at_capture=UNKNOWN\n");
                else {
                    boolean seen = false;
                    for (android.app.ActivityManager.RunningAppProcessInfo process : snapshot.mAppProcessInfoList) {
                        if (entry.packageName.equals(process.processName) && process.uid == getApplicationInfo().uid) seen = true;
                    }
                    core.append("registered_main_seen_at_capture=").append(seen ? "YES" : "NO").append('\n');
                }
            } catch (RuntimeException unavailable) { core.append("registered_main_seen_at_capture=UNKNOWN\n"); }
            final String report = base + core + "finished_elapsed_ms=" + SystemClock.elapsedRealtime() + "\n";
            runOnUiThread(() -> publishFeedback(generation, report));
        });
    }

    private void publishFeedback(int generation, String report) {
        if (destroyed || !feedbackPending || generation != feedbackGeneration) return;
        feedbackPending = false; feedback.setEnabled(!busy);
        try {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            if (clipboard == null) throw new IllegalStateException("CLIPBOARD_UNAVAILABLE");
            clipboard.setPrimaryClip(ClipData.newPlainText("天外天预览反馈", report));
            showStatus("短反馈已复制，可直接粘贴发送。无需再跑实验。");
        } catch (RuntimeException unavailable) { showStatus("复制未完成，请稍后重试。"); }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != 2 || !waitingPermission) return;
        waitingPermission = false;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) openSelected();
        else showStatus("需要精确定位授权后才能启动定位应用；可再次点击打开授权。");
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 1 || result != RESULT_OK || data == null || data.getData() == null) return;
        final GuestImport.Entry entry = pendingImport == null ? selected() : pendingImport;
        pendingImport = null;
        importChosen(entry, data.getData(), false);
    }

    private void importChosen(GuestImport.Entry entry, Uri uri, boolean fromInstalled) {
        if (busy || !entry.supportsDevice()) return;
        busy = true; refresh(); showStatus("正在核验原版并导入，只需一次……");
        io.execute(() -> {
            boolean ready = false; File file = null;
            String failure = "导入未完成：请检查指定原版 APK 和可用存储。";
            try {
                file = fromInstalled ? GuestImport.copyInstalled(getApplicationContext(), entry)
                        : GuestImport.copyAndVerify(getApplicationContext(), uri, entry);
                GuestImport.installVerified(file, entry); ready = true;
            } catch (PackageManager.NameNotFoundException error) {
                failure = "手机未安装所选应用，可选择指定版本的原版 APK。";
            } catch (Exception error) {
                if ("INSTALLED_VERSION_NOT_PINNED".equals(error.getMessage()))
                    failure = "手机已装版本与清单不一致，请选择清单对应的原版 APK。";
                else if ("INSTALLED_SPLITS_UNSUPPORTED".equals(error.getMessage()))
                    failure = "已装版本采用分包，请选择对应的完整官方 APK。";
            }
            finally { if (file != null) file.delete(); }
            final boolean done = ready;
            final String message = failure;
            runOnUiThread(() -> { if (!destroyed) { busy = false; refresh(); if (!done) showStatus(message); } });
        });
    }

    @Override protected void onDestroy() {
        destroyed = true; feedbackPending = false; feedbackGeneration++;
        feedbackHandler.removeCallbacksAndMessages(null); io.shutdownNow(); super.onDestroy();
    }
}
