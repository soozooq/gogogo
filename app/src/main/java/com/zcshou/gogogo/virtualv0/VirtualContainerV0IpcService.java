package com.zcshou.gogogo.virtualv0;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;

/**
 * Private test-only IPC endpoint, reused in two explicitly different processes.
 * Static state is deliberately per-process, never a security boundary.
 */
public class VirtualContainerV0IpcService extends Service {
    public static final int MSG_PING = 1;
    public static final int MSG_PONG = 2;
    public static final int MSG_WRITE_MARKER = 3;
    public static final int MSG_READ_MARKER = 4;
    public static final int MSG_STATE_RESULT = 5;

    public static final String KEY_PID = "pid";
    public static final String KEY_UID = "uid";
    public static final String KEY_MARKER = "marker";
    public static final String KEY_ERROR = "error";
    private static String processMarker = "";

    private final Messenger endpoint = new Messenger(new Handler(Looper.getMainLooper(), message -> {
        if (message.replyTo == null) return false;
        int operation = message.what;
        if (operation != MSG_PING && operation != MSG_WRITE_MARKER
                && operation != MSG_READ_MARKER) return false;

        Message reply = Message.obtain(null, operation == MSG_PING ? MSG_PONG : MSG_STATE_RESULT);
        reply.arg1 = message.arg1;
        Bundle values = new Bundle();
        values.putInt(KEY_PID, Process.myPid());
        values.putInt(KEY_UID, Process.myUid());
        if (operation == MSG_WRITE_MARKER) {
            String marker = message.getData().getString(KEY_MARKER);
            if (marker == null || !marker.matches("[A-Za-z0-9_-]{1,80}")) {
                values.putString(KEY_ERROR, "invalid marker");
            } else {
                processMarker = marker;
            }
        }
        if (operation == MSG_WRITE_MARKER || operation == MSG_READ_MARKER) {
            values.putString(KEY_MARKER, processMarker);
        }
        reply.setData(values);
        try {
            message.replyTo.send(reply);
        } catch (RemoteException ignored) {
            // The user may close the experiment while the reply is in flight.
        }
        return true;
    }));

    @Override public IBinder onBind(Intent intent) {
        return endpoint.getBinder();
    }
}
