package com.zcshou.gogogo;

import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Toast;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.zcshou.gogogo.virtualv0.ContainerV0Probe;
import com.zcshou.gogogo.virtualv0.ContainerV0ExternalApkProbe;
import com.zcshou.gogogo.virtualv0.VirtualContainerV0OneClickDiagnostic;
import com.zcshou.gogogo.virtualv0.VirtualContainerV0IpcService;
import com.zcshou.gogogo.virtualv0.VirtualContainerV0IpcVerdict;
import com.zcshou.gogogo.virtualv0.VirtualContainerV0TwoSlotProbe;

/** Explicit, offline, own-APK-only feasibility experiment. */
public final class VirtualContainerV0Activity extends AppCompatActivity {
    private TextView report;
    private TextView summary;
    private LinearLayout advancedPanel;
    private MaterialButton advancedToggle;
    private static final String LAST_PAGE_RESULT = "v0_last_page_result";
    private static final String LAST_PAGE_AT = "v0_last_page_at";
    private static final String LAST_COMPLETE_AT = "v0_last_complete_at";
    private MaterialButton autoButton;
    private MaterialButton stopButton;
    private MaterialButton copyButton;
    private MaterialButton v1Button;
    private MaterialButton v2Button;
    private MaterialButton v3Button;
    private MaterialButton v31Button;
    private VirtualContainerV0OneClickDiagnostic autoDiagnostic;
    private MaterialButton button;
    private MaterialButton ipcButton;
    private MaterialButton stateButton;
    private MaterialButton fixtureButton;
    private MaterialButton guestProcessButton;
    private VirtualContainerV0TwoSlotProbe stateProbe;
    private ServiceConnection ipcConnection;
    private boolean ipcBound;
    private final Handler ipcHandler = new Handler(Looper.getMainLooper());
    private Runnable ipcTimeout;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        GoGoUi.applyScreenBackground(scroll);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = GoGoUi.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        GoGoUi.addLabHeader(root, "TIANWAITIAN / APPLICATIONS",
                "应用运行",
                "打开应用后直接使用，记录会自动保存", v -> finish());

        com.google.android.material.card.MaterialCardView quickCard = GoGoUi.card(this);
        LinearLayout quick = GoGoUi.cardContent(this);
        quickCard.addView(quick);
        v31Button = GoGoUi.primaryButton(this, "打开应用", v ->
                startActivity(new Intent(this, VirtualContainerV31ProxyActivity.class)));
        quick.addView(v31Button, GoGoUi.matchWrap());
        summary = GoGoUi.muted(this, "");
        quick.addView(summary, GoGoUi.matchWrap());
        copyButton = GoGoUi.secondaryButton(this, "一键复制全部记录", v -> copyAllRecords());
        copyButton.setContentDescription("一键复制总报告，包含全部保存记录");
        quick.addView(copyButton, GoGoUi.matchWrap());
        quick.addView(GoGoUi.muted(this,
                "运行与验证记录会一并保存，下次只需发送一次。"), GoGoUi.matchWrap());
        root.addView(quickCard, GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 14));
        advancedToggle = GoGoUi.secondaryButton(this, "高级验证 · 展开", v -> {
            boolean expand = advancedPanel.getVisibility() != View.VISIBLE;
            advancedPanel.setVisibility(expand ? View.VISIBLE : View.GONE);
            advancedToggle.setText(expand ? "高级验证 · 收起" : "高级验证 · 展开");
        });
        root.addView(advancedToggle, GoGoUi.matchWrap());
        advancedPanel = new LinearLayout(this);
        advancedPanel.setOrientation(LinearLayout.VERTICAL);
        advancedPanel.setVisibility(View.GONE);
        root.addView(advancedPanel, GoGoUi.matchWrap());
        com.google.android.material.card.MaterialCardView advancedCard = GoGoUi.card(this);
        LinearLayout advanced = GoGoUi.cardContent(this);
        advancedCard.addView(advanced);
        advancedPanel.addView(advancedCard, GoGoUi.matchWrap());
        advanced.addView(GoGoUi.muted(this,
                "已有验证和原始记录集中保留在这里；日常打开应用无需逐项重跑。"),
                GoGoUi.matchWrap());
        autoButton = GoGoUi.primaryButton(this,
                "一键诊断 V0 五项（自动生成总报告）", v -> runOneClick());
        advanced.addView(autoButton, GoGoUi.matchWrap());
        stopButton = GoGoUi.secondaryButton(this, "停止一键诊断", v -> stopOneClick());
        stopButton.setEnabled(false);
        advanced.addView(stopButton, GoGoUi.matchWrap());
        button = GoGoUi.secondaryButton(this, "单项：A → B → A 验证", v -> runProbe());
        advanced.addView(button, GoGoUi.matchWrap());
        ipcButton = GoGoUi.secondaryButton(this, "验证跨进程 Binder 往返", v -> runIpcProbe());
        advanced.addView(ipcButton, GoGoUi.matchWrap());
        stateButton = GoGoUi.secondaryButton(this,
                "验证双进程 A/B 状态隔离", v -> runTwoSlotProbe());
        advanced.addView(stateButton, GoGoUi.matchWrap());
        fixtureButton = GoGoUi.secondaryButton(this,
                "验证独立 APK 的 DEX 加载与执行", v -> runFixtureProbe());
        advanced.addView(fixtureButton, GoGoUi.matchWrap());
        guestProcessButton = GoGoUi.secondaryButton(this,
                "验证独立 APK 在双进程执行", v -> runRemoteGuestProbe());
        advanced.addView(guestProcessButton, GoGoUi.matchWrap());
        v1Button = GoGoUi.secondaryButton(this,
                "V1：打开受控 APK 生命周期代理页", v ->
                        startActivity(new Intent(this, VirtualContainerV1ProxyActivity.class)));
        advanced.addView(v1Button, GoGoUi.matchWrap());
        v2Button = GoGoUi.secondaryButton(this,
                "V2：受控 APK 组件 / Intent 代理实验", v ->
                        startActivity(new Intent(this, VirtualContainerV2Activity.class)));
        advanced.addView(v2Button, GoGoUi.matchWrap());
        v3Button = GoGoUi.secondaryButton(this,
                "V3：选择真实 APK（只读归档审计）", v ->
                        startActivity(new Intent(this, VirtualContainerV3ArchiveActivity.class)));
        advanced.addView(v3Button, GoGoUi.matchWrap());
        report = GoGoUi.reportPanel(this);
        String previous = getPreferences(MODE_PRIVATE).getString(LAST_PAGE_RESULT, "");
        if (previous.isEmpty()) {
            previous = getPreferences(MODE_PRIVATE).getString("v0_last_complete", "");
        }
        report.setText(previous.isEmpty()
                ? "高级验证尚无记录；可直接使用上方应用入口。"
                : "此前保存的高级验证记录（保留原始时间，未自动重跑）：\n" + previous);
        advanced.addView(report, GoGoUi.matchWrap());
        advanced.addView(GoGoUi.muted(this,
                "独立 ClassLoader、私有目录和 Host 代理不构成独立 Linux UID。"),
                GoGoUi.matchWrap());
        setContentView(scroll);
        refreshSummary();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshSummary();
    }

    private void refreshSummary() {
        if (summary == null) return;
        String latest = getSharedPreferences(VirtualContainerV31ProxyActivity.PREFS,
                MODE_PRIVATE).getString(VirtualContainerV31ProxyActivity.LAST_RESULT, "");
        summary.setText(latest.isEmpty()
                ? "首次选择配套文件后即可打开，后续操作记录会自动保存。"
                : "已保存最近一次应用运行记录，可与已有验证结果一并复制。");
    }

    private void showV0Result(String value, boolean finished) {
        long savedAt = System.currentTimeMillis();
        report.setText(value);
        if (finished) {
            getPreferences(MODE_PRIVATE).edit().putString(LAST_PAGE_RESULT, value)
                    .putLong(LAST_PAGE_AT, savedAt).apply();
            refreshSummary();
        }
    }

    private void copyAllRecords() {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("Tianwaitian V0-V3", collectAllReports(this)));
        Toast.makeText(this, "全部已保存记录已复制，下次发送一次即可", Toast.LENGTH_SHORT).show();
        refreshSummary();
    }

    /** Host runtime can call after persisting its real exit/cleanup result. */
    public static String collectAllReports(Context context) {
        // Activity.getPreferences uses getLocalClassName. Our historical Activity
        // namespace differs from applicationId, so its legacy name is normally
        // the complete class name. Match Android's rule rather than losing data.
        String fullName = VirtualContainerV0Activity.class.getName();
        String packagePrefix = context.getPackageName() + ".";
        String preferenceName = fullName.startsWith(packagePrefix)
                ? fullName.substring(packagePrefix.length()) : fullName;
        android.content.SharedPreferences v0Prefs =
                context.getSharedPreferences(preferenceName, MODE_PRIVATE);
        StringBuilder all = new StringBuilder("天外天 / 应用运行与验证总记录\n");
        all.append("导出时间=").append(formatTime(System.currentTimeMillis())).append('\n');
        all.append("来源说明：各附录为最近一次实际保存的记录，保留各自原始会话和时间；")
                .append("历史记录不代表本次重新运行。\n");
        String page = v0Prefs.getString(LAST_PAGE_RESULT, "");
        long pageAt = v0Prefs.getLong(LAST_PAGE_AT, 0);
        if (!page.isEmpty()) {
            all.append("\n========== 本页最近保存的高级验证记录 ==========\n")
                    .append("保存时间=").append(formatTime(pageAt)).append('\n').append(page);
        }
        String complete = v0Prefs.getString("v0_last_complete", "");
        if (!complete.isEmpty() && !complete.equals(page)) {
            all.append("\n\n========== 历史 V0 一键诊断（未自动重跑）==========\n")
                    .append("保存时间=").append(formatTime(v0Prefs
                            .getLong(LAST_COMPLETE_AT, 0))).append('\n').append(complete);
        }
        String v31 = context.getSharedPreferences(VirtualContainerV31ProxyActivity.PREFS,
                MODE_PRIVATE).getString(VirtualContainerV31ProxyActivity.LAST_RESULT, "");
        String recent = VirtualContainerV31ProxyActivity.collectRecentResults(context);
        if (recent.isEmpty() || !recent.contains(v31)) {
            appendRecord(all, "最新应用运行状态（原始会话及时间见记录）", v31);
        }
        appendRecord(all, "最近已结束的应用运行（最多五轮，各自时间见记录）", recent);
        String v1 = context.getSharedPreferences(VirtualContainerV1ProxyActivity.PREFS,
                MODE_PRIVATE).getString(VirtualContainerV1ProxyActivity.LAST_RESULT, "");
        appendRecord(all, "V1 生命周期实验附录（最近一次）", v1);
        String v2 = context.getSharedPreferences(VirtualContainerV2Activity.PREFS,
                MODE_PRIVATE).getString(VirtualContainerV2Activity.LAST_RESULT, "");
        appendRecord(all, "V2 组件 / Intent 实验附录（最近一次）", v2);
        String v22 = context.getSharedPreferences(VirtualContainerV2Activity.PREFS,
                MODE_PRIVATE).getString(VirtualContainerV2Activity.LAST_SESSION_RESULT, "");
        appendRecord(all, "V2.2 连续请求 / 真机生命周期附录（最近一次）", v22);
        String v22Summary = context.getSharedPreferences(VirtualContainerV2Activity.PREFS,
                MODE_PRIVATE).getString(VirtualContainerV2Activity.LAST_SESSION_SUMMARY, "");
        if (!v22.isEmpty()) appendRecord(all, "V2.2 宿主代理请求核对", v22Summary);
        String v23 = context.getSharedPreferences(VirtualContainerV2Activity.PREFS,
                MODE_PRIVATE).getString(VirtualContainerV2Activity.LAST_RECREATE_RESULT, "");
        appendRecord(all, "V2.3 真实 Activity 重建 / 请求防重附录（最近一次）", v23);
        String v23Summary = context.getSharedPreferences(VirtualContainerV2Activity.PREFS,
                MODE_PRIVATE).getString(VirtualContainerV2Activity.LAST_RECREATE_SUMMARY, "");
        if (!v23.isEmpty()) appendRecord(all, "V2.3 宿主代理核对", v23Summary);
        String v24 = context.getSharedPreferences(VirtualContainerV2Activity.PREFS,
                MODE_PRIVATE).getString(VirtualContainerV2Activity.LAST_COLD_RESULT, "");
        appendRecord(all, "V2.4 专属进程终止 / 冷启动恢复附录（最近一次）", v24);
        String v3 = context.getSharedPreferences(VirtualContainerV3ArchiveActivity.PREFS,
                MODE_PRIVATE).getString(VirtualContainerV3ArchiveActivity.LAST_RESULT, "");
        appendRecord(all, "V3 真实 APK 只读归档分析附录（最近一次）", v3);
        if (page.isEmpty() && complete.isEmpty() && v31.isEmpty() && recent.isEmpty() && v1.isEmpty()
                && v2.isEmpty() && v22.isEmpty() && v23.isEmpty() && v24.isEmpty()
                && v3.isEmpty()) {
            all.append("\n尚无实际运行或验证记录；此状态不表示任何验证已通过。\n");
        }
        return all.toString();
    }

    private static void appendRecord(StringBuilder all, String title, String value) {
        if (value != null && !value.isEmpty()) {
            all.append("\n\n========== ").append(title).append(" ==========\n").append(value);
        }
    }

    private static String formatTime(long at) {
        return at > 0 ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
                .format(new java.util.Date(at)) : "UNKNOWN（以原始记录内时间为准）";
    }

    private void runOneClick() {
        if (autoDiagnostic != null) return;
        setExperimentButtonsEnabled(false);
        stopButton.setEnabled(true);
        copyButton.setEnabled(false); // Do not copy a misleading partial report.
        autoDiagnostic = new VirtualContainerV0OneClickDiagnostic(
                getApplicationContext(), (fullReport, complete) -> {
            if (isFinishing() || isDestroyed()) return;
            showV0Result(fullReport, complete);
            if (complete) {
                getPreferences(MODE_PRIVATE).edit()
                        .putString("v0_last_complete", fullReport)
                        .putLong(LAST_COMPLETE_AT, System.currentTimeMillis()).apply();
                autoDiagnostic = null;
                setExperimentButtonsEnabled(true);
                stopButton.setEnabled(false);
                copyButton.setEnabled(true);
            }
        });
        autoDiagnostic.start();
    }

    private void stopOneClick() {
        if (autoDiagnostic != null) autoDiagnostic.cancel();
    }

    private void runProbe() {
        setExperimentButtonsEnabled(false);
        report.setText("正在离线测试两份独立 DexClassLoader …");
        android.content.Context app = getApplicationContext();
        new Thread(() -> {
            String result;
            try {
                result = ContainerV0Probe.run(app);
            } catch (Throwable failure) {
                result = "FAIL：" + failure.getClass().getSimpleName()
                        + (failure.getMessage() == null ? "" : " · " + failure.getMessage())
                        + "\n请保留此结果，不要将失败视为容器已成功。";
            }
            final String finalResult = result;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                showV0Result(finalResult, true);
                setExperimentButtonsEnabled(true);
            });
        }, "virtual-container-v0").start();
    }
    private void setExperimentButtonsEnabled(boolean enabled) {
        button.setEnabled(enabled);
        ipcButton.setEnabled(enabled);
        stateButton.setEnabled(enabled);
        fixtureButton.setEnabled(enabled);
        guestProcessButton.setEnabled(enabled);
        autoButton.setEnabled(enabled);
        v1Button.setEnabled(enabled);
        v2Button.setEnabled(enabled);
        v3Button.setEnabled(enabled);
        v31Button.setEnabled(enabled);
        copyButton.setEnabled(enabled);
    }

    private void runFixtureProbe() {
        setExperimentButtonsEnabled(false);
        report.setText("正在从主程序内置的独立测试 APK 装载 DEX（不安装第三方应用）…");
        Context app = getApplicationContext();
        new Thread(() -> {
            String result;
            try {
                result = ContainerV0ExternalApkProbe.run(app);
            } catch (Throwable failure) {
                result = "FAIL：独立 APK 实验 · " + failure.getClass().getSimpleName()
                        + (failure.getMessage() == null ? "" : " · " + failure.getMessage())
                        + "\n请复制此结果，失败不代表虚拟容器功能已实现。";
            }
            final String finalResult = result;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                showV0Result(finalResult, true);
                setExperimentButtonsEnabled(true);
            });
        }, "virtual-container-guest-apk").start();
    }

    private void runTwoSlotProbe() {
        runTwoSlot(false);
    }

    private void runRemoteGuestProbe() {
        runTwoSlot(true);
    }

    private void runTwoSlot(boolean guestMode) {
        setExperimentButtonsEnabled(false);
        report.setText(guestMode
                ? "正在两个子进程分别加载受控 APK，再执行 A → B → A → B 验证…"
                : "正在保持 A/B 两个进程连接并执行 A → B → A → B 验证…");
        stateProbe = new VirtualContainerV0TwoSlotProbe(getApplicationContext(), result -> {
            stateProbe = null;
            if (isFinishing() || isDestroyed()) return;
            showV0Result(result, true);
            setExperimentButtonsEnabled(true);
        }, guestMode);
        stateProbe.start();
    }

    private void runIpcProbe() {
        setExperimentButtonsEnabled(false);
        report.setText("正在验证独立 Android 进程的 Messenger/Binder 往返…");
        final Messenger receiver = new Messenger(new Handler(Looper.getMainLooper(), msg -> {
            if (msg.what != VirtualContainerV0IpcService.MSG_PONG) return false;
            Bundle values = msg.getData();
            int remotePid = values.getInt(VirtualContainerV0IpcService.KEY_PID, -1);
            int remoteUid = values.getInt(VirtualContainerV0IpcService.KEY_UID, -1);
            boolean distinctProcess = remotePid > 0 && remotePid != Process.myPid();
            boolean sameUid = remoteUid == Process.myUid();
            finishIpc((VirtualContainerV0IpcVerdict.passed(Process.myPid(),
                    Process.myUid(), remotePid, remoteUid) ? "PASS" : "FAIL")
                    + "：Binder 消息往返\n"
                    + "宿主 PID=" + Process.myPid() + "；远端 PID=" + remotePid + "\n"
                    + "宿主 UID=" + Process.myUid() + "；远端 UID=" + remoteUid + "\n"
                    + "不同 PID=" + distinctProcess + "；相同 UID=" + sameUid + "\n"
                    + "限制：仍为同包名、同 UID；不代表第三方 APK 虚拟化。");
            return true;
        }));
        ipcConnection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                try {
                    Message request = Message.obtain(null, VirtualContainerV0IpcService.MSG_PING);
                    request.replyTo = receiver;
                    new Messenger(binder).send(request);
                } catch (RemoteException | RuntimeException error) {
                    finishIpc("FAIL：IPC 发送失败 · " + error.getClass().getSimpleName());
                }
            }

            @Override public void onServiceDisconnected(ComponentName name) {
                finishIpc("FAIL：IPC 服务异常断开");
            }
        };
        try {
            ipcBound = bindService(new Intent(this, VirtualContainerV0IpcService.class),
                    ipcConnection, Context.BIND_AUTO_CREATE);
            if (!ipcBound) {
                finishIpc("FAIL：无法绑定独立测试进程");
                return;
            }
            ipcTimeout = () -> finishIpc("FAIL：跨进程 IPC 超时（7 秒）");
            ipcHandler.postDelayed(ipcTimeout, 7000);
        } catch (RuntimeException error) {
            finishIpc("FAIL：无法启动测试进程 · " + error.getClass().getSimpleName());
        }
    }

    private void finishIpc(String result) {
        if (!isFinishing() && !isDestroyed()) {
            showV0Result(result, true);
            setExperimentButtonsEnabled(true);
        }
        clearIpc();
    }

    private void clearIpc() {
        if (ipcTimeout != null) {
            ipcHandler.removeCallbacks(ipcTimeout);
            ipcTimeout = null;
        }
        if (ipcBound && ipcConnection != null) {
            ipcBound = false;
            unbindService(ipcConnection);
        }
        ipcConnection = null;
    }

    @Override protected void onDestroy() {
        if (autoDiagnostic != null) {
            autoDiagnostic.cancel();
            autoDiagnostic = null;
        }
        if (stateProbe != null) {
            stateProbe.close();
            stateProbe = null;
        }
        clearIpc();
        super.onDestroy();
    }

}
