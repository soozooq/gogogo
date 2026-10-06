package com.zcshou.gogogo;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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

import java.net.InetAddress;
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
        buildUi();
        initSensors();

        Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceived);
        Shizuku.addBinderDeadListener(shizukuBinderDead);
        Shizuku.addRequestPermissionResultListener(shizukuPermissionResult);
    }

    private void buildUi() {
        int pad = dp(14);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("📟 GoGoGo Lab · 实验仪表盘");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        root.addView(sectionTitle("传感器"));
        sensorView = body();
        root.addView(sensorView, matchWrap());

        root.addView(sectionTitle("网络环境"));
        networkView = body();
        root.addView(networkView, matchWrap());

        root.addView(sectionTitle("系统 / 实验能力"));
        systemView = body();
        root.addView(systemView, matchWrap());

        root.addView(sectionTitle("Shizuku 高级模式"));
        shizukuView = body();
        root.addView(shizukuView, matchWrap());

        Button requestShizuku = button("⚡ 连接 / 请求 Shizuku 权限", v -> requestShizukuPermission());
        root.addView(requestShizuku, matchWrap());

        Button shizuku = button("打开 Shizuku", v -> openShizuku());
        root.addView(shizuku, matchWrap());

        Button developer = button("🛠 打开开发者选项", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            } catch (Exception ignored) {
            }
        });
        root.addView(developer, matchWrap());

        Button refresh = button("↻ 立即刷新", v -> refreshViews());
        root.addView(refresh, matchWrap());

        Button back = button("← 返回 GoGoGo Lab", v -> finish());
        root.addView(back, matchWrap());

        setContentView(scroll);
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
        networkView.setText(buildNetworkSnapshot());
        systemView.setText(buildSystemSnapshot());
        shizukuView.setText(buildShizukuSnapshot());
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
                + "\nShizuku: " + (shizuku ? "已安装，下一阶段可接高级系统 API" : "未检测到")
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

        sb.append("高级模式：Binder IPC 已就绪");
        return sb.toString().trim();
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
