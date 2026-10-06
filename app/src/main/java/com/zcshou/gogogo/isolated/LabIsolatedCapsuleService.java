package com.zcshou.gogogo.isolated;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.IBinder;
import android.os.Process;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

public class LabIsolatedCapsuleService extends Service {
    private volatile IResourceMonitor referenceMonitor;

    private final ILabIsolatedCapsule.Stub binder = new ILabIsolatedCapsule.Stub() {
        @Override
        public void setReferenceMonitor(IResourceMonitor monitor) {
            referenceMonitor = monitor;
        }

        @Override
        public String getIdentityReport() {
            StringBuilder out = new StringBuilder();
            out.append("Isolated capsule identity\n");
            out.append("pid=").append(Process.myPid())
                    .append(" uid=").append(Process.myUid())
                    .append(" appUid=").append(getApplicationInfo().uid)
                    .append("\n");
            out.append("package=").append(getPackageName()).append("\n");
            out.append("process=").append(getProcessNameCompat()).append("\n");

            try {
                String[] packages = getPackageManager().getPackagesForUid(Process.myUid());
                out.append("packagesForUid=")
                        .append(packages == null ? "null" : Arrays.toString(packages))
                        .append("\n");
            } catch (Throwable t) {
                out.append("packagesForUid probe failed: ")
                        .append(t.getClass().getSimpleName()).append("\n");
            }

            out.append("ACCESS_FINE_LOCATION=")
                    .append(permissionState(Manifest.permission.ACCESS_FINE_LOCATION)).append("\n");
            out.append("INTERNET=")
                    .append(permissionState(Manifest.permission.INTERNET)).append("\n");
            out.append("ACCESS_NETWORK_STATE=")
                    .append(permissionState(Manifest.permission.ACCESS_NETWORK_STATE)).append("\n");

            return out.toString().trim();
        }

        @Override
        public String runDirectAccessProbe() {
            StringBuilder out = new StringBuilder();
            out.append("Direct resource access from isolatedProcess\n\n");

            probeFiles(out);
            probeLocation(out);
            probeNetwork(out);
            probePreferences(out);

            out.append("\nReference monitor attached: ")
                    .append(referenceMonitor != null ? "yes" : "no");
            return out.toString().trim();
        }

        @Override
        public String requestBrokerSnapshot() {
            IResourceMonitor monitor = referenceMonitor;
            if (monitor == null) {
                return "Reference monitor is not attached.";
            }
            try {
                return monitor.getBrokerSnapshot();
            } catch (Throwable t) {
                return "Broker request failed: " + t.getClass().getSimpleName()
                        + ": " + String.valueOf(t.getMessage());
            }
        }

        @Override
        public long echo(long nonce) {
            return nonce;
        }

        @Override
        public String benchmarkMonitor(int iterations) {
            IResourceMonitor monitor = referenceMonitor;
            if (monitor == null) return "Reference monitor is not attached.";

            int count = Math.max(1, Math.min(5000, iterations));
            long[] samples = new long[count];

            try {
                for (int i = 0; i < count; i++) {
                    long start = System.nanoTime();
                    long result = monitor.ping(i);
                    long end = System.nanoTime();
                    if (result != (i ^ 0x5A5A5A5AL)) {
                        return "Benchmark failed: invalid ping response at " + i;
                    }
                    samples[i] = end - start;
                }
            } catch (Throwable t) {
                return "Benchmark failed: " + t.getClass().getSimpleName()
                        + ": " + String.valueOf(t.getMessage());
            }

            long total = 0L;
            for (long sample : samples) total += sample;

            long[] sorted = samples.clone();
            Arrays.sort(sorted);

            double avgUs = total / (double) count / 1000.0;
            double p50Us = sorted[(int) Math.floor((count - 1) * 0.50)] / 1000.0;
            double p95Us = sorted[(int) Math.floor((count - 1) * 0.95)] / 1000.0;
            double minUs = sorted[0] / 1000.0;
            double maxUs = sorted[count - 1] / 1000.0;

            return String.format(Locale.US,
                    "Capsule → ReferenceMonitor Binder RTT\n"
                            + "iterations=%d\n"
                            + "avg=%.2f µs\n"
                            + "p50=%.2f µs\n"
                            + "p95=%.2f µs\n"
                            + "min=%.2f µs\n"
                            + "max=%.2f µs",
                    count, avgUs, p50Us, p95Us, minUs, maxUs);
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private String permissionState(String permission) {
        try {
            return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
                    ? "GRANTED" : "DENIED";
        } catch (Throwable t) {
            return "ERROR:" + t.getClass().getSimpleName();
        }
    }

    private void probeFiles(StringBuilder out) {
        out.append("[Files]\n");
        try {
            File dir = getFilesDir();
            out.append("filesDir=").append(dir).append("\n");
            File file = new File(dir, "isolated_probe.txt");
            try (FileOutputStream stream = new FileOutputStream(file)) {
                stream.write("isolated".getBytes(StandardCharsets.UTF_8));
                stream.flush();
            }
            out.append("write app filesDir: SUCCESS\n");
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        } catch (Throwable t) {
            out.append("write app filesDir: ")
                    .append(t.getClass().getSimpleName())
                    .append(": ").append(String.valueOf(t.getMessage())).append("\n");
        }
    }

    private void probeLocation(StringBuilder out) {
        out.append("\n[Location]\n");
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Object location = lm == null ? null : lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            out.append("getLastKnownLocation: SUCCESS value=")
                    .append(String.valueOf(location)).append("\n");
        } catch (Throwable t) {
            out.append("getLastKnownLocation: ")
                    .append(t.getClass().getSimpleName())
                    .append(": ").append(String.valueOf(t.getMessage())).append("\n");
        }
    }

    private void probeNetwork(StringBuilder out) {
        out.append("\n[Network]\n");
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network active = cm == null ? null : cm.getActiveNetwork();
            out.append("getActiveNetwork: SUCCESS value=")
                    .append(String.valueOf(active)).append("\n");
        } catch (Throwable t) {
            out.append("getActiveNetwork: ")
                    .append(t.getClass().getSimpleName())
                    .append(": ").append(String.valueOf(t.getMessage())).append("\n");
        }
    }

    private void probePreferences(StringBuilder out) {
        out.append("\n[SharedPreferences]\n");
        try {
            getSharedPreferences("isolated_probe", MODE_PRIVATE)
                    .edit()
                    .putLong("time", System.currentTimeMillis())
                    .commit();
            out.append("write SharedPreferences: SUCCESS\n");
        } catch (Throwable t) {
            out.append("write SharedPreferences: ")
                    .append(t.getClass().getSimpleName())
                    .append(": ").append(String.valueOf(t.getMessage())).append("\n");
        }
    }

    private static String getProcessNameCompat() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                return android.app.Application.getProcessName();
            }
        } catch (Throwable ignored) {
        }
        return "unknown";
    }
}
