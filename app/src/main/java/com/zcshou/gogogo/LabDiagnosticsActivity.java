package com.zcshou.gogogo;

import android.app.AlertDialog;
import android.content.Context;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.zcshou.gogogo.shizuku.ILabPrivilegedService;
import com.zcshou.gogogo.shizuku.LabPrivilegedService;
import com.zcshou.service.ServiceGo;

import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import rikka.shizuku.Shizuku;

@SuppressWarnings("deprecation")
public class LabDiagnosticsActivity extends AppCompatActivity implements SensorEventListener {
    private static final int REQ_SHIZUKU = 3301;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private Sensor accelerometer;
    private Sensor gyroscope;

    private final float[] rotationMatrix = new float[9];
    private final float[] orientation = new float[3];

    private volatile float heading;
    private volatile float pitch;
    private volatile float roll;
    private final float[] accel = new float[3];
    private final float[] gyro = new float[3];
    private volatile boolean hasOrientation;

    private TextView sensorView;
    private TextView networkView;
    private TextView systemView;
    private TextView shizukuView;
    private TextView serviceLifecycleView;
    private TextView providerEvidenceView;
    private TextView privilegedView;
    private TextView brokerView;
    private LabPolicyEngine policyEngine;
    private ILabPrivilegedService privilegedService;
    private boolean privilegedBinding = false;
    private boolean pendingPrivilegedBind = false;

    private final ServiceConnection privilegedConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            privilegedBinding = false;
            privilegedService = ILabPrivilegedService.Stub.asInterface(binder);
            privilegedView.setText("UserService：已连接\n"
                    + "这段代码现在运行在 Shizuku 提供的 shell/root 独立进程里 😈");
            refreshViews();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            privilegedBinding = false;
            privilegedService = null;
            privilegedView.setText("UserService：连接已断开");
            refreshViews();
        }
    };

    private final Shizuku.UserServiceArgs privilegedArgs =
            new Shizuku.UserServiceArgs(
                    new ComponentName(BuildConfig.APPLICATION_ID, LabPrivilegedService.class.getName()))
                    .daemon(false)
                    .processNameSuffix("lab_privileged")
                    .debuggable(BuildConfig.DEBUG)
                    .version(BuildConfig.VERSION_CODE);

    private final Shizuku.OnBinderReceivedListener shizukuBinderReceived =
            () -> refreshViews();
    private final Shizuku.OnBinderDeadListener shizukuBinderDead =
            () -> refreshViews();
    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionResult =
            (requestCode, grantResult) -> {
                if (requestCode == REQ_SHIZUKU) {
                    Toast.makeText(this,
                            grantResult == PackageManager.PERMISSION_GRANTED
                                    ? "Shizuku 权限已授予"
                                    : "Shizuku 权限未授予",
                            Toast.LENGTH_SHORT).show();
                    if (grantResult == PackageManager.PERMISSION_GRANTED && pendingPrivilegedBind) {
                        pendingPrivilegedBind = false;
                        bindPrivilegedUserService();
                    } else if (grantResult != PackageManager.PERMISSION_GRANTED) {
                        pendingPrivilegedBind = false;
                    }
                    refreshViews();
                }
            };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            refreshViews();
            handler.postDelayed(this, 250L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        policyEngine = new LabPolicyEngine(this);
        buildUi();
        initSensors();

        Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceived);
        Shizuku.addBinderDeadListener(shizukuBinderDead);
        Shizuku.addRequestPermissionResultListener(shizukuPermissionResult);
    }

    private void buildUi() {
        int pad = dp(14);

        ScrollView scroll = new ScrollView(this);
        GoGoUi.applyScreenBackground(scroll);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        root.addView(GoGoUi.backButton(this, v -> finish()),
                matchWrap());
        root.addView(GoGoUi.eyebrow(this, "DIAGNOSTICS  /  LAB 27"), matchWrap());
        root.addView(GoGoUi.heroTitle(this, "实验仪表盘"), matchWrap());
        root.addView(GoGoUi.subtitle(this,
                "集中查看服务、权限和系统状态。展开数据仅用于诊断，不会修改位置。"),
                matchWrap());

        root.addView(sectionTitle("传感器"));
        sensorView = body();
        root.addView(sensorView, matchWrap());

        root.addView(sectionTitle("网络环境"));
        networkView = body();
        root.addView(networkView, matchWrap());

        root.addView(sectionTitle("Resource Broker Shadow View"));
        brokerView = body();
        root.addView(brokerView, matchWrap());

        // Resource Broker is opened from LabHub; keep this page read-only.
        root.addView(sectionTitle("系统 / 实验能力"));
        systemView = body();
        root.addView(systemView, matchWrap());

        root.addView(sectionTitle("模拟服务 / 进程生命周期（Lab 23）"));
        serviceLifecycleView = body();
        serviceLifecycleView.setTextIsSelectable(true);
        root.addView(serviceLifecycleView, matchWrap());
        // The single refresh action below also updates lifecycle evidence.
        root.addView(sectionTitle("Test Provider 清理取证（Lab 25）"));
        providerEvidenceView = body();
        providerEvidenceView.setTextIsSelectable(true);
        root.addView(providerEvidenceView, matchWrap());

        root.addView(sectionTitle("Shizuku 高级模式"));
        shizukuView = body();
        root.addView(shizukuView, matchWrap());

        Button requestShizuku = button("⚡ 连接 / 请求 Shizuku 权限", v -> requestShizukuPermission());
        root.addView(requestShizuku, matchWrap());

        Button shizuku = button("打开 Shizuku", v -> openShizuku());
        root.addView(shizuku, matchWrap());

        privilegedView = body();
        privilegedView.setText("UserService：未连接");
        root.addView(privilegedView, matchWrap());

        root.addView(buttonRow(
                button("🚀 启动高权限进程", v -> bindPrivilegedUserService()),
                button("⏹ 停止高权限进程", v -> stopPrivilegedUserService())
        ));

        root.addView(buttonRow(
                button("🧬 身份报告", v -> runPrivilegedProbe(1)),
                button("📍 定位系统报告", v -> runPrivilegedProbe(2)),
                button("🖥 系统报告", v -> runPrivilegedProbe(3)),
                button("👥 用户 / Device Policy", v -> runPrivilegedProbe(4)),
                button("🧪 AVF / pKVM", v -> runPrivilegedProbe(5))
        ));

        // Sandbox / Work Profile navigation belongs to LabHub.
        Button developer = button("🛠 打开开发者选项", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            } catch (Exception ignored) {
            }
        });
        root.addView(developer, matchWrap());

        Button refresh = button("↻ 立即刷新", v -> refreshViews());
        root.addView(refresh, matchWrap());

        Button saveSnapshot = button("📸 保存环境快照", v -> saveEnvironmentSnapshot());
        root.addView(saveSnapshot, matchWrap());

        Button compareSnapshot = button("🔍 对比上次快照", v -> compareEnvironmentSnapshot());
        root.addView(compareSnapshot, matchWrap());

        // Back navigation stays at the top, avoiding duplicate controls.
        setContentView(scroll);
    }

    private TextView sectionTitle(String text) {
        TextView v = GoGoUi.sectionTitle(this, text);
        v.setPadding(0, dp(18), 0, dp(7));
        return v;
    }

    private TextView body() {
        TextView v = GoGoUi.status(this, "读取中…");
        v.setTextSize(13);
        v.setTextColor(GoGoUi.color(this, R.color.gogogo_text_muted));
        v.setPadding(dp(13), dp(12), dp(13), dp(12));
        android.graphics.drawable.GradientDrawable panel =
                new android.graphics.drawable.GradientDrawable();
        panel.setColor(GoGoUi.color(this, R.color.gogogo_surface));
        panel.setCornerRadius(dp(12));
        panel.setStroke(dp(1), GoGoUi.color(this, R.color.gogogo_border));
        v.setBackground(panel);
        return v;
    }

    private Button button(String text, android.view.View.OnClickListener listener) {
        return GoGoUi.secondaryButton(this, text, listener);
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

    private void initSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) return;

        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rotationSensor == null) {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        }
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null) {
            if (rotationSensor != null) {
                sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
            }
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            }
            if (gyroscope != null) {
                sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
            }
        }
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refreshTask);
        if (sensorManager != null) sensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.sensor == null || event.values == null) return;
        int type = event.sensor.getType();

        if (type == Sensor.TYPE_ROTATION_VECTOR
                || type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) {
            try {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                SensorManager.getOrientation(rotationMatrix, orientation);
                heading = (float) Math.toDegrees(orientation[0]);
                if (heading < 0f) heading += 360f;
                pitch = (float) Math.toDegrees(orientation[1]);
                roll = (float) Math.toDegrees(orientation[2]);
                hasOrientation = true;
            } catch (Throwable ignored) {
            }
        } else if (type == Sensor.TYPE_ACCELEROMETER) {
            copy3(event.values, accel);
        } else if (type == Sensor.TYPE_GYROSCOPE) {
            copy3(event.values, gyro);
        }
    }

    private static void copy3(float[] src, float[] dst) {
        int n = Math.min(3, src.length);
        for (int i = 0; i < n; i++) dst[i] = src[i];
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void refreshViews() {
        String sensorText;
        if (hasOrientation) {
            sensorText = String.format(Locale.US,
                    "朝向 Heading: %.1f°\n俯仰 Pitch: %.1f°\n翻滚 Roll: %.1f°\n"
                            + "加速度: X %.2f / Y %.2f / Z %.2f m/s²\n"
                            + "陀螺仪: X %.3f / Y %.3f / Z %.3f rad/s\n"
                            + "Rotation Vector: %s\nAccelerometer: %s\nGyroscope: %s",
                    heading, pitch, roll,
                    accel[0], accel[1], accel[2],
                    gyro[0], gyro[1], gyro[2],
                    yesNo(rotationSensor != null),
                    yesNo(accelerometer != null),
                    yesNo(gyroscope != null));
        } else {
            sensorText = "方向传感器：暂未产生数据\n"
                    + "Rotation Vector: " + yesNo(rotationSensor != null)
                    + "\nAccelerometer: " + yesNo(accelerometer != null)
                    + "\nGyroscope: " + yesNo(gyroscope != null);
        }
        sensorView.setText(sensorText);
        String rawNetwork = buildNetworkSnapshot();
        networkView.setText(rawNetwork);
        systemView.setText(buildSystemSnapshot());
        shizukuView.setText(buildShizukuSnapshot());
        serviceLifecycleView.setText(LabServiceLifecycleJournal.report(this, ServiceGo.sRunning));
        providerEvidenceView.setText(LabProviderReliabilityController.savedAuditSummary(this));

        if (policyEngine != null) {
            StringBuilder broker = new StringBuilder();
            broker.append("Policy: ").append(policyEngine.summary()).append("\n\n");
            broker.append("[Network]\n")
                    .append(policyEngine.describeNetworkShadow(rawNetwork))
                    .append("\n\n[Sensor]\n")
                    .append(policyEngine.describeSensorShadow(
                            hasOrientation, heading, pitch, roll));
            brokerView.setText(broker.toString());
        }
    }

    private String buildNetworkSnapshot() {
        StringBuilder sb = new StringBuilder();
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network active = cm == null ? null : cm.getActiveNetwork();
            NetworkCapabilities caps = active == null || cm == null ? null : cm.getNetworkCapabilities(active);
            LinkProperties link = active == null || cm == null ? null : cm.getLinkProperties(active);

            if (caps == null) {
                sb.append("活动网络：无\n");
            } else {
                sb.append("活动网络：");
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) sb.append("Wi-Fi ");
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) sb.append("蜂窝 ");
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) sb.append("以太网 ");
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) sb.append("VPN ");
                sb.append("\n");
                sb.append("Internet: ").append(yesNo(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))).append("\n");
                sb.append("Validated: ").append(yesNo(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))).append("\n");
                sb.append("Metered: ").append(yesNo(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))).append("\n");
                sb.append("下行估计: ").append(caps.getLinkDownstreamBandwidthKbps()).append(" Kbps\n");
                sb.append("上行估计: ").append(caps.getLinkUpstreamBandwidthKbps()).append(" Kbps\n");
            }

            if (link != null) {
                sb.append("接口: ").append(link.getInterfaceName()).append("\n");
                sb.append("DNS: ");
                List<InetAddress> dns = link.getDnsServers();
                if (dns.isEmpty()) {
                    sb.append("无");
                } else {
                    for (int i = 0; i < dns.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(dns.get(i).getHostAddress());
                    }
                }
                sb.append("\nIP: ");
                List<LinkAddress> addresses = link.getLinkAddresses();
                if (addresses.isEmpty()) {
                    sb.append("无");
                } else {
                    for (int i = 0; i < addresses.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(addresses.get(i).getAddress().getHostAddress());
                    }
                }
                sb.append("\n");
            }

            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                sb.append("Wi-Fi 开关: ").append(yesNo(wifi.isWifiEnabled())).append("\n");
                try {
                    WifiInfo info = wifi.getConnectionInfo();
                    if (info != null) {
                        sb.append("Wi-Fi 频率: ").append(info.getFrequency()).append(" MHz\n");
                        sb.append("Wi-Fi 链路: ").append(info.getLinkSpeed()).append(" Mbps\n");
                    }
                } catch (Throwable ignored) {
                }
            }

            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                String carrier = tm.getNetworkOperatorName();
                if (carrier != null && !carrier.isEmpty()) {
                    sb.append("运营商: ").append(carrier).append("\n");
                }
            }
        } catch (Throwable t) {
            sb.append("网络诊断异常: ").append(t.getClass().getSimpleName());
        }
        return sb.toString().trim();
    }

    private String buildSystemSnapshot() {
        boolean dev = Settings.Global.getInt(
                getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1;

        return "Android: " + android.os.Build.VERSION.RELEASE
                + " (API " + android.os.Build.VERSION.SDK_INT + ")"
                + "\n设备: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                + "\n开发者选项: " + yesNo(dev)
                + "\nGoGoGo 包名: " + getPackageName()
                + "\nLab 目标: 诊断 / 路线 / 漫游 / 沙箱 / 离线地图";
    }

    private String buildShizukuSnapshot() {
        boolean installed = isPackageInstalled("moe.shizuku.privileged.api");
        if (!installed) {
            return "安装状态：未检测到 Shizuku\nBinder：不可用\n高级模式：关闭";
        }

        boolean binderAlive;
        try {
            binderAlive = Shizuku.pingBinder();
        } catch (Throwable t) {
            binderAlive = false;
        }

        if (!binderAlive) {
            return "安装状态：已安装\nBinder：未连接\n"
                    + "请先在 Shizuku App 中启动服务（Android 11+ 可用无线调试启动）";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("安装状态：已安装\nBinder：已连接\n");

        try {
            int permission = Shizuku.checkSelfPermission();
            sb.append("权限：")
                    .append(permission == PackageManager.PERMISSION_GRANTED ? "已授予" : "未授予")
                    .append("\n");
        } catch (Throwable t) {
            sb.append("权限：读取失败\n");
        }

        try {
            int uid = Shizuku.getUid();
            sb.append("服务 UID：").append(uid);
            if (uid == 0) {
                sb.append(" (ROOT)");
            } else if (uid == 2000) {
                sb.append(" (ADB / shell)");
            }
            sb.append("\n");
        } catch (Throwable t) {
            sb.append("服务 UID：未知\n");
        }

        try {
            sb.append("API 版本：").append(Shizuku.getVersion()).append("\n");
        } catch (Throwable t) {
            sb.append("API 版本：未知\n");
        }

        try {
            String context = Shizuku.getSELinuxContext();
            sb.append("SELinux：").append(context == null ? "未知" : context).append("\n");
        } catch (Throwable t) {
            sb.append("SELinux：未知\n");
        }

        sb.append("高级模式：Binder IPC 已就绪\n");
        sb.append("UserService：").append(privilegedService != null ? "已连接" :
                (privilegedBinding ? "连接中" : "未连接"));
        return sb.toString().trim();
    }

    private void bindPrivilegedUserService() {
        if (privilegedService != null || privilegedBinding) {
            Toast.makeText(this, "高权限 UserService 已经在运行", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, "先启动 Shizuku 服务", Toast.LENGTH_LONG).show();
                openShizuku();
                return;
            }

            if (Shizuku.isPreV11() || Shizuku.getVersion() < 10) {
                Toast.makeText(this, "当前 Shizuku 版本不支持 UserService", Toast.LENGTH_LONG).show();
                return;
            }

            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    Toast.makeText(this, "请先在 Shizuku 中给 GoGoGo 授权", Toast.LENGTH_LONG).show();
                    openShizuku();
                    return;
                }
                pendingPrivilegedBind = true;
                Shizuku.requestPermission(REQ_SHIZUKU);
                return;
            }

            privilegedBinding = true;
            privilegedView.setText("UserService：正在启动 shell/root 独立进程…");
            Shizuku.bindUserService(privilegedArgs, privilegedConnection);
        } catch (Throwable t) {
            privilegedBinding = false;
            privilegedView.setText("UserService 启动失败：" + t.getClass().getSimpleName()
                    + "\n" + String.valueOf(t.getMessage()));
        }
    }

    private void stopPrivilegedUserService() {
        try {
            Shizuku.unbindUserService(privilegedArgs, privilegedConnection, true);
            privilegedService = null;
            privilegedBinding = false;
            privilegedView.setText("UserService：已停止");
        } catch (Throwable t) {
            privilegedView.setText("UserService 停止失败：" + t.getClass().getSimpleName());
        }
        refreshViews();
    }

    private void runPrivilegedProbe(int type) {
        ILabPrivilegedService service = privilegedService;
        if (service == null) {
            Toast.makeText(this, "先点“启动高权限进程”", Toast.LENGTH_SHORT).show();
            return;
        }

        privilegedView.setText("UserService：正在执行白名单诊断…");
        new Thread(() -> {
            String report;
            try {
                if (type == 1) {
                    report = service.getIdentityReport();
                } else if (type == 2) {
                    report = service.getLocationReport();
                } else if (type == 3) {
                    report = service.getSystemReport();
                } else if (type == 4) {
                    report = service.getUserPolicyReport();
                } else {
                    report = service.getVirtualizationReport();
                }
            } catch (Throwable t) {
                report = "高权限诊断失败：" + t.getClass().getSimpleName()
                        + "\n" + String.valueOf(t.getMessage());
            }

            final String finalReport = report;
            runOnUiThread(() -> {
                privilegedView.setText("UserService：已连接");
                showPrivilegedReport(type, finalReport);
            });
        }, "GoGoGo-PrivilegedProbe").start();
    }

    private void showPrivilegedReport(int type, String report) {
        String title;
        if (type == 1) title = "🧬 UserService 身份报告";
        else if (type == 2) title = "📍 高权限定位系统报告";
        else if (type == 3) title = "🖥 高权限系统报告";
        else if (type == 4) title = "👥 高权限用户 / Device Policy 报告";
        else title = "🧪 AVF / pKVM / Microdroid 报告";

        TextView view = new TextView(this);
        int pad = dp(16);
        view.setPadding(pad, pad, pad, pad);
        view.setTextIsSelectable(true);
        view.setText(report == null ? "（无输出）" : report);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(view);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void requestShizukuPermission() {
        if (!isPackageInstalled("moe.shizuku.privileged.api")) {
            Toast.makeText(this, "还没有安装 Shizuku", Toast.LENGTH_SHORT).show();
            openShizuku();
            return;
        }

        try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, "Shizuku 已安装，但服务还没有启动", Toast.LENGTH_LONG).show();
                openShizuku();
                return;
            }

            if (Shizuku.isPreV11()) {
                Toast.makeText(this, "Shizuku API 版本太旧，需要 v11+", Toast.LENGTH_LONG).show();
                return;
            }

            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Shizuku 权限已经有了 😈", Toast.LENGTH_SHORT).show();
                refreshViews();
                return;
            }

            if (Shizuku.shouldShowRequestPermissionRationale()) {
                Toast.makeText(this,
                        "Shizuku 权限之前被拒绝，请到 Shizuku App 里重新授权",
                        Toast.LENGTH_LONG).show();
                openShizuku();
                return;
            }

            Shizuku.requestPermission(REQ_SHIZUKU);
        } catch (Throwable t) {
            Toast.makeText(this,
                    "Shizuku 连接异常：" + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private String buildFullEnvironmentSnapshot() {
        StringBuilder sb = new StringBuilder();
        sb.append("[系统]\n").append(buildSystemSnapshot()).append("\n\n");
        sb.append("[网络]\n").append(buildNetworkSnapshot()).append("\n\n");
        sb.append("[Shizuku]\n").append(buildShizukuSnapshot()).append("\n\n");
        sb.append("[模拟服务生命周期]\n")
                .append(LabServiceLifecycleJournal.report(this, ServiceGo.sRunning))
                .append("\n\n");
        sb.append("[Test Provider 清理取证]\n")
                .append(LabProviderReliabilityController.savedAuditSummary(this))
                .append("\n\n");
        if (policyEngine != null) {
            sb.append("[Resource Broker]\n")
                    .append(policyEngine.summary())
                    .append("\n\n");
        }

        if (hasOrientation) {
            sb.append("[传感器]\n");
            sb.append(String.format(Locale.US,
                    "Heading: %.1f°\nPitch: %.1f°\nRoll: %.1f°\n"
                            + "Accel: %.2f, %.2f, %.2f\n"
                            + "Gyro: %.3f, %.3f, %.3f",
                    heading, pitch, roll,
                    accel[0], accel[1], accel[2],
                    gyro[0], gyro[1], gyro[2]));
        } else {
            sb.append("[传感器]\n暂无方向数据");
        }
        return sb.toString().trim();
    }

    private void saveEnvironmentSnapshot() {
        String snapshot = buildFullEnvironmentSnapshot();
        long now = System.currentTimeMillis();
        SharedPreferences prefs = getSharedPreferences("lab_environment_snapshots", MODE_PRIVATE);
        prefs.edit()
                .putString("snapshot", snapshot)
                .putLong("snapshot_time", now)
                .apply();

        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date(now));
        Toast.makeText(this, "环境快照已保存 · " + time, Toast.LENGTH_SHORT).show();
    }

    private void compareEnvironmentSnapshot() {
        SharedPreferences prefs = getSharedPreferences("lab_environment_snapshots", MODE_PRIVATE);
        String oldSnapshot = prefs.getString("snapshot", null);
        long oldTime = prefs.getLong("snapshot_time", 0L);

        if (oldSnapshot == null || oldSnapshot.trim().isEmpty()) {
            Toast.makeText(this, "还没有保存过环境快照", Toast.LENGTH_SHORT).show();
            return;
        }

        String current = buildFullEnvironmentSnapshot();
        String diff = diffSnapshots(oldSnapshot, current);
        String time = oldTime > 0L
                ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(oldTime))
                : "未知";

        TextView view = new TextView(this);
        int pad = dp(16);
        view.setPadding(pad, pad, pad, pad);
        view.setTextIsSelectable(true);
        view.setText("基准快照：" + time + "\n\n" + diff);

        new AlertDialog.Builder(this)
                .setTitle("环境变化")
                .setView(view)
                .setPositiveButton("关闭", null)
                .setNeutralButton("用当前覆盖基准", (d, w) -> saveEnvironmentSnapshot())
                .show();
    }

    private static String diffSnapshots(String before, String after) {
        String[] a = before.split("\\n", -1);
        String[] b = after.split("\\n", -1);
        int max = Math.max(a.length, b.length);
        StringBuilder out = new StringBuilder();
        int changes = 0;

        for (int i = 0; i < max; i++) {
            String oldLine = i < a.length ? a[i] : "";
            String newLine = i < b.length ? b[i] : "";
            if (!oldLine.equals(newLine)) {
                changes++;
                out.append("• 第 ").append(i + 1).append(" 行\n")
                        .append("  之前：").append(oldLine.isEmpty() ? "（空）" : oldLine).append("\n")
                        .append("  现在：").append(newLine.isEmpty() ? "（空）" : newLine).append("\n\n");
            }
        }

        if (changes == 0) {
            return "没有检测到变化 ✅";
        }
        return "检测到 " + changes + " 处变化：\n\n" + out.toString().trim();
    }

    private boolean isPackageInstalled(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void openShizuku() {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if (launch != null) {
                startActivity(launch);
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            Intent store = new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/RikkaApps/Shizuku"));
            startActivity(store);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (privilegedService != null || privilegedBinding) {
                Shizuku.unbindUserService(privilegedArgs, privilegedConnection, false);
            }
        } catch (Throwable ignored) {
        }
        privilegedService = null;
        privilegedBinding = false;

        if (policyEngine != null) {
            try {
                policyEngine.close();
            } catch (Throwable ignored) {
            }
            policyEngine = null;
        }

        try {
            Shizuku.removeBinderReceivedListener(shizukuBinderReceived);
            Shizuku.removeBinderDeadListener(shizukuBinderDead);
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionResult);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    private static String yesNo(boolean value) {
        return value ? "是" : "否";
    }
}
