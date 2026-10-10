package com.zcshou.gogogo;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import com.zcshou.service.ServiceGo;

import rikka.shizuku.Shizuku;

/**
 * Single read-only source for Lab 31 and Lab 33 prerequisite snapshots.
 * This class never requests permissions, injects locations or invokes any
 * Shizuku privileged API; missing capabilities remain UNKNOWN.
 */
public final class LabQuickCheckReader {
    private LabQuickCheckReader() {}

    public static LabQuickCheckReport.Snapshot read(Context context) {
        return new LabQuickCheckReport.Snapshot(
                Build.VERSION.SDK_INT, locationEnabled(context),
                permission(context, Manifest.permission.ACCESS_FINE_LOCATION),
                permission(context, Manifest.permission.ACCESS_COARSE_LOCATION),
                mockAllowed(context), shizukuBinder(), ServiceGo.sRunning);
    }

    private static LabQuickCheckReport.Signal permission(Context context, String name) {
        try {
            return context.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
                    ? LabQuickCheckReport.Signal.YES : LabQuickCheckReport.Signal.NO;
        } catch (RuntimeException ignored) {
            return LabQuickCheckReport.Signal.UNKNOWN;
        }
    }

    private static LabQuickCheckReport.Signal locationEnabled(Context context) {
        try {
            LocationManager location =
                    (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (location == null) return LabQuickCheckReport.Signal.UNKNOWN;
            boolean enabled;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                enabled = location.isLocationEnabled();
            } else {
                enabled = Settings.Secure.getInt(context.getContentResolver(),
                        Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF)
                        != Settings.Secure.LOCATION_MODE_OFF;
            }
            return enabled ? LabQuickCheckReport.Signal.YES : LabQuickCheckReport.Signal.NO;
        } catch (RuntimeException ignored) {
            return LabQuickCheckReport.Signal.UNKNOWN;
        }
    }

    private static LabQuickCheckReport.Signal mockAllowed(Context context) {
        try {
            AppOpsManager ops =
                    (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) return LabQuickCheckReport.Signal.UNKNOWN;
            int mode = ops.checkOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(), context.getPackageName());
            if (mode == AppOpsManager.MODE_ALLOWED) return LabQuickCheckReport.Signal.YES;
            if (mode == AppOpsManager.MODE_IGNORED || mode == AppOpsManager.MODE_ERRORED) {
                return LabQuickCheckReport.Signal.NO;
            }
            return LabQuickCheckReport.Signal.UNKNOWN;
        } catch (RuntimeException ignored) {
            return LabQuickCheckReport.Signal.UNKNOWN;
        }
    }

    private static LabQuickCheckReport.Signal shizukuBinder() {
        try {
            return Shizuku.pingBinder()
                    ? LabQuickCheckReport.Signal.YES
                    : LabQuickCheckReport.Signal.NO;
        } catch (Throwable ignored) {
            return LabQuickCheckReport.Signal.UNKNOWN;
        }
    }
}
