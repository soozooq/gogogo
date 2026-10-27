package com.soozooq.tianwaitian.containerresearch;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        TextView title = new TextView(this);
        title.setText("天外天 · 无 Root 预览\n原版导入后在此入口登录。微信、抖音位置效果待验证。");
        title.setTextSize(18);
        root.addView(title);
        apps = new Spinner(this); apps.setId(R.id.guest_choice);
        try { entries = GuestImport.catalog(this); }
        catch (Exception error) { title.setText("准入清单读取失败，请重新构建研究包。"); setContentView(root); return; }
        apps.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, entries));
        IncomingTarget incoming = incomingTarget();
        String previous = saved.getString("selected", "com.android.gpstest.osmdroid");
        if (incoming != null) previous = incoming.packageName;
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).packageName.equals(previous)) apps.setSelection(i);
        root.addView(apps);
        Button download = new Button(this); download.setText("获取对应版本的原版 APK");
        download.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(selected().url))); }
            catch (android.content.ActivityNotFoundException error) { status.setText("请使用浏览器打开官方发布页下载对应版本。"); }
        });
        root.addView(download);
        LinearLayout imports = new LinearLayout(this);
        installed = new Button(this); installed.setId(R.id.import_installed_guest);
        installed.setText("导入手机已装版本");
        installed.setOnClickListener(v -> importChosen(selected(), null, true));
        imports.addView(installed, new LinearLayout.LayoutParams(0, -2, 1));
        choose = new Button(this); choose.setId(R.id.import_guest); choose.setText("选择原版 APK");
        choose.setOnClickListener(v -> {
            pendingImport = selected();
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.setType("application/vnd.android.package-archive");
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents", "primary:Download"));
            startActivityForResult(pick, 1);
        });
        imports.addView(choose, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(imports);
        coordinates = new LinearLayout(this); coordinates.setOrientation(LinearLayout.VERTICAL);
        TextView hint = new TextView(this);
        hint.setText(incoming == null ? "目标坐标（WGS84）" : "已带入地图选点（WGS84），确认后应用");
        coordinates.addView(hint);
        latitude = coordinate(coordinates, R.id.target_latitude, "纬度 −90～90",
                incoming == null ? saved.getString("latitude", "38.125") : Double.toString(incoming.latitude));
        longitude = coordinate(coordinates, R.id.target_longitude, "经度 −180～180",
                incoming == null ? saved.getString("longitude", "-121.875") : Double.toString(incoming.longitude));
        root.addView(coordinates);
        open = new Button(this); open.setId(R.id.open_guest); open.setOnClickListener(v -> openSelected()); root.addView(open);
        Button stop = new Button(this); stop.setId(R.id.stop_guest); stop.setText("停止预览");
        stop.setOnClickListener(v -> {
            try { stopActive(); status.setText("已请求停止；可修改坐标后重新打开。"); }
            catch (RuntimeException error) { status.setText("停止请求失败，请稍后重试。"); }
        });
        LinearLayout actions = new LinearLayout(this);
        actions.addView(stop, new LinearLayout.LayoutParams(0, -2, 1));
        feedback = new Button(this); feedback.setId(R.id.copy_preview_feedback); feedback.setText("一键复制反馈");
        feedback.setOnClickListener(v -> chooseFeedbackResult());
        actions.addView(feedback, new LinearLayout.LayoutParams(0, -2, 1)); root.addView(actions);
        status = new TextView(this); status.setId(R.id.import_status); root.addView(status);
        apps.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saved.edit().putString("selected", selected().packageName).apply(); refresh();
            }
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll);
        refresh();
    }

    private EditText coordinate(LinearLayout parent, int id, String hint, String value) {
        EditText edit = new EditText(this); edit.setId(id); edit.setHint(hint); edit.setText(value);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        parent.addView(edit); return edit;
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
    }

    @Override protected void onResume() { super.onResume(); if (!busy) refresh(); }

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
        if (!entry.supportsDevice()) { status.setText("所选原版仅支持原生 ARM64 设备。"); return; }
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
                stopActive(); status.setText("启动请求失败，请重试。"); return;
            }
            status.setText("已应用设置并请求打开，请以应用实际显示为准。");
        } catch (IllegalArgumentException error) { status.setText("请检查纬度和经度。"); }
        catch (RuntimeException error) { status.setText("未能应用并打开，请重试。"); }
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

    private void collectFeedback(GuestImport.Entry entry, String scene, String result) {
        if (destroyed) return;
        long started = SystemClock.elapsedRealtime();
        String ownVersion = feedbackField(BuildConfig.VERSION_NAME) + "/" + BuildConfig.VERSION_CODE;
        final String base = "GOGOGO_PREVIEW_FEEDBACK_V1\n"
                + "scope=OWN_PREVIEW_COPY_TIME_SEQUENTIAL_SNAPSHOT_NOT_SDK_OR_EXTERNAL_UI_PROOF\n"
                + "host_build_version=" + ownVersion + "\nandroid_api=" + Build.VERSION.SDK_INT
                + "\nselected_app=" + feedbackField(entry.packageName)
                + "\nexpected_original=" + feedbackField(entry.versionName) + "/" + entry.versionCode
                + "\ndevice_abi_supported=" + (entry.supportsDevice() ? "YES" : "NO")
                + "\nprecise_permission=" + (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED")
                + "\ntarget_input=" + (entry.location ? feedbackInputValid(entry) ? "VALID_NOT_RECORDED" : "INVALID" : "NOT_APPLICABLE")
                + "\nuser_entry=" + scene + "\nuser_visual_result=" + result
                + "\nobservation_source=USER_REPORTED_NOT_TIME_CORRELATED_WITH_SNAPSHOT\n"
                + "external_app_actually_opened=NOT_AUTO_VERIFIED\nsdk_location_adoption=UNKNOWN\n"
                + "real_consumer_coordinates=NOT_READ\nno_active_fix_or_mock_start_stop=true\n"
                + "no_ip_ssid_cell_ids_keys_phone_identifiers=true\nstarted_elapsed_ms=" + started + "\n";
        feedbackPending = true; feedback.setEnabled(false); status.setText("正在整理短反馈……");
        final int generation = ++feedbackGeneration;
        final long deadline = SystemClock.elapsedRealtime() + 1500;
        // A slow/dead Core cannot make copying wait indefinitely. Late results
        // cannot replace the user's clipboard after the fallback or a new request.
        feedbackHandler.postDelayed(() -> publishFeedback(generation, base
                + "core_snapshot=UNKNOWN_READ_TIMEOUT\n"), 1500);
        io.execute(() -> {
            if (destroyed || SystemClock.elapsedRealtime() > deadline) return;
            StringBuilder core = new StringBuilder();
            try { core.append("virtual_import_present=").append(BlackBoxCore.get().isInstalled(entry.packageName, 0) ? "YES" : "NO").append('\n'); }
            catch (RuntimeException unavailable) { core.append("virtual_import_present=UNKNOWN\n"); }
            if (destroyed || SystemClock.elapsedRealtime() > deadline) return;
            try {
                IBLocationManagerService modes = BLocationManager.get().getService();
                if (modes == null) core.append("virtual_mode=UNKNOWN\n");
                else {
                    int pattern = modes.getPattern(0, entry.packageName);
                    core.append("virtual_mode=").append(pattern == BLocationManager.OWN_MODE ? "OWN" :
                            pattern == BLocationManager.CLOSE_MODE ? "CLOSE" : "OTHER").append('\n');
                }
            } catch (Exception unavailable) { core.append("virtual_mode=UNKNOWN\n"); }
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
            status.setText("短反馈已复制，可直接粘贴发送。无需再跑实验。");
        } catch (RuntimeException unavailable) { status.setText("复制未完成，请稍后重试。"); }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != 2 || !waitingPermission) return;
        waitingPermission = false;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) openSelected();
        else status.setText("需要精确定位授权后才能启动定位应用；可再次点击打开授权。");
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
        busy = true; refresh(); status.setText("正在核验原版并导入，只需一次……");
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
            runOnUiThread(() -> { if (!destroyed) { busy = false; refresh(); if (!done) status.setText(message); } });
        });
    }

    @Override protected void onDestroy() {
        destroyed = true; feedbackPending = false; feedbackGeneration++;
        feedbackHandler.removeCallbacksAndMessages(null); io.shutdownNow(); super.onDestroy();
    }
}
