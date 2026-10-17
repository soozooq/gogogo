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
import android.view.ViewGroup;
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
    private MaterialButton autoButton;
    private MaterialButton stopButton;
    private MaterialButton copyButton;
    private MaterialButton v1Button;
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
        GoGoUi.addLabHeader(root, "SYSTEMS / VIRTUAL CONTAINER",
                "虚拟容器 V0 · 双实例",
                "Android 13 · 同名类加载 · 私有文件隔离",
                v -> finish());
        root.addView(GoGoUi.muted(this,
                "V0 只执行内置的受控测试代码与独立测试 APK；不触碰微信、不请求定位、不安装第二个应用。"),
                GoGoUi.matchWrap());
        autoButton = GoGoUi.primaryButton(this,
                "一键诊断 V0 五项（自动生成总报告）", v -> runOneClick());
        root.addView(autoButton, GoGoUi.matchWrap());
        stopButton = GoGoUi.secondaryButton(this, "停止一键诊断", v -> stopOneClick());
        stopButton.setEnabled(false);
        root.addView(stopButton, GoGoUi.matchWrap());
        root.addView(GoGoUi.muted(this,
                "下方五项仅供失败后单独复测；通常只需要上面一键诊断、最后复制一次。"),
                GoGoUi.matchWrap());
        button = GoGoUi.secondaryButton(this, "单项：A → B → A 验证", v -> runProbe());
        root.addView(button, GoGoUi.matchWrap());
        ipcButton = GoGoUi.secondaryButton(this, "验证跨进程 Binder 往返", v -> runIpcProbe());
        root.addView(ipcButton, GoGoUi.matchWrap());
        stateButton = GoGoUi.secondaryButton(this,
                "验证双进程 A/B 状态隔离", v -> runTwoSlotProbe());
        root.addView(stateButton, GoGoUi.matchWrap());
        fixtureButton = GoGoUi.secondaryButton(this,
                "验证独立 APK 的 DEX 加载与执行", v -> runFixtureProbe());
        root.addView(fixtureButton, GoGoUi.matchWrap());
        guestProcessButton = GoGoUi.secondaryButton(this,
                "验证独立 APK 在双进程执行", v -> runRemoteGuestProbe());
        root.addView(guestProcessButton, GoGoUi.matchWrap());
        v1Button = GoGoUi.secondaryButton(this,
                "V1：打开受控 APK 生命周期代理页", v ->
                        startActivity(new Intent(this, VirtualContainerV1ProxyActivity.class)));
        root.addView(v1Button, GoGoUi.matchWrap());
        report = GoGoUi.reportPanel(this);
        String previous = getPreferences(MODE_PRIVATE).getString("v0_last_complete", "");
        report.setText(previous.isEmpty()
                ? "尚未运行。点击「一键诊断 V0 五项」，完成后只需复制一次总报告。"
                : "上次完成的 V0 一键诊断报告（如需复测请重新运行）：\n" + previous);
        root.addView(report, GoGoUi.matchWrap());
        copyButton = GoGoUi.secondaryButton(this, "一键复制总报告 / 单项结果", v -> {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                String all = report.getText().toString();
                String v1 = getSharedPreferences(VirtualContainerV1ProxyActivity.PREFS,
                        MODE_PRIVATE).getString(VirtualContainerV1ProxyActivity.LAST_RESULT, "");
                if (!v1.isEmpty()) {
                    all += "\n\n========== V1 生命周期实验附录（最近一次）==========\n" + v1;
                }
                clipboard.setPrimaryClip(ClipData.newPlainText("Tianwaitian V0+V1", all));
                Toast.makeText(this, "总报告已复制（含可用的 V1 附录）", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(copyButton, GoGoUi.matchWrap());
        root.addView(GoGoUi.muted(this,
                "注意：不同 ClassLoader / 不同私有目录不构成独立 Linux UID。"),
                GoGoUi.matchWrap());
        setContentView(scroll);
    }

    private void runOneClick() {
        if (autoDiagnostic != null) return;
        setExperimentButtonsEnabled(false);
        stopButton.setEnabled(true);
        copyButton.setEnabled(false); // Do not copy a misleading partial report.
        autoDiagnostic = new VirtualContainerV0OneClickDiagnostic(
                getApplicationContext(), (fullReport, complete) -> {
            if (isFinishing() || isDestroyed()) return;
            report.setText(fullReport);
            if (complete) {
                getPreferences(MODE_PRIVATE).edit()
                        .putString("v0_last_complete", fullReport).apply();
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
                report.setText(finalResult);
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
                report.setText(finalResult);
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
            report.setText(result);
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
            report.setText(result);
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
