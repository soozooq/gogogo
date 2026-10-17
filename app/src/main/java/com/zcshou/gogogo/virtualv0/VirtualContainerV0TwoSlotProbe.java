package com.zcshou.gogogo.virtualv0;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;

/**
 * User-initiated, in-app A -> B -> A -> B state probe across two bound processes.
 * No arbitrary APK loading, Shizuku, location API, third-party app or privileged API.
 */
public final class VirtualContainerV0TwoSlotProbe {
    public interface Callback {
        void onResult(String result);
    }

    private final Context app;
    private final Callback callback;
    private final boolean guestMode;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Messenger response = new Messenger(new Handler(Looper.getMainLooper(), msg -> {
        receive(msg);
        return true;
    }));
    private final String markerA = "A_" + Long.toHexString(System.nanoTime());
    private final String markerB = "B_" + Long.toHexString(System.nanoTime());
    private ServiceConnection connectionA;
    private ServiceConnection connectionB;
    private Messenger remoteA;
    private Messenger remoteB;
    private boolean boundA;
    private boolean boundB;
    private boolean finished;
    private boolean started;
    private int stage;
    private int pidA = -1;
    private int pidB = -1;
    private int uidA = -1;
    private int uidB = -1;
    private String observedA;
    private String observedB;
    private String guestShaA;
    private String guestShaB;
    private final Runnable timeout;

    public VirtualContainerV0TwoSlotProbe(Context context, Callback callback) {
        this(context, callback, false);
    }

    public VirtualContainerV0TwoSlotProbe(Context context, Callback callback,
            boolean guestMode) {
        this.app = context.getApplicationContext();
        this.callback = callback;
        this.guestMode = guestMode;
        this.timeout = () -> finish("FAIL：A/B 双进程验证超时（"
                + (this.guestMode ? 20 : 10) + " 秒）");
    }

    public void start() {
        if (started) throw new IllegalStateException("probe already started");
        started = true;
        handler.postDelayed(timeout, guestMode ? 20000 : 10000);
        connectionA = connect(true);
        connectionB = connect(false);
        try {
            boundA = app.bindService(
                    new Intent(app, VirtualContainerV0IpcService.class),
                    connectionA, Context.BIND_AUTO_CREATE);
            if (!boundA) {
                finish("FAIL：进程 A 绑定失败");
                return;
            }
            boundB = app.bindService(
                    new Intent(app, VirtualContainerV0IpcServiceB.class),
                    connectionB, Context.BIND_AUTO_CREATE);
            if (!boundB) finish("FAIL：进程 B 绑定失败");
        } catch (RuntimeException error) {
            finish("FAIL：启动子进程失败 · " + error.getClass().getSimpleName());
        }
    }

    private ServiceConnection connect(boolean isA) {
        return new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                if (finished) return;
                if (isA) remoteA = new Messenger(binder);
                else remoteB = new Messenger(binder);
                if (remoteA != null && remoteB != null) sendStage();
            }

            @Override public void onServiceDisconnected(ComponentName name) {
                if (!finished) finish("FAIL：进程 " + (isA ? "A" : "B") + " 断开");
            }
        };
    }

    private void sendStage() {
        if (finished) return;
        Messenger recipient = stage == 0 || stage == 2 ? remoteA : remoteB;
        if (recipient == null) {
            finish("FAIL：目标进程尚未连接");
            return;
        }
        Message message = Message.obtain(null,
                guestMode
                        ? (stage < 2 ? VirtualContainerV0IpcService.MSG_GUEST_WRITE
                                : VirtualContainerV0IpcService.MSG_GUEST_READ)
                        : (stage < 2 ? VirtualContainerV0IpcService.MSG_WRITE_MARKER
                                : VirtualContainerV0IpcService.MSG_READ_MARKER));
        message.arg1 = stage;
        message.replyTo = response;
        if (stage < 2) {
            Bundle data = new Bundle();
            data.putString(VirtualContainerV0IpcService.KEY_MARKER,
                    stage == 0 ? markerA : markerB);
            message.setData(data);
        }
        try {
            recipient.send(message);
        } catch (RemoteException | RuntimeException error) {
            finish("FAIL：发送测试消息失败 · " + error.getClass().getSimpleName());
        }
    }

    private void receive(Message message) {
        if (finished) return;
        int expectedResponse = guestMode ? VirtualContainerV0IpcService.MSG_GUEST_RESULT
                : VirtualContainerV0IpcService.MSG_STATE_RESULT;
        if (message.what != expectedResponse || message.arg1 != stage) {
            finish("FAIL：收到不匹配的阶段响应");
            return;
        }
        Bundle data = message.getData();
        if (data.containsKey(VirtualContainerV0IpcService.KEY_ERROR)) {
            finish("FAIL：远端拒绝测试标记");
            return;
        }
        int pid = data.getInt(VirtualContainerV0IpcService.KEY_PID, -1);
        int uid = data.getInt(VirtualContainerV0IpcService.KEY_UID, -1);
        String marker = data.getString(VirtualContainerV0IpcService.KEY_MARKER);
        String sha = data.getString(VirtualContainerV0IpcService.KEY_GUEST_SHA256);
        if (guestMode) {
            if (!VirtualContainerV0GuestDigestVerdict.validDigest(sha)) {
                finish("FAIL：远端未返回有效的独立 APK SHA-256");
                return;
            }
            if (stage == 0) guestShaA = sha;
            else if (stage == 1) guestShaB = sha;
            else if ((stage == 2 && !sha.equals(guestShaA))
                    || (stage == 3 && !sha.equals(guestShaB))) {
                finish("FAIL：同一进程的 APK 哈希值发生变化");
                return;
            }
        }
        if (stage == 0) {
            pidA = pid;
            uidA = uid;
            if (!markerA.equals(marker)) {
                finish("FAIL：进程 A 写入验证失败");
                return;
            }
        } else if (stage == 1) {
            pidB = pid;
            uidB = uid;
            if (!markerB.equals(marker)) {
                finish("FAIL：进程 B 写入验证失败");
                return;
            }
        } else if (stage == 2) {
            if (pid != pidA || uid != uidA) {
                finish("FAIL：进程 A 在实验中被重启或切换");
                return;
            }
            observedA = marker;
        } else if (stage == 3) {
            if (pid != pidB || uid != uidB) {
                finish("FAIL：进程 B 在实验中被重启或切换");
                return;
            }
            observedB = marker;
        }

        if (++stage < 4) {
            sendStage();
            return;
        }
        boolean pass = VirtualContainerV0TwoSlotVerdict.passed(
                Process.myPid(), Process.myUid(), pidA, uidA, pidB, uidB,
                markerA, observedA, markerB, observedB)
                && (!guestMode || VirtualContainerV0GuestDigestVerdict.passed(
                        guestShaA, guestShaB));
        finish((pass ? "PASS" : "FAIL") + (guestMode
                ? "：独立 APK 在两个 Android 子进程运行\n"
                : "：A → B → A → B 独立进程状态验证\n")
                + "宿主 PID=" + Process.myPid() + "；UID=" + Process.myUid() + "\n"
                + "A PID=" + pidA + "；UID=" + uidA + "；标记保留=" + markerA.equals(observedA) + "\n"
                + "B PID=" + pidB + "；UID=" + uidB + "；标记保留=" + markerB.equals(observedB) + "\n"
                + (guestMode ? "受控 APK SHA-256：" + guestShaA + "\n" : "")
                + "注意：不同 PID、相同 UID，不是独立安装的第三方应用。");
    }

    private void finish(String result) {
        if (finished) return;
        finished = true;
        close();
        callback.onResult(result);
    }

    public void close() {
        finished = true;
        handler.removeCallbacks(timeout);
        if (boundA && connectionA != null) {
            boundA = false;
            try {
                app.unbindService(connectionA);
            } catch (IllegalArgumentException ignored) { /* Already unbound. */ }
        }
        if (boundB && connectionB != null) {
            boundB = false;
            try {
                app.unbindService(connectionB);
            } catch (IllegalArgumentException ignored) { /* Already unbound. */ }
        }
        remoteA = null;
        remoteB = null;
    }
}
