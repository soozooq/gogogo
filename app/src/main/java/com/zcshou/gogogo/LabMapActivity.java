package com.zcshou.gogogo;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.zcshou.service.ServiceGo;

import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.annotations.Polyline;
import org.maplibre.android.annotations.PolylineOptions;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.style.layers.RasterLayer;
import org.maplibre.android.style.sources.RasterSource;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

@SuppressWarnings("deprecation")
public class LabMapActivity extends AppCompatActivity {
    private static final int REQ_ROUTE_FILE = 2301;
    private static final int REQ_PMTILES_FILE = 2302;
    private static final int REQ_EXPORT_GPX = 2303;

    public static final String EXTRA_START_LATITUDE = "gogogo.map.startLatitude";
    public static final String EXTRA_START_LONGITUDE = "gogogo.map.startLongitude";
    private static final double DEFAULT_LAT = LabLocationPresets.defaultPreset().latitude;
    private static final double DEFAULT_LNG = LabLocationPresets.defaultPreset().longitude;
    private static final String STYLE_DEMO = "https://demotiles.maplibre.org/style.json";
    private static final String STYLE_LIBERTY = "https://tiles.openfreemap.org/styles/liberty";

    private MapView mapView;
    private MapLibreMap map;
    private Marker selectedMarker;
    private Marker liveMarker;
    private Polyline routeLine;
    private LatLng selectedPoint = new LatLng(DEFAULT_LAT, DEFAULT_LNG);
    private final List<RouteFileParser.RoutePoint> routePoints = new ArrayList<>();
    private final List<Marker> routeEditMarkers = new ArrayList<>();
    private boolean routeEditMode = false;
    private String currentStyle = STYLE_LIBERTY;

    private TextView statusView;
    private TextView routeView;
    private TextView liveView;
    private ProgressBar routeProgress;
    private EditText routeSpeedInput;
    private Spinner routeModeSpinner;
    private Spinner routeProfileSpinner;
    private EditText roamRadiusInput;
    private EditText roamSpeedInput;
    private Button followButton;
    private Button headingFollowButton;
    private Button routeEditButton;

    private ServiceGo.ServiceGoBinder serviceBinder;
    private boolean serviceBound = false;
    private boolean followMock = true;
    private boolean followHeading = false;
    private long lastFollowElapsed = 0L;

    private double[] pendingExportLats;
    private double[] pendingExportLngs;
    private long[] pendingExportTimes;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (service instanceof ServiceGo.ServiceGoBinder) {
                serviceBinder = (ServiceGo.ServiceGoBinder) service;
                serviceBound = true;
                refreshLiveState();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBinder = null;
            serviceBound = false;
        }
    };

    private final Runnable liveRefreshTask = new Runnable() {
        @Override
        public void run() {
            if (!serviceBound && ServiceGo.sRunning) {
                bindIfRunning();
            }
            refreshLiveState();
            uiHandler.postDelayed(this, 500L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // The home form can center the map, but cannot start mock publishing.
        Intent launch = getIntent();
        if (launch != null && launch.hasExtra(EXTRA_START_LATITUDE)
                && launch.hasExtra(EXTRA_START_LONGITUDE)) {
            double lat = launch.getDoubleExtra(EXTRA_START_LATITUDE, DEFAULT_LAT);
            double lng = launch.getDoubleExtra(EXTRA_START_LONGITUDE, DEFAULT_LNG);
            if (Double.isFinite(lat) && Double.isFinite(lng)
                    && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
                selectedPoint = new LatLng(lat, lng);
            }
        }
        MapLibre.getInstance(this);
        buildUi(savedInstanceState);
    }

    private void buildUi(Bundle savedInstanceState) {
        int pad = GoGoUi.dp(this, 12);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, GoGoUi.dp(this, 10), pad, pad);
        GoGoUi.applyScreenBackground(root);

        // Compact app bar: map first, tools second.
        LinearLayout appBar = GoGoUi.row(this);
        com.google.android.material.button.MaterialButton backButton =
                GoGoUi.textButton(this, "←", v -> finish());
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(
                GoGoUi.dp(this, 52), ViewGroup.LayoutParams.WRAP_CONTENT);
        appBar.addView(backButton, backLp);

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        TextView title = GoGoUi.sectionTitle(this, "地图实验室");
        title.setTextSize(19);
        title.setPadding(0, 0, 0, 0);
        titleBlock.addView(title, GoGoUi.matchWrap());
        titleBlock.addView(
                GoGoUi.muted(this, "位置 · 路线 · 漫游 · 回放"),
                GoGoUi.matchWrap());
        appBar.addView(titleBlock, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        com.google.android.material.button.MaterialButton toolsButton =
                GoGoUi.secondaryButton(this, "工具", v -> showMapToolsPanel());
        LinearLayout.LayoutParams toolsLp = new LinearLayout.LayoutParams(
                GoGoUi.dp(this, 78), ViewGroup.LayoutParams.WRAP_CONTENT);
        appBar.addView(toolsButton, toolsLp);
        root.addView(appBar, GoGoUi.matchWrap());

        statusView = GoGoUi.muted(
                this,
                "点地图选择位置 · 工具菜单可快捷跳转国家 / 城市");
        statusView.setPadding(GoGoUi.dp(this, 4), GoGoUi.dp(this, 6),
                GoGoUi.dp(this, 4), GoGoUi.dp(this, 6));
        root.addView(statusView, GoGoUi.matchWrap());

        mapView = new MapView(this);
        mapView.onCreate(savedInstanceState);
        LinearLayout.LayoutParams mapLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        mapLp.setMargins(0, GoGoUi.dp(this, 4), 0, GoGoUi.dp(this, 10));
        root.addView(mapView, mapLp);

        // Bottom command deck.
        com.google.android.material.card.MaterialCardView controlCard = GoGoUi.card(this);
        LinearLayout control = GoGoUi.cardContent(this);
        controlCard.addView(control);
        control.setPadding(
                GoGoUi.dp(this, 14),
                GoGoUi.dp(this, 12),
                GoGoUi.dp(this, 14),
                GoGoUi.dp(this, 12));

        liveView = GoGoUi.muted(this, "Service 未运行");
        liveView.setTextIsSelectable(true);
        control.addView(liveView, GoGoUi.matchWrap());

        routeProgress = new ProgressBar(
                this, null, android.R.attr.progressBarStyleHorizontal);
        routeProgress.setMax(1000);
        routeProgress.setProgress(0);
        LinearLayout.LayoutParams progressLp = GoGoUi.matchWrap();
        progressLp.setMargins(0, GoGoUi.dp(this, 8), 0, 0);
        control.addView(routeProgress, progressLp);

        routeView = GoGoUi.muted(this, "路线：未导入");
        routeView.setPadding(0, GoGoUi.dp(this, 4), 0, 0);
        control.addView(routeView, GoGoUi.matchWrap());

        control.addView(GoGoUi.gap(this, 10));

        // Keep the primary simulation action full width; two-column controls
        // wrap labels on compact displays instead of squeezing four into one row.
        control.addView(GoGoUi.primaryButton(this, "模拟这里",
                v -> simulateSelected()), GoGoUi.matchWrap());
        control.addView(GoGoUi.gap(this, 6));

        LinearLayout routeActions = GoGoUi.row(this);
        routeActions.addView(
                GoGoUi.secondaryButton(this, "路线设置", v -> showRoutePanel()),
                GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, routeActions, 8);
        routeActions.addView(
                GoGoUi.secondaryButton(this, "漫游设置", v -> showRoamPanel()),
                GoGoUi.weighted());
        control.addView(routeActions, GoGoUi.matchWrap());

        LinearLayout followRow = GoGoUi.row(this);
        followButton = GoGoUi.textButton(this, "跟随·开", v -> toggleFollow());
        followRow.addView(followButton, GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, followRow, 8);
        headingFollowButton =
                GoGoUi.textButton(this, "朝向·关", v -> toggleHeadingFollow());
        followRow.addView(headingFollowButton, GoGoUi.weighted());
        control.addView(followRow, GoGoUi.matchWrap());

        LinearLayout moreRow = GoGoUi.row(this);
        moreRow.addView(
                GoGoUi.textButton(this, "运动工具", v -> showMotionPanel()),
                GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, moreRow, 8);
        moreRow.addView(
                GoGoUi.textButton(this, "实验与诊断", v ->
                        startActivity(new Intent(this, LabHubActivity.class))),
                GoGoUi.weighted());
        control.addView(moreRow, GoGoUi.matchWrap());

        root.addView(controlCard, GoGoUi.matchWrap());
        setContentView(root);

        mapView.getMapAsync(mapLibreMap -> {
            map = mapLibreMap;
            map.addOnMapClickListener(point -> {
                selectPoint(point, true);
                return true;
            });
            map.addOnMapLongClickListener(point -> {
                if (!routeEditMode) return false;
                addManualRoutePoint(point);
                return true;
            });
            map.setCameraPosition(new CameraPosition.Builder()
                    .target(selectedPoint)
                    .zoom(14.5)
                    .build());
            switchStyle(currentStyle);
        });
    }

    private void showMapToolsPanel() {
        final String[] items = new String[]{
                "收藏当前位置",
                "打开收藏夹",
                "打开历史记录",
                "地图归北",
                "OpenFreeMap Liberty",
                "MapLibre Demo",
                "导入 PMTiles",
                "离线地图管理",
                "🌏 快捷跳转国家 / 城市",
                "查看位置来源链",
                "清空位置来源链"
        };

        new AlertDialog.Builder(this)
                .setTitle("地图与位置工具")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            promptFavorite();
                            break;
                        case 1:
                            showSavedPoints(true);
                            break;
                        case 2:
                            showSavedPoints(false);
                            break;
                        case 3:
                            resetMapBearing();
                            break;
                        case 4:
                            switchStyle(STYLE_LIBERTY);
                            break;
                        case 5:
                            switchStyle(STYLE_DEMO);
                            break;
                        case 6:
                            pickPmtilesFile();
                            break;
                        case 7:
                            showOfflineMaps();
                            break;
                        case 8:
                            showLocationPresets();
                            break;
                        case 9:
                            showProvenanceTimeline();
                            break;
                        case 10:
                            clearProvenanceTimeline();
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void showRoutePanel() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = GoGoUi.dp(this, 8);
        content.setPadding(p, p, p, p);

        content.addView(GoGoUi.muted(
                this,
                "导入 GPX/KML，或开启编辑后长按地图添加路点。"),
                GoGoUi.matchWrap());
        content.addView(GoGoUi.gap(this, 10));

        content.addView(GoGoUi.muted(this, "路线速度（m/s）"), GoGoUi.matchWrap());
        routeSpeedInput = numberField(
                routeSpeedInput == null ? "1.4" : routeSpeedInput.getText().toString(),
                120);
        content.addView(routeSpeedInput, GoGoUi.matchWrap());

        content.addView(GoGoUi.gap(this, 10));
        content.addView(GoGoUi.muted(this, "运动预设"), GoGoUi.matchWrap());

        int previousProfile = routeProfileSpinner == null
                ? ServiceGo.MOTION_PROFILE_WALK
                : routeProfileSpinner.getSelectedItemPosition();
        routeProfileSpinner = new Spinner(this);
        ArrayAdapter<String> profileAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"步行 WALK", "骑行 BIKE", "驾车 CAR"});
        routeProfileSpinner.setAdapter(profileAdapter);
        routeProfileSpinner.setSelection(Math.max(
                ServiceGo.MOTION_PROFILE_WALK,
                Math.min(ServiceGo.MOTION_PROFILE_CAR, previousProfile)));
        content.addView(routeProfileSpinner, GoGoUi.matchWrap());

        content.addView(GoGoUi.gap(this, 10));
        content.addView(GoGoUi.muted(this, "路线模式"), GoGoUi.matchWrap());

        int previousMode = routeModeSpinner == null
                ? 0 : routeModeSpinner.getSelectedItemPosition();
        routeModeSpinner = new Spinner(this);
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"单次", "循环", "往返"});
        routeModeSpinner.setAdapter(modeAdapter);
        routeModeSpinner.setSelection(previousMode);
        content.addView(routeModeSpinner, GoGoUi.matchWrap());

        content.addView(GoGoUi.gap(this, 12));

        LinearLayout row1 = GoGoUi.row(this);
        row1.addView(
                GoGoUi.secondaryButton(this, "导入 GPX/KML", v -> pickRouteFile()),
                GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, row1, 8);
        row1.addView(
                GoGoUi.primaryButton(this, "开始路线", v -> startRoute()),
                GoGoUi.weighted());
        content.addView(row1, GoGoUi.matchWrap());

        content.addView(GoGoUi.gap(this, 8));
        content.addView(
                GoGoUi.secondaryButton(
                        this,
                        "⏱ 按 GPX 原始时间回放",
                        v -> startTimedReplay()),
                GoGoUi.matchWrap());

        LinearLayout row2 = GoGoUi.row(this);
        routeEditButton = GoGoUi.secondaryButton(
                this,
                routeEditMode ? "编辑·开" : "编辑·关",
                v -> toggleRouteEditor());
        row2.addView(routeEditButton, GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, row2, 8);
        row2.addView(
                GoGoUi.secondaryButton(this, "撤销路点", v -> undoRoutePoint()),
                GoGoUi.weighted());
        content.addView(row2, GoGoUi.matchWrap());

        LinearLayout row3 = GoGoUi.row(this);
        row3.addView(
                GoGoUi.secondaryButton(this, "路线信息", v -> showRouteInfo()),
                GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, row3, 8);
        row3.addView(
                GoGoUi.dangerButton(this, "停止 / 清空", v -> {
                    stopRoute();
                    clearRoute();
                }),
                GoGoUi.weighted());
        content.addView(row3, GoGoUi.matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("路线控制")
                .setView(content)
                .setNegativeButton("完成", null)
                .show();
    }

    private void showRoamPanel() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int p = GoGoUi.dp(this, 8);
        content.setPadding(p, p, p, p);

        content.addView(GoGoUi.muted(
                this,
                "以当前选中位置为中心，在设定半径内随机漫游。"),
                GoGoUi.matchWrap());
        content.addView(GoGoUi.gap(this, 10));

        content.addView(GoGoUi.muted(this, "漫游半径（m）"), GoGoUi.matchWrap());
        roamRadiusInput = numberField(
                roamRadiusInput == null ? "100" : roamRadiusInput.getText().toString(),
                120);
        content.addView(roamRadiusInput, GoGoUi.matchWrap());

        content.addView(GoGoUi.gap(this, 10));
        content.addView(GoGoUi.muted(this, "速度（m/s）"), GoGoUi.matchWrap());
        roamSpeedInput = numberField(
                roamSpeedInput == null ? "1.4" : roamSpeedInput.getText().toString(),
                120);
        content.addView(roamSpeedInput, GoGoUi.matchWrap());

        content.addView(GoGoUi.gap(this, 12));

        LinearLayout actions = GoGoUi.row(this);
        actions.addView(
                GoGoUi.primaryButton(this, "开始漫游", v -> startRoam()),
                GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, actions, 8);
        actions.addView(
                GoGoUi.dangerButton(this, "停止", v -> stopRoam()),
                GoGoUi.weighted());
        content.addView(actions, GoGoUi.matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("随机漫游")
                .setView(content)
                .setNegativeButton("完成", null)
                .show();
    }

    private void showMotionPanel() {
        final String[] items = new String[]{
                "暂停运动",
                "继续运动",
                "速度 0.5×",
                "速度 1×",
                "速度 2×",
                "速度 4×",
                "开始录轨迹",
                "停止录轨迹",
                "导出轨迹 GPX",
                "清空轨迹"
        };

        new AlertDialog.Builder(this)
                .setTitle("运动与轨迹")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            sendMotionAction(ServiceGo.ACTION_MOTION_PAUSE);
                            break;
                        case 1:
                            sendMotionAction(ServiceGo.ACTION_MOTION_RESUME);
                            break;
                        case 2:
                            setMotionMultiplier(0.5);
                            break;
                        case 3:
                            setMotionMultiplier(1.0);
                            break;
                        case 4:
                            setMotionMultiplier(2.0);
                            break;
                        case 5:
                            setMotionMultiplier(4.0);
                            break;
                        case 6:
                            startTrackRecording();
                            break;
                        case 7:
                            sendTrackAction(ServiceGo.ACTION_RECORD_STOP);
                            break;
                        case 8:
                            exportTrack();
                            break;
                        case 9:
                            sendTrackAction(ServiceGo.ACTION_RECORD_CLEAR);
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(13);
        return view;
    }

    private EditText numberField(String value, int widthDp) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setSelectAllOnFocus(true);
        GoGoUi.styleInput(input);
        input.setLayoutParams(new LinearLayout.LayoutParams(
                dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT));
        return input;
    }

    private HorizontalScrollView buttonRow(Button... buttons) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
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

    private Button button(String text, android.view.View.OnClickListener listener) {
        return GoGoUi.secondaryButton(this, text, listener);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showLocationPresets() {
        new AlertDialog.Builder(this)
                .setTitle("地图跳转 · 国家 / 城市（WGS-84）")
                .setItems(LabLocationPresets.labels(), (dialog, index) -> {
                    LabLocationPresets.Preset p = LabLocationPresets.get(index);
                    LatLng chosen = new LatLng(p.latitude, p.longitude);
                    selectPoint(chosen, false);
                    if (map != null) {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(chosen, 12.5));
                    }
                    Toast.makeText(this, "已选：" + p.name
                            + " · 点击「模拟这里」才会启动", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void selectPoint(LatLng point, boolean moveCamera) {
        selectedPoint = point;
        if (map != null) {
            if (selectedMarker == null) {
                selectedMarker = map.addMarker(new MarkerOptions()
                        .position(point)
                        .title("模拟位置 / 漫游中心"));
            } else {
                selectedMarker.setPosition(point);
                map.updateMarker(selectedMarker);
            }
            if (moveCamera) {
                map.animateCamera(CameraUpdateFactory.newLatLng(point));
            }
        }
        statusView.setText(String.format(Locale.US,
                "已选择：%.6f, %.6f", point.getLongitude(), point.getLatitude()));
    }

    private void updateLiveMarker(double lat, double lng, float bearingDegrees) {
        if (map == null) return;
        LatLng point = new LatLng(lat, lng);

        if (liveMarker == null) {
            liveMarker = map.addMarker(new MarkerOptions()
                    .position(point)
                    .title("当前 Mock 位置"));
        } else {
            liveMarker.setPosition(point);
            map.updateMarker(liveMarker);
        }

        long now = android.os.SystemClock.elapsedRealtime();
        if (followMock && now - lastFollowElapsed >= 250L) {
            lastFollowElapsed = now;
            CameraPosition current = map.getCameraPosition();
            CameraPosition next = new CameraPosition.Builder()
                    .target(point)
                    .zoom(current.zoom)
                    .tilt(current.tilt)
                    .bearing(followHeading ? bearingDegrees : current.bearing)
                    .build();
            map.moveCamera(CameraUpdateFactory.newCameraPosition(next));
        }
    }

    private void toggleFollow() {
        followMock = !followMock;
        followButton.setText(followMock ? "🎯 跟随：开" : "🎯 跟随：关");
        if (followMock && serviceBinder != null) {
            updateLiveMarker(
                    serviceBinder.getLatitude(),
                    serviceBinder.getLongitude(),
                    serviceBinder.getPublishedBearingDegrees());
        }
    }

    private void toggleHeadingFollow() {
        followHeading = !followHeading;
        headingFollowButton.setText(followHeading ? "🧭 地图朝向：开" : "🧭 地图朝向：关");
        if (followHeading) {
            followMock = true;
            followButton.setText("🎯 跟随：开");
        }
    }

    private void resetMapBearing() {
        followHeading = false;
        if (headingFollowButton != null) {
            headingFollowButton.setText("🧭 地图朝向：关");
        }
        if (map == null) return;
        CameraPosition current = map.getCameraPosition();
        CameraPosition north = new CameraPosition.Builder()
                .target(current.target)
                .zoom(current.zoom)
                .tilt(current.tilt)
                .bearing(0.0)
                .build();
        map.animateCamera(CameraUpdateFactory.newCameraPosition(north));
    }

    private void refreshLiveState() {
        ServiceGo.ServiceGoBinder binder = serviceBinder;
        if (binder == null) {
            liveView.setText("实时状态：Service 未连接"
                    + (ServiceGo.sRunning ? "（正在重连）" : "（未运行）"));
            routeProgress.setProgress(0);
            return;
        }

        try {
            double lat = binder.getLatitude();
            double lng = binder.getLongitude();
            double speed = binder.getSpeedMps();
            double multiplier = binder.getMotionMultiplier();
            boolean paused = binder.isMotionPaused();
            boolean route = binder.isRouteActive();
            boolean roam = binder.isRoamActive();
            boolean replay = binder.isReplayActive();
            boolean recording = binder.isTrackRecording();
            int trackPoints = binder.getTrackPointCount();

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.US,
                    "Raw %.6f, %.6f · %.2f m/s · %.1f° · 来源 %s",
                    lng, lat, speed, binder.getBearingDegrees(),
                    binder.getProvenanceSource()));

            if (binder.isPolicyPublishing()) {
                sb.append(String.format(Locale.US,
                        "\nPublished %.6f, %.6f · acc %.0f m · %s",
                        binder.getPublishedLongitude(),
                        binder.getPublishedLatitude(),
                        binder.getPublishedAccuracyMeters(),
                        binder.getPolicySummary()));
            } else {
                sb.append("\nPublished PAUSED · ").append(binder.getPolicySummary());
            }

            sb.append(String.format(Locale.US,
                    "\nScenario Tick %d · batt %d%% · head %.1f° · %s",
                    binder.getScenarioStep(),
                    binder.getScenarioBatteryPercent(),
                    binder.getScenarioHeadingDegrees(),
                    binder.getScenarioSummary()));

            sb.append(String.format(Locale.US,
                    "\n🧭 Device %s%.1f° · %s · acc=%d | Published %.1f°",
                    binder.isDeviceHeadingAvailable() ? "" : "(无) ",
                    binder.getDeviceHeadingDegrees(),
                    binder.getHeadingSensorSource(),
                    binder.getDeviceHeadingAccuracy(),
                    binder.getPublishedBearingDegrees()));

            String ledger = binder.getKinematicLedgerHash();
            if (ledger != null && ledger.length() > 16) {
                ledger = ledger.substring(0, 16);
            }
            sb.append(String.format(Locale.US,
                    "\n⚙ %s · a=%+.2f m/s² · jerk=%+.2f · turn=%+.1f°/s · %s · h=%s",
                    binder.getKinematicState(),
                    binder.getKinematicAccelerationMps2(),
                    binder.getKinematicJerkMps3(),
                    binder.getKinematicTurnRateDegPerSec(),
                    binder.getKinematicBearingSource(),
                    ledger));

            if (replay) {
                double progress = binder.getReplayProgressFraction();
                long playheadMs = binder.getReplayPlayheadMs();
                long durationMs = binder.getReplayDurationMs();
                routeProgress.setProgress((int) Math.round(progress * 1000.0));
                sb.append(String.format(Locale.US,
                        "\n⏱ GPX Replay %.1f%% · %s / %s · %.2f× · backend=%s",
                        progress * 100.0,
                        formatDuration(Math.round(playheadMs / 1000.0)),
                        formatDuration(Math.round(durationMs / 1000.0)),
                        multiplier,
                        binder.getSimulationBackendSummary()));
            } else if (route) {
                double progress = binder.getRouteProgressFraction();
                double remain = binder.getRouteRemainingMeters();
                long eta = binder.getRouteEtaSeconds();
                routeProgress.setProgress((int) Math.round(progress * 1000.0));
                sb.append(String.format(Locale.US,
                        "\n路线 %.1f%% · 剩余 %.0f m · ETA %s · %s · %.2f×",
                        progress * 100.0,
                        remain,
                        eta < 0 ? "--" : formatDuration(eta),
                        paused ? "暂停" : "运行",
                        multiplier));
                sb.append(String.format(Locale.US,
                        "\n🏎 Physics %s/%s · %.2f→%.2f m/s · backend=%s",
                        binder.getRouteMotionProfileName(),
                        binder.getRoutePhysicsPhase(),
                        binder.getRoutePhysicsSpeedMps(),
                        binder.getRoutePhysicsTargetSpeedMps(),
                        binder.getSimulationBackendSummary()));
            } else if (roam) {
                routeProgress.setProgress(0);
                sb.append(String.format(Locale.US,
                        "\n随机漫游 · %s · %.2f×",
                        paused ? "暂停" : "运行", multiplier));
            } else {
                routeProgress.setProgress(0);
            }

            double trackDistance = binder.getTrackDistanceMeters();
            long trackDuration = binder.getTrackDurationSeconds();
            double trackAverage = binder.getTrackAverageSpeedMps();

            sb.append("\n轨迹录制：")
                    .append(recording ? "● 录制中" : "停止")
                    .append(" · ")
                    .append(trackPoints)
                    .append(" 点");
            if (trackPoints > 0) {
                sb.append(String.format(Locale.US,
                        " · %s · %s · 均速 %.2f m/s",
                        formatDistance(trackDistance),
                        formatDuration(trackDuration),
                        trackAverage));
            }

            liveView.setText(sb.toString());
            updateLiveMarker(lat, lng, binder.getPublishedBearingDegrees());
        } catch (Throwable t) {
            liveView.setText("实时状态读取失败：" + t.getClass().getSimpleName());
        }
    }

    private static String formatDistance(double meters) {
        if (meters >= 1000.0) {
            return String.format(Locale.US, "%.2f km", meters / 1000.0);
        }
        return String.format(Locale.US, "%.0f m", meters);
    }

    private static String formatDuration(long seconds) {
        if (seconds < 0) return "--";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    private void bindIfRunning() {
        if (serviceBound || !ServiceGo.sRunning) return;
        try {
            bindService(new Intent(this, ServiceGo.class), serviceConnection, 0);
        } catch (Throwable ignored) {
        }
    }

    private void unbindFromService() {
        if (!serviceBound) return;
        try {
            unbindService(serviceConnection);
        } catch (Throwable ignored) {
        }
        serviceBound = false;
        serviceBinder = null;
    }

    private void simulateSelected() {
        if (selectedPoint == null) return;

        Intent intent = new Intent(this, ServiceGo.class);
        intent.putExtra(MainActivity.LNG_MSG_ID, selectedPoint.getLongitude());
        intent.putExtra(MainActivity.LAT_MSG_ID, selectedPoint.getLatitude());
        intent.putExtra(MainActivity.ALT_MSG_ID, 55.0);

        if (startLocationService(intent)) {
            LabStore.addHistory(this, selectedPoint.getLongitude(), selectedPoint.getLatitude());
            Toast.makeText(this, "已把模拟位置切到地图选点", Toast.LENGTH_SHORT).show();
        }
    }

    private void promptFavorite() {
        if (selectedPoint == null) return;
        EditText input = new EditText(this);
        input.setHint("例如：妙瓦底酒店 / 测试点 A");

        new AlertDialog.Builder(this)
                .setTitle("收藏这个位置")
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = "收藏位置";
                    LabStore.addFavorite(this, name,
                            selectedPoint.getLongitude(), selectedPoint.getLatitude());
                    Toast.makeText(this, "已收藏：" + name, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showSavedPoints(boolean favorites) {
        List<LabStore.SavedPoint> items = favorites
                ? LabStore.getFavorites(this)
                : LabStore.getHistory(this);

        if (items.isEmpty()) {
            Toast.makeText(this, favorites ? "收藏夹还是空的" : "还没有模拟历史", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[items.size()];
        for (int i = 0; i < items.size(); i++) labels[i] = items.get(i).displayText();

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(favorites ? "收藏夹" : "最近模拟")
                .setItems(labels, (d, which) -> {
                    LabStore.SavedPoint p = items.get(which);
                    LatLng point = new LatLng(p.latitude, p.longitude);
                    selectPoint(point, true);
                    if (map != null) {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 16.0));
                    }
                })
                .setNegativeButton("关闭", null);

        if (!favorites) {
            builder.setNeutralButton("清空历史", (d, w) -> {
                LabStore.clearHistory(this);
                Toast.makeText(this, "历史已清空", Toast.LENGTH_SHORT).show();
            });
        }
        builder.show();
    }

    private void pickRouteFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/gpx+xml",
                "application/vnd.google-earth.kml+xml",
                "application/xml",
                "text/xml",
                "text/plain"
        });
        startActivityForResult(intent, REQ_ROUTE_FILE);
    }

    private void pickPmtilesFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_PMTILES_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        if (requestCode == REQ_EXPORT_GPX) {
            writePendingGpx(uri);
            return;
        }

        if (requestCode == REQ_PMTILES_FILE) {
            importPmtiles(uri);
            return;
        }

        if (requestCode != REQ_ROUTE_FILE) return;

        String name = queryDisplayName(uri);
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            List<RouteFileParser.RoutePoint> parsed = RouteFileParser.parse(name, input);
            if (parsed.isEmpty()) {
                Toast.makeText(this, "文件里没有找到可用的 GPX/KML 路径点", Toast.LENGTH_LONG).show();
                return;
            }
            routePoints.clear();
            routePoints.addAll(parsed);
            routeEditMode = false;
            if (routeEditButton != null) routeEditButton.setText("✏️ 编辑路线：关");
            drawRoute();
            long timelineMs = RouteFileParser.timelineDurationMillis(routePoints);
            routeView.setText("路线：" + (name == null ? "导入文件" : name)
                    + " · " + routePoints.size() + " 个点"
                    + (timelineMs > 0L
                            ? " · 时间轴 " + formatDuration(Math.round(timelineMs / 1000.0))
                            : " · 无时间轴"));
            Toast.makeText(
                    this,
                    "路线导入成功：" + routePoints.size() + " 个点"
                            + (timelineMs > 0L ? " · 可按 GPX 时间回放" : ""),
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "路线解析失败：" + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private void switchStyle(String styleUrl) {
        if (map == null) return;
        currentStyle = styleUrl;
        selectedMarker = null;
        liveMarker = null;
        routeLine = null;
        routeEditMarkers.clear();
        map.setStyle(styleUrl, style -> {
            selectPoint(selectedPoint, false);
            if (!routePoints.isEmpty()) drawRoute();
            if (serviceBinder != null) {
                updateLiveMarker(
                        serviceBinder.getLatitude(),
                        serviceBinder.getLongitude(),
                        serviceBinder.getPublishedBearingDegrees());
            }
            statusView.setText((STYLE_LIBERTY.equals(styleUrl) ? "OpenFreeMap Liberty" : "MapLibre Demo")
                    + " · " + String.format(Locale.US, "%.6f, %.6f",
                    selectedPoint.getLongitude(), selectedPoint.getLatitude()));
        });
    }

    private void importPmtiles(Uri uri) {
        File dir = getOfflineDir();
        if (dir == null) {
            Toast.makeText(this, "无法访问应用离线目录", Toast.LENGTH_LONG).show();
            return;
        }

        String displayName = queryDisplayName(uri);
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = "offline.pmtiles";
        }
        displayName = sanitizeFileName(displayName);
        if (!displayName.toLowerCase(Locale.US).endsWith(".pmtiles")) {
            displayName += ".pmtiles";
        }

        File out = uniqueFile(dir, displayName);
        try (InputStream input = getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(out)) {
            if (input == null) throw new IllegalStateException("input");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) output.write(buffer, 0, read);
            }
            output.flush();
            attachRasterPmtiles(out);
        } catch (Exception e) {
            Toast.makeText(this,
                    "PMTiles 导入失败：" + e.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private File getOfflineDir() {
        File base = getExternalFilesDir(null);
        if (base == null) return null;
        File dir = new File(base, "offline");
        if (!dir.exists() && !dir.mkdirs()) return null;
        return dir;
    }

    private void showOfflineMaps() {
        File dir = getOfflineDir();
        if (dir == null) {
            Toast.makeText(this, "离线地图目录不可用", Toast.LENGTH_SHORT).show();
            return;
        }

        File[] files = dir.listFiles((d, name) ->
                name != null && name.toLowerCase(Locale.US).endsWith(".pmtiles"));
        if (files == null || files.length == 0) {
            Toast.makeText(this, "还没有导入 PMTiles", Toast.LENGTH_SHORT).show();
            return;
        }

        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        String[] labels = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            labels[i] = files[i].getName() + "\n"
                    + String.format(Locale.US, "%.1f MB", files[i].length() / 1048576.0);
        }

        new AlertDialog.Builder(this)
                .setTitle("离线 PMTiles")
                .setItems(labels, (d, which) -> attachRasterPmtiles(files[which]))
                .setNeutralButton("删除全部", (d, w) -> {
                    int deleted = 0;
                    for (File file : files) {
                        if (file.delete()) deleted++;
                    }
                    Toast.makeText(this, "已删除 " + deleted + " 个离线地图", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private static File uniqueFile(File dir, String name) {
        File first = new File(dir, name);
        if (!first.exists()) return first;

        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        int index = 2;
        File candidate;
        do {
            candidate = new File(dir, base + "_" + index + ext);
            index++;
        } while (candidate.exists());
        return candidate;
    }

    private void attachRasterPmtiles(File file) {
        if (map == null || file == null || !file.exists()) return;
        map.getStyle(style -> {
            try {
                if (style.getLayer("lab-pmtiles-layer") != null) {
                    style.removeLayer("lab-pmtiles-layer");
                }
                if (style.getSource("lab-pmtiles-source") != null) {
                    style.removeSource("lab-pmtiles-source");
                }

                String uri = "pmtiles://file://" + file.getAbsolutePath();
                RasterSource source = new RasterSource("lab-pmtiles-source", uri, 256);
                style.addSource(source);
                style.addLayer(new RasterLayer("lab-pmtiles-layer", "lab-pmtiles-source"));
                Toast.makeText(this,
                        "离线 Raster PMTiles 已挂载 😈\n" + file.getName(),
                        Toast.LENGTH_LONG).show();
            } catch (Throwable t) {
                Toast.makeText(this,
                        "文件已保存，但当前 PMTiles 可能不是 Raster 类型",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        }
        return uri.getLastPathSegment();
    }

    private void drawRoute() {
        drawRoute(true);
    }

    private void drawRoute(boolean recenter) {
        if (map == null || routePoints.isEmpty()) {
            removeRouteLineAndMarkers();
            updateRouteView();
            return;
        }

        if (routeLine != null) {
            try {
                map.removePolyline(routeLine);
            } catch (Throwable ignored) {
            }
            routeLine = null;
        }

        if (routePoints.size() >= 2) {
            PolylineOptions options = new PolylineOptions()
                    .color(Color.rgb(30, 136, 229))
                    .width(7f);

            for (RouteFileParser.RoutePoint p : routePoints) {
                options.add(new LatLng(p.latitude, p.longitude));
            }
            routeLine = map.addPolyline(options);
        }

        renderRouteEditMarkers();
        updateRouteView();

        if (recenter) {
            RouteFileParser.RoutePoint first = routePoints.get(0);
            LatLng firstPoint = new LatLng(first.latitude, first.longitude);
            selectPoint(firstPoint, false);
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(firstPoint, 14.0));
        }
    }

    private void toggleRouteEditor() {
        routeEditMode = !routeEditMode;
        if (routeEditButton != null) {
            routeEditButton.setText(routeEditMode ? "✏️ 编辑路线：开" : "✏️ 编辑路线：关");
        }
        renderRouteEditMarkers();
        Toast.makeText(this,
                routeEditMode
                        ? "路线编辑已开启：长按地图追加路点"
                        : "路线编辑已关闭",
                Toast.LENGTH_SHORT).show();
    }

    private void addManualRoutePoint(LatLng point) {
        if (point == null) return;
        if (routePoints.size() >= RouteFileParser.MAX_POINTS) {
            Toast.makeText(this, "路线点已经到上限", Toast.LENGTH_SHORT).show();
            return;
        }

        routePoints.add(new RouteFileParser.RoutePoint(
                point.getLongitude(), point.getLatitude(), 55.0));
        selectedPoint = point;
        drawRoute(false);

        Toast.makeText(this,
                "已添加路点 #" + routePoints.size(),
                Toast.LENGTH_SHORT).show();
    }

    private void undoRoutePoint() {
        if (routePoints.isEmpty()) {
            Toast.makeText(this, "没有可撤销的路点", Toast.LENGTH_SHORT).show();
            return;
        }
        routePoints.remove(routePoints.size() - 1);
        drawRoute(false);
        Toast.makeText(this, "已撤销最后一个路点", Toast.LENGTH_SHORT).show();
    }

    private void renderRouteEditMarkers() {
        if (map == null) return;

        for (Marker marker : routeEditMarkers) {
            try {
                map.removeMarker(marker);
            } catch (Throwable ignored) {
            }
        }
        routeEditMarkers.clear();

        if (!routeEditMode || routePoints.size() > 200) return;

        for (int i = 0; i < routePoints.size(); i++) {
            RouteFileParser.RoutePoint p = routePoints.get(i);
            try {
                Marker marker = map.addMarker(new MarkerOptions()
                        .position(new LatLng(p.latitude, p.longitude))
                        .title("路点 #" + (i + 1)));
                routeEditMarkers.add(marker);
            } catch (Throwable ignored) {
            }
        }
    }

    private void removeRouteLineAndMarkers() {
        if (map != null && routeLine != null) {
            try {
                map.removePolyline(routeLine);
            } catch (Throwable ignored) {
            }
        }
        routeLine = null;

        if (map != null) {
            for (Marker marker : routeEditMarkers) {
                try {
                    map.removeMarker(marker);
                } catch (Throwable ignored) {
                }
            }
        }
        routeEditMarkers.clear();
    }

    private void updateRouteView() {
        if (routePoints.isEmpty()) {
            routeView.setText("路线：未导入 / 未编辑");
            return;
        }
        long timelineMs = RouteFileParser.timelineDurationMillis(routePoints);
        routeView.setText(String.format(Locale.US,
                "路线：%d 个点 · %s%s%s",
                routePoints.size(),
                formatDistance(routeDistanceMeters()),
                timelineMs > 0L
                        ? " · 时间轴 " + formatDuration(Math.round(timelineMs / 1000.0))
                        : " · 无时间轴",
                routeEditMode ? " · 编辑中" : ""));
    }

    private double routeDistanceMeters() {
        if (routePoints.size() < 2) return 0.0;
        double total = 0.0;
        for (int i = 1; i < routePoints.size(); i++) {
            RouteFileParser.RoutePoint a = routePoints.get(i - 1);
            RouteFileParser.RoutePoint b = routePoints.get(i);
            total += distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude);
        }
        return total;
    }

    private void showRouteInfo() {
        if (routePoints.isEmpty()) {
            Toast.makeText(this, "当前没有路线", Toast.LENGTH_SHORT).show();
            return;
        }

        double distance = routeDistanceMeters();
        double speed = clamp(parseNumber(routeSpeedInput, 1.4), 0.2, 60.0);
        long eta = speed > 0.01 ? Math.round(distance / speed) : -1L;

        new AlertDialog.Builder(this)
                .setTitle("📏 当前路线")
                .setMessage("路点：" + routePoints.size()
                        + "\n总距离：" + formatDistance(distance)
                        + "\n当前设定速度：" + String.format(Locale.US, "%.2f m/s", speed)
                        + "\n运动预设：" + motionProfileName(selectedMotionProfile())
                        + "\n预计单程时间：" + (eta < 0 ? "--" : formatDuration(eta))
                        + "\nGPX 时间轴：" + (RouteFileParser.hasUsableTimeline(routePoints)
                                ? formatDuration(Math.round(
                                        RouteFileParser.timelineDurationMillis(routePoints) / 1000.0))
                                : "无")
                        + "\n模式：" + routeModeName(routeModeSpinner.getSelectedItemPosition()))
                .setPositiveButton("关闭", null)
                .show();
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

    private void startRoute() {
        if (routePoints.size() < 2) {
            Toast.makeText(this, "先导入至少两个路径点", Toast.LENGTH_SHORT).show();
            return;
        }

        double speed = clamp(parseNumber(routeSpeedInput, 1.4), 0.2, 60.0);
        int mode = routeModeSpinner.getSelectedItemPosition();
        mode = Math.max(ServiceGo.ROUTE_MODE_ONCE, Math.min(ServiceGo.ROUTE_MODE_PINGPONG, mode));
        int motionProfile = selectedMotionProfile();

        double[] lats = new double[routePoints.size()];
        double[] lngs = new double[routePoints.size()];
        for (int i = 0; i < routePoints.size(); i++) {
            RouteFileParser.RoutePoint p = routePoints.get(i);
            lats[i] = p.latitude;
            lngs[i] = p.longitude;
        }

        Intent intent = new Intent(this, ServiceGo.class);
        intent.setAction(ServiceGo.ACTION_ROUTE_START);
        intent.putExtra(ServiceGo.EXTRA_ROUTE_LATS, lats);
        intent.putExtra(ServiceGo.EXTRA_ROUTE_LNGS, lngs);
        intent.putExtra(ServiceGo.EXTRA_ROUTE_SPEED_MPS, speed);
        intent.putExtra(ServiceGo.EXTRA_ROUTE_MODE, mode);
        intent.putExtra(ServiceGo.EXTRA_ROUTE_MOTION_PROFILE, motionProfile);

        if (startLocationService(intent)) {
            RouteFileParser.RoutePoint p = routePoints.get(0);
            LabStore.addHistory(this, p.longitude, p.latitude);
            Toast.makeText(this,
                    String.format(Locale.US, "路线开始：%.1f m/s · %s · %s",
                            speed, motionProfileName(motionProfile), routeModeName(mode)),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void startTimedReplay() {
        if (!RouteFileParser.hasUsableTimeline(routePoints)) {
            Toast.makeText(
                    this,
                    "这条路线没有完整递增的 GPX <time> 时间轴，改用“开始路线”即可。",
                    Toast.LENGTH_LONG).show();
            return;
        }

        int count = routePoints.size();
        double[] lats = new double[count];
        double[] lngs = new double[count];
        double[] alts = new double[count];
        long[] times = new long[count];

        for (int i = 0; i < count; i++) {
            RouteFileParser.RoutePoint p = routePoints.get(i);
            lats[i] = p.latitude;
            lngs[i] = p.longitude;
            alts[i] = p.altitude;
            times[i] = p.timestampMillis;
        }

        Intent intent = new Intent(this, ServiceGo.class);
        intent.setAction(ServiceGo.ACTION_REPLAY_START);
        intent.putExtra(ServiceGo.EXTRA_ROUTE_LATS, lats);
        intent.putExtra(ServiceGo.EXTRA_ROUTE_LNGS, lngs);
        intent.putExtra(ServiceGo.EXTRA_REPLAY_ALTS, alts);
        intent.putExtra(ServiceGo.EXTRA_REPLAY_TIMES_MS, times);

        if (startLocationService(intent)) {
            long durationMs = RouteFileParser.timelineDurationMillis(routePoints);
            Toast.makeText(
                    this,
                    "GPX 时间回放开始 · "
                            + formatDuration(Math.round(durationMs / 1000.0)),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRoute() {
        Intent intent = new Intent(this, ServiceGo.class);
        intent.setAction(ServiceGo.ACTION_ROUTE_STOP);
        try {
            startService(intent);
            Toast.makeText(this, "路线已停止，位置停在当前位置", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "停止路线失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearRoute() {
        routePoints.clear();
        removeRouteLineAndMarkers();
        updateRouteView();
    }

    private void startRoam() {
        if (selectedPoint == null) return;

        double radius = clamp(parseNumber(roamRadiusInput, 100.0), 5.0, 5000.0);
        double speed = clamp(parseNumber(roamSpeedInput, 1.4), 0.2, 30.0);

        Intent intent = new Intent(this, ServiceGo.class);
        intent.setAction(ServiceGo.ACTION_ROAM_START);
        intent.putExtra(ServiceGo.EXTRA_ROAM_CENTER_LAT, selectedPoint.getLatitude());
        intent.putExtra(ServiceGo.EXTRA_ROAM_CENTER_LNG, selectedPoint.getLongitude());
        intent.putExtra(ServiceGo.EXTRA_ROAM_RADIUS_M, radius);
        intent.putExtra(ServiceGo.EXTRA_ROAM_SPEED_MPS, speed);

        if (startLocationService(intent)) {
            LabStore.addHistory(this, selectedPoint.getLongitude(), selectedPoint.getLatitude());
            Toast.makeText(this,
                    String.format(Locale.US, "随机漫游：半径 %.0f m · %.1f m/s", radius, speed),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRoam() {
        Intent intent = new Intent(this, ServiceGo.class);
        intent.setAction(ServiceGo.ACTION_ROAM_STOP);
        try {
            startService(intent);
            Toast.makeText(this, "随机漫游已停止", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "停止漫游失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendMotionAction(String action) {
        Intent intent = new Intent(this, ServiceGo.class);
        intent.setAction(action);
        try {
            startService(intent);
            Toast.makeText(this,
                    ServiceGo.ACTION_MOTION_PAUSE.equals(action) ? "运动已暂停" : "运动继续",
                    Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "运动控制失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void setMotionMultiplier(double multiplier) {
        Intent intent = new Intent(this, ServiceGo.class);
        intent.setAction(ServiceGo.ACTION_MOTION_SPEED);
        intent.putExtra(ServiceGo.EXTRA_MOTION_MULTIPLIER, multiplier);
        try {
            startService(intent);
            Toast.makeText(this,
                    String.format(Locale.US, "运动倍速：%.2f×", multiplier),
                    Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "倍速切换失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void showProvenanceTimeline() {
        ServiceGo.ServiceGoBinder binder = serviceBinder;
        if (binder == null) {
            Toast.makeText(this, "Service 还没有连接", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] events = binder.getProvenanceEvents();
        StringBuilder text = new StringBuilder();
        text.append("当前来源：").append(binder.getProvenanceSource())
                .append("\n事件数：").append(events.length)
                .append("\n\n");

        if (events.length == 0) {
            text.append("还没有 provenance 事件。");
        } else {
            // Show newest first so the most relevant state is immediately visible.
            for (int i = events.length - 1; i >= 0; i--) {
                text.append(events[i]);
                if (i > 0) text.append("\n");
            }
        }

        TextView view = new TextView(this);
        int pad = dp(16);
        view.setPadding(pad, pad, pad, pad);
        view.setTextIsSelectable(true);
        view.setText(text.toString());

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(view);

        new AlertDialog.Builder(this)
                .setTitle("🧬 GoGoGo Data Provenance")
                .setView(scroll)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void clearProvenanceTimeline() {
        ServiceGo.ServiceGoBinder binder = serviceBinder;
        if (binder == null) {
            Toast.makeText(this, "Service 还没有连接", Toast.LENGTH_SHORT).show();
            return;
        }
        binder.clearProvenanceEvents();
        Toast.makeText(this, "来源时间线已清空", Toast.LENGTH_SHORT).show();
    }

    private void startTrackRecording() {
        if (!ServiceGo.sRunning) {
            simulateSelected();
            uiHandler.postDelayed(() -> sendTrackAction(ServiceGo.ACTION_RECORD_START), 200L);
        } else {
            sendTrackAction(ServiceGo.ACTION_RECORD_START);
        }
    }

    private void sendTrackAction(String action) {
        Intent intent = new Intent(this, ServiceGo.class);
        intent.setAction(action);
        try {
            if (ServiceGo.ACTION_RECORD_START.equals(action)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ServiceGo.sRunning) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
                Toast.makeText(this, "轨迹录制开始", Toast.LENGTH_SHORT).show();
            } else {
                startService(intent);
                Toast.makeText(this,
                        ServiceGo.ACTION_RECORD_CLEAR.equals(action) ? "轨迹已清空" : "轨迹录制停止",
                        Toast.LENGTH_SHORT).show();
            }
            uiHandler.postDelayed(this::bindIfRunning, 150L);
        } catch (Throwable t) {
            Toast.makeText(this, "轨迹控制失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportTrack() {
        ServiceGo.ServiceGoBinder binder = serviceBinder;
        if (binder == null) {
            Toast.makeText(this, "Service 还没有连接", Toast.LENGTH_SHORT).show();
            return;
        }

        pendingExportLats = binder.getTrackLats();
        pendingExportLngs = binder.getTrackLngs();
        pendingExportTimes = binder.getTrackTimes();

        if (pendingExportLats.length == 0
                || pendingExportLats.length != pendingExportLngs.length
                || pendingExportLats.length != pendingExportTimes.length) {
            Toast.makeText(this, "还没有可导出的轨迹", Toast.LENGTH_SHORT).show();
            return;
        }

        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/gpx+xml");
        intent.putExtra(Intent.EXTRA_TITLE, "GoGoGo_track_" + stamp + ".gpx");
        startActivityForResult(intent, REQ_EXPORT_GPX);
    }

    private void writePendingGpx(Uri uri) {
        if (pendingExportLats == null || pendingExportLngs == null || pendingExportTimes == null) {
            Toast.makeText(this, "导出数据已经失效", Toast.LENGTH_SHORT).show();
            return;
        }

        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));

        try (OutputStream output = getContentResolver().openOutputStream(uri);
             BufferedWriter writer = output == null ? null :
                     new BufferedWriter(new OutputStreamWriter(output, java.nio.charset.StandardCharsets.UTF_8))) {
            if (writer == null) throw new IllegalStateException("output");

            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<gpx version=\"1.1\" creator=\"GoGoGo Lab 4\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
            writer.write("  <trk><name>GoGoGo Recorded Track</name><trkseg>\n");
            for (int i = 0; i < pendingExportLats.length; i++) {
                writer.write(String.format(Locale.US,
                        "    <trkpt lat=\"%.8f\" lon=\"%.8f\"><time>%s</time></trkpt>\n",
                        pendingExportLats[i],
                        pendingExportLngs[i],
                        iso.format(new Date(pendingExportTimes[i]))));
            }
            writer.write("  </trkseg></trk>\n</gpx>\n");
            writer.flush();

            Toast.makeText(this,
                    "GPX 已导出：" + pendingExportLats.length + " 个点",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this,
                    "GPX 导出失败：" + e.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        } finally {
            pendingExportLats = null;
            pendingExportLngs = null;
            pendingExportTimes = null;
        }
    }

    private boolean startLocationService(Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            uiHandler.postDelayed(this::bindIfRunning, 150L);
            return true;
        } catch (Throwable t) {
            Toast.makeText(this,
                    "启动模拟失败：" + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private static double parseNumber(EditText input, double fallback) {
        try {
            return Double.parseDouble(input.getText().toString().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int selectedMotionProfile() {
        if (routeProfileSpinner == null) return ServiceGo.MOTION_PROFILE_WALK;
        return Math.max(
                ServiceGo.MOTION_PROFILE_WALK,
                Math.min(
                        ServiceGo.MOTION_PROFILE_CAR,
                        routeProfileSpinner.getSelectedItemPosition()));
    }

    private static String motionProfileName(int profile) {
        if (profile == ServiceGo.MOTION_PROFILE_CAR) return "驾车 CAR";
        if (profile == ServiceGo.MOTION_PROFILE_BIKE) return "骑行 BIKE";
        return "步行 WALK";
    }

    private static String routeModeName(int mode) {
        if (mode == ServiceGo.ROUTE_MODE_LOOP) return "循环";
        if (mode == ServiceGo.ROUTE_MODE_PINGPONG) return "往返";
        return "单次";
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStart();
        uiHandler.removeCallbacks(liveRefreshTask);
        uiHandler.post(liveRefreshTask);
        bindIfRunning();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        if (mapView != null) mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        uiHandler.removeCallbacks(liveRefreshTask);
        unbindFromService();
        if (mapView != null) mapView.onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacksAndMessages(null);
        unbindFromService();
        if (mapView != null) mapView.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }
}
