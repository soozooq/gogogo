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

/** Explicit test-only IPC endpoint. It never accesses location or guest apps. */
public final class VirtualContainerV0IpcService extends Service {
    public static final int MSG_PING = 1;
    public static final int MSG_PONG = 2;
    public static final String KEY_PID = "pid";
    public static final String KEY_UID = "uid";

    private final Messenger endpoint = new Messenger(new Handler(Looper.getMainLooper(), message -> {
        if (message.what != MSG_PING || message.replyTo == null) return false;
        Message response = Message.obtain(null, MSG_PONG);
        Bundle values = new Bundle();
        values.putInt(KEY_PID, Process.myPid());
        values.putInt(KEY_UID, Process.myUid());
        response.setData(values);
        try {
            message.replyTo.send(response);
        } catch (RemoteException ignored) {
            // The caller may have closed its test screen; no background retry.
        }
        return true;
    }));

    @Override public IBinder onBind(Intent intent) {
        return endpoint.getBinder();
    }
}
