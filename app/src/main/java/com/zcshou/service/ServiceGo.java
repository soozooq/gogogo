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

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GoGoGo:MockLocation");
            mWakeLock.setReferenceCounted(false);
            try {
                mWakeLock.acquire();
            } catch (Throwable ignored) {
            }
        }

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
            if (mHeadingSensor == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                mHeadingSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
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
                        mCurBea = heading;
                        mHeadingAvailable = true;
                    } catch (Throwable ignored) {
                    }
                }

                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
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

        if (intent != null && ACTION_MOTION_PAUSE.equals(intent.getAction())) {
            if (mRouteActive || mRoamActive) {
                mMotionPaused = true;
                mSpeed = 0.0;
            }
            return START_STICKY;
        }

        if (intent != null && ACTION_MOTION_RESUME.equals(intent.getAction())) {
            if (mRouteActive || mRoamActive) {
                mMotionPaused = false;
                mLastMotionElapsed = SystemClock.elapsedRealtime();
                mSpeed = mRouteActive ? mRouteSpeedMps * mMotionMultiplier
                        : mRoamSpeedMps * mMotionMultiplier;
            }
            return START_STICKY;
        }

        if (intent != null && ACTION_MOTION_SPEED.equals(intent.getAction())) {
            mMotionMultiplier = clamp(
                    intent.getDoubleExtra(EXTRA_MOTION_MULTIPLIER, 1.0), 0.25, 4.0);
            if (!mMotionPaused) {
                mSpeed = (mRouteActive ? mRouteSpeedMps : mRoamSpeedMps) * mMotionMultiplier;
            }
            return START_STICKY;
        }

        if (intent != null && ACTION_ROUTE_STOP.equals(intent.getAction())) {
            stopLabMotion();
            return START_STICKY;
        }

        if (intent != null && ACTION_ROAM_STOP.equals(intent.getAction())) {
            mRoamActive = false;
            mSpeed = 0.0;
            return START_STICKY;
        }

        if (intent != null && ACTION_ROUTE_START.equals(intent.getAction())) {
            double[] lats = intent.getDoubleArrayExtra(EXTRA_ROUTE_LATS);
            double[] lngs = intent.getDoubleArrayExtra(EXTRA_ROUTE_LNGS);
            if (lats != null && lngs != null && lats.length > 1 && lats.length == lngs.length) {
                mRouteLats = lats;
                mRouteLngs = lngs;
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
            persistCurrentLocation(state);
        } else {
            mCurLng = Double.longBitsToDouble(
                    state.getLong("lng_bits", Double.doubleToRawLongBits(DEFAULT_LNG)));
            mCurLat = Double.longBitsToDouble(
                    state.getLong("lat_bits", Double.doubleToRawLongBits(DEFAULT_LAT)));
            mCurAlt = Double.longBitsToDouble(
                    state.getLong("alt_bits", Double.doubleToRawLongBits(DEFAULT_ALT)));
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
        mSpeed = 0.0;
        mLastMotionElapsed = 0L;
        mMotionPaused = false;
        mMotionMultiplier = 1.0;
    }

    @Override
    public void onDestroy() {
        isStop = true;
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
                mFusedClient.setMockMode(false);
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

        mSpeed = mRouteSpeedMps;
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

            if (!mHeadingAvailable) {
                mCurBea = bearingDegrees(mCurLat, mCurLng, targetLat, targetLng);
            }

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
        mSpeed = mRoamSpeedMps;
        int guard = 0;
        while (metersToMove > 0.0001 && mRoamActive && guard++ < 10) {
            double remaining = distanceMeters(mCurLat, mCurLng, mRoamTargetLat, mRoamTargetLng);
            if (remaining < 0.5) {
                chooseNewRoamTarget();
                continue;
            }

            if (!mHeadingAvailable) {
                mCurBea = bearingDegrees(mCurLat, mCurLng, mRoamTargetLat, mRoamTargetLng);
            }

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
        try {
            // 尽可能模拟真实的 GPS 数据
            Location loc = new Location(LocationManager.GPS_PROVIDER);
            loc.setAccuracy(0.8f);    // 设定此位置的估计水平精度，以米为单位。
            loc.setAltitude(mCurAlt);                     // 设置高度，在 WGS 84 参考坐标系中的米
            if (mHeadingAvailable || mSpeed > 0.3) {
                loc.setBearing(mCurBea);
            }
            loc.setLatitude(mCurLat);                   // 纬度（度）
            loc.setLongitude(mCurLng);                  // 经度（度）
            loc.setTime(System.currentTimeMillis());    // 本地时间
            loc.setSpeed((float) mSpeed);
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                loc.setVerticalAccuracyMeters(1.5f);
                loc.setSpeedAccuracyMetersPerSecond(0.2f);
                if (mHeadingAvailable || mSpeed > 0.3) {
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
        try {
            // 尽可能模拟真实的 NETWORK 数据
            Location loc = new Location(LocationManager.NETWORK_PROVIDER);
            // 高德会融合网络定位；把 mock NETWORK 也保持为较高质量，减少真实网络定位抢回。
            loc.setAccuracy(2.0f);  // 设定此位置的估计水平精度，以米为单位。
            loc.setAltitude(mCurAlt);                     // 设置高度，在 WGS 84 参考坐标系中的米
            if (mHeadingAvailable || mSpeed > 0.3) {
                loc.setBearing(mCurBea);
            }
            loc.setLatitude(mCurLat);                   // 纬度（度）
            loc.setLongitude(mCurLng);                  // 经度（度）
            loc.setTime(System.currentTimeMillis());    // 本地时间
            loc.setSpeed((float) mSpeed);
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                loc.setVerticalAccuracyMeters(3.0f);
                loc.setSpeedAccuracyMetersPerSecond(0.5f);
                if (mHeadingAvailable || mSpeed > 0.3) {
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
        try {
            Location loc = new Location(LocationManager.FUSED_PROVIDER);
            loc.setAccuracy(Criteria.ACCURACY_FINE);
            loc.setAltitude(mCurAlt);
            if (mHeadingAvailable || mSpeed > 0.3) {
                loc.setBearing(mCurBea);
            }
            loc.setLatitude(mCurLat);
            loc.setLongitude(mCurLng);
            loc.setTime(System.currentTimeMillis());
            loc.setSpeed((float) mSpeed);
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

            mLocManager.setTestProviderLocation(LocationManager.FUSED_PROVIDER, loc);
        } catch (Exception e) {
            // fused test provider 没注上时这里会 fail,正常
        }
    }

    @SuppressLint("MissingPermission")
    private void setLocationFusedClient() {
        if (mFusedClient == null || !mFusedMockEnabled) return;
        try {
            Location loc = new Location("fused");
            loc.setAccuracy(Criteria.ACCURACY_FINE);
            loc.setAltitude(mCurAlt);
            if (mHeadingAvailable || mSpeed > 0.3) {
                loc.setBearing(mCurBea);
            }
            loc.setLatitude(mCurLat);
            loc.setLongitude(mCurLng);
            loc.setTime(System.currentTimeMillis());
            loc.setSpeed((float) mSpeed);
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
    }
}


