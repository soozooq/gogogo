package com.zcshou.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.view.WindowManager;
import android.view.Surface;
import android.hardware.SensorManager;
import android.hardware.SensorEventListener;
import android.hardware.SensorEvent;
import android.hardware.Sensor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Process;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.elvishew.xlog.XLog;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.zcshou.gogogo.MainActivity;
import com.zcshou.gogogo.LabPolicyEngine;
import com.zcshou.gogogo.LabScenarioEngine;
import com.zcshou.gogogo.LabKinematicsEngine;
import com.zcshou.gogogo.R;
import com.zcshou.gogogo.SimpleMockActivity;
import com.zcshou.joystick.JoyStick;

public class ServiceGo extends Service {
    // 定位相关变量
    public static final double DEFAULT_LAT = 36.667662;
    public static final double DEFAULT_LNG = 117.027707;
    public static final double DEFAULT_ALT = 55.0D;
    public static final float DEFAULT_BEA = 0.0F;
    private double mCurLat = DEFAULT_LAT;
    private double mCurLng = DEFAULT_LNG;
    private double mCurAlt = DEFAULT_ALT;
    private float mCurBea = DEFAULT_BEA;
    private double mSpeed = 0.0;        /* 默认的速度，单位 m/s */

    // GoGoGo Lab motion engine. Imported routes are capped at 5000 points to stay Binder-safe.
    public static final String ACTION_ROUTE_START = "com.soozooq.gogogo.action.ROUTE_START";
    public static final String ACTION_ROUTE_STOP = "com.soozooq.gogogo.action.ROUTE_STOP";
    public static final String ACTION_ROAM_START = "com.soozooq.gogogo.action.ROAM_START";
    public static final String ACTION_ROAM_STOP = "com.soozooq.gogogo.action.ROAM_STOP";
    public static final String ACTION_MOTION_PAUSE = "com.soozooq.gogogo.action.MOTION_PAUSE";
    public static final String ACTION_MOTION_RESUME = "com.soozooq.gogogo.action.MOTION_RESUME";
    public static final String ACTION_MOTION_SPEED = "com.soozooq.gogogo.action.MOTION_SPEED";
    public static final String ACTION_RECORD_START = "com.soozooq.gogogo.action.RECORD_START";
    public static final String ACTION_RECORD_STOP = "com.soozooq.gogogo.action.RECORD_STOP";
    public static final String ACTION_RECORD_CLEAR = "com.soozooq.gogogo.action.RECORD_CLEAR";
    public static final String ACTION_SCENARIO_RESET = "com.soozooq.gogogo.action.SCENARIO_RESET";

    public static final String EXTRA_ROUTE_LATS = "ROUTE_LATS";
    public static final String EXTRA_ROUTE_LNGS = "ROUTE_LNGS";
    public static final String EXTRA_ROUTE_SPEED_MPS = "ROUTE_SPEED_MPS";
    public static final String EXTRA_ROUTE_MODE = "ROUTE_MODE";
    public static final int ROUTE_MODE_ONCE = 0;
    public static final int ROUTE_MODE_LOOP = 1;
    public static final int ROUTE_MODE_PINGPONG = 2;

    public static final String EXTRA_ROAM_CENTER_LAT = "ROAM_CENTER_LAT";
    public static final String EXTRA_ROAM_CENTER_LNG = "ROAM_CENTER_LNG";
    public static final String EXTRA_ROAM_RADIUS_M = "ROAM_RADIUS_M";
    public static final String EXTRA_ROAM_SPEED_MPS = "ROAM_SPEED_MPS";
    public static final String EXTRA_MOTION_MULTIPLIER = "MOTION_MULTIPLIER";

    private double[] mRouteLats;
    private double[] mRouteLngs;
    private int mRouteIndex = 0;
    private int mRouteDirection = 1;
    private int mRouteMode = ROUTE_MODE_ONCE;
    private double mRouteSpeedMps = 1.4;
    private boolean mRouteActive = false;
    private double[] mRouteCumulative;
    private double mRouteTotalDistance = 0.0;

    private boolean mRoamActive = false;
    private double mRoamCenterLat;
    private double mRoamCenterLng;
    private double mRoamRadiusM = 100.0;
    private double mRoamSpeedMps = 1.4;
    private double mRoamTargetLat;
    private double mRoamTargetLng;
    private final java.util.Random mRandom = new java.util.Random();

    private long mLastMotionElapsed = 0L;
    private boolean mMotionPaused = false;
    private double mMotionMultiplier = 1.0;

    public static volatile boolean sRunning = false;

    private final Object mTrackLock = new Object();
    private final java.util.ArrayList<Double> mTrackLats = new java.util.ArrayList<>();
    private final java.util.ArrayList<Double> mTrackLngs = new java.util.ArrayList<>();
    private final java.util.ArrayList<Long> mTrackTimes = new java.util.ArrayList<>();
    private boolean mTrackRecording = false;
    private long mLastTrackSampleElapsed = 0L;
    private double mLastTrackLat = Double.NaN;
    private double mLastTrackLng = Double.NaN;
    private static final int MAX_TRACK_POINTS = 20000;

    // TaintDroid-inspired lightweight provenance for GoGoGo's own synthetic data pipeline.
    // This is NOT OS-wide taint tracking; it records where our emitted mock samples came from.
    private final Object mProvenanceLock = new Object();
    private final java.util.ArrayDeque<String> mProvenanceEvents = new java.util.ArrayDeque<>();
    private static final int MAX_PROVENANCE_EVENTS = 300;
    private String mProvenanceSource = "RESTORED";
    private long mLastProvenanceSampleElapsed = 0L;

    // MockDroid/MOSES-inspired Resource Broker: raw state stays internal,
    // providers only see the policy-filtered published state.
    private LabPolicyEngine mPolicyEngine;
    private volatile boolean mPolicyPublish = true;
    private volatile double mPublishedLat = DEFAULT_LAT;
    private volatile double mPublishedLng = DEFAULT_LNG;
    private volatile double mPublishedAlt = DEFAULT_ALT;
    private volatile double mPublishedSpeed = 0.0;
    private volatile float mPublishedBearing = DEFAULT_BEA;
    private volatile float mPublishedAccuracy = 0.8f;
    private volatile boolean mPublishedIncludeMotion = true;
    private volatile String mPolicySummary = "PERSONAL · location=EXACT";
    private String mLastPolicySignature = "";

    private LabScenarioEngine mScenarioEngine;
    private volatile long mScenarioStep = 0L;
    private volatile long mScenarioVirtualTimeMillis = LabScenarioEngine.DEFAULT_BASE_EPOCH_MS;
    private volatile int mScenarioBatteryPercent = 37;
    private volatile float mScenarioHeadingDegrees = 90.0f;
    private volatile String mScenarioSummary = "OFF";
    private volatile double mScenarioNoiseNorthMeters = 0.0;
    private volatile double mScenarioNoiseEastMeters = 0.0;
    private String mLastScenarioSignature = "";

    // Lab 12: keep movement course and handset orientation separate, then arbitrate
    // a published bearing and derive kinematic telemetry without spoofing sensors.
    private LabKinematicsEngine mKinematicsEngine;
    private volatile LabKinematicsEngine.Frame mKinematicFrame;
    private volatile float mDeviceHeadingDegrees = 0.0f;
    private volatile int mDeviceHeadingAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
    private volatile String mHeadingSensorSource = "NONE";
    private String mLastKinematicSignature = "";

    private static final int HANDLER_MSG_ID = 0;
    // 33ms (~30Hz) tick: 缩短"mock 过期"窗口,避免某些应用在两次 push 之间读到真实位置后漂移
    private static final long TICK_INTERVAL_MS = 33;
    private static final String SERVICE_GO_HANDLER_NAME = "ServiceGoLocation";
    private LocationManager mLocManager;
    private HandlerThread mLocHandlerThread;
    private Handler mLocHandler;
    private LocationListener mPersistentListener;     // 保持 Provider 活跃订阅,见 onCreate 注释
    private FusedLocationProviderClient mFusedClient; // Google Play Services fused mock, null = GMS 不可用
    private boolean mFusedMockEnabled = false;
    private boolean isStop = false;
    private PowerManager.WakeLock mWakeLock;

    // 手机朝向：直接从系统方向传感器读取，避免高德在 Mock 模式下不再更新自身罗盘。
    private SensorManager mSensorManager;
    private Sensor mHeadingSensor;
    private SensorEventListener mHeadingListener;
    private volatile boolean mHeadingAvailable = false;
    // 通知栏消息
    private static final int SERVICE_GO_NOTE_ID = 1;
    private static final String SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW = "ShowJoyStick";
    private static final String SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE = "HideJoyStick";
    private static final String SERVICE_GO_NOTE_CHANNEL_ID = "SERVICE_GO_NOTE";
    private static final String SERVICE_GO_NOTE_CHANNEL_NAME = "SERVICE_GO_NOTE";
    private NoteActionReceiver mActReceiver;
    // 摇杆相关。测试版默认禁用 overlay，避免遮挡其它应用。
    private static final boolean ENABLE_JOYSTICK_OVERLAY = false;
    private JoyStick mJoyStick;

    private final ServiceGoBinder mBinder = new ServiceGoBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sRunning = true;
        recordProvenance("SERVICE", "ServiceGo created");

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GoGoGo:MockLocation");
            mWakeLock.setReferenceCounted(false);
            try {
                mWakeLock.acquire();
            } catch (Throwable ignored) {
            }
        }

        mPolicyEngine = new LabPolicyEngine(this);
        mScenarioEngine = new LabScenarioEngine(this);
        mKinematicsEngine = new LabKinematicsEngine();
        refreshPublishedPolicy();

        mLocManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        initHeadingSensor();

        removeTestProviderNetwork();
        addTestProviderNetwork();

        removeTestProviderGPS();
        addTestProviderGPS();

        // Android 12+ 起 LocationManager 也有 "fused" provider,部分应用(WeChat 走的就是
        // 这条) 不直接读 GPS/NETWORK 而是读 fused。能注就注,失败也无所谓。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            removeTestProviderFused();
            addTestProviderFused();
        }

        initGoLocation();

        initNotification();

        if (ENABLE_JOYSTICK_OVERLAY) {
            // No-map test build: floating joystick intentionally disabled.
        }

        // 关键:维持 Provider 活跃订阅。Android 12+ 上没有活跃 listener 的 Provider
        // 会进入低功耗/缓存退化状态,微信小程序调 getLastKnownLocation 拿不到
        // 刚 push 的 mock 数据 (高德/百度 App 自己有订阅就能看到)。
        // Baidu 版靠 LocationClient(:remote, scanSpan=1000)做这件事,迁移后需要补回来。
        initPersistentLocationListener();

        // 第二条路径: 用 GMS FusedLocationProviderClient.setMockMode + setMockLocation
        // 覆盖走 Google Play Services 路径的应用(部分 WeChat/腾讯小程序场景)。
        // 设备没装 GMS 就 try/catch 静默跳过。
        initFusedMock();
    }

    private void initHeadingSensor() {
        try {
            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if (mSensorManager == null) {
                XLog.e("SERVICEGO: SensorManager unavailable");
                return;
            }

            mHeadingSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (mHeadingSensor != null) {
                mHeadingSensorSource = "ROTATION_VECTOR";
            }
            if (mHeadingSensor == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                mHeadingSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
                if (mHeadingSensor != null) {
                    mHeadingSensorSource = "GEOMAGNETIC_ROTATION_VECTOR";
                }
            }
            if (mHeadingSensor == null) {
                XLog.e("SERVICEGO: no rotation-vector heading sensor");
                return;
            }

            mHeadingListener = new SensorEventListener() {
                private final float[] rotation = new float[9];
                private final float[] adjusted = new float[9];
                private final float[] orientation = new float[3];

                @Override
                public void onSensorChanged(SensorEvent event) {
                    if (event == null || event.values == null) return;
                    try {
                        SensorManager.getRotationMatrixFromVector(rotation, event.values);

                        int displayRotation = Surface.ROTATION_0;
                        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                        if (wm != null) {
                            displayRotation = wm.getDefaultDisplay().getRotation();
                        }

                        int axisX = SensorManager.AXIS_X;
                        int axisY = SensorManager.AXIS_Y;
                        if (displayRotation == Surface.ROTATION_90) {
                            axisX = SensorManager.AXIS_Y;
                            axisY = SensorManager.AXIS_MINUS_X;
                        } else if (displayRotation == Surface.ROTATION_180) {
                            axisX = SensorManager.AXIS_MINUS_X;
                            axisY = SensorManager.AXIS_MINUS_Y;
                        } else if (displayRotation == Surface.ROTATION_270) {
                            axisX = SensorManager.AXIS_MINUS_Y;
                            axisY = SensorManager.AXIS_X;
                        }

                        SensorManager.remapCoordinateSystem(rotation, axisX, axisY, adjusted);
                        SensorManager.getOrientation(adjusted, orientation);

                        float heading = (float) Math.toDegrees(orientation[0]);
                        if (heading < 0f) heading += 360f;
                        mDeviceHeadingDegrees = mHeadingAvailable
                                ? smoothHeadingDegrees(mDeviceHeadingDegrees, heading, 0.25f)
                                : heading;
                        mHeadingAvailable = true;
                    } catch (Throwable ignored) {
                    }
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                    mDeviceHeadingAccuracy = accuracy;
                }
            };

            boolean registered = mSensorManager.registerListener(
                    mHeadingListener, mHeadingSensor, SensorManager.SENSOR_DELAY_GAME);
            XLog.i("SERVICEGO: heading sensor registered=" + registered
                    + " type=" + mHeadingSensor.getType());
        } catch (Throwable t) {
            XLog.e("SERVICEGO: heading sensor init failed: " + t.getMessage());
        }
    }

    @SuppressLint("MissingPermission")
    private void initFusedMock() {
        try {
            mFusedClient = LocationServices.getFusedLocationProviderClient(this);
            mFusedClient.setMockMode(true)
                    .addOnSuccessListener(unused -> {
                        mFusedMockEnabled = true;
                        XLog.i("SERVICEGO: FusedLocation setMockMode(true) OK");
                    })
                    .addOnFailureListener(e -> XLog.e("SERVICEGO: FusedLocation setMockMode failed: " + e.getMessage()));
        } catch (Throwable t) {
            // 设备没 GMS / play-services-location 不可用 → 退化到仅 LocationManager 注入
            XLog.e("SERVICEGO: FusedLocation init failed (GMS not available?): " + t.getMessage());
            mFusedClient = null;
        }
    }

    @SuppressLint("MissingPermission")
    private void initPersistentLocationListener() {
        mPersistentListener = new LocationListener() {
            @Override public void onLocationChanged(@NonNull Location location) {}
            @Override public void onProviderEnabled(@NonNull String provider) {}
            @Override public void onProviderDisabled(@NonNull String provider) {}
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        };
        try {
            mLocManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0f, mPersistentListener);
            mLocManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0f, mPersistentListener);
        } catch (SecurityException e) {
            XLog.e("SERVICEGO: ERROR - requestLocationUpdates permission missing");
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - initPersistentLocationListener: " + e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        android.content.SharedPreferences state =
                getSharedPreferences("mock_location_state", MODE_PRIVATE);

        if (intent != null && ACTION_SCENARIO_RESET.equals(intent.getAction())) {
            mScenarioStep = 0L;
            recordProvenance("SCENARIO", "replay reset to tick 0");
            refreshPublishedPolicy();
            return START_STICKY;
        }

        if (intent != null && ACTION_RECORD_START.equals(intent.getAction())) {
            synchronized (mTrackLock) {
                mTrackRecording = true;
                mLastTrackSampleElapsed = 0L;
                mLastTrackLat = Double.NaN;
                mLastTrackLng = Double.NaN;
            }
            sampleTrackIfNeeded(true);
            recordProvenance("TRACK", "recording started");
            return START_STICKY;
        }

        if (intent != null && ACTION_RECORD_STOP.equals(intent.getAction())) {
            synchronized (mTrackLock) {
                mTrackRecording = false;
            }
            recordProvenance("TRACK", "recording stopped");
            return START_STICKY;
        }

        if (intent != null && ACTION_RECORD_CLEAR.equals(intent.getAction())) {
            synchronized (mTrackLock) {
                mTrackLats.clear();
                mTrackLngs.clear();
                mTrackTimes.clear();
                mLastTrackSampleElapsed = 0L;
                mLastTrackLat = Double.NaN;
                mLastTrackLng = Double.NaN;
            }
            recordProvenance("TRACK", "recording buffer cleared");
            return START_STICKY;
        }

        if (intent != null && ACTION_MOTION_PAUSE.equals(intent.getAction())) {
            if (mRouteActive || mRoamActive) {
                mMotionPaused = true;
                mSpeed = 0.0;
                recordProvenance("MOTION", "paused");
            }
            return START_STICKY;
        }

        if (intent != null && ACTION_MOTION_RESUME.equals(intent.getAction())) {
            if (mRouteActive || mRoamActive) {
                mMotionPaused = false;
                mLastMotionElapsed = SystemClock.elapsedRealtime();
                mSpeed = mRouteActive ? mRouteSpeedMps * mMotionMultiplier
                        : mRoamSpeedMps * mMotionMultiplier;
                recordProvenance("MOTION", "resumed");
            }
            return START_STICKY;
        }

        if (intent != null && ACTION_MOTION_SPEED.equals(intent.getAction())) {
            mMotionMultiplier = clamp(
                    intent.getDoubleExtra(EXTRA_MOTION_MULTIPLIER, 1.0), 0.25, 4.0);
            if (!mMotionPaused) {
                mSpeed = (mRouteActive ? mRouteSpeedMps : mRoamSpeedMps) * mMotionMultiplier;
            }
            recordProvenance("MOTION", "multiplier=" + mMotionMultiplier + "x");
            return START_STICKY;
        }

        if (intent != null && ACTION_ROUTE_STOP.equals(intent.getAction())) {
            recordProvenance("ROUTE", "route stopped");
            stopLabMotion();
            mProvenanceSource = "IDLE";
            return START_STICKY;
        }

        if (intent != null && ACTION_ROAM_STOP.equals(intent.getAction())) {
            mRoamActive = false;
            mSpeed = 0.0;
            recordProvenance("ROAM", "random roam stopped");
            mProvenanceSource = "IDLE";
            return START_STICKY;
        }

        if (intent != null && ACTION_ROUTE_START.equals(intent.getAction())) {
            double[] lats = intent.getDoubleArrayExtra(EXTRA_ROUTE_LATS);
            double[] lngs = intent.getDoubleArrayExtra(EXTRA_ROUTE_LNGS);
            if (lats != null && lngs != null && lats.length > 1 && lats.length == lngs.length) {
                mRouteLats = lats;
                mRouteLngs = lngs;
                buildRouteMetrics();
                mRouteIndex = 0;
                mRouteDirection = 1;
                mRouteMode = Math.max(ROUTE_MODE_ONCE,
                        Math.min(ROUTE_MODE_PINGPONG,
                                intent.getIntExtra(EXTRA_ROUTE_MODE, ROUTE_MODE_ONCE)));
                mRouteSpeedMps = clamp(
                        intent.getDoubleExtra(EXTRA_ROUTE_SPEED_MPS, 1.4), 0.2, 60.0);
                mRouteActive = true;
                mRoamActive = false;
                mMotionPaused = false;
                mMotionMultiplier = 1.0;
                mCurLat = mRouteLats[0];
                mCurLng = mRouteLngs[0];
                mCurAlt = DEFAULT_ALT;
                mSpeed = mRouteSpeedMps * mMotionMultiplier;
                mLastMotionElapsed = SystemClock.elapsedRealtime();

                mProvenanceSource = "ROUTE";
                recordProvenance("ROUTE", "started points=" + lats.length
                        + " speed=" + mRouteSpeedMps + "m/s mode=" + mRouteMode);
                persistCurrentLocation(state);
                XLog.i("SERVICEGO: Lab route started, points=" + lats.length
                        + " speed=" + mRouteSpeedMps + " mode=" + mRouteMode);
                return START_STICKY;
            }
        }

        if (intent != null && ACTION_ROAM_START.equals(intent.getAction())) {
            mRoamCenterLat = intent.getDoubleExtra(EXTRA_ROAM_CENTER_LAT, mCurLat);
            mRoamCenterLng = intent.getDoubleExtra(EXTRA_ROAM_CENTER_LNG, mCurLng);
            mRoamRadiusM = clamp(intent.getDoubleExtra(EXTRA_ROAM_RADIUS_M, 100.0), 5.0, 5000.0);
            mRoamSpeedMps = clamp(intent.getDoubleExtra(EXTRA_ROAM_SPEED_MPS, 1.4), 0.2, 30.0);
            mCurLat = mRoamCenterLat;
            mCurLng = mRoamCenterLng;
            mCurAlt = DEFAULT_ALT;
            mRouteActive = false;
            mRoamActive = true;
            mMotionPaused = false;
            mMotionMultiplier = 1.0;
            mSpeed = mRoamSpeedMps * mMotionMultiplier;
            chooseNewRoamTarget();
            mLastMotionElapsed = SystemClock.elapsedRealtime();
            mProvenanceSource = "ROAM";
            recordProvenance("ROAM", "started radius=" + mRoamRadiusM
                    + "m speed=" + mRoamSpeedMps + "m/s");
            persistCurrentLocation(state);
            XLog.i("SERVICEGO: Lab random roam started, radius=" + mRoamRadiusM
                    + " speed=" + mRoamSpeedMps);
            return START_STICKY;
        }

        if (intent != null) {
            // Ordinary manual position commands cancel Lab motion.
            stopLabMotion();

            mCurLng = intent.getDoubleExtra(MainActivity.LNG_MSG_ID, DEFAULT_LNG);
            mCurLat = intent.getDoubleExtra(MainActivity.LAT_MSG_ID, DEFAULT_LAT);
            mCurAlt = intent.getDoubleExtra(MainActivity.ALT_MSG_ID, DEFAULT_ALT);
            mProvenanceSource = "MANUAL";
            recordProvenance("MANUAL", "position selected");
            persistCurrentLocation(state);
        } else {
            mCurLng = Double.longBitsToDouble(
                    state.getLong("lng_bits", Double.doubleToRawLongBits(DEFAULT_LNG)));
            mCurLat = Double.longBitsToDouble(
                    state.getLong("lat_bits", Double.doubleToRawLongBits(DEFAULT_LAT)));
            mCurAlt = Double.longBitsToDouble(
                    state.getLong("alt_bits", Double.doubleToRawLongBits(DEFAULT_ALT)));
            mProvenanceSource = "RESTORED";
        }

        if (mJoyStick != null) {
            mJoyStick.setCurrentPosition(mCurLng, mCurLat, mCurAlt);
        }

        return START_STICKY;
    }

    private void persistCurrentLocation(android.content.SharedPreferences state) {
        state.edit()
                .putLong("lng_bits", Double.doubleToRawLongBits(mCurLng))
                .putLong("lat_bits", Double.doubleToRawLongBits(mCurLat))
                .putLong("alt_bits", Double.doubleToRawLongBits(mCurAlt))
                .apply();
    }

    private void stopLabMotion() {
        mRouteActive = false;
        mRoamActive = false;
        mRouteLats = null;
        mRouteLngs = null;
        mRouteCumulative = null;
        mRouteTotalDistance = 0.0;
        mSpeed = 0.0;
        mLastMotionElapsed = 0L;
        mMotionPaused = false;
        mMotionMultiplier = 1.0;
    }

    @Override
    public void onDestroy() {
        isStop = true;
        sRunning = false;
        mLocHandler.removeMessages(HANDLER_MSG_ID);
        mLocHandlerThread.quit();

        if (mJoyStick != null) {
            if (mJoyStick != null) mJoyStick.destroy();
            mJoyStick = null;
        }

        removeTestProviderNetwork();
        removeTestProviderGPS();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            removeTestProviderFused();
        }

        if (mPersistentListener != null) {
            try {
                mLocManager.removeUpdates(mPersistentListener);
            } catch (Exception ignored) {
            }
        }

        if (mSensorManager != null && mHeadingListener != null) {
            try {
                mSensorManager.unregisterListener(mHeadingListener);
            } catch (Throwable ignored) {
            }
        }

        if (mFusedClient != null && mFusedMockEnabled) {
            try {
                boolean fine = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED;
                boolean coarse = checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED;
                if (fine || coarse) {
                    mFusedClient.setMockMode(false);
                }
            } catch (Exception ignored) {
            }
        }

        unregisterReceiver(mActReceiver);
        stopForeground(STOP_FOREGROUND_REMOVE);

        if (mWakeLock != null && mWakeLock.isHeld()) {
            try {
                mWakeLock.release();
            } catch (Throwable ignored) {
            }
        }

        if (mScenarioEngine != null) {
            try {
                mScenarioEngine.close();
            } catch (Throwable ignored) {
            }
            mScenarioEngine = null;
        }

        mKinematicFrame = null;
        mKinematicsEngine = null;

        if (mPolicyEngine != null) {
            try {
                mPolicyEngine.close();
            } catch (Throwable ignored) {
            }
            mPolicyEngine = null;
        }

        super.onDestroy();
    }

    private void initNotification() {
        mActReceiver = new NoteActionReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW);
        filter.addAction(SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mActReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            //noinspection UnspecifiedRegisterReceiverFlag
            registerReceiver(mActReceiver, filter);
        }

        NotificationChannel mChannel = new NotificationChannel(SERVICE_GO_NOTE_CHANNEL_ID, SERVICE_GO_NOTE_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            notificationManager.createNotificationChannel(mChannel);
        }

        //准备intent
        Intent clickIntent = new Intent(this, SimpleMockActivity.class);
        PendingIntent clickPI = PendingIntent.getActivity(this, 1, clickIntent, PendingIntent.FLAG_IMMUTABLE);
        Intent showIntent = new Intent(SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW);
        PendingIntent showPendingPI = PendingIntent.getBroadcast(this, 0, showIntent, PendingIntent.FLAG_IMMUTABLE);
        Intent hideIntent = new Intent(SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE);
        PendingIntent hidePendingPI = PendingIntent.getBroadcast(this, 0, hideIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, SERVICE_GO_NOTE_CHANNEL_ID)
                .setChannelId(SERVICE_GO_NOTE_CHANNEL_ID)
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentText(getResources().getString(R.string.app_service_tips))
                .setContentIntent(clickPI)
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();

        // Android 14+ (API 34) 要求显式声明 foregroundServiceType,否则在前台服务中
        // requestLocationUpdates 会抛 SecurityException
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(SERVICE_GO_NOTE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(SERVICE_GO_NOTE_ID, notification);
        }
    }

    private void initJoyStick() {
        mJoyStick = new JoyStick(this);
        mJoyStick.setListener(new JoyStick.JoyStickClickListener() {
            @Override
            public void onMoveInfo(double speed, double disLng, double disLat, double angle) {
                mSpeed = speed;
                // 根据当前的经纬度和距离，计算下一个经纬度
                // Latitude: 1 deg = 110.574 km // 纬度的每度的距离大约为 110.574km
                // Longitude: 1 deg = 111.320*cos(latitude) km  // 经度的每度的距离从0km到111km不等
                // 具体见：http://wp.mlab.tw/?p=2200
                mCurLng += disLng / (111.320 * Math.cos(Math.abs(mCurLat) * Math.PI / 180));
                mCurLat += disLat / 110.574;
                mCurBea = (float) angle;
            }

            @Override
            public void onPositionInfo(double lng, double lat, double alt) {
                mCurLng = lng;
                mCurLat = lat;
                mCurAlt = alt;
            }
        });
        mJoyStick.show();
    }

    private void initGoLocation() {
        // 创建 HandlerThread 实例，第一个参数是线程的名字
        mLocHandlerThread = new HandlerThread(SERVICE_GO_HANDLER_NAME, Process.THREAD_PRIORITY_FOREGROUND);
        // 启动 HandlerThread 线程
        mLocHandlerThread.start();
        // Handler 对象与 HandlerThread 的 Looper 对象的绑定
        mLocHandler = new Handler(mLocHandlerThread.getLooper()) {
            // 这里的Handler对象可以看作是绑定在HandlerThread子线程中，所以handlerMessage里的操作是在子线程中运行的
            @Override
            public void handleMessage(@NonNull Message msg) {
                try {
                    Thread.sleep(TICK_INTERVAL_MS);

                    if (!isStop) {
                        advanceLabMotion();
                        sampleTrackIfNeeded(false);
                        sampleProvenanceIfNeeded();
                        refreshPublishedPolicy();
                        LabScenarioEngine scenario = mScenarioEngine;
                        if (scenario != null && scenario.isEnabled() && mScenarioStep < Long.MAX_VALUE) {
                            mScenarioStep++;
                        }
                        setLocationNetwork();
                        setLocationGPS();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            setLocationFused();
                        }
                        setLocationFusedClient();

                        sendEmptyMessage(HANDLER_MSG_ID);
                    }
                } catch (InterruptedException e) {
                    XLog.e("SERVICEGO: ERROR - handleMessage");
                    Thread.currentThread().interrupt();
                }
            }
        };

        mLocHandler.sendEmptyMessage(HANDLER_MSG_ID);
    }

    private void buildRouteMetrics() {
        if (mRouteLats == null || mRouteLngs == null
                || mRouteLats.length == 0 || mRouteLats.length != mRouteLngs.length) {
            mRouteCumulative = null;
            mRouteTotalDistance = 0.0;
            return;
        }

        mRouteCumulative = new double[mRouteLats.length];
        mRouteCumulative[0] = 0.0;
        double total = 0.0;
        for (int i = 1; i < mRouteLats.length; i++) {
            total += distanceMeters(
                    mRouteLats[i - 1], mRouteLngs[i - 1],
                    mRouteLats[i], mRouteLngs[i]);
            mRouteCumulative[i] = total;
        }
        mRouteTotalDistance = total;
    }

    private void sampleTrackIfNeeded(boolean force) {
        synchronized (mTrackLock) {
            if (!mTrackRecording) return;

            long now = SystemClock.elapsedRealtime();
            if (!force && mLastTrackSampleElapsed > 0L
                    && now - mLastTrackSampleElapsed < 1000L) {
                return;
            }

            double moved = (Double.isNaN(mLastTrackLat) || Double.isNaN(mLastTrackLng))
                    ? Double.MAX_VALUE
                    : distanceMeters(mLastTrackLat, mLastTrackLng, mCurLat, mCurLng);

            if (!force && moved < 0.5
                    && mLastTrackSampleElapsed > 0L
                    && now - mLastTrackSampleElapsed < 5000L) {
                return;
            }

            if (mTrackLats.size() >= MAX_TRACK_POINTS) {
                mTrackLats.remove(0);
                mTrackLngs.remove(0);
                mTrackTimes.remove(0);
            }

            mTrackLats.add(mCurLat);
            mTrackLngs.add(mCurLng);
            mTrackTimes.add(System.currentTimeMillis());
            mLastTrackLat = mCurLat;
            mLastTrackLng = mCurLng;
            mLastTrackSampleElapsed = now;
        }
    }

    private void refreshPublishedPolicy() {
        LabPolicyEngine engine = mPolicyEngine;
        LabPolicyEngine.LocationDecision decision;

        if (engine == null) {
            decision = new LabPolicyEngine.LocationDecision(
                    true,
                    mCurLat,
                    mCurLng,
                    mCurAlt,
                    mSpeed,
                    mCurBea,
                    0.8f,
                    true,
                    "FALLBACK");
            mPolicySummary = "FALLBACK · location=EXACT";
        } else {
            decision = engine.decideLocation(
                    mCurLat, mCurLng, mCurAlt, mSpeed, mCurBea);
            mPolicySummary = engine.summary();
        }

        LabScenarioEngine scenario = mScenarioEngine;
        if (scenario != null) {
            LabScenarioEngine.ScenarioLocation transformed = scenario.applyLocation(
                    decision.publish,
                    decision.latitude,
                    decision.longitude,
                    decision.altitude,
                    decision.speedMps,
                    decision.bearingDegrees,
                    decision.accuracyMeters,
                    decision.includeMotion,
                    mScenarioStep);

            mPolicyPublish = transformed.publish;
            mPublishedLat = transformed.latitude;
            mPublishedLng = transformed.longitude;
            mPublishedAlt = transformed.altitude;
            mPublishedSpeed = transformed.speedMps;
            mPublishedBearing = transformed.bearingDegrees;
            mPublishedAccuracy = transformed.accuracyMeters;
            mPublishedIncludeMotion = transformed.includeMotion;
            mScenarioNoiseNorthMeters = transformed.noiseNorthMeters;
            mScenarioNoiseEastMeters = transformed.noiseEastMeters;
            mScenarioVirtualTimeMillis = scenario.virtualTimeMillis(
                    mScenarioStep, TICK_INTERVAL_MS);
            mScenarioBatteryPercent = scenario.batteryPercent(mScenarioStep);
            mScenarioHeadingDegrees = scenario.headingDegrees(mScenarioStep);
            mScenarioSummary = scenario.summary();

            String scenarioSignature = mScenarioSummary;
            if (!scenarioSignature.equals(mLastScenarioSignature)) {
                mLastScenarioSignature = scenarioSignature;
                recordProvenance("SCENARIO", scenarioSignature);
            }
        } else {
            mPolicyPublish = decision.publish;
            mPublishedLat = decision.latitude;
            mPublishedLng = decision.longitude;
            mPublishedAlt = decision.altitude;
            mPublishedSpeed = decision.speedMps;
            mPublishedBearing = decision.bearingDegrees;
            mPublishedAccuracy = decision.accuracyMeters;
            mPublishedIncludeMotion = decision.includeMotion;
            mScenarioSummary = "UNAVAILABLE";
            mScenarioNoiseNorthMeters = 0.0;
            mScenarioNoiseEastMeters = 0.0;
        }

        LabKinematicsEngine kinematics = mKinematicsEngine;
        if (kinematics != null) {
            LabKinematicsEngine.Frame frame = kinematics.update(
                    mPublishedLat,
                    mPublishedLng,
                    mPublishedSpeed,
                    mPublishedBearing,
                    mMotionPaused,
                    mHeadingAvailable,
                    mDeviceHeadingDegrees,
                    mProvenanceSource,
                    mScenarioStep,
                    TICK_INTERVAL_MS);
            mKinematicFrame = frame;
            if (mPublishedIncludeMotion) {
                mPublishedBearing = frame.effectiveBearingDeg;
            }

            String kinematicSignature = frame.state + " · " + frame.bearingSource;
            if (!kinematicSignature.equals(mLastKinematicSignature)) {
                mLastKinematicSignature = kinematicSignature;
                recordProvenance("KINEMATICS",
                        kinematicSignature + " · ledger=" + frame.shortHash());
            }
        }

        String signature = mPolicySummary + " · publish=" + mPolicyPublish;
        if (!signature.equals(mLastPolicySignature)) {
            mLastPolicySignature = signature;
            recordProvenance("POLICY", signature);
        }
    }

    private void sampleProvenanceIfNeeded() {
        long now = SystemClock.elapsedRealtime();
        if (mLastProvenanceSampleElapsed > 0L
                && now - mLastProvenanceSampleElapsed < 5000L) {
            return;
        }
        mLastProvenanceSampleElapsed = now;
        recordProvenance("SAMPLE",
                String.format(java.util.Locale.US,
                        "src=%s lng=%.6f lat=%.6f speed=%.2fm/s bearing=%.1f°",
                        mProvenanceSource, mCurLng, mCurLat, mSpeed, mCurBea));
    }

    private void recordProvenance(String kind, String detail) {
        String stamp;
        try {
            java.text.SimpleDateFormat format =
                    new java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US);
            stamp = format.format(new java.util.Date());
        } catch (Throwable t) {
            stamp = String.valueOf(System.currentTimeMillis());
        }

        String line = stamp + "  [" + kind + "]  " + detail;
        synchronized (mProvenanceLock) {
            while (mProvenanceEvents.size() >= MAX_PROVENANCE_EVENTS) {
                mProvenanceEvents.removeFirst();
            }
            mProvenanceEvents.addLast(line);
        }
    }

    private double getCurrentPassProgressMeters() {
        if (!mRouteActive || mRouteLats == null || mRouteCumulative == null
                || mRouteLats.length < 2 || mRouteIndex < 0 || mRouteIndex >= mRouteLats.length) {
            return 0.0;
        }

        int next = mRouteIndex + mRouteDirection;
        double fromVertexDistance = 0.0;
        if (next >= 0 && next < mRouteLats.length) {
            fromVertexDistance = distanceMeters(
                    mRouteLats[mRouteIndex], mRouteLngs[mRouteIndex],
                    mCurLat, mCurLng);
        }

        if (mRouteDirection >= 0) {
            return clamp(mRouteCumulative[mRouteIndex] + fromVertexDistance,
                    0.0, mRouteTotalDistance);
        } else {
            return clamp((mRouteTotalDistance - mRouteCumulative[mRouteIndex])
                    + fromVertexDistance, 0.0, mRouteTotalDistance);
        }
    }

    private void advanceLabMotion() {
        if (!mRouteActive && !mRoamActive) return;
        if (mMotionPaused) {
            mLastMotionElapsed = SystemClock.elapsedRealtime();
            mSpeed = 0.0;
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (mLastMotionElapsed <= 0L) {
            mLastMotionElapsed = now;
            return;
        }

        double dt = (now - mLastMotionElapsed) / 1000.0;
        mLastMotionElapsed = now;
        // Avoid a giant teleport after the process/thread was paused for a while.
        dt = clamp(dt, 0.0, 0.25);

        if (mRouteActive) {
            moveAlongRoute(mRouteSpeedMps * mMotionMultiplier * dt);
        } else if (mRoamActive) {
            moveRandomRoam(mRoamSpeedMps * mMotionMultiplier * dt);
        }
    }

    private void moveAlongRoute(double metersToMove) {
        if (mRouteLats == null || mRouteLngs == null
                || mRouteLats.length < 2 || mRouteLats.length != mRouteLngs.length) {
            stopLabMotion();
            return;
        }

        mSpeed = mRouteSpeedMps * mMotionMultiplier;
        int guard = 0;
        while (metersToMove > 0.0001 && mRouteActive && guard++ < 20) {
            int next = mRouteIndex + mRouteDirection;

            if (next < 0 || next >= mRouteLats.length) {
                if (mRouteMode == ROUTE_MODE_LOOP) {
                    next = mRouteDirection > 0 ? 0 : mRouteLats.length - 1;
                } else if (mRouteMode == ROUTE_MODE_PINGPONG) {
                    mRouteDirection *= -1;
                    next = mRouteIndex + mRouteDirection;
                } else {
                    mRouteActive = false;
                    mSpeed = 0.0;
                    XLog.i("SERVICEGO: Lab route finished");
                    return;
                }
            }

            double targetLat = mRouteLats[next];
            double targetLng = mRouteLngs[next];
            double remaining = distanceMeters(mCurLat, mCurLng, targetLat, targetLng);

            if (remaining < 0.05) {
                mCurLat = targetLat;
                mCurLng = targetLng;
                mRouteIndex = next;
                continue;
            }

            // Route course is geometry-derived. Do not let handset orientation overwrite it.
            mCurBea = bearingDegrees(mCurLat, mCurLng, targetLat, targetLng);

            if (metersToMove >= remaining) {
                mCurLat = targetLat;
                mCurLng = targetLng;
                mRouteIndex = next;
                metersToMove -= remaining;
            } else {
                double fraction = metersToMove / remaining;
                mCurLat += (targetLat - mCurLat) * fraction;
                mCurLng += shortestLongitudeDelta(mCurLng, targetLng) * fraction;
                normalizeCurrentLongitude();
                metersToMove = 0.0;
            }
        }
    }

    private void moveRandomRoam(double metersToMove) {
        mSpeed = mRoamSpeedMps * mMotionMultiplier;
        int guard = 0;
        while (metersToMove > 0.0001 && mRoamActive && guard++ < 10) {
            double remaining = distanceMeters(mCurLat, mCurLng, mRoamTargetLat, mRoamTargetLng);
            if (remaining < 0.5) {
                chooseNewRoamTarget();
                continue;
            }

            // Roam course is geometry-derived. Device heading is tracked separately.
            mCurBea = bearingDegrees(mCurLat, mCurLng, mRoamTargetLat, mRoamTargetLng);

            if (metersToMove >= remaining) {
                mCurLat = mRoamTargetLat;
                mCurLng = mRoamTargetLng;
                metersToMove -= remaining;
                chooseNewRoamTarget();
            } else {
                double fraction = metersToMove / remaining;
                mCurLat += (mRoamTargetLat - mCurLat) * fraction;
                mCurLng += shortestLongitudeDelta(mCurLng, mRoamTargetLng) * fraction;
                normalizeCurrentLongitude();
                metersToMove = 0.0;
            }
        }
    }

    private void chooseNewRoamTarget() {
        double angle = mRandom.nextDouble() * Math.PI * 2.0;
        double radius = Math.sqrt(mRandom.nextDouble()) * mRoamRadiusM;
        double north = Math.cos(angle) * radius;
        double east = Math.sin(angle) * radius;

        mRoamTargetLat = mRoamCenterLat + north / 111320.0;
        double cos = Math.max(0.05, Math.cos(Math.toRadians(mRoamCenterLat)));
        mRoamTargetLng = mRoamCenterLng + east / (111320.0 * cos);
        if (mRoamTargetLng > 180.0) mRoamTargetLng -= 360.0;
        if (mRoamTargetLng < -180.0) mRoamTargetLng += 360.0;
    }

    private static double shortestLongitudeDelta(double from, double to) {
        double d = to - from;
        if (d > 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }

    private void normalizeCurrentLongitude() {
        if (mCurLng > 180.0) mCurLng -= 360.0;
        if (mCurLng < -180.0) mCurLng += 360.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        final double earth = 6371000.0;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2.0) * Math.sin(dp / 2.0)
                + Math.cos(p1) * Math.cos(p2)
                * Math.sin(dl / 2.0) * Math.sin(dl / 2.0);
        return earth * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
    }

    private static float bearingDegrees(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dl = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2)
                - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        double result = Math.toDegrees(Math.atan2(y, x));
        if (result < 0.0) result += 360.0;
        return (float) result;
    }

    private static float smoothHeadingDegrees(float previous, float next, float alpha) {
        float from = previous % 360f;
        if (from < 0f) from += 360f;
        float to = next % 360f;
        if (to < 0f) to += 360f;
        float delta = to - from;
        if (delta > 180f) delta -= 360f;
        if (delta < -180f) delta += 360f;
        float out = from + delta * Math.max(0f, Math.min(1f, alpha));
        out %= 360f;
        if (out < 0f) out += 360f;
        return out;
    }

    private void removeTestProviderGPS() {
        try {
            if (mLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false);
                mLocManager.removeTestProvider(LocationManager.GPS_PROVIDER);
            }
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - removeTestProviderGPS");
        }
    }

    // 注意下面临时添加 @SuppressLint("wrongconstant") 以处理 addTestProvider 参数值的 lint 错误
    @SuppressLint("wrongconstant")
    private void addTestProviderGPS() {
        try {
            // 注意，由于 android api 问题，下面的参数会提示错误(以下参数是通过相关API获取的真实GPS参数，不是随便写的)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mLocManager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false,
                        false, true, true, true, ProviderProperties.POWER_USAGE_HIGH, ProviderProperties.ACCURACY_FINE);
            } else {
                mLocManager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false,
                        false, true, true, true, Criteria.POWER_HIGH, Criteria.ACCURACY_FINE);
            }
            if (!mLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
            }
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - addTestProviderGPS");
        }
    }

    private void setLocationGPS() {
        if (!mPolicyPublish) return;
        try {
            Location loc = new Location(LocationManager.GPS_PROVIDER);
            loc.setAccuracy(Math.max(0.8f, mPublishedAccuracy));
            loc.setAltitude(mPublishedAlt);
            if (mPublishedIncludeMotion) {
                loc.setBearing(mPublishedBearing);
            }
            loc.setLatitude(mPublishedLat);
            loc.setLongitude(mPublishedLng);
            loc.setTime(System.currentTimeMillis());
            loc.setSpeed((float) mPublishedSpeed);
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                loc.setVerticalAccuracyMeters(1.5f);
                loc.setSpeedAccuracyMetersPerSecond(0.2f);
                if (mPublishedIncludeMotion) {
                    loc.setBearingAccuracyDegrees(2.0f);
                }
            }
            Bundle bundle = new Bundle();
            bundle.putInt("satellites", 12);
            loc.setExtras(bundle);

            mLocManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc);
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - setLocationGPS, reinitializing provider");
            removeTestProviderGPS();
            addTestProviderGPS();
        }
    }

    private void removeTestProviderNetwork() {
        try {
            if (mLocManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, false);
                mLocManager.removeTestProvider(LocationManager.NETWORK_PROVIDER);
            }
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - removeTestProviderNetwork");
        }
    }

    // 注意下面临时添加 @SuppressLint("wrongconstant") 以处理 addTestProvider 参数值的 lint 错误
    @SuppressLint("wrongconstant")
    private void addTestProviderNetwork() {
        try {
            // 注意，由于 android api 问题，下面的参数会提示错误(以下参数是通过相关API获取的真实NETWORK参数，不是随便写的)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mLocManager.addTestProvider(LocationManager.NETWORK_PROVIDER, true, false,
                        true, true, true, true,
                        true, ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_COARSE);
            } else {
                mLocManager.addTestProvider(LocationManager.NETWORK_PROVIDER, true, false,
                        true, true, true, true,
                        true, Criteria.POWER_LOW, Criteria.ACCURACY_COARSE);
            }
            if (!mLocManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
            }
        } catch (SecurityException e) {
            XLog.e("SERVICEGO: ERROR - addTestProviderNetwork");
        }
    }

    private void setLocationNetwork() {
        if (!mPolicyPublish) return;
        try {
            Location loc = new Location(LocationManager.NETWORK_PROVIDER);
            loc.setAccuracy(Math.max(2.0f, mPublishedAccuracy));
            loc.setAltitude(mPublishedAlt);
            if (mPublishedIncludeMotion) {
                loc.setBearing(mPublishedBearing);
            }
            loc.setLatitude(mPublishedLat);
            loc.setLongitude(mPublishedLng);
            loc.setTime(System.currentTimeMillis());
            loc.setSpeed((float) mPublishedSpeed);
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                loc.setVerticalAccuracyMeters(3.0f);
                loc.setSpeedAccuracyMetersPerSecond(0.5f);
                if (mPublishedIncludeMotion) {
                    loc.setBearingAccuracyDegrees(3.0f);
                }
            }
            mLocManager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, loc);
        } catch (Exception e) {
            XLog.e("SERVICEGO: ERROR - setLocationNetwork, reinitializing provider");
            removeTestProviderNetwork();
            addTestProviderNetwork();
        }
    }

    private void removeTestProviderFused() {
        try {
            if (mLocManager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.FUSED_PROVIDER, false);
                mLocManager.removeTestProvider(LocationManager.FUSED_PROVIDER);
            }
        } catch (Exception e) {
            // 系统 fused 通常不让 addTestProvider,失败正常 — 不写日志免刷屏
        }
    }

    @SuppressLint("wrongconstant")
    private void addTestProviderFused() {
        try {
            mLocManager.addTestProvider(LocationManager.FUSED_PROVIDER, false, true, false,
                    false, true, true, true, ProviderProperties.POWER_USAGE_HIGH, ProviderProperties.ACCURACY_FINE);
            if (!mLocManager.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.FUSED_PROVIDER, true);
            }
            XLog.i("SERVICEGO: FUSED_PROVIDER test provider added");
        } catch (Exception e) {
            // 系统 fused 通常不让 addTestProvider,失败正常 — GMS 路径仍可覆盖
        }
    }

    private void setLocationFused() {
        if (!mPolicyPublish) return;
        try {
            Location loc = new Location(LocationManager.FUSED_PROVIDER);
            loc.setAccuracy(Math.max(1.0f, mPublishedAccuracy));
            loc.setAltitude(mPublishedAlt);
            if (mPublishedIncludeMotion) {
                loc.setBearing(mPublishedBearing);
            }
            loc.setLatitude(mPublishedLat);
            loc.setLongitude(mPublishedLng);
            loc.setTime(System.currentTimeMillis());
            loc.setSpeed((float) mPublishedSpeed);
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

            mLocManager.setTestProviderLocation(LocationManager.FUSED_PROVIDER, loc);
        } catch (Exception e) {
            // fused test provider 没注上时这里会 fail,正常
        }
    }

    @SuppressLint("MissingPermission")
    private void setLocationFusedClient() {
        if (!mPolicyPublish || mFusedClient == null || !mFusedMockEnabled) return;
        try {
            Location loc = new Location("fused");
            loc.setAccuracy(Math.max(1.0f, mPublishedAccuracy));
            loc.setAltitude(mPublishedAlt);
            if (mPublishedIncludeMotion) {
                loc.setBearing(mPublishedBearing);
            }
            loc.setLatitude(mPublishedLat);
            loc.setLongitude(mPublishedLng);
            loc.setTime(System.currentTimeMillis());
            loc.setSpeed((float) mPublishedSpeed);
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            mFusedClient.setMockLocation(loc);
        } catch (Throwable t) {
            // 不打日志,避免每 33ms 刷屏
        }
    }

    public class NoteActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                if (mJoyStick != null && action.equals(SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW)) {
                    if (mJoyStick != null) mJoyStick.show();
                }

                if (mJoyStick != null && action.equals(SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE)) {
                    if (mJoyStick != null) mJoyStick.hide();
                }
            }
        }
    }

    public class ServiceGoBinder extends Binder {
        public void setPosition(double lng, double lat, double alt) {
            mLocHandler.removeMessages(HANDLER_MSG_ID);
            mCurLng = lng;
            mCurLat = lat;
            mCurAlt = alt;
            mLocHandler.sendEmptyMessage(HANDLER_MSG_ID);
            if (mJoyStick != null) mJoyStick.setCurrentPosition(mCurLng, mCurLat, mCurAlt);
        }

        public double getLongitude() { return mCurLng; }
        public double getLatitude() { return mCurLat; }
        public double getAltitude() { return mCurAlt; }
        public double getSpeedMps() { return mSpeed; }
        public float getBearingDegrees() { return mCurBea; }

        public boolean isPolicyPublishing() { return mPolicyPublish; }
        public double getPublishedLongitude() { return mPublishedLng; }
        public double getPublishedLatitude() { return mPublishedLat; }
        public double getPublishedAltitude() { return mPublishedAlt; }
        public double getPublishedSpeedMps() { return mPublishedSpeed; }
        public float getPublishedBearingDegrees() { return mPublishedBearing; }
        public float getPublishedAccuracyMeters() { return mPublishedAccuracy; }
        public String getPolicySummary() { return mPolicySummary; }

        public boolean isDeviceHeadingAvailable() { return mHeadingAvailable; }
        public float getDeviceHeadingDegrees() { return mDeviceHeadingDegrees; }
        public int getDeviceHeadingAccuracy() { return mDeviceHeadingAccuracy; }
        public String getHeadingSensorSource() { return mHeadingSensorSource; }

        public String getKinematicState() {
            LabKinematicsEngine.Frame f = mKinematicFrame;
            return f == null ? "UNAVAILABLE" : f.state;
        }
        public double getKinematicAccelerationMps2() {
            LabKinematicsEngine.Frame f = mKinematicFrame;
            return f == null ? 0.0 : f.accelerationMps2;
        }
        public double getKinematicJerkMps3() {
            LabKinematicsEngine.Frame f = mKinematicFrame;
            return f == null ? 0.0 : f.jerkMps3;
        }
        public double getKinematicTurnRateDegPerSec() {
            LabKinematicsEngine.Frame f = mKinematicFrame;
            return f == null ? 0.0 : f.turnRateDegPerSec;
        }
        public String getKinematicBearingSource() {
            LabKinematicsEngine.Frame f = mKinematicFrame;
            return f == null ? "UNAVAILABLE" : f.bearingSource;
        }
        public String getKinematicLedgerHash() {
            LabKinematicsEngine.Frame f = mKinematicFrame;
            return f == null ? "UNAVAILABLE" : f.chainHash;
        }
        public String getKinematicSummary() {
            LabKinematicsEngine.Frame f = mKinematicFrame;
            return f == null ? "UNAVAILABLE" : f.summary();
        }

        public long getScenarioStep() { return mScenarioStep; }
        public String getScenarioSummary() { return mScenarioSummary; }
        public long getScenarioVirtualTimeMillis() { return mScenarioVirtualTimeMillis; }
        public int getScenarioBatteryPercent() { return mScenarioBatteryPercent; }
        public float getScenarioHeadingDegrees() { return mScenarioHeadingDegrees; }
        public double getScenarioNoiseNorthMeters() { return mScenarioNoiseNorthMeters; }
        public double getScenarioNoiseEastMeters() { return mScenarioNoiseEastMeters; }
        public void resetScenarioStep() {
            mScenarioStep = 0L;
            recordProvenance("SCENARIO", "replay reset to tick 0 via Binder");
            refreshPublishedPolicy();
        }

        public boolean isRouteActive() { return mRouteActive; }
        public boolean isRoamActive() { return mRoamActive; }
        public boolean isMotionPaused() { return mMotionPaused; }
        public double getMotionMultiplier() { return mMotionMultiplier; }

        public double getRouteProgressFraction() {
            if (mRouteTotalDistance <= 0.0) return 0.0;
            return clamp(getCurrentPassProgressMeters() / mRouteTotalDistance, 0.0, 1.0);
        }

        public double getRouteRemainingMeters() {
            if (!mRouteActive || mRouteTotalDistance <= 0.0) return 0.0;
            return Math.max(0.0, mRouteTotalDistance - getCurrentPassProgressMeters());
        }

        public long getRouteEtaSeconds() {
            double effective = mRouteSpeedMps * mMotionMultiplier;
            if (!mRouteActive || mMotionPaused || effective <= 0.01) return -1L;
            return Math.round(getRouteRemainingMeters() / effective);
        }

        public int getRouteMode() { return mRouteMode; }

        public String getProvenanceSource() {
            return mProvenanceSource;
        }

        public String[] getProvenanceEvents() {
            synchronized (mProvenanceLock) {
                return mProvenanceEvents.toArray(new String[0]);
            }
        }

        public void clearProvenanceEvents() {
            synchronized (mProvenanceLock) {
                mProvenanceEvents.clear();
            }
            recordProvenance("PROVENANCE", "timeline cleared");
        }

        public boolean isTrackRecording() {
            synchronized (mTrackLock) {
                return mTrackRecording;
            }
        }

        public int getTrackPointCount() {
            synchronized (mTrackLock) {
                return mTrackLats.size();
            }
        }

        public double getTrackDistanceMeters() {
            synchronized (mTrackLock) {
                if (mTrackLats.size() < 2 || mTrackLats.size() != mTrackLngs.size()) {
                    return 0.0;
                }
                double total = 0.0;
                for (int i = 1; i < mTrackLats.size(); i++) {
                    total += distanceMeters(
                            mTrackLats.get(i - 1), mTrackLngs.get(i - 1),
                            mTrackLats.get(i), mTrackLngs.get(i));
                }
                return total;
            }
        }

        public long getTrackDurationSeconds() {
            synchronized (mTrackLock) {
                if (mTrackTimes.size() < 2) return 0L;
                long ms = mTrackTimes.get(mTrackTimes.size() - 1) - mTrackTimes.get(0);
                return Math.max(0L, ms / 1000L);
            }
        }

        public double getTrackAverageSpeedMps() {
            long seconds = getTrackDurationSeconds();
            if (seconds <= 0L) return 0.0;
            return getTrackDistanceMeters() / seconds;
        }

        public double[] getTrackLats() {
            synchronized (mTrackLock) {
                double[] out = new double[mTrackLats.size()];
                for (int i = 0; i < out.length; i++) out[i] = mTrackLats.get(i);
                return out;
            }
        }

        public double[] getTrackLngs() {
            synchronized (mTrackLock) {
                double[] out = new double[mTrackLngs.size()];
                for (int i = 0; i < out.length; i++) out[i] = mTrackLngs.get(i);
                return out;
            }
        }

        public long[] getTrackTimes() {
            synchronized (mTrackLock) {
                long[] out = new long[mTrackTimes.size()];
                for (int i = 0; i < out.length; i++) out[i] = mTrackTimes.get(i);
                return out;
            }
        }
    }
}


