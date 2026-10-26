package com.soozooq.tianwaitian.containerresearch;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
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

/** Single ordinary flow: select pinned app, import once, set coordinates and open. */
public final class ProbeActivity extends Activity {
    public static final String PREFILL_ACTION = "com.soozooq.tianwaitian.containerresearch.action.PREFILL_TARGET_V1";
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private TextView status;
    private Button open, choose, installed;
    private EditText latitude, longitude;
    private LinearLayout coordinates;
    private Spinner apps;
    private List<GuestImport.Entry> entries;
    private SharedPreferences saved;
    private GuestImport.Entry pendingImport;
    private boolean destroyed, busy, waitingPermission;

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
        Button stop = new Button(this); stop.setId(R.id.stop_guest); stop.setText("停止容器内应用和模拟");
        stop.setOnClickListener(v -> {
            try { stopActive(); status.setText("已请求停止；可修改坐标后重新打开。"); }
            catch (RuntimeException error) { status.setText("停止请求失败，请稍后重试。"); }
        });
        root.addView(stop);
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

    @Override protected void onDestroy() { destroyed = true; io.shutdownNow(); super.onDestroy(); }
}
