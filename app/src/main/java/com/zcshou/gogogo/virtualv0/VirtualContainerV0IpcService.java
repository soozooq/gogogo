package com.zcshou.gogogo.virtualv0;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;

/**
 * Private IPC endpoint used in two Android processes of the SAME app UID.
 * Loads only the source-controlled fixture APK for explicit guest commands.
 */
public class VirtualContainerV0IpcService extends Service {
    public static final int MSG_PING = 1;
    public static final int MSG_PONG = 2;
    public static final int MSG_WRITE_MARKER = 3;
    public static final int MSG_READ_MARKER = 4;
    public static final int MSG_STATE_RESULT = 5;
    public static final int MSG_GUEST_WRITE = 6;
    public static final int MSG_GUEST_READ = 7;
    public static final int MSG_GUEST_RESULT = 8;

    public static final String KEY_PID = "pid";
    public static final String KEY_UID = "uid";
    public static final String KEY_MARKER = "marker";
    public static final String KEY_ERROR = "error";
    public static final String KEY_GUEST_SHA256 = "guest_sha256";

    private static String processMarker = "";

    private HandlerThread worker;
    private Messenger endpoint;
    private ContainerV0TrustedGuest guest;

    @Override public void onCreate() {
        super.onCreate();
        worker = new HandlerThread("twt-v0-ipc", Process.THREAD_PRIORITY_BACKGROUND);
        worker.start();
        endpoint = new Messenger(new Handler(worker.getLooper(), this::handleMessage));
    }

    private boolean handleMessage(Message request) {
        if (request.replyTo == null) return false;
        int action = request.what;
        boolean guestAction = action == MSG_GUEST_WRITE || action == MSG_GUEST_READ;
        if (action != MSG_PING && action != MSG_WRITE_MARKER
                && action != MSG_READ_MARKER && !guestAction) return false;

        int replyCode = action == MSG_PING ? MSG_PONG
                : guestAction ? MSG_GUEST_RESULT : MSG_STATE_RESULT;
        Message response = Message.obtain(null, replyCode);
        response.arg1 = request.arg1;
        Bundle data = new Bundle();
        data.putInt(KEY_PID, Process.myPid());
        data.putInt(KEY_UID, Process.myUid());

        if (guestAction) {
            try {
                if (guest == null) guest = ContainerV0TrustedGuest.open(this);
                String marker = action == MSG_GUEST_WRITE
                        ? guest.record(request.getData().getString(KEY_MARKER))
                        : guest.read();
                data.putString(KEY_MARKER, marker);
                data.putString(KEY_GUEST_SHA256, guest.getSha256());
            } catch (Exception | LinkageError failure) {
                data.putString(KEY_ERROR, failure.getClass().getSimpleName()
                        + (failure.getMessage() == null ? "" : ": " + failure.getMessage()));
            }
        } else {
            if (action == MSG_WRITE_MARKER) {
                String marker = request.getData().getString(KEY_MARKER);
                if (marker == null || !marker.matches("[A-Za-z0-9_-]{1,80}")) {
                    data.putString(KEY_ERROR, "invalid marker");
                } else {
                    processMarker = marker;
                }
            }
            if (action == MSG_WRITE_MARKER || action == MSG_READ_MARKER) {
                data.putString(KEY_MARKER, processMarker);
            }
        }
        response.setData(data);
        try {
            request.replyTo.send(response);
        } catch (RemoteException ignored) {
            // The caller may have closed this optional test. Never retry.
        }
        return true;
    }

    @Override public IBinder onBind(Intent intent) {
        return endpoint.getBinder();
    }

    @Override public void onDestroy() {
        if (guest != null) {
            guest.close();
            guest = null;
        }
        if (worker != null) {
            worker.quitSafely();
            worker = null;
        }
        super.onDestroy();
    }
}
