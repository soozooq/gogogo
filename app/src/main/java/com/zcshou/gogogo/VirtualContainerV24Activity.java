package com.zcshou.gogogo;

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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.zcshou.gogogo.virtualv0.VirtualContainerV24WorkerService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * Host-main-process controller. Only the disposable :virtualv24 child is
 * killed. The host Activity and all V0/other service processes stay alive.
 */
public final class VirtualContainerV24Activity extends AppCompatActivity {
    private static final int IDLE=0, BIND_FIRST=1, PREPARING=2, WAIT_DEATH=3,
            BIND_SECOND=4, RECOVERING=5, PASS=6, FAILED=7;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final StringBuilder events=new StringBuilder(2800);
    private final Messenger inbox=new Messenger(new Handler(Looper.getMainLooper(),
            this::receive));
    private int stage=IDLE;
    private String started;
    private String oldToken;
    private String newToken;
    private String sha="(none)";
    private int oldPid=-1;
    private int newPid=-1;
    private int parentPid;
    private int parentUid;
    private boolean sawDeath;
    private boolean duplicateSafe;
    private boolean staleRejected;
    private boolean firstBound;
    private boolean secondBound;
    private ServiceConnection firstConnection;
    private ServiceConnection secondConnection;
    private Messenger remote;
    private String failure="";
    private Runnable timeout;
    private TextView status;
    private TextView report;
    private boolean activityDestroyed;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        started=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA).format(new Date());
        parentPid=Process.myPid();
        parentUid=Process.myUid();

        ScrollView scroll=new ScrollView(this);
        GoGoUi.applyScreenBackground(scroll);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad=GoGoUi.dp(this,18);
        root.setPadding(pad,pad,pad,pad);
        scroll.addView(root);
        GoGoUi.addLabHeader(root,"SYSTEMS / CONTAINER V2.4",
                "子进程死亡 / 独立 APK 冷启动恢复",
                "受控子进程 + 原子日志 + 新 PID，主进程保持运行",
                v -> finish());
        root.addView(GoGoUi.muted(this,
                "只终止天外天专属的 :virtualv24 实验子进程。"
                        + "先持久化旧请求，再确认 Binder 断开、重新创建子进程，"
                        + "验证旧请求不会重放，新请求只完成一次。不会终止主程序或 V0 服务。"),
                GoGoUi.matchWrap());
        status=GoGoUi.muted(this,"尚未执行实验");
        root.addView(status,GoGoUi.matchWrap());
        root.addView(GoGoUi.primaryButton(this,
                "一键执行 V2.4 受控进程死亡 / 冷启动验证",
                v -> start()),GoGoUi.matchWrap());
        root.addView(GoGoUi.secondaryButton(this,
                "完成返回 V0（最后只复制一次总报告）",v -> finish()),
                GoGoUi.matchWrap());
        report=GoGoUi.reportPanel(this);
        root.addView(report,GoGoUi.matchWrap());
        setContentView(scroll);
        if(saved!=null) {
            fail("controller Activity recreated during process-death protocol; fail closed");
        } else {
            writeReport();
        }
    }

    private void start() {
        if(stage!=IDLE || activityDestroyed) return;
        oldToken=UUID.randomUUID().toString();
        newToken=UUID.randomUUID().toString();
        events.append("HOST PID=").append(parentPid).append("；UID=").append(parentUid)
                .append("；prepare unique old/new tokens\n");
        stage=BIND_FIRST;
        writeReport();
        bind(false);
    }

    private void bind(boolean recovery) {
        if(activityDestroyed) return;
        final ServiceConnection connection=new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name,IBinder binder) {
                if(activityDestroyed) return;
                if(recovery ? stage!=BIND_SECOND : stage!=BIND_FIRST) return;
                remote=new Messenger(binder);
                stage=recovery ? RECOVERING : PREPARING;
                events.append("HOST Binder bound to ")
                        .append(recovery?"cold worker":"original worker").append('\n');
                send(recovery ? VirtualContainerV24WorkerService.MSG_RECOVER
                        : VirtualContainerV24WorkerService.MSG_PREPARE);
                armTimeout(recovery?"new worker recovery IPC":"journal preparation",15000);
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                if(activityDestroyed) return;
                if(recovery) {
                    fail("fresh recovery worker also died unexpectedly");
                } else if(stage==WAIT_DEATH && oldPid>0) {
                    sawDeath=true;
                    events.append("HOST received real onServiceDisconnected for OLD PID=")
                            .append(oldPid).append('\n');
                    clearTimeout();
                    drop(false);
                    stage=BIND_SECOND;
                    remote=null;
                    events.append("HOST requesting NEW :virtualv24 process\n");
                    writeReport();
                    bind(true);
                } else {
                    fail("original worker unexpectedly disconnected before controlled termination");
                }
            }
            @Override public void onBindingDied(ComponentName name) {
                fail("worker binding died due to unexpected package/binder change");
            }
            @Override public void onNullBinding(ComponentName name) {
                fail("worker returned a null Binder");
            }
        };
        if(recovery) secondConnection=connection;
        else firstConnection=connection;
        try {
            Intent intent=new Intent(this,VirtualContainerV24WorkerService.class);
            boolean ok=bindService(intent,connection,Context.BIND_AUTO_CREATE);
            if(recovery) secondBound=ok;
            else firstBound=ok;
            if(!ok) {
                fail("could not bind dedicated V2.4 child worker");
                return;
            }
            armTimeout(recovery?"cold worker bind":"original worker bind",15000);
        } catch(RuntimeException failure) {
            fail("bind child worker: "+failure.getClass().getSimpleName());
        }
    }

    private void send(int what) {
        if(remote==null) {fail("no remote Messenger connected");return;}
        Message request=Message.obtain(null,what);
        request.replyTo=inbox;
        Bundle b=new Bundle();
        b.putString(VirtualContainerV24WorkerService.KEY_OLD,oldToken);
        b.putString(VirtualContainerV24WorkerService.KEY_NEW,newToken);
        b.putInt(VirtualContainerV24WorkerService.KEY_HOST_PID,parentPid);
        request.setData(b);
        try {
            remote.send(request);
        } catch(RemoteException | RuntimeException failure) {
            fail("send IPC "+what+": "+failure.getClass().getSimpleName());
        }
    }

    private boolean receive(Message reply) {
        if(reply.what!=VirtualContainerV24WorkerService.MSG_RESULT
                || activityDestroyed || stage==FAILED || stage==PASS) return true;
        Bundle b=reply.getData();
        int kind=reply.arg2;
        if(!b.getBoolean(VirtualContainerV24WorkerService.KEY_OK,false)) {
            fail("remote operation "+kind+" failed: "
                    + b.getString(VirtualContainerV24WorkerService.KEY_ERROR,"unknown"));
            return true;
        }
        int workerPid=b.getInt(VirtualContainerV24WorkerService.KEY_PID,-1);
        int workerUid=b.getInt(VirtualContainerV24WorkerService.KEY_UID,-1);
        if(workerPid<=0 || workerPid==parentPid || workerUid!=parentUid) {
            fail("child PID or shared UID boundary verification failed");
            return true;
        }
        if(stage==PREPARING && kind==VirtualContainerV24WorkerService.MSG_PREPARE) {
            oldPid=workerPid;
            sha=b.getString(VirtualContainerV24WorkerService.KEY_SHA,"");
            if(!sha.matches("[a-f0-9]{64}") || !"PENDING".equals(
                    b.getString(VirtualContainerV24WorkerService.KEY_STATE))) {
                fail("old fixture SHA or journal PENDING verification failed");
                return true;
            }
            events.append("OLD worker PID=").append(oldPid)
                    .append(" committed AtomicFile PENDING; guest SHA=").append(sha).append('\n');
            stage=WAIT_DEATH;
            send(VirtualContainerV24WorkerService.MSG_KILL_CHILD);
            armTimeout("wait for real old Binder death",15000);
        } else if(stage==WAIT_DEATH
                && kind==VirtualContainerV24WorkerService.MSG_KILL_CHILD) {
            if(workerPid!=oldPid) {
                fail("termination ACK did not originate from old worker");
                return true;
            }
            events.append("OLD worker acknowledged guarded self-termination\n");
            writeReport();
        } else if(stage==RECOVERING && kind==VirtualContainerV24WorkerService.MSG_RECOVER) {
            newPid=workerPid;
            duplicateSafe=b.getBoolean(VirtualContainerV24WorkerService.KEY_DUPLICATE,false);
            staleRejected=b.getBoolean(VirtualContainerV24WorkerService.KEY_STALE,false);
            boolean shaSame=sha.equals(b.getString(VirtualContainerV24WorkerService.KEY_SHA));
            boolean state="RECOVERED".equals(
                    b.getString(VirtualContainerV24WorkerService.KEY_STATE));
            if(!sawDeath || newPid==oldPid || !shaSame
                    || !state || !duplicateSafe || !staleRejected
                    || Process.myPid()!=parentPid) {
                fail("new PID, journal recovery, stale/duplicate or living host check failed");
                return true;
            }
            events.append("NEW worker PID=").append(newPid)
                    .append(" loaded same guest APK journal; OLD result rejected\n")
                    .append("NEW synthetic request committed once; duplicate retry idempotent\n");
            stage=PASS;
            clearTimeout();
            writeReport();
            drop(true);
        } else {
            fail("out-of-order Binder result operation="+kind+" stage="+stage);
            return true;
        }
        return true;
    }

    private void armTimeout(String label,long millis) {
        clearTimeout();
        timeout=() -> fail("TIMEOUT: "+label);
        ui.postDelayed(timeout,millis);
    }

    private void clearTimeout() {
        if(timeout!=null) ui.removeCallbacks(timeout);
        timeout=null;
    }

    private void drop(boolean recovery) {
        try {
            if(recovery && secondBound && secondConnection!=null) {
                unbindService(secondConnection);
                secondBound=false;
            } else if(!recovery && firstBound && firstConnection!=null) {
                unbindService(firstConnection);
                firstBound=false;
            }
        } catch(RuntimeException ignored) {}
    }

    private void fail(String reason) {
        if(stage==FAILED || stage==PASS) return;
        failure=reason;
        events.append("FAIL: ").append(reason).append('\n');
        stage=FAILED;
        clearTimeout();
        drop(false);
        drop(true);
        writeReport();
    }

    private String buildReport() {
        StringBuilder out=new StringBuilder();
        out.append("天外天 / Virtual Container V2.4 专属子进程冷启动报告\n")
                .append("开始时间：").append(started).append('\n')
                .append("Android API=").append(Build.VERSION.SDK_INT)
                .append("；HOST PID=").append(parentPid)
                .append("；UID=").append(parentUid).append('\n')
                .append("原子日志 guest fixture SHA-256=").append(sha).append('\n')
                .append("旧子进程 PID=").append(oldPid)
                .append("；新子进程 PID=").append(newPid).append('\n')
                .append("旧进程真实 Binder 断开=").append(sawDeath).append('\n')
                .append("恢复后新请求只记一次/重试幂等=").append(duplicateSafe).append('\n')
                .append("恢复后旧请求拒绝=").append(staleRejected).append('\n')
                .append("主进程未被杀/未换 PID=").append(Process.myPid()==parentPid)
                .append('\n');
        if(!failure.isEmpty()) out.append("错误：").append(failure).append('\n');
        out.append("HOST/Worker 事件：\n").append(events);
        out.append("V2.4 验收：").append(stage==PASS?"PASS":
                stage==FAILED?"FAIL/INCOMPLETE":"RUNNING/INCOMPLETE").append('\n');
        out.append("限制：本次明确终止天外天专属 :virtualv24 子进程，"
                +"主进程及 V0 IPC 服务存活；旧合成请求被取消、新合成请求幂等提交。"
                +"不证明主应用进程被杀后的恢复、真实外部副作用 exactly-once、"
                +"独立 UID/权限或未安装第三方 Activity 的系统启动。\n");
        return out.toString();
    }

    private void writeReport() {
        if(report==null) return;
        status.setText(stage==PASS?"子进程重启与 Guest 日志恢复通过":
                stage==FAILED?"失败，可返回 V0 一次复制报告":
                stage==IDLE?"等待操作":"正在执行受控子进程恢复实验…");
        String output=buildReport();
        report.setText(output);
        getSharedPreferences(VirtualContainerV2Activity.PREFS,MODE_PRIVATE)
                .edit().putString(VirtualContainerV2Activity.LAST_COLD_RESULT,output).apply();
    }

    @Override protected void onDestroy() {
        activityDestroyed=true;
        clearTimeout();
        drop(false);
        drop(true);
        if(stage!=PASS && stage!=FAILED && stage!=IDLE) {
            failure="HOST Activity destroyed while V2.4 experiment running";
            stage=FAILED;
            writeReport();
        }
        super.onDestroy();
    }
}
