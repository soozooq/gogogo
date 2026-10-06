package com.zcshou.gogogo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * Lab 17 foreground session recorder running in the :consumer process.
 */
public class CompatibilitySessionService extends Service {
    public static final String ACTION_START =
            "com.soozooq.gogogo.action.LAB17_START";
    public static final String ACTION_STOP =
            "com.soozooq.gogogo.action.LAB17_STOP";
    public static final String ACTION_CLEAR =
            "com.soozooq.gogogo.action.LAB17_CLEAR";

    private static final String CHANNEL_ID = "gogogo_lab17_session";
    private static final int NOTIFICATION_ID = 1717;

    private final LocalBinder binder = new LocalBinder();
    private final LabCompatibilitySessionRecorder recorder =
            new LabCompatibilitySessionRecorder();

    private LocationManager locationManager;
    private FusedLocationProviderClient fusedClient;
    private LocationListener gpsListener;
    private LocationListener networkListener;
    private LocationCallback gmsCallback;

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        ensureNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_START.equals(action)) {
            startSession();
        } else if (ACTION_STOP.equals(action)) {
            stopSession();
        } else if (ACTION_CLEAR.equals(action)) {
            recorder.clear();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @SuppressLint("MissingPermission")
    private void startSession() {
        if (recorder.isRunning()) return;

        if (!hasLocationPermission()) {
            recorder.clear();
            return;
        }

        recorder.start(SystemClock.elapsedRealtime());
        startForeground(NOTIFICATION_ID, buildNotification("正在记录标准位置消费链"));
        startConsumers();
    }

    private void stopSession() {
        if (!recorder.isRunning()) return;
        recorder.stop(SystemClock.elapsedRealtime());
        stopConsumers();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    @SuppressLint("MissingPermission")
    private void startConsumers() {
        stopConsumers();

        gpsListener = location -> recordLocation("GPS", location);
        networkListener = location -> recordLocation("NETWORK", location);

        try {
            if (locationManager != null) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        100L,
                        0f,
                        gpsListener,
                        Looper.getMainLooper());
            }
        } catch (Throwable t) {
            recorder.addError(
                    SystemClock.elapsedRealtime(),
                    "GPS",
                    t.getClass().getSimpleName());
        }

        try {
            if (locationManager != null) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        100L,
                        0f,
                        networkListener,
                        Looper.getMainLooper());
            }
        } catch (Throwable t) {
            recorder.addError(
                    SystemClock.elapsedRealtime(),
                    "NETWORK",
                    t.getClass().getSimpleName());
        }

        gmsCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;
                for (Location location : result.getLocations()) {
                    if (location != null) {
                        recordLocation("GMS_UPDATES", location);
                    }
                }
            }
        };

        try {
            LocationRequest request = new LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    250L)
                    .setMinUpdateIntervalMillis(100L)
                    .setWaitForAccurateLocation(false)
                    .build();

            fusedClient.requestLocationUpdates(
                    request,
                    gmsCallback,
                    Looper.getMainLooper())
                    .addOnFailureListener(e -> recorder.addError(
                            SystemClock.elapsedRealtime(),
                            "GMS_UPDATES",
                            e.getClass().getSimpleName()));
        } catch (Throwable t) {
            recorder.addError(
                    SystemClock.elapsedRealtime(),
                    "GMS_UPDATES",
                    t.getClass().getSimpleName());
        }
    }

    private void recordLocation(String channel, Location location) {
        if (location == null || !recorder.isRunning()) return;
        recorder.addLocation(
                SystemClock.elapsedRealtime(),
                channel,
                location.getLatitude(),
                location.getLongitude(),
                location.hasAccuracy() ? location.getAccuracy() : 0f,
                location.hasSpeed() ? location.getSpeed() : 0f,
                location.hasBearing() ? location.getBearing() : 0f,
                isMock(location));
    }

    private boolean isMock(Location location) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return location.isMock();
        }
        return location.isFromMockProvider();
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void stopConsumers() {
        if (locationManager != null) {
            try {
                if (gpsListener != null) locationManager.removeUpdates(gpsListener);
            } catch (Throwable ignored) {
            }
            try {
                if (networkListener != null) locationManager.removeUpdates(networkListener);
            } catch (Throwable ignored) {
            }
        }

        if (fusedClient != null && gmsCallback != null) {
            try {
                fusedClient.removeLocationUpdates(gmsCallback);
            } catch (Throwable ignored) {
            }
        }

        gpsListener = null;
        networkListener = null;
        gmsCallback = null;
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "GoGoGo Compatibility Session",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Records GoGoGo standard location-consumer continuity");
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("GoGoGo Lab 17")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        stopConsumers();
        super.onDestroy();
    }

    public final class LocalBinder extends Binder {
        public boolean isRunning() {
            return recorder.isRunning();
        }

        public int getEventCount() {
            return recorder.size();
        }

        public long getDurationMs() {
            return recorder.durationMs(SystemClock.elapsedRealtime());
        }

        public LabCompatibilitySessionRecorder.Summary getSummary() {
            return recorder.summarize();
        }

        public String[] getCsvRows() {
            return recorder.toCsvRows();
        }

        public void mark(String marker) {
            recorder.addMarker(SystemClock.elapsedRealtime(), marker);
        }

        public void clear() {
            recorder.clear();
        }
    }
}
