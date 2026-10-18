package com.zcshou.gogogo;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.zcshou.gogogo.virtualv0.ContainerV0TrustedGuest;
import com.zcshou.gogogo.virtualv0.ContainerV2ManifestRouteProbe;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * V2.3 actual HOST Activity.recreate() experiment. The old Java guest object is
 * disposed and the new Android Activity loads an entirely fresh guest instance.
 * Neither ActivityManager nor any third-party app is virtualized here.
 */
public final class VirtualContainerV23RecreationActivity extends AppCompatActivity {
    private static final String GUEST_CLASS =
            "com.soozooq.tianwaitian.fixture.GuestV23Recreation";
    private static final String PREFS = "twt_v23_internal_proof";
    private static final String OLD_PROOF = "old_token";
    private static final String OLD_DIGEST = "old_digest";
    private static final String OLD_DISPOSED = "old_disposed";
    private static final String OLD_CLEARED = "old_cleared";
    private static final String OLD_SNAPSHOT = "old_snapshot";
    private static final String KEY_TOKEN = "v23_old_token";
    private static final String KEY_REQUESTED = "v23_recreate_requested";
    private static final String KEY_TRACE = "v23_host_trace";
    private static final String KEY_STARTED = "v23_started";
    private static final String KEY_NONCE = "v23_nonce";

    private final StringBuilder timeline = new StringBuilder(2800);
    private String started;
    private String controllerNonce;
    private String oldToken;
    private String digest = "(not loaded)";
    private String error = "";
    private boolean recreated;
    private boolean recreateRequested;
    private boolean oldCancelled;
    private boolean oldProofVerified;
    private boolean newGuestVerified;
    private boolean finishingVerified;
    private boolean destroyed;
    private boolean disposed;
    private String oldSnapshot = "(none)";
    private ContainerV0TrustedGuest fixture;
    private Object guest;
    private Method prepareOld;
    private Method cancelOld;
    private Method oldCleared;
    private Method startNew;
    private Method completeNew;
    private Method newReady;
    private Method snapshot;
    private Method dispose;
    private TextView stateText;
    private TextView report;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        controllerNonce = getIntent() == null ? null
                : getIntent().getStringExtra(VirtualContainerV2Activity.EXTRA_NONCE);
        started = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                .format(new Date());
        if (saved != null) {
            recreated = true;
            recreateRequested = saved.getBoolean(KEY_REQUESTED, false);
            oldToken = saved.getString(KEY_TOKEN);
            started = saved.getString(KEY_STARTED, started);
            timeline.append(saved.getString(KEY_TRACE, ""));
            String originalNonce = saved.getString(KEY_NONCE);
            if (originalNonce == null || !originalNonce.equals(controllerNonce)) {
                fail("saved state nonce does not match host request");
            }
            log("HOST onCreate new instance from saved state");
        } else {
            log("HOST onCreate first instance");
        }

        ScrollView scroll = new ScrollView(this);
        GoGoUi.applyScreenBackground(scroll);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = GoGoUi.dp(this, 18);
        root.setPadding(p, p, p, p);
        scroll.addView(root);
        GoGoUi.addLabHeader(root, "SYSTEMS / CONTAINER V2.3",
                "真实 Activity 重建 / Guest 会话隔离",
                "主动重建宿主页面，旧 Guest 取消并销毁，新 Guest 拒绝旧结果",
                v -> finish());
        root.addView(GoGoUi.muted(this,
                "第一步点击“触发真正重建”。Android 将调用 Activity.recreate()。"
                        + "新实例核对旧实例销毁及 Guest 清理后，只允许新的请求完成一次。"
                        + "第二步点击“完成并退出”，回 V0 一次复制总报告。"),
                GoGoUi.matchWrap());
        stateText = GoGoUi.muted(this, "正在加载独立 APK 的 V2.3 状态机…");
        root.addView(stateText, GoGoUi.matchWrap());
        root.addView(GoGoUi.primaryButton(this,
                "触发 Activity 真正重建（旧 Guest 挂起 D 必须取消）",
                v -> requestRecreate()), GoGoUi.matchWrap());
        root.addView(GoGoUi.secondaryButton(this,
                "完成并退出（只接受已核验的重建）", v -> completeAndExit()),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.secondaryButton(this,
                "提前退出 / 失败也保留日志", v -> finish()),
                GoGoUi.matchWrap());
        report = GoGoUi.reportPanel(this);
        root.addView(report, GoGoUi.matchWrap());
        setContentView(scroll);
        update();

        if (controllerNonce == null || !controllerNonce.matches("[a-f0-9-]{36}")) {
            fail("untrusted host request nonce");
            return;
        }
        if (recreated && (!recreateRequested || oldToken == null
                || !oldToken.matches("[a-f0-9-]{36}"))) {
            fail("Activity recreated without requested saved-state contract");
            return;
        }
        loadGuestAsync();
    }

    private void loadGuestAsync() {
        new Thread(() -> {
            ContainerV0TrustedGuest candidate = null;
            Class<?> cls = null;
            Object object = null;
            String sha = null;
            String failure = null;
            try {
                ContainerV2ManifestRouteProbe.Result manifest =
                        ContainerV2ManifestRouteProbe.run(getApplicationContext(),
                                ContainerV2ManifestRouteProbe.ACTION, "V23_CHECK");
                if (!manifest.passed) throw new SecurityException(manifest.detail);
                candidate = ContainerV0TrustedGuest.open(getApplicationContext());
                sha = candidate.getSha256();
                if (!sha.equals(manifest.sha256)) {
                    throw new SecurityException("guest SHA differs from Manifest probe");
                }
                ClassLoader loader = candidate.getGuestClass().getClassLoader();
                cls = Class.forName(GUEST_CLASS, true, loader);
                if (cls.getClassLoader() != loader) {
                    throw new SecurityException("V2.3 guest class was not defined by fixture DEX");
                }
                cls.getMethod("prepareOld", String.class);
                cls.getMethod("cancelOld", String.class);
                cls.getMethod("oldCleared");
                cls.getMethod("startNew", String.class, String.class);
                cls.getMethod("completeNew", String.class);
                cls.getMethod("newReady");
                cls.getMethod("snapshot");
                cls.getMethod("dispose");
                object = cls.getDeclaredConstructor().newInstance();
            } catch (Throwable caught) {
                failure = describe(caught);
            }
            final ContainerV0TrustedGuest loadedFixture = candidate;
            final Class<?> loadedClass = cls;
            final Object loadedGuest = object;
            final String loadedSha = sha;
            final String loadingFailure = failure;
            runOnUiThread(() -> {
                if (isFinishing() || destroyed || isDestroyed()) {
                    if (loadedFixture != null) loadedFixture.close();
                    return;
                }
                if (loadingFailure != null) {
                    if (loadedFixture != null) loadedFixture.close();
                    fail("load/verify guest APK: " + loadingFailure);
                    return;
                }
                try {
                    fixture = loadedFixture;
                    digest = loadedSha;
                    guest = loadedGuest;
                    prepareOld = loadedClass.getMethod("prepareOld", String.class);
                    cancelOld = loadedClass.getMethod("cancelOld", String.class);
                    oldCleared = loadedClass.getMethod("oldCleared");
                    startNew = loadedClass.getMethod("startNew", String.class, String.class);
                    completeNew = loadedClass.getMethod("completeNew", String.class);
                    newReady = loadedClass.getMethod("newReady");
                    snapshot = loadedClass.getMethod("snapshot");
                    dispose = loadedClass.getMethod("dispose");
                    log("HOST verified fresh guest from trusted independent APK SHA=" + digest);
                    if (recreated) {
                        verifyRecreated();
                    } else {
                        oldToken = UUID.randomUUID().toString();
                        if (!"PENDING_D".equals(call(prepareOld, oldToken))) {
                            throw new IllegalStateException("old pending request was not prepared");
                        }
                        log("HOST first instance: Guest pending D, token assigned");
                    }
                    update();
                } catch (Throwable caught) {
                    fail("guest state initialization: " + describe(caught));
                }
            });
        }, "twt-v23-guest-load").start();
    }

    private void requestRecreate() {
        if (recreated || recreateRequested || guest == null || !error.isEmpty()) {
            Toast.makeText(this, "请等待旧 Guest 初始化；只能触发一次重建", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (!"CANCELLED_D".equals(call(cancelOld, oldToken))
                    || !Boolean.TRUE.equals(call(oldCleared))) {
                throw new IllegalStateException("old pending D was not cancelled");
            }
            oldCancelled = true;
            recreateRequested = true;
            log("HOST cancelled original guest D; now calling real Activity.recreate()");
            update();
            recreate();
        } catch (Throwable caught) {
            fail("cancel before Activity.recreate(): " + describe(caught));
        }
    }

    private void verifyRecreated() throws Exception {
        String proofToken = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(OLD_PROOF, "");
        String proofDigest = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(OLD_DIGEST, "");
        boolean proofDisposed = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(OLD_DISPOSED, false);
        boolean proofCleared = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(OLD_CLEARED, false);
        oldSnapshot = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(OLD_SNAPSHOT, "(none)");
        oldProofVerified = recreateRequested && oldToken.equals(proofToken)
                && digest.equals(proofDigest) && proofDisposed && proofCleared
                && oldSnapshot.contains("disposed=true")
                && oldSnapshot.contains("completions=0")
                && oldSnapshot.contains("pending=false");
        if (!oldProofVerified) {
            throw new SecurityException("missing matching OLD onDestroy/dispose proof");
        }
        log("HOST NEW verified OLD instance onDestroy + cancelled pending D + Guest dispose");
        String newToken = UUID.randomUUID().toString();
        if (!"NEW_PENDING_D".equals(call(startNew, oldToken, newToken))) {
            throw new IllegalStateException("new guest refused new request setup");
        }
        String result = (String) call(completeNew, newToken);
        newGuestVerified = "GUEST_V2_RESULT:32V_REVOCER".equals(result)
                && Boolean.TRUE.equals(call(newReady));
        if (!newGuestVerified) {
            throw new IllegalStateException("stale token leak or fresh request not exactly once");
        }
        log("HOST NEW loaded independent guest object; stale old token rejected; "
                + "new request completed exactly once");
        // Do not repeat the old proof if the user manually starts another run.
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .remove(OLD_PROOF).remove(OLD_DIGEST).remove(OLD_DISPOSED)
                .remove(OLD_CLEARED).remove(OLD_SNAPSHOT).apply();
    }

    private void completeAndExit() {
        if (!recreated || !oldProofVerified || !newGuestVerified || !error.isEmpty()) {
            Toast.makeText(this, "还未通过真实重建验证；可提前退出留 FAIL 日志",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        finishingVerified = true;
        log("HOST NEW result verified; waiting for Guest disposal in onDestroy");
        Intent result = new Intent();
        result.putExtra(VirtualContainerV2Activity.EXTRA_NONCE, controllerNonce);
        result.putExtra(VirtualContainerV2Activity.EXTRA_RESPONSE, "V23_RECREATION_PASS");
        result.putExtra(VirtualContainerV2Activity.EXTRA_DIGEST, digest);
        result.putExtra(VirtualContainerV2Activity.EXTRA_ARCHIVE_OK, true);
        result.putExtra(VirtualContainerV2Activity.EXTRA_PROXY_PID, Process.myPid());
        result.putExtra(VirtualContainerV2Activity.EXTRA_PROXY_UID, Process.myUid());
        setResult(Activity.RESULT_OK, result);
        finish();
    }

    private Object call(Method method, Object... values) throws Exception {
        try {
            return method.invoke(guest, values);
        } catch (InvocationTargetException failure) {
            Throwable actual = failure.getTargetException();
            throw new IllegalStateException(actual.getClass().getSimpleName()
                    + ": " + actual.getMessage(), actual);
        }
    }

    private void fail(String reason) {
        if (error.isEmpty()) error = "FAIL: " + reason;
        log(error);
        update();
    }

    private static String describe(Throwable failure) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; failure != null && i < 4; i++) {
            if (i > 0) out.append(" <- ");
            out.append(failure.getClass().getSimpleName());
            if (failure.getMessage() != null) {
                String msg = failure.getMessage();
                out.append(": ").append(msg, 0, Math.min(220, msg.length()));
            }
            if (failure == failure.getCause()) break;
            failure = failure.getCause();
        }
        return out.toString();
    }

    private void log(String event) {
        if (timeline.length() < 2800) timeline.append(event).append('\n');
    }

    private String buildReport() {
        StringBuilder out = new StringBuilder();
        out.append("天外天 / V2.3 Activity 真重建与 Guest 请求防重报告\n")
                .append("开始时间：").append(started).append('\n')
                .append("Android API=").append(Build.VERSION.SDK_INT)
                .append("；HOST PID=").append(Process.myPid())
                .append("；UID=").append(Process.myUid()).append('\n')
                .append("独立 fixture APK SHA-256=").append(digest).append('\n')
                .append("请求主动重建=").append(recreateRequested)
                .append("；新 Activity 实例=").append(recreated).append('\n')
                .append("旧 Guest onDestroy/dispose/取消证据=").append(oldProofVerified).append('\n')
                .append("新 Guest 拒绝旧请求并仅完成一次新请求=").append(newGuestVerified).append('\n')
                .append("新 Guest 最终 dispose=").append(disposed).append('\n')
                .append("旧 Guest 快照：\n").append(oldSnapshot).append('\n')
                .append("宿主生命周期与会话时间线：\n").append(timeline);
        if (guest != null && snapshot != null) {
            try {
                out.append("当前独立 Guest DEX 快照：\n").append(call(snapshot));
            } catch (Throwable caught) {
                out.append("FAIL snapshot: ").append(describe(caught)).append('\n');
            }
        }
        if (!error.isEmpty()) out.append(error).append('\n');
        out.append("V2.3 真机验收：")
                .append(destroyed && finishingVerified && recreated
                        && oldProofVerified && newGuestVerified && disposed
                        && error.isEmpty() ? "PASS" : "INCOMPLETE/FAIL").append('\n')
                .append("边界：证明宿主 Android Activity 真重建后的旧 Java Guest 取消/清理"
                        + "及新 Guest 拒绝过期请求，不代表跨进程死亡恢复、独立 UID、"
                        + "未安装 Guest Activity 真实系统启动或任意第三方 APK 兼容。\n");
        return out.toString();
    }

    private void update() {
        if (report == null || stateText == null) return;
        String hint = !error.isEmpty() ? "存在失败，退出后回 V0 复制报告"
                : finishingVerified ? "结果已验收，正在清理退出"
                : newGuestVerified ? "重建已验证；请点“完成并退出”"
                : guest != null && !recreated ? "旧 Guest 的 D 已挂起；现在可以触发真正重建"
                : "请等待独立 Guest DEX 与历史销毁证据核对";
        stateText.setText(hint);
        String output = buildReport();
        report.setText(output);
        getSharedPreferences(VirtualContainerV2Activity.PREFS, MODE_PRIVATE)
                .edit().putString(VirtualContainerV2Activity.LAST_RECREATE_RESULT, output).apply();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        log("HOST onSaveInstanceState; requested=" + recreateRequested);
        state.putBoolean(KEY_REQUESTED, recreateRequested);
        state.putString(KEY_TOKEN, oldToken);
        state.putString(KEY_TRACE, timeline.toString());
        state.putString(KEY_STARTED, started);
        state.putString(KEY_NONCE, controllerNonce);
        update();
        super.onSaveInstanceState(state);
    }

    @Override protected void onStart() { super.onStart(); log("HOST onStart"); update(); }
    @Override protected void onResume() { super.onResume(); log("HOST onResume"); update(); }
    @Override protected void onRestart() { super.onRestart(); log("HOST onRestart"); update(); }
    @Override protected void onPause() { log("HOST onPause"); update(); super.onPause(); }
    @Override protected void onStop() { log("HOST onStop"); update(); super.onStop(); }

    @Override protected void onDestroy() {
        log("HOST onDestroy");
        if (guest != null && dispose != null) {
            try {
                // Old session: its pending D MUST have been cancelled before
                // recreation. New session: dispose after single completion.
                boolean cleared = !recreated && oldCancelled
                        && Boolean.TRUE.equals(call(oldCleared));
                call(dispose);
                disposed = true;
                log("GUEST dispose() called in real HOST onDestroy");
                if (!recreated && recreateRequested) {
                    String evidence = (String) call(snapshot);
                    boolean committed = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString(OLD_PROOF, oldToken)
                            .putString(OLD_DIGEST, digest)
                            .putBoolean(OLD_DISPOSED, disposed)
                            .putBoolean(OLD_CLEARED, cleared)
                            .putString(OLD_SNAPSHOT, evidence).commit();
                    if (!committed) fail("old cleanup proof was not committed");
                }
            } catch (Throwable failure) {
                fail("onDestroy Guest dispose: " + describe(failure));
            }
        }
        destroyed = true;
        update();
        if (fixture != null) {
            fixture.close();
            fixture = null;
        }
        super.onDestroy();
    }
}
