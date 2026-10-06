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
        int pad = dp(14);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("🧪 GoGoGo Isolated Capsule · Lab 10");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView intro = body();
        intro.setText(
                "架构：Host UI → isolatedProcess Capsule → Binder Reference Monitor → Resource Broker。\n\n"
                        + "Capsule 使用 Android isolatedProcess，拥有独立隔离 UID，"
                        + "不会继承 GoGoGo Manifest 里的普通 App 权限。"
                        + "需要实验资源时，只通过我们显式传进去的 Binder monitor 请求。");
        root.addView(intro, matchWrap());

        statusView = body();
        root.addView(statusView, matchWrap());

        root.addView(buttonRow(
                button("🔌 重新绑定 / 插线", v -> rebindAll()),
                button("🧬 Capsule 身份", v -> showCapsuleIdentity()),
                button("🛡 Monitor 身份", v -> showMonitorIdentity())
        ));

        root.addView(buttonRow(
                button("🚫 直接资源访问探针", v -> runDirectProbe()),
                button("🧠 通过 Broker 请求资源", v -> requestBrokerSnapshot())
        ));

        root.addView(sectionTitle("Binder IPC Benchmark"));
        root.addView(buttonRow(
                button("⚡ 100 次", v -> runBenchmarks(100)),
                button("⚡ 1000 次", v -> runBenchmarks(1000))
        ));

        summaryView = body();
        summaryView.setText("还没有跑 Benchmark。");
        root.addView(summaryView, matchWrap());

        TextView design = body();
        design.setText(
                "实验关注点：\n"
                        + "• isolated UID 与普通 App UID 是否不同；\n"
                        + "• Manifest 已授权的定位/网络权限，在 isolatedProcess 里是否仍为 DENIED；\n"
                        + "• isolatedProcess 是否能直接写普通 app dataDir；\n"
                        + "• Capsule → Monitor 的 Binder 调用里，Monitor 实际看到的 callingUid；\n"
                        + "• Resource Broker 的 Published 数据是否能通过受控 Binder 正常送入 Capsule；\n"
                        + "• Host↔Capsule 与 Capsule↔Monitor 的 RTT / P50 / P95。");
        root.addView(design, matchWrap());

        Button back = button("← 返回 Sandbox Lab", v -> finish());
        root.addView(back, matchWrap());

        setContentView(scroll);
        refreshStatus();
    }

    private TextView sectionTitle(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(18);
        v.setPadding(0, dp(14), 0, dp(4));
        return v;
    }

    private TextView body() {
        TextView v = new TextView(this);
        v.setTextSize(14);
        v.setTextIsSelectable(true);
        v.setPadding(dp(8), dp(8), dp(8), dp(8));
        return v;
    }

    private Button button(String text, android.view.View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        return b;
    }

    private android.widget.HorizontalScrollView buttonRow(Button... buttons) {
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (Button b : buttons) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(6), 0);
            row.addView(b, lp);
        }
        scroll.addView(row);
        return scroll;
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
