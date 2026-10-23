package com.zcshou.gogogo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Looper;
import android.os.SystemClock;

import androidx.core.location.LocationCompat;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Tasks;

import java.util.concurrent.TimeUnit;

/** One export-time cache snapshot, never a new fix, listener or experiment. */
public final class LabSupportSnapshot {
    private LabSupportSnapshot() {}

    /** Worker thread only. A missing GMS reply does not block the remaining report. */
    @SuppressLint("MissingPermission")
    public static String capture(Context context) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("SNAPSHOT_REQUIRES_WORKER");
        }
        StringBuilder out = new StringBuilder();
        out.append("scope=AT_EXPORT_NOT_DURING_EXTERNAL_APP; other_app_result=UNKNOWN\n")
                .append("reads=GPS_NETWORK_getLastKnownLocation,GMS_getLastLocation_only\n")
                .append("real_coordinates=REDACTED; no_active_fix_requested=true\n")
                .append("started_wall_ms=").append(System.currentTimeMillis()).append('\n')
                .append("started_elapsed_ms=").append(SystemClock.elapsedRealtime()).append('\n');
        try {
            out.append(LabCellularEthernetSnapshot.capture(context));
        } catch (RuntimeException unavailable) {
            out.append("NETWORK_RADIO=UNAVAILABLE_")
                    .append(unavailable.getClass().getSimpleName()).append('\n');
        }
        boolean permitted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!permitted) {
            out.append("GPS_NETWORK_GMS=SKIPPED_NO_LOCATION_PERMISSION\n");
        } else {
            LocationManager manager = null;
            try { manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE); }
            catch (RuntimeException ignored) { }
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                String source = "gps".equals(provider) ? "GPS" : "NETWORK";
                try {
                    if (manager == null) out.append(source).append("=MANAGER_UNAVAILABLE\n");
                    else out.append(fixLine(context, source, manager.getLastKnownLocation(provider))).append('\n');
                } catch (RuntimeException unavailable) {
                    out.append(provider).append("=UNAVAILABLE_")
                            .append(unavailable.getClass().getSimpleName()).append('\n');
                }
            }
            try {
                Location fix = Tasks.await(LocationServices.getFusedLocationProviderClient(context)
                        .getLastLocation(), 2, TimeUnit.SECONDS);
                out.append(fixLine(context, "GMS_LAST", fix)).append('\n');
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                out.append("GMS_LAST=INTERRUPTED\n");
            } catch (Exception unavailable) {
                Throwable reason = unavailable.getCause() == null ? unavailable : unavailable.getCause();
                out.append("GMS_LAST=UNAVAILABLE_").append(reason.getClass().getSimpleName()).append('\n');
            }
        }
        out.append("finished_elapsed_ms=").append(SystemClock.elapsedRealtime()).append('\n');
        return out.toString();
    }

    private static String fixLine(Context context, String source, Location fix) {
        if (fix == null) return LabAutoDiagnosticPlan.cacheLine(source, false, false, -1, null);
        boolean mock = LocationCompat.isMock(fix);
        long age = LabLocationAge.fixAgeMs(SystemClock.elapsedRealtimeNanos(), fix.getElapsedRealtimeNanos());
        String bucket = mock ? LabSupportLog.distanceToRequestedTarget(context,
                fix.getLongitude(), fix.getLatitude()) : null;
        return LabAutoDiagnosticPlan.cacheLine(source, true, mock, age, bucket);
    }
}
