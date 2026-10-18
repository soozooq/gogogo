package com.zcshou.gogogo.virtualv0;

import android.app.Application;
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
import android.util.AtomicFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * Private, DEDICATED :virtualv24 host child process. This experiment intentionally
 * terminates ONLY this child process after an AtomicFile PENDING commit.
 * Never attach this service to V0's existing child processes or the host process.
 */
public final class VirtualContainerV24WorkerService extends Service {
    public static final int MSG_PREPARE = 41;
    public static final int MSG_KILL_CHILD = 42;
    public static final int MSG_RECOVER = 43;
    public static final int MSG_RESULT = 44;

    public static final String KEY_OLD = "v24_old";
    public static final String KEY_NEW = "v24_new";
    public static final String KEY_HOST_PID = "v24_host_pid";
    public static final String KEY_PID = "v24_pid";
    public static final String KEY_UID = "v24_uid";
    public static final String KEY_OK = "v24_ok";
    public static final String KEY_SHA = "v24_sha";
    public static final String KEY_STATE = "v24_state";
    public static final String KEY_DUPLICATE = "v24_idempotent";
    public static final String KEY_STALE = "v24_stale_rejected";
    public static final String KEY_ERROR = "v24_error";

    private static final String CLASS = "com.soozooq.tianwaitian.fixture.GuestV24Journal";
    private static final String JOURNAL = "v24_controlled_journal.txt";

    private HandlerThread thread;
    private Handler handler;
    private Messenger messenger;
    private String preparedToken;
    private int hostPid;
    private boolean killing;

    @Override public void onCreate() {
        super.onCreate();
        thread = new HandlerThread("twt-v24-atomic-guest", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        handler = new Handler(thread.getLooper());
        messenger = new Messenger(new Handler(thread.getLooper(), this::handleMessage));
    }

    @Override public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    private boolean handleMessage(Message request) {
        if (request.replyTo == null) return true;
        Bundle input = request.getData();
        Bundle output = new Bundle();
        output.putInt(KEY_PID, Process.myPid());
        output.putInt(KEY_UID, Process.myUid());
        int operation = request.what;
        try {
            if (operation != MSG_PREPARE && operation != MSG_KILL_CHILD
                    && operation != MSG_RECOVER) {
                throw new SecurityException("unsupported worker command");
            }
            if (!dedicatedProcess()) {
                throw new SecurityException("V2.4 service is not running in its dedicated process");
            }
            if (operation == MSG_PREPARE) {
                if (preparedToken != null || killing) {
                    throw new IllegalStateException("one prepared request per worker instance");
                }
                String token = input.getString(KEY_OLD);
                int parent = input.getInt(KEY_HOST_PID, -1);
                if (parent <= 0 || parent == Process.myPid()) {
                    throw new SecurityException("host/child PID isolation failed");
                }
                try (ContainerV0TrustedGuest guest = ContainerV0TrustedGuest.open(this)) {
                    String sha = guest.getSha256();
                    Class<?> cls = trustedClass(guest);
                    String pending = (String) call(cls,"prepare",
                            new Class<?>[]{String.class,String.class,int.class},
                            token,sha,Process.myPid());
                    if (!(Boolean) call(cls,"isPending",
                            new Class<?>[]{String.class,String.class,String.class,int.class},
                            pending,token,sha,Process.myPid())) {
                        throw new IllegalStateException("guest refused PENDING journal");
                    }
                    writeJournal(pending);
                    if (!pending.equals(readJournal())) {
                        throw new IllegalStateException("atomic journal readback mismatch");
                    }
                    preparedToken = token;
                    hostPid = parent;
                    output.putString(KEY_SHA,sha);
                    output.putString(KEY_STATE,"PENDING");
                    output.putBoolean(KEY_OK,true);
                }
            } else if (operation == MSG_KILL_CHILD) {
                String token = input.getString(KEY_OLD);
                int parent = input.getInt(KEY_HOST_PID,-1);
                if (preparedToken == null || !preparedToken.equals(token)
                        || parent != hostPid || killing) {
                    throw new SecurityException("no matching prepared request to terminate");
                }
                // The main Android app and all V0 services must stay running.
                if (!dedicatedProcess() || hostPid == Process.myPid()) {
                    throw new SecurityException("refusing unsafe process termination");
                }
                killing = true;
                output.putBoolean(KEY_OK,true);
                output.putString(KEY_STATE,"TERMINATING_CHILD");
            } else {
                String oldToken = input.getString(KEY_OLD);
                String newToken = input.getString(KEY_NEW);
                int parent = input.getInt(KEY_HOST_PID, -1);
                if (parent <= 0 || parent == Process.myPid() || killing) {
                    throw new SecurityException("invalid recovering process");
                }
                try (ContainerV0TrustedGuest guest = ContainerV0TrustedGuest.open(this)) {
                    String sha = guest.getSha256();
                    Class<?> cls = trustedClass(guest);
                    String pending = readJournal();
                    String recovered = (String) call(cls,"recover",
                            new Class<?>[]{String.class,String.class,String.class,String.class,int.class},
                            pending,oldToken,newToken,sha,Process.myPid());
                    if (!(Boolean) call(cls,"isRecovered",
                            new Class<?>[]{String.class,String.class,String.class,String.class,int.class,int.class},
                            recovered,oldToken,newToken,sha,oldPid(pending),Process.myPid())) {
                        throw new IllegalStateException("guest recovery verdict failed");
                    }
                    writeJournal(recovered);
                    String reread = readJournal();
                    String again = (String) call(cls,"recover",
                            new Class<?>[]{String.class,String.class,String.class,String.class,int.class},
                            reread,oldToken,newToken,sha,Process.myPid());
                    boolean idempotent = reread.equals(again);
                    boolean staleRejected = !(Boolean) call(cls,"acceptOldResult",
                            new Class<?>[]{String.class,String.class},reread,oldToken);
                    if (!idempotent || !staleRejected) {
                        throw new IllegalStateException("duplicate/stale old request accepted");
                    }
                    output.putString(KEY_SHA,sha);
                    output.putString(KEY_STATE,"RECOVERED");
                    output.putBoolean(KEY_DUPLICATE,true);
                    output.putBoolean(KEY_STALE,true);
                    output.putBoolean(KEY_OK,true);
                }
            }
        } catch (Throwable failure) {
            output.putBoolean(KEY_OK,false);
            Throwable root = failure instanceof InvocationTargetException
                    ? ((InvocationTargetException)failure).getTargetException() : failure;
            String message = root.getMessage();
            output.putString(KEY_ERROR,root.getClass().getSimpleName()
                    + (message == null ? "" : ": " + message.substring(0,Math.min(160,message.length()))));
        }
        Message answer = Message.obtain(null, MSG_RESULT);
        answer.arg1 = request.arg1;
        answer.arg2 = operation;
        answer.setData(output);
        try {
            request.replyTo.send(answer);
        } catch (RemoteException ignored) {
            // Never force a kill without successfully sending the ACK.
            if (operation == MSG_KILL_CHILD) killing = false;
            return true;
        }
        if (operation == MSG_KILL_CHILD && killing) {
            handler.postDelayed(() -> {
                if (killing && dedicatedProcess() && Process.myPid() != hostPid) {
                    Process.killProcess(Process.myPid());
                }
            }, 250);
        }
        return true;
    }

    private boolean dedicatedProcess() {
        return (getPackageName() + ":virtualv24").equals(Application.getProcessName());
    }

    private static int oldPid(String journal) {
        if (journal == null) throw new IllegalArgumentException("journal unavailable");
        String[] pieces = journal.split("\\|", -1);
        if (pieces.length != 9) throw new IllegalArgumentException("journal layout invalid");
        return Integer.parseInt(pieces[4]);
    }

    private static Class<?> trustedClass(ContainerV0TrustedGuest guest) throws Exception {
        ClassLoader loader = guest.getGuestClass().getClassLoader();
        Class<?> cls = Class.forName(CLASS,true,loader);
        if (cls.getClassLoader() != loader) {
            throw new SecurityException("GuestV24Journal not loaded from fixture DEX");
        }
        return cls;
    }

    private static Object call(Class<?> cls,String name,Class<?>[] parameters,
            Object...args) throws Exception {
        return cls.getMethod(name,parameters).invoke(null,args);
    }

    private AtomicFile file() {
        return new AtomicFile(new File(getFilesDir(),JOURNAL));
    }

    private void writeJournal(String contents) throws Exception {
        AtomicFile target=file();
        FileOutputStream stream=null;
        try {
            stream=target.startWrite();
            stream.write(contents.getBytes(StandardCharsets.UTF_8));
            target.finishWrite(stream);
        } catch (Exception | Error failure) {
            if (stream != null) target.failWrite(stream);
            throw failure;
        }
    }

    private String readJournal() throws Exception {
        AtomicFile target=file();
        try (InputStream in=target.openRead();
             ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            byte[] chunk=new byte[256];
            int count;
            while ((count=in.read(chunk))!=-1) {
                bytes.write(chunk,0,count);
                if (bytes.size()>512) throw new SecurityException("journal larger than allowed");
            }
            return bytes.toString("UTF-8");
        }
    }

    @Override public void onDestroy() {
        if (thread != null) {
            thread.quitSafely();
            thread=null;
        }
        super.onDestroy();
    }
}
