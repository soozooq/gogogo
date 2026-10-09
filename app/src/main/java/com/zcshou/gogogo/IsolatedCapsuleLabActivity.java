package com.zcshou.gogogo;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.zcshou.gogogo.isolated.ILabIsolatedCapsule;
import com.zcshou.gogogo.isolated.IResourceMonitor;
import com.zcshou.gogogo.isolated.LabIsolatedCapsuleService;
import com.zcshou.gogogo.isolated.LabReferenceMonitorService;

import java.util.Arrays;
import java.util.Locale;

public class IsolatedCapsuleLabActivity extends AppCompatActivity {
    private TextView statusView;
    private TextView summaryView;

    private IResourceMonitor monitor;
    private ILabIsolatedCapsule capsule;
    private boolean monitorBound;
    private boolean capsuleBound;
    private boolean wired;

    private final ServiceConnection monitorConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            monitor = IResourceMonitor.Stub.asInterface(service);
            monitorBound = true;
            tryWire();
            refreshStatus();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            monitor = null;
            monitorBound = false;
            wired = false;
            refreshStatus();
        }
    };

    private final ServiceConnection capsuleConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            capsule = ILabIsolatedCapsule.Stub.asInterface(service);
            capsuleBound = true;
            tryWire();
            refreshStatus();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            capsule = null;
            capsuleBound = false;
            wired = false;
            refreshStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        int pad = dp(18);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        GoGoUi.applyScreenBackground(scroll);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, dp(30));
        scroll.addView(root);

        GoGoUi.addLabHeader(root, "SYSTEMS  /  ISOLATION",
                "隔离进程 Capsule",
                "独立 UID · Binder Reference Monitor · Broker 资源请求",
                v -> finish());
        root.addView(GoGoUi.muted(this,
                "Host UI → isolatedProcess Capsule → Binder Monitor → Resource Broker。"
                        + "隔离进程不继承普通 App 权限，只能通过显式授权的 Binder 通道请求实验数据。"),
                matchWrap());

        root.addView(sectionTitle("连接与进程状态"), matchWrap());
        statusView = body();
        root.addView(statusView, matchWrap());
        root.addView(GoGoUi.gap(this, 9));
        root.addView(GoGoUi.actionStack(this,
                button("重新绑定 Capsule 与 Monitor", v -> rebindAll()),
                button("检查 Capsule 身份", v -> showCapsuleIdentity()),
                button("检查 Monitor 身份", v -> showMonitorIdentity())
        ), matchWrap());

        root.addView(sectionTitle("权限与数据通道"), matchWrap());
        root.addView(GoGoUi.actionStack(this,
                button("尝试隔离进程直接资源读取", v -> runDirectProbe()),
                button("通过 Broker 请求受控数据", v -> requestBrokerSnapshot())
        ), matchWrap());

        root.addView(sectionTitle("Binder IPC 性能"), matchWrap());
        root.addView(GoGoUi.muted(this,
                "比较 Host ↔ Capsule 与 Capsule ↔ Monitor 的响应时间。"
                        + "测试会执行指定次数的 IPC，不会启动模拟定位。"),
                matchWrap());
        root.addView(GoGoUi.gap(this, 9));
        root.addView(GoGoUi.actionStack(this,
                button("运行 100 次性能测试", v -> runBenchmarks(100)),
                button("运行 1000 次性能测试", v -> runBenchmarks(1000))
        ), matchWrap());

        summaryView = body();
        summaryView.setText("尚未进行 IPC 性能测试。");
        root.addView(GoGoUi.gap(this, 9));
        root.addView(summaryView, matchWrap());

        root.addView(sectionTitle("实验关注点"), matchWrap());
        TextView design = GoGoUi.muted(this,
                "• 隔离 UID 是否区别于普通 App UID\n"
                        + "• Manifest 位置和网络权限在隔离进程内是否拒绝\n"
                        + "• 是否能直接访问应用私有数据目录\n"
                        + "• Binder 调用在 Monitor 端观察到的 callingUid\n"
                        + "• Broker 的数据是否通过受控 IPC 正常送达\n"
                        + "• 两段 Binder RTT 的 P50 / P95");
        design.setTextIsSelectable(true);
        root.addView(design, matchWrap());

        setContentView(scroll);
        refreshStatus();
    }

    private TextView sectionTitle(String text) {
        TextView view = GoGoUi.sectionTitle(this, text);
        view.setPadding(0, dp(20), 0, dp(8));
        return view;
    }

    private TextView body() {
        return GoGoUi.reportPanel(this);
    }

    private com.google.android.material.button.MaterialButton button(
            String text, android.view.View.OnClickListener listener) {
        return GoGoUi.secondaryButton(this, text, listener);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void bindAll() {
        if (!monitorBound) {
            try {
                bindService(
                        new Intent(this, LabReferenceMonitorService.class),
                        monitorConnection,
                        BIND_AUTO_CREATE);
            } catch (Throwable t) {
                Toast.makeText(this,
                        "Reference Monitor 绑定失败：" + t.getClass().getSimpleName(),
                        Toast.LENGTH_LONG).show();
            }
        }

        if (!capsuleBound) {
            try {
                bindService(
                        new Intent(this, LabIsolatedCapsuleService.class),
                        capsuleConnection,
                        BIND_AUTO_CREATE);
            } catch (Throwable t) {
                Toast.makeText(this,
                        "Isolated Capsule 绑定失败：" + t.getClass().getSimpleName(),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void rebindAll() {
        unbindAll();
        bindAll();
        statusView.setText("正在重新绑定两个实验服务…");
    }

    private void tryWire() {
        if (monitor == null || capsule == null) return;

        try {
            capsule.setReferenceMonitor(monitor);
            wired = true;
        } catch (Throwable t) {
            wired = false;
            Toast.makeText(this,
                    "Binder 插线失败：" + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void refreshStatus() {
        StringBuilder out = new StringBuilder();
        out.append("Host pid=").append(Process.myPid())
                .append(" uid=").append(Process.myUid()).append("\n");
        out.append("Reference Monitor：")
                .append(monitorBound ? "已连接" : "未连接").append("\n");
        out.append("Isolated Capsule：")
                .append(capsuleBound ? "已连接" : "未连接").append("\n");
        out.append("Monitor Binder 已注入 Capsule：")
                .append(wired ? "是 ✅" : "否");
        statusView.setText(out.toString());
    }

    private void showCapsuleIdentity() {
        ILabIsolatedCapsule target = capsule;
        if (target == null) {
            Toast.makeText(this, "Capsule 还没连上", Toast.LENGTH_SHORT).show();
            return;
        }

        runRemote("🧬 Isolated Capsule 身份", () -> target.getIdentityReport());
    }

    private void showMonitorIdentity() {
        IResourceMonitor target = monitor;
        if (target == null) {
            Toast.makeText(this, "Reference Monitor 还没连上", Toast.LENGTH_SHORT).show();
            return;
        }

        runRemote("🛡 Reference Monitor 身份", () -> target.getMonitorIdentity());
    }

    private void runDirectProbe() {
        ILabIsolatedCapsule target = capsule;
        if (target == null) {
            Toast.makeText(this, "Capsule 还没连上", Toast.LENGTH_SHORT).show();
            return;
        }

        runRemote("🚫 isolatedProcess 直接资源访问", () -> target.runDirectAccessProbe());
    }

    private void requestBrokerSnapshot() {
        ILabIsolatedCapsule target = capsule;
        if (target == null || !wired) {
            Toast.makeText(this, "先让 Capsule 和 Monitor 都连上", Toast.LENGTH_SHORT).show();
            return;
        }

        runRemote("🧠 Capsule → Reference Monitor → Broker",
                () -> target.requestBrokerSnapshot());
    }

    private interface RemoteCall {
        String run() throws Exception;
    }

    private void runRemote(String title, RemoteCall call) {
        new Thread(() -> {
            String result;
            try {
                result = call.run();
            } catch (Throwable t) {
                result = "远程调用失败：" + t.getClass().getSimpleName()
                        + "\n" + String.valueOf(t.getMessage());
            }
            String finalResult = result;
            runOnUiThread(() -> showReport(title, finalResult));
        }, "GoGoGo-Lab10-Remote").start();
    }

    private void runBenchmarks(int iterations) {
        ILabIsolatedCapsule target = capsule;
        if (target == null || !wired) {
            Toast.makeText(this, "先连接 Capsule + Monitor", Toast.LENGTH_SHORT).show();
            return;
        }

        summaryView.setText("正在跑 " + iterations + " 次 Binder Benchmark…");

        new Thread(() -> {
            String hostToCapsule;
            String capsuleToMonitor;

            try {
                hostToCapsule = benchmarkHostToCapsule(target, iterations);
            } catch (Throwable t) {
                hostToCapsule = "Host → Capsule benchmark failed: "
                        + t.getClass().getSimpleName()
                        + ": " + String.valueOf(t.getMessage());
            }

            try {
                capsuleToMonitor = target.benchmarkMonitor(iterations);
            } catch (Throwable t) {
                capsuleToMonitor = "Capsule → Monitor benchmark failed: "
                        + t.getClass().getSimpleName()
                        + ": " + String.valueOf(t.getMessage());
            }

            String result = hostToCapsule + "\n\n" + capsuleToMonitor;
            runOnUiThread(() -> {
                summaryView.setText(result);
                showReport("⚡ Binder Benchmark · " + iterations + " 次", result);
            });
        }, "GoGoGo-Lab10-Bench").start();
    }

    private static String benchmarkHostToCapsule(
            ILabIsolatedCapsule target,
            int iterations) throws Exception {

        int count = Math.max(1, Math.min(5000, iterations));
        long[] samples = new long[count];

        for (int i = 0; i < count; i++) {
            long start = System.nanoTime();
            long result = target.echo(i);
            long end = System.nanoTime();
            if (result != i) {
                throw new IllegalStateException("invalid echo response at " + i);
            }
            samples[i] = end - start;
        }

        return renderBenchmark("Host → Isolated Capsule Binder RTT", samples);
    }

    private static String renderBenchmark(String title, long[] samples) {
        long[] sorted = samples.clone();
        Arrays.sort(sorted);

        long total = 0L;
        for (long sample : samples) total += sample;

        int count = samples.length;
        double avgUs = total / (double) count / 1000.0;
        double p50Us = sorted[(int) Math.floor((count - 1) * 0.50)] / 1000.0;
        double p95Us = sorted[(int) Math.floor((count - 1) * 0.95)] / 1000.0;
        double minUs = sorted[0] / 1000.0;
        double maxUs = sorted[count - 1] / 1000.0;

        return String.format(Locale.US,
                "%s\niterations=%d\navg=%.2f µs\np50=%.2f µs\np95=%.2f µs"
                        + "\nmin=%.2f µs\nmax=%.2f µs",
                title, count, avgUs, p50Us, p95Us, minUs, maxUs);
    }

    private void showReport(String title, String report) {
        TextView view = body();
        view.setText(report == null ? "（无输出）" : report);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(view);

        new android.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void unbindAll() {
        if (capsuleBound) {
            try {
                unbindService(capsuleConnection);
            } catch (Throwable ignored) {
            }
        }
        if (monitorBound) {
            try {
                unbindService(monitorConnection);
            } catch (Throwable ignored) {
            }
        }

        capsule = null;
        monitor = null;
        capsuleBound = false;
        monitorBound = false;
        wired = false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindAll();
    }

    @Override
    protected void onStop() {
        unbindAll();
        super.onStop();
    }
}
