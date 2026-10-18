package com.zcshou.gogogo;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * V2 stage 2: A HOST Activity receiving real Android callbacks.
 * Invokes a session state machine from the OWN embedded fixture APK, which is
 * neither installed nor launched by Android's ActivityManager.
 */
public final class VirtualContainerV2SessionActivity extends AppCompatActivity {
    private static final String GUEST_CLASS =
            "com.soozooq.tianwaitian.fixture.GuestV2Session";
    private static final String SAMPLE = "V2_SESSION";
    private static final String SAVED_INTERRUPTED = "twt_v2_interrupted";

    private final ArrayList<String> deferred = new ArrayList<>();
    private final StringBuilder trace = new StringBuilder(3100);
    private TextView status;
    private TextView report;
    private String started;
    private String nonce;
    private String digest = "(not loaded)";
    private String fatal = "";
    private ContainerV0TrustedGuest guest;
    private Object session;
    private Method lifecycle;
    private Method snapshot;
    private Method passed;
    private Method dispose;
    private Method runScript;
    private Method completeScript;
    private boolean ready;
    private boolean scriptReady;
    private boolean verifiedBeforeExit;
    private boolean destroyed;
    private boolean interrupted;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        nonce = getIntent() == null ? null
                : getIntent().getStringExtra(VirtualContainerV2Activity.EXTRA_NONCE);
        started = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
        interrupted = saved != null && saved.getBoolean(SAVED_INTERRUPTED, false);
        if (saved != null) {
            trace.append("HOST Activity reconstructed; previous Guest session not reused\n");
        }

        ScrollView scroll = new ScrollView(this);
        GoGoUi.applyScreenBackground(scroll);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = GoGoUi.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        GoGoUi.addLabHeader(root, "SYSTEMS / CONTAINER V2.2",
                "Guest 连续请求与生命周期会话",
                "受控 APK 代码执行请求；Android 回调来自天外天自己的页面",
                v -> finish());
        root.addView(GoGoUi.muted(this,
                "自动验证连续请求、取消、拒绝错误请求；之后点“打开系统设置”，"
                        + "再返回验证真机 onPause / onStop / onRestart / onResume。"
                        + "只有这些真实事件齐全才能完成 pending D。"),
                GoGoUi.matchWrap());
        status = GoGoUi.muted(this, "正在加载独立 APK 会话协议…");
        root.addView(status, GoGoUi.matchWrap());
        root.addView(GoGoUi.primaryButton(this,
                "打开系统设置并返回（真实后台恢复）", v -> {
                    if (!scriptReady) {
                        Toast.makeText(this, "请等待自动请求测试完成", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    } catch (RuntimeException e) {
                        failure("launch settings", e);
                    }
                }), GoGoUi.matchWrap());
        root.addView(GoGoUi.secondaryButton(this,
                "验证挂起请求并完成退出", v -> completeAndExit()),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.secondaryButton(this,
                "提前取消退出（应标记未完成）", v -> finish()),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.muted(this,
                "全部日志自动加入 V0「一键复制总报告」的 V2.2 附录，无需在此复制。"),
                GoGoUi.matchWrap());
        report = GoGoUi.reportPanel(this);
        root.addView(report, GoGoUi.matchWrap());
        setContentView(scroll);

        forward("onCreate");
        if (nonce == null || !nonce.matches("[a-f0-9-]{36}")) {
            failure("request nonce", new SecurityException("invalid V2 session request"));
            return;
        }
        loadSessionAsync();
    }

    private void loadSessionAsync() {
        new Thread(() -> {
            ContainerV0TrustedGuest candidate = null;
            Object object = null;
            Class<?> entry = null;
            String verifiedSha = null;
            String fault = null;
            try {
                ContainerV2ManifestRouteProbe.Result archive =
                        ContainerV2ManifestRouteProbe.run(getApplicationContext(),
                                ContainerV2ManifestRouteProbe.ACTION, SAMPLE);
                if (!archive.passed) throw new SecurityException(archive.detail);
                candidate = ContainerV0TrustedGuest.open(getApplicationContext());
                verifiedSha = candidate.getSha256();
                if (!verifiedSha.equals(archive.sha256)) {
                    throw new SecurityException("V2 Manifest and Session fixture digest mismatch");
                }
                ClassLoader loader = candidate.getGuestClass().getClassLoader();
                entry = Class.forName(GUEST_CLASS, true, loader);
                if (entry.getClassLoader() != loader) {
                    throw new SecurityException("GuestV2Session not defined by separate APK DEX");
                }
                entry.getMethod("onHostLifecycle", String.class);
                entry.getMethod("runScript");
                entry.getMethod("finishAfterRealReturn");
                entry.getMethod("snapshot");
                entry.getMethod("passed");
                entry.getMethod("dispose");
                object = entry.getDeclaredConstructor().newInstance();
            } catch (Throwable failure) {
                fault = describe(failure);
            }
            final ContainerV0TrustedGuest loadedGuest = candidate;
            final Object loadedObject = object;
            final Class<?> loadedClass = entry;
            final String loadedSha = verifiedSha;
            final String loadFailure = fault;
            runOnUiThread(() -> {
                if (destroyed || isFinishing() || isDestroyed()) {
                    if (loadedGuest != null) loadedGuest.close();
                    return;
                }
                if (loadFailure != null) {
                    if (loadedGuest != null) loadedGuest.close();
                    failure("load controlled manifest/session", new IllegalStateException(loadFailure));
                    return;
                }
                try {
                    guest = loadedGuest;
                    digest = loadedSha;
                    session = loadedObject;
                    lifecycle = loadedClass.getMethod("onHostLifecycle", String.class);
                    runScript = loadedClass.getMethod("runScript");
                    completeScript = loadedClass.getMethod("finishAfterRealReturn");
                    snapshot = loadedClass.getMethod("snapshot");
                    passed = loadedClass.getMethod("passed");
                    dispose = loadedClass.getMethod("dispose");
                    ready = true;
                    for (String item : deferred) invoke(lifecycle, item);
                    deferred.clear();
                    trace.append("HOST guest session class loaded from fixture DEX; SHA=")
                            .append(digest).append('\n');
                    String preliminary = (String) invoke(runScript);
                    if (!preliminary.contains("pending=D") || !preliminary.contains("rejected=1")
                            && !preliminary.contains("expectedRejected=1")) {
                        throw new IllegalStateException("controlled request script did not prepare pending D");
                    }
                    scriptReady = true;
                    trace.append("HOST Guest script: A/C completed, B cancelled, wrong request rejected\n");
                    update();
                } catch (Throwable caught) {
                    failure("script", caught);
                }
            });
        }, "twt-v2-session-guest-load").start();
    }

    private Object invoke(Method method, Object... args) throws Exception {
        try {
            return method.invoke(session, args);
        } catch (InvocationTargetException wrapped) {
            Throwable cause = wrapped.getTargetException();
            throw new IllegalStateException("guest " + cause.getClass().getSimpleName()
                    + ": " + cause.getMessage(), cause);
        }
    }

    private void completeAndExit() {
        if (!scriptReady || !fatal.isEmpty() || interrupted) {
            failure("complete", new IllegalStateException(
                    "script not ready / failed / Activity recreated during V2 session"));
            return;
        }
        try {
            String result = (String) invoke(completeScript);
            verifiedBeforeExit = Boolean.TRUE.equals(invoke(passed))
                    && result.contains("completed=3")
                    && result.contains("pending=none")
                    && result.contains("realHostReturnResume=true");
            if (!verifiedBeforeExit) throw new IllegalStateException("Guest did not attest completion");
            trace.append("HOST verified Guest continuous/cancel/reject/background-return PASS\n");
            Intent reply = new Intent();
            reply.putExtra(VirtualContainerV2Activity.EXTRA_NONCE, nonce);
            reply.putExtra(VirtualContainerV2Activity.EXTRA_DIGEST, digest);
            reply.putExtra(VirtualContainerV2Activity.EXTRA_PROXY_PID, Process.myPid());
            reply.putExtra(VirtualContainerV2Activity.EXTRA_PROXY_UID, Process.myUid());
            reply.putExtra(VirtualContainerV2Activity.EXTRA_ARCHIVE_OK, true);
            reply.putExtra(VirtualContainerV2Activity.EXTRA_RESPONSE, "V2_SESSION_PASS");
            setResult(Activity.RESULT_OK, reply);
            update();
            finish();
        } catch (Throwable caught) {
            failure("complete pending request after real background", caught);
        }
    }

    private void forward(String event) {
        if (destroyed) return;
        trace.append("HOST ").append(event).append('\n');
        if (ready && lifecycle != null) {
            try {
                invoke(lifecycle, event);
            } catch (Throwable caught) {
                failure("forward " + event, caught);
            }
        } else if (deferred.size() < 48) {
            deferred.add(event);
        }
        update();
    }

    private static String describe(Throwable failure) {
        StringBuilder out = new StringBuilder();
        Throwable current = failure;
        for (int i=0; current != null && i < 4; i++) {
            if (i > 0) out.append(" <- ");
            out.append(current.getClass().getSimpleName());
            if (current.getMessage() != null) {
                String message = current.getMessage();
                out.append(": ").append(message, 0, Math.min(message.length(), 190));
            }
            if (current == current.getCause()) break;
            current = current.getCause();
        }
        return out.toString();
    }

    private void failure(String step, Throwable caught) {
        fatal = "FAIL：V2.2 stage=" + step + "；" + describe(caught);
        trace.append(fatal).append('\n');
        update();
    }

    private String render() {
        StringBuilder out = new StringBuilder(3300);
        out.append("天外天 / V2.2 Guest Session 真机代理报告\n")
                .append("开始时间：").append(started).append('\n')
                .append("Android API=").append(Build.VERSION.SDK_INT)
                .append("；HOST PID=").append(Process.myPid())
                .append("；UID=").append(Process.myUid()).append('\n')
                .append("受控独立 APK SHA-256=").append(digest).append('\n')
                .append("Manifest/DEX 检验与会话加载=").append(ready).append('\n')
                .append("受控连续/取消/异常恢复脚本=").append(scriptReady).append('\n')
                .append("真机回后台后恢复并完成挂起 D=").append(verifiedBeforeExit).append('\n')
                .append("宿主 Activity 重建中断标志=").append(interrupted).append('\n');
        if (!fatal.isEmpty()) out.append(fatal).append('\n');
        out.append("\nHOST 生命周期与工作进度：\n").append(trace);
        if (ready && snapshot != null) {
            try {
                out.append("\n独立 guest DEX Session 快照：\n")
                        .append(invoke(snapshot));
            } catch (Throwable caught) {
                out.append("FAIL snapshot ").append(describe(caught)).append('\n');
            }
        }
        if (destroyed) {
            boolean pass = verifiedBeforeExit && scriptReady && ready
                    && !interrupted && fatal.isEmpty();
            out.append("\nV2 第二阶段联合验收：").append(pass ? "PASS" : "INCOMPLETE/FAIL")
                    .append('\n');
        } else {
            out.append("\n状态：实验运行中；退出后才能验收清理\n");
        }
        out.append("边界：实际 Activity 属于天外天宿主；Guest 只是未安装受控 APK DEX 的 Java 会话对象，"
                + "不代表 Guest Activity 受 Android 系统启动或独立权限沙箱。\n");
        return out.toString();
    }

    private void update() {
        if (report != null) {
            status.setText(fatal.isEmpty()
                    ? verifiedBeforeExit ? "已完成，正在退出并清理"
                    : scriptReady ? "自动请求已验证，请打开系统设置后返回，再验证挂起 D"
                    : "独立 Guest 会话装载中…"
                    : "存在失败：直接退出返回 V0，复制一次报告");
            String reportText = render();
            report.setText(reportText);
            getSharedPreferences(VirtualContainerV2Activity.PREFS, MODE_PRIVATE)
                    .edit().putString(VirtualContainerV2Activity.LAST_SESSION_RESULT,
                            reportText).apply();
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        // Deliberately fail closed on recreation: do NOT resurrect a Java object
        // through an implicitly trusted serialized Bundle.
        state.putBoolean(SAVED_INTERRUPTED, true);
        trace.append("HOST saved state; V2 session cannot be silently rehydrated\n");
        update();
        super.onSaveInstanceState(state);
    }

    @Override protected void onStart() { super.onStart(); forward("onStart"); }
    @Override protected void onResume() { super.onResume(); forward("onResume"); }
    @Override protected void onRestart() { super.onRestart(); forward("onRestart"); }
    @Override protected void onPause() { forward("onPause"); super.onPause(); }
    @Override protected void onStop() { forward("onStop"); super.onStop(); }

    @Override protected void onDestroy() {
        forward("onDestroy");
        if (ready && dispose != null) {
            try {
                invoke(dispose);
                trace.append("GUEST session disposed=true\n");
            } catch (Throwable caught) {
                failure("guest disposal", caught);
            }
        }
        destroyed = true;
        update();
        if (guest != null) {
            guest.close();
            guest = null;
        }
        super.onDestroy();
    }
}
