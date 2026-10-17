package com.zcshou.gogogo.virtualv0;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Five existing V0 probes, run STRICTLY sequentially from one explicit tap.
 * No location access, third-party app access, installation or privileged API.
 * Returns a single self-contained, copyable report, including failures.
 */
public final class VirtualContainerV0OneClickDiagnostic {
    public interface Callback {
        void onReport(String fullReport, boolean complete);
    }

    private static final String[] NAMES = {
            "双 ClassLoader 与私有目录",
            "Messenger/Binder 跨进程",
            "双进程 A/B 状态隔离",
            "独立测试 APK DEX 加载",
            "独立测试 APK 双进程执行"
    };
    private static final long[] TIMEOUTS_MS = {15000, 9000, 14000, 22000, 25000};
    private static final int TOTAL = NAMES.length;

    private final Context app;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final String[] results = new String[TOTAL];
    private final String startedAt = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
    private boolean running;
    private boolean complete;
    private int stage;
    private Runnable timeout;
    private ServiceConnection ipcConnection;
    private boolean ipcBound;
    private VirtualContainerV0TwoSlotProbe twoSlot;

    public VirtualContainerV0OneClickDiagnostic(Context context, Callback callback) {
        this.app = context.getApplicationContext();
        this.callback = callback;
    }

    public void start() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("V0 one-click must start on the UI thread");
        }
        if (running || complete) throw new IllegalStateException("Diagnostic already used");
        running = true;
        callback.onReport(render(false), false);
        handler.post(this::startStage);
    }

    private void startStage() {
        if (!running || complete) return;
        if (stage >= TOTAL) {
            complete = true;
            running = false;
            cleanup();
            callback.onReport(render(false), true);
            return;
        }
        final int index = stage;
        timeout = () -> finishStage(index, "TIMEOUT：本项超过 "
                + (TIMEOUTS_MS[index] / 1000) + " 秒，继续其余诊断");
        handler.postDelayed(timeout, TIMEOUTS_MS[index]);
        callback.onReport(render(false), false);
        switch (index) {
            case 0:
                executeOnWorker(index, () -> ContainerV0Probe.run(app));
                break;
            case 1:
                runIpc(index);
                break;
            case 2:
                runTwoSlot(index, false);
                break;
            case 3:
                executeOnWorker(index, () -> ContainerV0ExternalApkProbe.run(app));
                break;
            case 4:
                runTwoSlot(index, true);
                break;
            default:
                finishStage(index, "FAIL：未知诊断阶段");
        }
    }

    private interface CheckedTask {
        String run() throws Exception;
    }

    private void executeOnWorker(int index, CheckedTask task) {
        Thread thread = new Thread(() -> {
            String value;
            try {
                value = task.run();
            } catch (Throwable error) {
                value = "FAIL：" + error.getClass().getSimpleName()
                        + (error.getMessage() == null ? "" : " · " + error.getMessage());
            }
            final String captured = value;
            handler.post(() -> finishStage(index, captured));
        }, "twt-v0-batch-" + index);
        thread.setDaemon(true);
        thread.start();
    }

    private void runIpc(int index) {
        final Messenger receiver = new Messenger(new Handler(Looper.getMainLooper(), msg -> {
            if (msg.what != VirtualContainerV0IpcService.MSG_PONG) return false;
            if (!running || stage != index) return true;
            Bundle values = msg.getData();
            int pid = values.getInt(VirtualContainerV0IpcService.KEY_PID, -1);
            int uid = values.getInt(VirtualContainerV0IpcService.KEY_UID, -1);
            boolean ok = VirtualContainerV0IpcVerdict.passed(
                    Process.myPid(), Process.myUid(), pid, uid);
            finishStage(index, (ok ? "PASS" : "FAIL") + "：Binder 消息往返\n"
                    + "宿主 PID=" + Process.myPid() + "；远端 PID=" + pid + "\n"
                    + "宿主 UID=" + Process.myUid() + "；远端 UID=" + uid + "\n"
                    + "不同 PID=" + (pid > 0 && pid != Process.myPid())
                    + "；相同 UID=" + (uid == Process.myUid()));
            return true;
        }));
        ipcConnection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                if (!running || stage != index) return;
                try {
                    Message request = Message.obtain(null, VirtualContainerV0IpcService.MSG_PING);
                    request.replyTo = receiver;
                    new Messenger(binder).send(request);
                } catch (RemoteException | RuntimeException error) {
                    finishStage(index, "FAIL：Binder 请求失败 · "
                            + error.getClass().getSimpleName());
                }
            }

            @Override public void onServiceDisconnected(ComponentName name) {
                finishStage(index, "FAIL：Binder 服务异常断开");
            }
        };
        try {
            ipcBound = app.bindService(
                    new Intent(app, VirtualContainerV0IpcService.class),
                    ipcConnection, Context.BIND_AUTO_CREATE);
            if (!ipcBound) finishStage(index, "FAIL：Binder 测试进程绑定失败");
        } catch (RuntimeException error) {
            finishStage(index, "FAIL：绑定失败 · " + error.getClass().getSimpleName());
        }
    }

    private void runTwoSlot(int index, boolean guestMode) {
        try {
            twoSlot = new VirtualContainerV0TwoSlotProbe(app,
                    result -> finishStage(index, result), guestMode);
            twoSlot.start();
        } catch (Throwable error) {
            finishStage(index, "FAIL：双进程测试启动异常 · "
                    + error.getClass().getSimpleName());
        }
    }

    private void finishStage(int index, String result) {
        if (!running || stage != index || complete) return;
        if (timeout != null) {
            handler.removeCallbacks(timeout);
            timeout = null;
        }
        cleanupConnections();
        if (result == null || result.trim().isEmpty()) {
            result = "FAIL：该项没有返回结果";
        }
        if (result.length() > 3000) result = result.substring(0, 3000) + "\n[结果已截断]";
        results[index] = result;
        stage++;
        callback.onReport(render(false), false);
        handler.post(this::startStage);
    }

    public void cancel() {
        if (!running || complete) return;
        running = false;
        complete = true;
        cleanup();
        callback.onReport(render(true), true);
    }

    private void cleanup() {
        if (timeout != null) {
            handler.removeCallbacks(timeout);
            timeout = null;
        }
        cleanupConnections();
    }

    private void cleanupConnections() {
        if (twoSlot != null) {
            twoSlot.close();
            twoSlot = null;
        }
        if (ipcBound && ipcConnection != null) {
            ipcBound = false;
            try {
                app.unbindService(ipcConnection);
            } catch (IllegalArgumentException ignored) {
                // Already unbound.
            }
        }
        ipcConnection = null;
    }

    private String render(boolean cancelled) {
        int pass = 0;
        int fail = 0;
        for (String result : results) {
            if (result != null) {
                if (result.startsWith("PASS")) pass++;
                else fail++;
            }
        }
        StringBuilder output = new StringBuilder(4600);
        output.append("天外天 / Virtual Container V0 一键诊断总报告\n")
                .append("生成时间：").append(startedAt).append('\n')
                .append("Android API：").append(Build.VERSION.SDK_INT).append('\n')
                .append("宿主 PID：").append(Process.myPid())
                .append("；UID：").append(Process.myUid()).append('\n')
                .append("应用包名：").append(app.getPackageName()).append('\n')
                .append("进度：").append(pass + fail).append('/').append(TOTAL)
                .append("；PASS=").append(pass).append("；FAIL/TIMEOUT=").append(fail).append('\n')
                .append(cancelled ? "状态：用户主动取消\n"
                        : complete ? (pass == TOTAL ? "总结果：5/5 PASS\n" : "总结果：未全部通过\n")
                        : "状态：诊断中，请等待全部结束再复制\n");
        for (int i = 0; i < TOTAL; i++) {
            output.append("\n[").append(i + 1).append('/').append(TOTAL)
                    .append("] ").append(NAMES[i]).append('\n');
            if (results[i] != null) output.append(results[i]).append('\n');
            else if (!complete && i == stage) output.append("RUNNING：正在检查\n");
            else output.append(cancelled ? "SKIP：取消后未运行\n" : "PENDING：等待\n");
        }
        output.append("\n边界：这些测试共享天外天 Linux UID；不等于独立沙箱、")
                .append("未安装 APK 的完整 Activity 生命周期或微信定位兼容。\n");
        return output.toString();
    }
}
