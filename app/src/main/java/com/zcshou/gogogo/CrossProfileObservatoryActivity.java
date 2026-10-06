package com.zcshou.gogogo;

import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.CrossProfileApps;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

public class CrossProfileObservatoryActivity extends AppCompatActivity {
    private static final int REQ_EXPORT = 6901;
    private static final int REQ_IMPORT = 6902;

    private TextView currentView;
    private TextView crossProfileView;
    private TextView importedView;

    private JSONObject currentSnapshot;
    private JSONObject importedSnapshot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        refresh();
    }

    private void buildUi() {
        int pad = dp(14);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("🔭 GoGoGo Cross-profile Observatory · Lab 9");
        title.setTextSize(21);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView intro = body();
        intro.setText(
                "目标：观察同一个 GoGoGo APK 在个人空间与 Work Profile 中的真实隔离差异。\n\n"
                        + "每个 profile 只读取自己的环境。跨资料对比通过 JSON 快照完成；"
                        + "不会使用 shell/root 越过资料边界读取另一个 profile 的私有数据。");
        root.addView(intro, matchWrap());

        root.addView(sectionTitle("当前 Profile 指纹"));
        currentView = body();
        root.addView(currentView, matchWrap());

        root.addView(buttonRow(
                button("↻ 刷新", v -> refresh()),
                button("💾 导出当前快照", v -> exportCurrent()),
                button("📂 导入另一资料快照", v -> importSnapshot())
        ));

        root.addView(sectionTitle("CrossProfileApps"));
        crossProfileView = body();
        root.addView(crossProfileView, matchWrap());

        root.addView(buttonRow(
                button("✅ Profile Owner 允许 GoGoGo 跨资料", v -> allowlistSelf()),
                button("🙋 请求用户跨资料同意", v -> requestCrossProfileConsent()),
                button("🔀 切换到另一 Profile", v -> switchToOtherProfile())
        ));

        root.addView(sectionTitle("导入 / 差异矩阵"));
        importedView = body();
        importedView.setText("尚未导入另一份 profile 快照。");
        root.addView(importedView, matchWrap());

        root.addView(buttonRow(
                button("🔬 对比当前 vs 导入", v -> compareSnapshots()),
                button("🧹 清除导入", v -> {
                    importedSnapshot = null;
                    importedView.setText("已清除导入快照。");
                })
        ));

        TextView tips = body();
        tips.setText(
                "推荐实验流程：\n"
                        + "① 在个人空间导出 personal.json；\n"
                        + "② 切到 Work Profile，导出 work.json；\n"
                        + "③ 任意一边导入另一份并点“对比”；\n"
                        + "④ 重点看 user serial、UID、dataDir、Profile Owner、Broker Policy、"
                        + "网络接口/DNS、CrossProfileApps 可见性。");
        root.addView(tips, matchWrap());

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

    private void refresh() {
        try {
            currentSnapshot = buildSnapshot();
            currentView.setText(renderSnapshot(currentSnapshot));
        } catch (Throwable t) {
            currentView.setText("生成当前快照失败：" + t.getClass().getSimpleName()
                    + "\n" + String.valueOf(t.getMessage()));
        }

        crossProfileView.setText(buildCrossProfileStatus());
    }

    private JSONObject buildSnapshot() throws Exception {
        JSONObject json = new JSONObject();

        long now = System.currentTimeMillis();
        UserManager um = (UserManager) getSystemService(Context.USER_SERVICE);
        DevicePolicyManager dpm =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        PackageManager pm = getPackageManager();
        ApplicationInfo appInfo = getApplicationInfo();

        boolean managed = false;
        boolean profileOwner = false;
        boolean deviceOwner = false;
        long serial = -1L;

        try {
            if (um != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    managed = um.isManagedProfile();
                }
                serial = um.getSerialNumberForUser(Process.myUserHandle());
            }
        } catch (Throwable ignored) {
        }

        try {
            if (dpm != null) {
                profileOwner = dpm.isProfileOwnerApp(getPackageName());
                deviceOwner = dpm.isDeviceOwnerApp(getPackageName());
            }
        } catch (Throwable ignored) {
        }

        json.put("schema", 1);
        json.put("timestamp_utc", isoTime(now));
        json.put("profile_kind", managed ? "WORK_MANAGED" : "PERSONAL_OR_PARENT");
        json.put("user_handle", String.valueOf(Process.myUserHandle()));
        json.put("user_serial", serial);
        json.put("process_uid", Process.myUid());
        json.put("profile_owner", profileOwner);
        json.put("device_owner", deviceOwner);
        json.put("package", getPackageName());
        json.put("version", getVersionName());
        json.put("android_release", Build.VERSION.RELEASE);
        json.put("api", Build.VERSION.SDK_INT);
        json.put("manufacturer", Build.MANUFACTURER);
        json.put("model", Build.MODEL);
        json.put("build_fingerprint", Build.FINGERPRINT);
        json.put("data_dir", appInfo.dataDir == null ? "" : appInfo.dataDir);
        json.put("source_dir", appInfo.sourceDir == null ? "" : appInfo.sourceDir);
        json.put("managed_users_feature",
                pm.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS));
        json.put("avf_feature",
                pm.hasSystemFeature("android.software.virtualization_framework"));

        LabPolicyEngine policy = new LabPolicyEngine(this);
        try {
            json.put("broker_domain", policy.getDomain());
            json.put("broker_location", policy.getLocationMode());
            json.put("broker_network", policy.getNetworkMode());
            json.put("broker_sensor", policy.getSensorMode());
        } finally {
            policy.close();
        }

        appendNetwork(json);
        appendCrossProfile(json);

        json.put("profile_fingerprint", stableFingerprint(json));
        return json;
    }

    private void appendNetwork(JSONObject json) {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network active = cm == null ? null : cm.getActiveNetwork();
            NetworkCapabilities caps =
                    active == null || cm == null ? null : cm.getNetworkCapabilities(active);
            LinkProperties link =
                    active == null || cm == null ? null : cm.getLinkProperties(active);

            StringBuilder transports = new StringBuilder();
            if (caps != null) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) appendToken(transports, "WIFI");
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) appendToken(transports, "CELLULAR");
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) appendToken(transports, "ETHERNET");
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) appendToken(transports, "VPN");
                json.put("network_validated",
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
                json.put("network_metered",
                        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
            }
            json.put("network_transports", transports.toString());

            if (link != null) {
                json.put("network_interface",
                        link.getInterfaceName() == null ? "" : link.getInterfaceName());

                StringBuilder dns = new StringBuilder();
                for (java.net.InetAddress address : link.getDnsServers()) {
                    appendToken(dns, address.getHostAddress());
                }
                json.put("dns", dns.toString());

                StringBuilder ips = new StringBuilder();
                for (LinkAddress address : link.getLinkAddresses()) {
                    appendToken(ips, address.getAddress().getHostAddress());
                }
                json.put("ip_addresses", ips.toString());
            } else {
                json.put("network_interface", "");
                json.put("dns", "");
                json.put("ip_addresses", "");
            }
        } catch (Throwable t) {
            try {
                json.put("network_error", t.getClass().getSimpleName());
            } catch (Exception ignored) {
            }
        }
    }

    private void appendCrossProfile(JSONObject json) {
        if (Build.VERSION.SDK_INT < 28) return;

        try {
            CrossProfileApps cross = getSystemService(CrossProfileApps.class);
            if (cross == null) return;

            List<UserHandle> targets = cross.getTargetUserProfiles();
            json.put("cross_profile_target_count", targets.size());

            StringBuilder labels = new StringBuilder();
            for (UserHandle target : targets) {
                String label;
                try {
                    label = String.valueOf(cross.getProfileSwitchingLabel(target));
                } catch (Throwable t) {
                    label = String.valueOf(target);
                }
                appendToken(labels, label);
            }
            json.put("cross_profile_targets", labels.toString());

            if (Build.VERSION.SDK_INT >= 30) {
                json.put("cross_profile_can_interact", cross.canInteractAcrossProfiles());
                json.put("cross_profile_can_request", cross.canRequestInteractAcrossProfiles());
            }
        } catch (Throwable t) {
            try {
                json.put("cross_profile_error", t.getClass().getSimpleName());
            } catch (Exception ignored) {
            }
        }
    }

    private String buildCrossProfileStatus() {
        if (Build.VERSION.SDK_INT < 28) {
            return "CrossProfileApps 需要 Android 9 / API 28+";
        }

        try {
            CrossProfileApps cross = getSystemService(CrossProfileApps.class);
            if (cross == null) return "CrossProfileApps 系统服务不可用";

            List<UserHandle> targets = cross.getTargetUserProfiles();
            StringBuilder out = new StringBuilder();
            out.append("目标 Profile 数量：").append(targets.size()).append("\n");

            for (int i = 0; i < targets.size(); i++) {
                UserHandle target = targets.get(i);
                out.append("#").append(i + 1).append("  ")
                        .append(cross.getProfileSwitchingLabel(target))
                        .append(" · ").append(target).append("\n");
            }

            DevicePolicyManager dpm =
                    (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            boolean profileOwner = dpm != null && dpm.isProfileOwnerApp(getPackageName());
            out.append("当前 GoGoGo 是 Profile Owner：").append(yesNo(profileOwner)).append("\n");

            if (Build.VERSION.SDK_INT >= 30) {
                out.append("canInteractAcrossProfiles：")
                        .append(yesNo(cross.canInteractAcrossProfiles())).append("\n");
                out.append("canRequestInteractAcrossProfiles：")
                        .append(yesNo(cross.canRequestInteractAcrossProfiles())).append("\n");

                if (profileOwner && dpm != null) {
                    try {
                        Set<String> allow = dpm.getCrossProfilePackages(
                                LabDeviceAdminReceiver.component(this));
                        out.append("Admin allowlist 包含 GoGoGo：")
                                .append(yesNo(allow.contains(getPackageName()))).append("\n");
                    } catch (Throwable t) {
                        out.append("Admin allowlist：读取失败 ")
                                .append(t.getClass().getSimpleName()).append("\n");
                    }
                }
            }

            if (targets.isEmpty()) {
                out.append("\n要出现可切换目标，GoGoGo 必须也安装在同一 profile group 的另一个已启用资料里。");
            }
            return out.toString().trim();
        } catch (Throwable t) {
            return "CrossProfileApps 状态读取失败：" + t.getClass().getSimpleName()
                    + "\n" + String.valueOf(t.getMessage());
        }
    }

    private void allowlistSelf() {
        if (Build.VERSION.SDK_INT < 30) {
            Toast.makeText(this, "Admin allowlist API 需要 Android 11+", Toast.LENGTH_LONG).show();
            return;
        }

        DevicePolicyManager dpm =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm == null || !dpm.isProfileOwnerApp(getPackageName())) {
            Toast.makeText(this,
                    "这一步必须在 GoGoGo 已成为 Profile Owner 的 Work Profile 里执行",
                    Toast.LENGTH_LONG).show();
            return;
        }

        try {
            dpm.setCrossProfilePackages(
                    LabDeviceAdminReceiver.component(this),
                    Collections.singleton(getPackageName()));
            Toast.makeText(this,
                    "已把 GoGoGo 加入 DPC cross-profile allowlist ✅\n"
                            + "接下来切到个人空间实例请求用户同意。",
                    Toast.LENGTH_LONG).show();
            refresh();
        } catch (Throwable t) {
            Toast.makeText(this,
                    "Allowlist 设置失败：" + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void requestCrossProfileConsent() {
        if (Build.VERSION.SDK_INT < 30) {
            Toast.makeText(this, "用户 consent API 需要 Android 11+", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            CrossProfileApps cross = getSystemService(CrossProfileApps.class);
            if (cross == null) return;

            if (cross.canInteractAcrossProfiles()) {
                Toast.makeText(this, "跨资料通信已经获得用户同意 ✅", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!cross.canRequestInteractAcrossProfiles()) {
                Toast.makeText(this,
                        "当前实例不能发起 consent。\n"
                                + "如果这是 Work Profile 里的 Profile Owner，请先 Allowlist，"
                                + "再切到个人空间的 GoGoGo 发起请求。",
                        Toast.LENGTH_LONG).show();
                return;
            }

            startActivity(cross.createRequestInteractAcrossProfilesIntent());
        } catch (Throwable t) {
            Toast.makeText(this,
                    "打开跨资料同意页面失败：" + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void switchToOtherProfile() {
        if (Build.VERSION.SDK_INT < 28) return;

        try {
            CrossProfileApps cross = getSystemService(CrossProfileApps.class);
            if (cross == null) return;

            List<UserHandle> targets = cross.getTargetUserProfiles();
            if (targets.isEmpty()) {
                Toast.makeText(this,
                        "没有目标 Profile。先确保 GoGoGo 在另一个资料中也安装并启用。",
                        Toast.LENGTH_LONG).show();
                return;
            }

            UserHandle target = targets.get(0);
            cross.startMainActivity(
                    new ComponentName(this, SimpleMockActivity.class),
                    target);
        } catch (Throwable t) {
            Toast.makeText(this,
                    "Profile 切换失败：" + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void exportCurrent() {
        if (currentSnapshot == null) refresh();
        if (currentSnapshot == null) return;

        String kind = currentSnapshot.optString("profile_kind", "profile").toLowerCase(Locale.US);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE,
                "GoGoGo_" + kind + "_snapshot_" + stamp + ".json");
        startActivityForResult(intent, REQ_EXPORT);
    }

    private void importSnapshot() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQ_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            writeJson(uri);
        } else if (requestCode == REQ_IMPORT) {
            readJson(uri);
        }
    }

    private void writeJson(Uri uri) {
        if (currentSnapshot == null) return;

        try (OutputStream output = getContentResolver().openOutputStream(uri);
             BufferedWriter writer = output == null ? null
                     : new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            if (writer == null) throw new IllegalStateException("output");
            writer.write(currentSnapshot.toString(2));
            writer.flush();
            Toast.makeText(this, "当前 Profile 快照已导出 ✅", Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this,
                    "导出失败：" + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void readJson(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri);
             BufferedReader reader = input == null ? null
                     : new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            if (reader == null) throw new IllegalStateException("input");

            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (text.length() > 256 * 1024) {
                    throw new IllegalArgumentException("snapshot too large");
                }
                text.append(line).append('\n');
            }

            importedSnapshot = new JSONObject(text.toString());
            importedView.setText(
                    "已导入："
                            + importedSnapshot.optString("profile_kind", "UNKNOWN")
                            + "\nFingerprint: "
                            + importedSnapshot.optString("profile_fingerprint", "n/a")
                            + "\n\n点“对比当前 vs 导入”生成差异矩阵。");
        } catch (Throwable t) {
            Toast.makeText(this,
                    "导入失败：" + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void compareSnapshots() {
        if (currentSnapshot == null) refresh();
        if (currentSnapshot == null || importedSnapshot == null) {
            Toast.makeText(this, "先导入另一份快照", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<String> keys = new TreeSet<>();
        Iterator<String> a = currentSnapshot.keys();
        while (a.hasNext()) keys.add(a.next());
        Iterator<String> b = importedSnapshot.keys();
        while (b.hasNext()) keys.add(b.next());

        int same = 0;
        int changed = 0;
        StringBuilder diff = new StringBuilder();
        diff.append("CURRENT: ")
                .append(currentSnapshot.optString("profile_kind", "UNKNOWN"))
                .append("\nIMPORTED: ")
                .append(importedSnapshot.optString("profile_kind", "UNKNOWN"))
                .append("\n\n");

        for (String key : keys) {
            if ("timestamp_utc".equals(key)) continue;

            String left = String.valueOf(currentSnapshot.opt(key));
            String right = String.valueOf(importedSnapshot.opt(key));
            if (left.equals(right)) {
                same++;
            } else {
                changed++;
                diff.append("≠ ").append(key).append("\n")
                        .append("  当前：").append(left).append("\n")
                        .append("  导入：").append(right).append("\n\n");
            }
        }

        diff.insert(0,
                "差异字段 " + changed + " · 相同字段 " + same + "\n\n");

        TextView view = body();
        view.setText(diff.toString().trim());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(view);

        new AlertDialog.Builder(this)
                .setTitle("🔬 Cross-profile 差异矩阵")
                .setView(scroll)
                .setPositiveButton("关闭", null)
                .show();
    }

    private String renderSnapshot(JSONObject json) {
        if (json == null) return "无快照";
        StringBuilder out = new StringBuilder();

        String[] important = {
                "profile_kind",
                "profile_fingerprint",
                "user_handle",
                "user_serial",
                "process_uid",
                "profile_owner",
                "device_owner",
                "version",
                "android_release",
                "api",
                "data_dir",
                "broker_domain",
                "broker_location",
                "broker_network",
                "broker_sensor",
                "network_transports",
                "network_interface",
                "dns",
                "ip_addresses",
                "cross_profile_target_count",
                "cross_profile_can_interact",
                "cross_profile_can_request"
        };

        for (String key : important) {
            if (json.has(key)) {
                out.append(key).append(": ").append(json.opt(key)).append("\n");
            }
        }
        return out.toString().trim();
    }

    private String stableFingerprint(JSONObject json) throws Exception {
        StringBuilder canonical = new StringBuilder();
        String[] keys = {
                "profile_kind",
                "user_serial",
                "process_uid",
                "profile_owner",
                "device_owner",
                "package",
                "android_release",
                "api",
                "data_dir",
                "broker_domain",
                "broker_location",
                "broker_network",
                "broker_sensor"
        };
        for (String key : keys) {
            canonical.append(key).append('=')
                    .append(String.valueOf(json.opt(key))).append('\n');
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 8 && i < hash.length; i++) {
            hex.append(String.format(Locale.US, "%02x", hash[i] & 0xff));
        }
        return hex.toString();
    }

    private String getVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (Throwable t) {
            return "";
        }
    }

    private static String isoTime(long time) {
        SimpleDateFormat format =
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(time));
    }

    private static void appendToken(StringBuilder out, String value) {
        if (value == null || value.isEmpty()) return;
        if (out.length() > 0) out.append(", ");
        out.append(value);
    }

    private static String yesNo(boolean value) {
        return value ? "是" : "否";
    }
}
