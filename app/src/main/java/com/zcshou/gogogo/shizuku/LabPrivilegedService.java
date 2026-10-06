package com.zcshou.gogogo.shizuku;

import android.content.Context;
import android.os.Build;
import android.system.Os;
import android.system.StructUtsname;

import androidx.annotation.Keep;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class LabPrivilegedService extends ILabPrivilegedService.Stub {
    private static final int MAX_OUTPUT_CHARS = 24000;

    public LabPrivilegedService() {
    }

    @Keep
    public LabPrivilegedService(Context context) {
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    @Override
    public String getIdentityReport() {
        StringBuilder out = new StringBuilder();
        out.append("UserService process identity\n");
        out.append("pid=").append(Os.getpid())
                .append(" uid=").append(Os.getuid())
                .append(" gid=").append(Os.getgid()).append("\n");

        try {
            StructUtsname uts = Os.uname();
            out.append("kernel=")
                    .append(uts.sysname).append(" ")
                    .append(uts.release).append(" ")
                    .append(uts.machine).append("\n");
        } catch (Throwable ignored) {
        }

        appendProbe(out, "$ id", new String[]{"id"});
        appendProbe(out, "$ cat /proc/self/status", new String[]{"cat", "/proc/self/status"});
        return trim(out);
    }

    @Override
    public String getSystemReport() {
        StringBuilder out = new StringBuilder();
        out.append("Build.MODEL=").append(Build.MODEL).append("\n");
        out.append("Build.MANUFACTURER=").append(Build.MANUFACTURER).append("\n");
        out.append("Build.FINGERPRINT=").append(Build.FINGERPRINT).append("\n");
        out.append("SDK=").append(Build.VERSION.SDK_INT)
                .append(" / Android ").append(Build.VERSION.RELEASE).append("\n");

        appendProbe(out, "$ getprop ro.product.cpu.abi",
                new String[]{"getprop", "ro.product.cpu.abi"});
        appendProbe(out, "$ getprop ro.boot.verifiedbootstate",
                new String[]{"getprop", "ro.boot.verifiedbootstate"});
        appendProbe(out, "$ getprop ro.boot.vbmeta.device_state",
                new String[]{"getprop", "ro.boot.vbmeta.device_state"});
        appendProbe(out, "$ getenforce",
                new String[]{"getenforce"});
        return trim(out);
    }

    @Override
    public String getLocationReport() {
        StringBuilder out = new StringBuilder();
        appendProbe(out, "$ cmd location is-location-enabled",
                new String[]{"cmd", "location", "is-location-enabled"});
        appendProbe(out, "$ settings get secure location_mode",
                new String[]{"settings", "get", "secure", "location_mode"});
        appendProbe(out, "$ cmd appops get GoGoGo mock_location",
                new String[]{"cmd", "appops", "get",
                        "com.soozooq.gogogo.test", "android:mock_location"});
        appendProbe(out, "$ dumpsys location (summary)",
                new String[]{"sh", "-c",
                        "dumpsys location 2>/dev/null | head -n 80"});
        return trim(out);
    }

    private static void appendProbe(StringBuilder out, String title, String[] command) {
        if (out.length() >= MAX_OUTPUT_CHARS) return;
        out.append("\n").append(title).append("\n");
        out.append(run(command)).append("\n");
    }

    private static String run(String[] command) {
        Process process = null;
        StringBuilder out = new StringBuilder();
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (out.length() + line.length() + 1 > MAX_OUTPUT_CHARS) {
                        out.append("… output truncated …\n");
                        break;
                    }
                    out.append(line).append("\n");
                }
            }

            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                out.append("[timeout]\n");
            } else {
                out.append("[exit ").append(process.exitValue()).append("]\n");
            }
        } catch (Throwable t) {
            out.append("[probe failed: ")
                    .append(t.getClass().getSimpleName())
                    .append(": ")
                    .append(t.getMessage())
                    .append("]\n");
        } finally {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
        return trim(out);
    }

    private static String trim(CharSequence value) {
        String text = value == null ? "" : value.toString().trim();
        if (text.length() > MAX_OUTPUT_CHARS) {
            return text.substring(0, MAX_OUTPUT_CHARS) + "\n… truncated …";
        }
        return text;
    }
}
