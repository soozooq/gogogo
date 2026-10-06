package com.zcshou.gogogo;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("deprecation")
public class LabMapActivity extends AppCompatActivity {
    private static final int REQ_ROUTE_FILE = 2301;
    private static final int REQ_PMTILES_FILE = 2302;
    private static final double DEFAULT_LAT = 16.68914;
    private static final double DEFAULT_LNG = 98.50895;
    private static final String STYLE_DEMO = "https://demotiles.maplibre.org/style.json";
    private static final String STYLE_LIBERTY = "https://tiles.openfreemap.org/styles/liberty";
    private String currentStyle = STYLE_LIBERTY;

    private MapView mapView;
    private MapLibreMap map;
    private Marker selectedMarker;
    private Polyline routeLine;
    private LatLng selectedPoint = new LatLng(DEFAULT_LAT, DEFAULT_LNG);
    private final List<RouteFileParser.RoutePoint> routePoints = new ArrayList<>();

    private TextView statusView;
    private TextView routeView;
    private EditText routeSpeedInput;
    private Spinner routeModeSpinner;
    private EditText roamRadiusInput;
    private EditText roamSpeedInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapLibre.getInstance(this);
        buildUi(savedInstanceState);
    }

    private void buildUi(Bundle savedInstanceState) {
        int pad = dp(10);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("🧪 GoGoGo Lab 3 · MapLibre");
        title.setTextSize(21);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        statusView = new TextView(this);
        statusView.setText("点地图选位置。默认：妙瓦底 98.50895, 16.68914");
        statusView.setTextSize(14);
        root.addView(statusView, matchWrap());

        mapView = new MapView(this);
        mapView.onCreate(savedInstanceState);
        LinearLayout.LayoutParams mapLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        mapLp.setMargins(0, dp(6), 0, dp(6));
        root.addView(mapView, mapLp);

        root.addView(buttonRow(
                button("🌐 Liberty", v -> switchStyle(STYLE_LIBERTY)),
                button("🧱 Demo", v -> switchStyle(STYLE_DEMO)),
                button("📦 导入离线 PMTiles", v -> pickPmtilesFile())
        ));

        root.addView(buttonRow(
                button("📍 模拟这里", v -> simulateSelected()),
                button("❤️ 收藏", v -> promptFavorite()),
                button("⭐ 收藏夹", v -> showSavedPoints(true)),
                button("🕘 历史", v -> showSavedPoints(false))
        ));

        root.addView(buttonRow(
                button("📂 导入 GPX/KML", v -> pickRouteFile()),
                button("▶ 路线", v -> startRoute()),
                button("⏹ 路线", v -> stopRoute()),
                button("🗑 路线", v -> clearRoute())
        ));

        LinearLayout routeSettings = new LinearLayout(this);
        routeSettings.setOrientation(LinearLayout.HORIZONTAL);
        routeSettings.setGravity(Gravity.CENTER_VERTICAL);
        routeSettings.addView(label("路线速度 m/s "));
        routeSpeedInput = numberField("1.4", 88);
        routeSettings.addView(routeSpeedInput);

        routeSettings.addView(label("  模式 "));
        routeModeSpinner = new Spinner(this);
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"单次", "循环", "往返"});
        routeModeSpinner.setAdapter(modeAdapter);
        routeSettings.addView(routeModeSpinner,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(routeSettings, matchWrap());

        routeView = new TextView(this);
        routeView.setText("路线：未导入");
        routeView.setTextSize(13);
        routeView.setPadding(0, dp(2), 0, dp(4));
        root.addView(routeView, matchWrap());

        LinearLayout roamSettings = new LinearLayout(this);
        roamSettings.setOrientation(LinearLayout.HORIZONTAL);
        roamSettings.setGravity(Gravity.CENTER_VERTICAL);
        roamSettings.addView(label("漫游半径 m "));
        roamRadiusInput = numberField("100", 88);
        roamSettings.addView(roamRadiusInput);
        roamSettings.addView(label("  速度 m/s "));
        roamSpeedInput = numberField("1.4", 88);
        roamSettings.addView(roamSpeedInput);
        root.addView(roamSettings, matchWrap());

        root.addView(buttonRow(
                button("🎲 随机漫游", v -> startRoam()),
                button("⏹ 停止漫游", v -> stopRoam()),
                button("📟 实验仪表盘", v ->
                        startActivity(new Intent(this, LabDiagnosticsActivity.class)))
        ));

        root.addView(buttonRow(
                button("⏸ 暂停运动", v -> sendMotionAction(ServiceGo.ACTION_MOTION_PAUSE)),
                button("▶ 继续运动", v -> sendMotionAction(ServiceGo.ACTION_MOTION_RESUME)),
                button("0.5×", v -> setMotionMultiplier(0.5)),
                button("1×", v -> setMotionMultiplier(1.0)),
                button("2×", v -> setMotionMultiplier(2.0)),
                button("4×", v -> setMotionMultiplier(4.0))
        ));

        Button back = button("← 返回定位测试面板", v -> finish());
        root.addView(back, matchWrap());

        setContentView(root);

        mapView.getMapAsync(mapLibreMap -> {
            map = mapLibreMap;
            map.addOnMapClickListener(point -> {
                selectPoint(point, true);
                return true;
            });
            map.setCameraPosition(new CameraPosition.Builder()
                    .target(selectedPoint)
                    .zoom(14.5)
                    .build());
            switchStyle(currentStyle);
        });
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
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setSelectAllOnFocus(true);
        input.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), ViewGroup.LayoutParams.WRAP_CONTENT));
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

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
            drawRoute();
            routeView.setText("路线：" + (name == null ? "导入文件" : name)
                    + " · " + routePoints.size() + " 个点");
            Toast.makeText(this, "路线导入成功：" + routePoints.size() + " 个点", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "路线解析失败：" + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private void switchStyle(String styleUrl) {
        if (map == null) return;
        currentStyle = styleUrl;
        selectedMarker = null;
        routeLine = null;
        map.setStyle(styleUrl, style -> {
            selectPoint(selectedPoint, false);
            if (!routePoints.isEmpty()) drawRoute();
            statusView.setText((STYLE_LIBERTY.equals(styleUrl) ? "OpenFreeMap Liberty" : "MapLibre Demo")
                    + " · " + String.format(Locale.US, "%.6f, %.6f",
                    selectedPoint.getLongitude(), selectedPoint.getLatitude()));
        });
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

    private void pickPmtilesFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQ_PMTILES_FILE);
    }

    private void importPmtiles(Uri uri) {
        File base = getExternalFilesDir(null);
        if (base == null) {
            Toast.makeText(this, "无法访问应用离线目录", Toast.LENGTH_LONG).show();
            return;
        }

        File dir = new File(base, "offline");
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, "无法创建离线地图目录", Toast.LENGTH_LONG).show();
            return;
        }

        File out = new File(dir, "imported-raster.pmtiles");
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
                        "离线 Raster PMTiles 已挂载 😈\n文件：" + file.getName(),
                        Toast.LENGTH_LONG).show();
            } catch (Throwable t) {
                Toast.makeText(this,
                        "PMTiles 已保存，但当前文件可能不是 Raster 类型",
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
        if (map == null || routePoints.isEmpty()) return;

        if (routeLine != null) {
            try {
                map.removePolyline(routeLine);
            } catch (Throwable ignored) {
            }
            routeLine = null;
        }

        PolylineOptions options = new PolylineOptions()
                .color(Color.rgb(30, 136, 229))
                .width(7f);

        for (RouteFileParser.RoutePoint p : routePoints) {
            options.add(new LatLng(p.latitude, p.longitude));
        }
        routeLine = map.addPolyline(options);

        RouteFileParser.RoutePoint first = routePoints.get(0);
        LatLng firstPoint = new LatLng(first.latitude, first.longitude);
        selectPoint(firstPoint, false);
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(firstPoint, 14.0));
    }

    private void startRoute() {
        if (routePoints.size() < 2) {
            Toast.makeText(this, "先导入至少两个路径点", Toast.LENGTH_SHORT).show();
            return;
        }

        double speed = parseNumber(routeSpeedInput, 1.4);
        speed = clamp(speed, 0.2, 60.0);
        int mode = routeModeSpinner.getSelectedItemPosition();
        mode = Math.max(ServiceGo.ROUTE_MODE_ONCE, Math.min(ServiceGo.ROUTE_MODE_PINGPONG, mode));

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

        if (startLocationService(intent)) {
            RouteFileParser.RoutePoint p = routePoints.get(0);
            LabStore.addHistory(this, p.longitude, p.latitude);
            Toast.makeText(this,
                    String.format(Locale.US, "路线开始：%.1f m/s · %s",
                            speed, routeModeName(mode)),
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
        if (map != null && routeLine != null) {
            try {
                map.removePolyline(routeLine);
            } catch (Throwable ignored) {
            }
        }
        routeLine = null;
        routeView.setText("路线：未导入");
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

    private boolean startLocationService(Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
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

    private static String routeModeName(int mode) {
        if (mode == ServiceGo.ROUTE_MODE_LOOP) return "循环";
        if (mode == ServiceGo.ROUTE_MODE_PINGPONG) return "往返";
        return "单次";
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStart();
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
        if (mapView != null) mapView.onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
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
