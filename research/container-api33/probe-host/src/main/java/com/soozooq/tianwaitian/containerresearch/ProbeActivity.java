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
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private TextView status;
    private Button open, choose;
    private EditText latitude, longitude;
    private LinearLayout coordinates;
    private Spinner apps;
    private List<GuestImport.Entry> entries;
    private SharedPreferences saved;
    private GuestImport.Entry pendingImport;
    private boolean destroyed, busy, waitingPermission;

    private GuestImport.Entry selected() { return entries.get(apps.getSelectedItemPosition()); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        saved = getSharedPreferences("research-entry", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        TextView title = new TextView(this);
        title.setText("天外天 · 容器研究\n已验证 GPSTest 前台定位；微信、抖音尚未验证。");
        title.setTextSize(18);
        root.addView(title);
        apps = new Spinner(this); apps.setId(R.id.guest_choice);
        try { entries = GuestImport.catalog(this); }
        catch (Exception error) { title.setText("准入清单读取失败，请重新构建研究包。"); setContentView(root); return; }
        apps.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, entries));
        String previous = saved.getString("selected", "com.android.gpstest.osmdroid");
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).packageName.equals(previous)) apps.setSelection(i);
        root.addView(apps);
        Button download = new Button(this); download.setText("获取对应版本的原版 APK");
        download.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(selected().url))); }
            catch (android.content.ActivityNotFoundException error) { status.setText("请使用浏览器打开官方发布页下载对应版本。"); }
        });
        root.addView(download);
        choose = new Button(this); choose.setId(R.id.import_guest); choose.setText("导入原版 APK（只需一次）");
        choose.setOnClickListener(v -> {
            pendingImport = selected();
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.setType("application/vnd.android.package-archive");
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents", "primary:Download"));
            startActivityForResult(pick, 1);
        });
        root.addView(choose);
        coordinates = new LinearLayout(this); coordinates.setOrientation(LinearLayout.VERTICAL);
        TextView hint = new TextView(this); hint.setText("目标坐标（WGS84）"); coordinates.addView(hint);
        latitude = coordinate(coordinates, R.id.target_latitude, "纬度 −90～90", saved.getString("latitude", "38.125"));
        longitude = coordinate(coordinates, R.id.target_longitude, "经度 −180～180", saved.getString("longitude", "-121.875"));
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
        open.setEnabled(imported && !busy); choose.setEnabled(!busy); apps.setEnabled(!busy);
        status.setText(imported ? "已导入 " + entry + "。可直接打开；修改坐标后再次应用即可。"
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
        Uri uri = data.getData(); busy = true; refresh(); status.setText("正在核验原 APK 并导入……");
        io.execute(() -> {
            boolean installed = false; File file = null;
            try { file = GuestImport.copyAndVerify(getApplicationContext(), uri, entry); GuestImport.installVerified(file, entry); installed = true; }
            catch (Exception error) { /* Only bounded, actionable status is shown; no paths or raw logs. */ }
            finally { if (file != null) file.delete(); }
            final boolean ready = installed;
            runOnUiThread(() -> { if (!destroyed) { busy = false; refresh(); if (!ready) status.setText("导入失败：请选择 " + entry + " 的官方原版 APK。"); } });
        });
    }

    @Override protected void onDestroy() { destroyed = true; io.shutdownNow(); super.onDestroy(); }
}
