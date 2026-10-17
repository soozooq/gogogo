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
import com.zcshou.gogogo.virtualv0.VirtualContainerV0IpcService;
import com.zcshou.gogogo.virtualv0.VirtualContainerV0IpcVerdict;
import com.zcshou.gogogo.virtualv0.VirtualContainerV0TwoSlotProbe;

/** Explicit, offline, own-APK-only feasibility experiment. */
public final class VirtualContainerV0Activity extends AppCompatActivity {
    private TextView report;
    private MaterialButton button;
    private MaterialButton ipcButton;
    private MaterialButton stateButton;
    private MaterialButton fixtureButton;
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
        button = GoGoUi.primaryButton(this, "运行 A → B → A 验证", v -> runProbe());
        root.addView(button, GoGoUi.matchWrap());
        ipcButton = GoGoUi.secondaryButton(this, "验证跨进程 Binder 往返", v -> runIpcProbe());
        root.addView(ipcButton, GoGoUi.matchWrap());
        stateButton = GoGoUi.secondaryButton(this,
                "验证双进程 A/B 状态隔离", v -> runTwoSlotProbe());
        root.addView(stateButton, GoGoUi.matchWrap());
        fixtureButton = GoGoUi.secondaryButton(this,
                "验证独立 APK 的 DEX 加载与执行", v -> runFixtureProbe());
        root.addView(fixtureButton, GoGoUi.matchWrap());
        report = GoGoUi.reportPanel(this);
        report.setText("尚未运行。这只是虚拟容器的第一项底层验收，不等于微信兼容成功。");
        root.addView(report, GoGoUi.matchWrap());
        root.addView(GoGoUi.secondaryButton(this, "复制 V0 实验结果", v -> {
            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Container V0", report.getText()));
                Toast.makeText(this, "已复制实验结果", Toast.LENGTH_SHORT).show();
            }
        }), GoGoUi.matchWrap());
        root.addView(GoGoUi.muted(this,
                "注意：不同 ClassLoader / 不同私有目录不构成独立 Linux UID。"),
                GoGoUi.matchWrap());
        setContentView(scroll);
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
        setExperimentButtonsEnabled(false);
        report.setText("正在保持 A/B 两个进程连接并执行 A → B → A → B 验证…");
        stateProbe = new VirtualContainerV0TwoSlotProbe(getApplicationContext(), result -> {
            stateProbe = null;
            if (isFinishing() || isDestroyed()) return;
            report.setText(result);
            setExperimentButtonsEnabled(true);
        });
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
        if (stateProbe != null) {
            stateProbe.close();
            stateProbe = null;
        }
        clearIpc();
        super.onDestroy();
    }

}
