package com.zcshou.gogogo;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.zcshou.gogogo.virtualv0.ContainerV2ManifestRouteProbe;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * User-triggered V2 experiment controller. Proxy is a host Android Activity,
 * guest remains uninstalled and is accessed only from the embedded fixture APK.
 */
public final class VirtualContainerV2Activity extends AppCompatActivity {
    public static final String PREFS = "twt_v2_component";
    public static final String LAST_RESULT = "last_result";
    public static final String LAST_SESSION_RESULT = "last_session_result";
    public static final String LAST_SESSION_SUMMARY = "last_session_summary";
    public static final String LAST_RECREATE_RESULT = "last_recreate_result";
    public static final String LAST_RECREATE_SUMMARY = "last_recreate_summary";
    public static final String LAST_COLD_RESULT = "last_cold_result";

    public static final String EXTRA_NONCE = "twt_v2_nonce";
    public static final String EXTRA_PAYLOAD = "twt_v2_payload";
    public static final String EXTRA_RESPONSE = "twt_v2_response";
    public static final String EXTRA_DETAIL = "twt_v2_detail";
    public static final String EXTRA_DIGEST = "twt_v2_digest";
    public static final String EXTRA_ARCHIVE_OK = "twt_v2_archive_ok";
    public static final String EXTRA_PROXY_PID = "twt_v2_proxy_pid";
    public static final String EXTRA_PROXY_UID = "twt_v2_proxy_uid";

    private static final int REQUEST = 2202;
    private static final int REQUEST_SESSION = 2203;
    private static final int REQUEST_RECREATE = 2204;
    private static final String PAYLOAD = "TWT_V2_2026";
    private String pendingNonce;
    private TextView report;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        GoGoUi.applyScreenBackground(scroll);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = GoGoUi.dp(this, 18);
        root.setPadding(p, p, p, p);
        scroll.addView(root);
        GoGoUi.addLabHeader(root, "SYSTEMS / CONTAINER V2",
                "受控 APK · Manifest 与 Intent 代理",
                "Android 系统启动宿主代理，宿主读取 Guest 的组件声明与路由合约",
                v -> finish());
        root.addView(GoGoUi.muted(this,
                "只检查天外天自己打包的测试 APK。Guest 声明的 Activity 不会安装、"
                        + "不会由 Android 系统启动；这是显式 Intent → 宿主代理 → Guest DEX → 返回结果的实验。"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.primaryButton(this,
                "一键运行 V2 组件 / Intent 往返", v -> runV2()), GoGoUi.matchWrap());
        root.addView(GoGoUi.primaryButton(this,
                "V2 第二阶段：连续请求 / 取消 / 后台恢复", v -> runSession()),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.primaryButton(this,
                "V2 第三阶段：真实 Activity 重建 / 防重复请求",
                v -> runRecreation()), GoGoUi.matchWrap());
        root.addView(GoGoUi.primaryButton(this,
                "V2 第四阶段：专属子进程死亡 / 冷启动恢复",
                v -> startActivity(new Intent(this, VirtualContainerV24Activity.class))),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.secondaryButton(this,
                "完成并返回 V0（总日志自动合并）", v -> finish()), GoGoUi.matchWrap());
        report = GoGoUi.reportPanel(this);
        String previous = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(LAST_RESULT, "尚未执行 V2 组件 / Intent 实验。");
        report.setText(previous);
        root.addView(report, GoGoUi.matchWrap());
        setContentView(scroll);
        if (state != null) pendingNonce = state.getString(EXTRA_NONCE);
    }

    private void runV2() {
        if (pendingNonce != null) return;
        pendingNonce = UUID.randomUUID().toString();
        showAndSave("V2 状态：运行中\nAndroid API=" + Build.VERSION.SDK_INT
                + "；宿主 PID=" + Process.myPid() + "；UID=" + Process.myUid()
                + "\n正在启动已注册的天外天代理 Activity（不是 Guest Activity）...");
        Intent request = new Intent(this, VirtualContainerV2ProxyActivity.class);
        request.setAction(ContainerV2ManifestRouteProbe.ACTION);
        request.putExtra(EXTRA_NONCE, pendingNonce);
        request.putExtra(EXTRA_PAYLOAD, PAYLOAD);
        try {
            // Only a registered, non-exported host proxy may be dispatched.
            startActivityForResult(request, REQUEST);
        } catch (RuntimeException failure) {
            showAndSave("V2 结果：FAIL\nhost-proxy-start: "
                    + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            pendingNonce = null;
        }
    }

    private void runSession() {
        if (pendingNonce != null) return;
        pendingNonce = UUID.randomUUID().toString();
        Intent request = new Intent(this, VirtualContainerV2SessionActivity.class);
        request.putExtra(EXTRA_NONCE, pendingNonce);
        try {
            startActivityForResult(request, REQUEST_SESSION);
        } catch (RuntimeException failure) {
            showAndSave("V2.2 FAIL：启动宿主 Session 代理失败 · "
                    + failure.getClass().getSimpleName());
            pendingNonce = null;
        }
    }

    private void runRecreation() {
        if (pendingNonce != null) return;
        pendingNonce = UUID.randomUUID().toString();
        Intent request = new Intent(this, VirtualContainerV23RecreationActivity.class);
        request.putExtra(EXTRA_NONCE, pendingNonce);
        try {
            startActivityForResult(request, REQUEST_RECREATE);
        } catch (RuntimeException failure) {
            String result = "V2.3 FAIL：无法启动私有宿主重建代理 · "
                    + failure.getClass().getSimpleName();
            report.setText(result);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(LAST_RECREATE_SUMMARY, result).apply();
            pendingNonce = null;
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(EXTRA_NONCE, pendingNonce);
        super.onSaveInstanceState(state);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_RECREATE) {
            if (pendingNonce == null) return;
            String expectedNonce = pendingNonce;
            pendingNonce = null;
            String actualNonce = data == null ? null : data.getStringExtra(EXTRA_NONCE);
            String response = data == null ? null : data.getStringExtra(EXTRA_RESPONSE);
            String digest = data == null ? null : data.getStringExtra(EXTRA_DIGEST);
            int pid = data == null ? -1 : data.getIntExtra(EXTRA_PROXY_PID, -1);
            int uid = data == null ? -1 : data.getIntExtra(EXTRA_PROXY_UID, -1);
            boolean valid = data != null && resultCode == Activity.RESULT_OK
                    && expectedNonce.equals(actualNonce)
                    && "V23_RECREATION_PASS".equals(response)
                    && digest != null && digest.matches("[a-f0-9]{64}")
                    && data.getBooleanExtra(EXTRA_ARCHIVE_OK, false)
                    && pid == Process.myPid() && uid == Process.myUid();
            String summary = "V2.3 Activity 重建代理返回：" + (valid ? "PASS" : "FAIL/INCOMPLETE")
                    + "\n旧请求/新请求及真实 onDestroy 证据见 V2.3 独立附录"
                    + "\n随机标记匹配=" + expectedNonce.equals(actualNonce)
                    + "\nHOST PID=" + Process.myPid() + "；代理 PID=" + pid
                    + "\nAPK SHA-256=" + digest;
            report.setText(summary);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(LAST_RECREATE_SUMMARY, summary).apply();
            return;
        }
        if (requestCode == REQUEST_SESSION) {
            if (pendingNonce == null) return;
            String expectedToken = pendingNonce;
            pendingNonce = null;
            String receivedToken = data == null ? null : data.getStringExtra(EXTRA_NONCE);
            String response = data == null ? null : data.getStringExtra(EXTRA_RESPONSE);
            String digest = data == null ? null : data.getStringExtra(EXTRA_DIGEST);
            int pid = data == null ? -1 : data.getIntExtra(EXTRA_PROXY_PID, -1);
            int uid = data == null ? -1 : data.getIntExtra(EXTRA_PROXY_UID, -1);
            boolean ok = resultCode == Activity.RESULT_OK && data != null
                    && expectedToken.equals(receivedToken)
                    && "V2_SESSION_PASS".equals(response)
                    && digest != null && digest.matches("[a-f0-9]{64}")
                    && data.getBooleanExtra(EXTRA_ARCHIVE_OK, false)
                    && pid == Process.myPid() && uid == Process.myUid();
            // The real onDestroy/dispose evidence is independently appended
            // from VirtualContainerV2SessionActivity's persisted V2.2 report.
            String result = "V2.2 Session 代理请求/响应：" + (ok ? "PASS" : "FAIL/INCOMPLETE")
                    + "\nHOST PID=" + Process.myPid() + "；代理 PID=" + pid
                    + "\n随机标记匹配=" + expectedToken.equals(receivedToken)
                    + "\n独立 APK SHA-256=" + digest
                    + "\n实际 Guest 状态及资源清理见 V2.2 独立附录。";
            report.setText(result);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(LAST_SESSION_SUMMARY, result).apply();
            return;
        }
        if (requestCode != REQUEST || pendingNonce == null) return;
        String expectedNonce = pendingNonce;
        pendingNonce = null;
        String receivedNonce = data == null ? null : data.getStringExtra(EXTRA_NONCE);
        String received = data == null ? null : data.getStringExtra(EXTRA_RESPONSE);
        String digest = data == null ? "" : data.getStringExtra(EXTRA_DIGEST);
        String detail = data == null ? "Host proxy returned no result"
                : data.getStringExtra(EXTRA_DETAIL);
        boolean archiveOk = data != null && data.getBooleanExtra(EXTRA_ARCHIVE_OK, false);
        int proxyPid = data == null ? -1 : data.getIntExtra(EXTRA_PROXY_PID, -1);
        int proxyUid = data == null ? -1 : data.getIntExtra(EXTRA_PROXY_UID, -1);
        String expected = "GUEST_V2_RESULT:" + new StringBuilder(PAYLOAD).reverse().toString();
        boolean digestOk = digest != null && digest.matches("[a-f0-9]{64}");
        boolean nonceOk = expectedNonce.equals(receivedNonce);
        boolean responseOk = expected.equals(received);
        boolean pidOk = proxyPid == Process.myPid();
        boolean uidOk = proxyUid == Process.myUid();
        boolean passed = resultCode == Activity.RESULT_OK && archiveOk
                && nonceOk && responseOk && pidOk && uidOk && digestOk;

        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                .format(new Date());
        String output = "天外天 / Virtual Container V2 受控 Manifest / Intent 总报告\n"
                + "生成时间：" + time + "\n"
                + "Android API：" + Build.VERSION.SDK_INT + "\n"
                + "宿主 PID=" + Process.myPid() + "；UID=" + Process.myUid() + "\n"
                + "代理 PID=" + proxyPid + "；UID=" + proxyUid + "\n"
                + "guest APK SHA-256=" + digest + "\n"
                + "宿主显式 Intent action=" + ContainerV2ManifestRouteProbe.ACTION + "\n"
                + "payload=" + PAYLOAD + "（仅合成测试值）\n"
                + "代理与宿主同 PID/UID=" + (pidOk && uidOk) + "\n"
                + "请求与返回的随机标记匹配=" + nonceOk + "\n"
                + "Guest DEX 路由响应正确=" + responseOk + "\n"
                + "Guest APK Manifest 验证=" + archiveOk + "\n"
                + (detail == null ? "" : detail) + "\n"
                + "V2 验收结果：" + (passed ? "PASS" : "FAIL") + "\n"
                + "限制：Guest Activity 只被解析为声明和 DEX 类；"
                + "系统实际启动的是天外天的私有代理 Activity，"
                + "不代表未安装 Guest Activity 原生启动、独立 UID/权限或微信兼容。\n";
        showAndSave(output);
    }

    private void showAndSave(String value) {
        report.setText(value);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(LAST_RESULT, value).apply();
    }
}
