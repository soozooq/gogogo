package com.zcshou.gogogo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Lab 17 foreground session recorder running in the :consumer process.
 */
public class CompatibilitySessionService extends Service {
    public static final String ACTION_START =
            "com.soozooq.gogogo.action.LAB17_START";
    public static final String ACTION_START_WAKELOCK =
            "com.soozooq.gogogo.action.LAB17_START_WAKELOCK";
    public static final String ACTION_STOP =
            "com.soozooq.gogogo.action.LAB17_STOP";
    public static final String ACTION_CLEAR =
            "com.soozooq.gogogo.action.LAB17_CLEAR";
    public static final String ACTION_START_AUTO =
            "com.soozooq.gogogo.action.LAB18_START_AUTO";
    public static final String EXTRA_AUTO_TYPE = "lab18_auto_type";
    public static final String EXTRA_AUTO_DURATION_MS = "lab18_auto_duration_ms";

    private static final String CHANNEL_ID = "gogogo_lab17_session";
    private static final int NOTIFICATION_ID = 1717;
    private static final int AUTO_DONE_NOTIFICATION_ID = 1818;
    private static final long DEFAULT_AUTO_DURATION_MS = 60000L;

    private final LocalBinder binder = new LocalBinder();
    private final LabCompatibilitySessionRecorder recorder =
            new LabCompatibilitySessionRecorder();

    private LocationManager locationManager;
    private FusedLocationProviderClient fusedClient;
    private LocationListener gpsListener;
    private LocationListener networkListener;
    private LocationCallback gmsCallback;

    private PowerManager.WakeLock wakeLock;
    private volatile boolean wakeLockMode = false;
    private volatile boolean wakeLockLossReported = false;

    private volatile String autoExperimentType = null;
    private volatile long autoExperimentStartedElapsedMs = -1L;
    private volatile long autoExperimentRequestedDurationMs = 0L;

    private final Handler heartbeatHandler = new Handler(Looper.getMainLooper());
    private ScheduledExecutorService backgroundHeartbeatExecutor;
    private ScheduledFuture<?> backgroundHeartbeatFuture;

    private final Runnable heartbeatTask = new Runnable() {
        @Override
        public void run() {
            recordHeartbeat();
            if (recorder.isRunning()) {
                heartbeatHandler.postDelayed(this, 500L);
            }
        }
    };

    private final Runnable autoStopTask = () ->
            finishAutoExperiment("AUTO_TIMER");

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
            startSession(false);
        } else if (ACTION_START_WAKELOCK.equals(action)) {
            startSession(true);
        } else if (ACTION_START_AUTO.equals(action)) {
            String type = intent == null
                    ? null
                    : intent.getStringExtra(EXTRA_AUTO_TYPE);
            long durationMs = intent == null
                    ? DEFAULT_AUTO_DURATION_MS
                    : intent.getLongExtra(
                            EXTRA_AUTO_DURATION_MS,
                            DEFAULT_AUTO_DURATION_MS);
            startAutoExperiment(type, durationMs);
        } else if (ACTION_STOP.equals(action)) {
            stopSession();
        } else if (ACTION_CLEAR.equals(action)) {
            recorder.clear();
            LabAutoExperimentStore.clear(this);
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @SuppressLint("MissingPermission")
    private void startSession(boolean useWakeLock) {
        if (recorder.isRunning()) return;

        if (!hasLocationPermission()) {
            recorder.clear();
            return;
        }

        wakeLockMode = useWakeLock;
        wakeLockLossReported = false;
        recorder.start(SystemClock.elapsedRealtime());
        recorder.addMarker(
                SystemClock.elapsedRealtime(),
                useWakeLock ? "SURVIVAL_MODE_WAKELOCK" : "SURVIVAL_MODE_BASELINE");

        if (useWakeLock) {
            acquireWakeLock();
            recorder.addMarker(
                    SystemClock.elapsedRealtime(),
                    isWakeLockHeld() ? "WAKELOCK_ACQUIRED" : "WAKELOCK_ACQUIRE_FAILED");
        } else {
            releaseWakeLock();
            recorder.addMarker(
                    SystemClock.elapsedRealtime(),
                    "WAKELOCK_BASELINE_OFF");
        }

        startForeground(
                NOTIFICATION_ID,
                buildNotification(
                        useWakeLock
                                ? "WakeLock A/B 会话正在记录"
                                : "基线会话正在记录"));
        startConsumers();
        heartbeatHandler.removeCallbacks(heartbeatTask);
        heartbeatHandler.post(heartbeatTask);
        startBackgroundHeartbeat();
    }

    private void startAutoExperiment(String type, long durationMs) {
        if (recorder.isRunning()) return;
        if (!LabAutoExperimentStore.TYPE_WECHAT.equals(type)
                && !LabAutoExperimentStore.TYPE_CONTROL.equals(type)) {
            return;
        }

        autoExperimentType = type;
        autoExperimentRequestedDurationMs =
                Math.max(15000L, Math.min(durationMs, 5 * 60 * 1000L));

        startSession(false);
        if (!recorder.isRunning()) {
            autoExperimentType = null;
            return;
        }

        autoExperimentStartedElapsedMs = SystemClock.elapsedRealtime();
        recorder.addMarker(
                autoExperimentStartedElapsedMs,
                "AUTO_EXPERIMENT_" + autoExperimentType);
        recorder.addMarker(
                autoExperimentStartedElapsedMs,
                "AUTO_DURATION_MS_" + autoExperimentRequestedDurationMs);

        heartbeatHandler.removeCallbacks(autoStopTask);
        heartbeatHandler.postDelayed(
                autoStopTask,
                autoExperimentRequestedDurationMs);
    }

    private void finishAutoExperiment(String reason) {
        String type = autoExperimentType;
        if (type == null || !recorder.isRunning()) return;

        long now = SystemClock.elapsedRealtime();
        recorder.addMarker(now, "AUTO_FINISH_" + reason);
        recorder.stop(now);

        heartbeatHandler.removeCallbacks(autoStopTask);
        heartbeatHandler.removeCallbacks(autoStopTask);
        heartbeatHandler.removeCallbacks(heartbeatTask);
        stopBackgroundHeartbeat();
        stopConsumers();
        releaseWakeLock();
        wakeLockMode = false;

        LabCompatibilitySessionRecorder.Summary summary =
                recorder.summarize();
        long durationMs = recorder.durationMs(now);
        LabAutoExperimentStore.save(
                this,
                type,
                summary,
                durationMs,
                isBatteryOptimizationExempt());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }

        postAutoDoneNotification(type, summary, durationMs);

        autoExperimentType = null;
        autoExperimentStartedElapsedMs = -1L;
        autoExperimentRequestedDurationMs = 0L;
    }

    private void stopSession() {
        if (!recorder.isRunning()) return;

        if (autoExperimentType != null) {
            finishAutoExperiment("MANUAL_STOP");
            return;
        }

        recorder.stop(SystemClock.elapsedRealtime());
        heartbeatHandler.removeCallbacks(autoStopTask);
        heartbeatHandler.removeCallbacks(heartbeatTask);
        stopBackgroundHeartbeat();
        stopConsumers();
        releaseWakeLock();
        wakeLockMode = false;
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

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm == null) return;

            if (wakeLock == null) {
                wakeLock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "GoGoGo:Lab17Survival");
                wakeLock.setReferenceCounted(false);
            }

            if (!wakeLock.isHeld()) {
                // Survival sessions are intentionally short. Timeout prevents an
                // abandoned diagnostic session from holding the CPU indefinitely.
                wakeLock.acquire(10 * 60 * 1000L);
            }
        } catch (Throwable t) {
            recorder.addError(
                    SystemClock.elapsedRealtime(),
                    "WAKE_LOCK",
                    t.getClass().getSimpleName());
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean hasBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true;
        return ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void recordHeartbeat() {
        if (!recorder.isRunning()) return;

        ActivityManager.RunningAppProcessInfo info =
                new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(info);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean powerSave = pm != null && pm.isPowerSaveMode();
        boolean interactive = pm == null || pm.isInteractive();
        boolean ignoringBatteryOptimizations = pm != null
                && pm.isIgnoringBatteryOptimizations(getPackageName());

        recorder.addHeartbeatMain(
                SystemClock.elapsedRealtime(),
                android.os.Process.getElapsedCpuTime(),
                info.importance,
                powerSave,
                interactive,
                hasBackgroundLocationPermission(),
                ignoringBatteryOptimizations);
        if (recorder.isRunning()
                && wakeLockMode
                && !isWakeLockHeld()
                && !wakeLockLossReported) {
            wakeLockLossReported = true;
            recorder.addMarker(
                    SystemClock.elapsedRealtime(),
                    "WAKELOCK_NOT_HELD");
        }
    }

    private void startBackgroundHeartbeat() {
        stopBackgroundHeartbeat();

        backgroundHeartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GoGoGo-Lab17-BG-Heartbeat");
            t.setDaemon(true);
            return t;
        });

        backgroundHeartbeatFuture = backgroundHeartbeatExecutor.scheduleAtFixedRate(
                () -> {
                    try {
                        if (recorder.isRunning()) {
                            recorder.addHeartbeatBackground(
                                    SystemClock.elapsedRealtime(),
                                    android.os.Process.getElapsedCpuTime());
                        }
                    } catch (Throwable ignored) {
                    }
                },
                0L,
                500L,
                TimeUnit.MILLISECONDS);
    }

    private void stopBackgroundHeartbeat() {
        try {
            if (backgroundHeartbeatFuture != null) {
                backgroundHeartbeatFuture.cancel(true);
            }
        } catch (Throwable ignored) {
        }
        backgroundHeartbeatFuture = null;

        try {
            if (backgroundHeartbeatExecutor != null) {
                backgroundHeartbeatExecutor.shutdownNow();
            }
        } catch (Throwable ignored) {
        }
        backgroundHeartbeatExecutor = null;
    }

    private boolean isWakeLockHeld() {
        try {
            return wakeLock != null && wakeLock.isHeld();
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isBatteryOptimizationExempt() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
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

    private void postAutoDoneNotification(
            String type,
            LabCompatibilitySessionRecorder.Summary summary,
            long durationMs) {
        NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent open = new Intent(this, CompatibilitySessionActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                this,
                1800,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String label = LabAutoExperimentStore.TYPE_WECHAT.equals(type)
                ? "微信组"
                : "控制组";
        String text = label
                + "完成 · "
                + summary.grade()
                + " · "
                + summary.freezeDiagnosis()
                + " · "
                + Math.max(1L, durationMs / 1000L)
                + "s";

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        Notification notification = builder
                .setContentTitle("GoGoGo Lab 18 自动实验完成")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build();

        nm.notify(AUTO_DONE_NOTIFICATION_ID, notification);
    }

    private Notification buildNotification(String text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("GoGoGo Lab 18")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (recorder.isRunning()) {
            recorder.addMarker(SystemClock.elapsedRealtime(), "TASK_REMOVED");
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onTrimMemory(int level) {
        if (recorder.isRunning()) {
            recorder.addMarker(
                    SystemClock.elapsedRealtime(),
                    "TRIM_MEMORY_" + level);
        }
        super.onTrimMemory(level);
    }

    @Override
    public void onDestroy() {
        heartbeatHandler.removeCallbacks(heartbeatTask);
        stopBackgroundHeartbeat();
        releaseWakeLock();
        if (recorder.isRunning()) {
            recorder.addMarker(SystemClock.elapsedRealtime(), "SERVICE_DESTROY");
        }
        stopConsumers();
        super.onDestroy();
    }

    public final class LocalBinder extends Binder {
        public boolean isRunning() {
            return recorder.isRunning();
        }

        public boolean isWakeLockMode() {
            return wakeLockMode;
        }

        public boolean isWakeLockHeld() {
            return CompatibilitySessionService.this.isWakeLockHeld();
        }

        public boolean isBatteryOptimizationExempt() {
            return CompatibilitySessionService.this.isBatteryOptimizationExempt();
        }

        public boolean hasBackgroundLocationPermission() {
            return CompatibilitySessionService.this.hasBackgroundLocationPermission();
        }

        public int getCurrentProcessImportance() {
            ActivityManager.RunningAppProcessInfo info =
                    new ActivityManager.RunningAppProcessInfo();
            ActivityManager.getMyMemoryState(info);
            return info.importance;
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
