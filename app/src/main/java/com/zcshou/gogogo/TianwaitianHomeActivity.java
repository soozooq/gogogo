package com.zcshou.gogogo;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;

import java.util.Locale;

/**
 * Actual Tianwaitian LAUNCHER home. Uses OpenFreeMap/MapLibre rather than
 * the Tencent map requiring a valid key (CI preview ships a DUMMY key).
 *
 * This page is presentation/selection ONLY. All mock, VPN, time-zone and
 * Shizuku controls remain in SimpleMockActivity, accessed with one tap.
 */
public final class TianwaitianHomeActivity extends AppCompatActivity {
    private MaterialButton previewButton;
    public static final String EXTRA_PREFILL_LONGITUDE = "twt.home.prefill.longitude";
    public static final String EXTRA_PREFILL_LATITUDE = "twt.home.prefill.latitude";
    public static final String EXTRA_PREFILL_LABEL = "twt.home.prefill.label";

    private static final String MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty";
    private static final String STATE_LONGITUDE = "twt.home.target.longitude";
    private static final String STATE_LATITUDE = "twt.home.target.latitude";
    private static final String STATE_LABEL = "twt.home.target.label";

    private MapView mapView;
    private MapLibreMap map;
    private Marker selectedMarker;
    private boolean styleReady;
    private LatLng selectedPoint = new LatLng(
            LabLocationPresets.defaultPreset().latitude,
            LabLocationPresets.defaultPreset().longitude);
    private String selectedLabel = LabLocationPresets.defaultPreset().name;
    private TextView targetView;
    private TextView labelView;

    private final ActivityResultLauncher<Intent> placePicker =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK ||
                                result.getData() == null) return;
                        Intent data = result.getData();
                        if (!data.hasExtra(LabPlacePickerActivity.EXTRA_LONGITUDE)
                                || !data.hasExtra(LabPlacePickerActivity.EXTRA_LATITUDE)) return;
                        acceptCoordinates(
                                data.getDoubleExtra(LabPlacePickerActivity.EXTRA_LONGITUDE, Double.NaN),
                                data.getDoubleExtra(LabPlacePickerActivity.EXTRA_LATITUDE, Double.NaN),
                                data.getStringExtra(LabPlacePickerActivity.EXTRA_LABEL), true);
                    });

    private final ActivityResultLauncher<Intent> unifiedSearch =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK
                                || result.getData() == null) return;
                        Intent data = result.getData();
                        if (!data.hasExtra(LabPlacePickerActivity.EXTRA_LONGITUDE)
                                || !data.hasExtra(LabPlacePickerActivity.EXTRA_LATITUDE)) return;
                        acceptCoordinates(
                                data.getDoubleExtra(LabPlacePickerActivity.EXTRA_LONGITUDE,
                                        Double.NaN),
                                data.getDoubleExtra(LabPlacePickerActivity.EXTRA_LATITUDE,
                                        Double.NaN),
                                data.getStringExtra(LabPlacePickerActivity.EXTRA_LABEL), true);
                    });

    private final ActivityResultLauncher<Intent> mapPicker =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK ||
                                result.getData() == null) return;
                        Intent data = result.getData();
                        if (!data.hasExtra(LabMapActivity.EXTRA_RESULT_LONGITUDE)
                                || !data.hasExtra(LabMapActivity.EXTRA_RESULT_LATITUDE)) return;
                        acceptCoordinates(
                                data.getDoubleExtra(LabMapActivity.EXTRA_RESULT_LONGITUDE, Double.NaN),
                                data.getDoubleExtra(LabMapActivity.EXTRA_RESULT_LATITUDE, Double.NaN),
                                data.getStringExtra(LabMapActivity.EXTRA_RESULT_LABEL), true);
                    });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            try {
                LabHomeCoordinates.Point restored = LabHomeCoordinates.parse(
                        Double.toString(state.getDouble(STATE_LONGITUDE, selectedPoint.getLongitude())),
                        Double.toString(state.getDouble(STATE_LATITUDE, selectedPoint.getLatitude())));
                selectedPoint = new LatLng(restored.latitude, restored.longitude);
                selectedLabel = state.getString(STATE_LABEL, selectedLabel);
            } catch (IllegalArgumentException ignored) {
                // Invalid restored coordinates must not prevent a fresh home screen.
            }
        }
        // Saved UI state is only a fallback. The shared selected-target store is
        // authoritative because the console or a picker may have been used
        // while this map Activity was stopped/recreated by Android.
        TianwaitianSelectedTarget.Target selected = TianwaitianSelectedTarget.read(this);
        if (selected != null) {
            selectedPoint = new LatLng(selected.latitude, selected.longitude);
            selectedLabel = selected.label;
        }
        MapLibre.getInstance(this);
        buildUi(state);
    }

    private int dp(int value) {
        return GoGoUi.dp(this, value);
    }

    private void buildUi(Bundle state) {
        FrameLayout root = new FrameLayout(this);
        GoGoUi.applyScreenBackground(root);

        mapView = new MapView(this);
        mapView.onCreate(state);
        root.addView(mapView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Top overlay is branded; the live map remains visible between panels.
        MaterialCardView brandCard = GoGoUi.card(this);
        brandCard.setRadius(dp(20));
        brandCard.setCardElevation(dp(5));
        brandCard.setStrokeWidth(0);
        brandCard.setCardBackgroundColor(GoGoUi.color(this, R.color.twt_appbar_bg));

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setBackgroundResource(R.drawable.twt_launch_header_bg);
        brand.setPadding(dp(13), dp(9), dp(13), dp(9));
        ImageView emblem = new ImageView(this);
        emblem.setImageResource(R.drawable.twt_constellation_motif);
        emblem.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        emblem.setContentDescription(null);
        brand.addView(emblem, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wordLayout = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wordLayout.leftMargin = dp(10);
        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        words.addView(title);
        TextView tagline = new TextView(this);
        tagline.setText("探索坐标之外  ·  星座视界");
        tagline.setTextColor(0xFFE0E8FF);
        tagline.setTextSize(11);
        words.addView(tagline);
        brand.addView(words, wordLayout);
        MaterialButton tools = GoGoUi.textButton(this, "⋯", v -> openMapTools());
        tools.setTextColor(Color.WHITE);
        tools.setTextSize(22);
        tools.setSingleLine(true);
        tools.setContentDescription("打开地图工具");
        brand.addView(tools, new LinearLayout.LayoutParams(
                dp(44), ViewGroup.LayoutParams.WRAP_CONTENT));
        brandCard.addView(brand);

        // Compact map search surface sits UNDER the original constellation
        // brand, not in the bottom control deck. No network call happens here.
        LinearLayout topStack = new LinearLayout(this);
        topStack.setOrientation(LinearLayout.VERTICAL);
        topStack.addView(brandCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialCardView searchCard = GoGoUi.card(this);
        searchCard.setRadius(dp(16));
        searchCard.setCardElevation(dp(4));
        searchCard.setContentDescription("搜索城市、地址或经纬度，打开统一搜索页面");
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(dp(16), dp(6), dp(12), dp(6));
        TextView searchIcon = new TextView(this);
        searchIcon.setText("⌕");
        searchIcon.setTextSize(28);
        searchIcon.setTextColor(GoGoUi.color(this, R.color.twt_nav_selected));
        searchRow.addView(searchIcon, new LinearLayout.LayoutParams(dp(30),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView searchHint = new TextView(this);
        searchHint.setText("搜索城市、街道、建筑或经纬度");
        searchHint.setSingleLine(true);
        searchHint.setEllipsize(android.text.TextUtils.TruncateAt.END);
        searchHint.setTextSize(14);
        searchHint.setTextColor(GoGoUi.color(this, R.color.gogogo_text_muted));
        searchRow.addView(searchHint, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(22);
        arrow.setTextColor(GoGoUi.color(this, R.color.twt_nav_selected));
        searchRow.addView(arrow);
        searchCard.addView(searchRow);
        searchCard.setClickable(true);
        searchCard.setFocusable(true);
        searchCard.setOnClickListener(v -> unifiedSearch.launch(
                new Intent(this, TianwaitianSearchActivity.class)));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.topMargin = dp(9);
        topStack.addView(searchCard, searchLp);

        FrameLayout.LayoutParams top = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        top.setMargins(dp(12), dp(10), dp(12), 0);
        root.addView(topStack, top);

        // Bottom glass-like command deck, using semantic DayNight surface colors.
        MaterialCardView deck = GoGoUi.card(this);
        deck.setRadius(dp(19));
        deck.setCardElevation(dp(8));
        LinearLayout content = GoGoUi.cardContent(this);
        content.setPadding(dp(13), dp(10), dp(13), dp(10));

        labelView = GoGoUi.sectionTitle(this, selectedLabel);
        labelView.setSingleLine(true);
        labelView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labelView.setTextSize(16);
        content.addView(labelView, GoGoUi.matchWrap());

        targetView = GoGoUi.muted(this, "");
        targetView.setTextIsSelectable(true);
        targetView.setPadding(0, dp(3), 0, 0);
        content.addView(targetView, GoGoUi.matchWrap());
        updateTargetText();

        content.addView(GoGoUi.gap(this, 5));
        TextView note = GoGoUi.muted(this,
                "点按地图选点 · 不会自动启动模拟");
        note.setTextSize(11);
        content.addView(note, GoGoUi.matchWrap());
        content.addView(GoGoUi.gap(this, 8));

        // The native bottom navigation owns Diagnostics, Audit and Settings.
        // Keep only the two location-specific actions in this lightweight deck.
        MaterialButton console = GoGoUi.primaryButton(this,
                "模拟控制", v -> openSimulationConsole());
        MaterialButton places = GoGoUi.secondaryButton(this,
                "全球地点库", v -> placePicker.launch(
                        new Intent(this, LabPlacePickerActivity.class)));
        boolean stack = LabResponsiveUiPolicy.stackTianwaitianLauncherActions(
                getResources().getConfiguration().screenWidthDp,
                getResources().getConfiguration().fontScale);
        if (stack) {
            content.addView(console, GoGoUi.matchWrap());
            content.addView(GoGoUi.gap(this, 5));
            content.addView(places, GoGoUi.matchWrap());
        } else {
            LinearLayout locationActions = GoGoUi.row(this);
            locationActions.addView(console, GoGoUi.weighted());
            GoGoUi.addHorizontalGap(this, locationActions, 8);
            locationActions.addView(places, GoGoUi.weighted());
            content.addView(locationActions, GoGoUi.matchWrap());
        }
        previewButton = GoGoUi.textButton(this, "将选点带入微信 / 抖音预览",
                v -> TianwaitianContainerPreview.chooseApp(this,
                        selectedPoint.getLongitude(), selectedPoint.getLatitude()));
        previewButton.setVisibility(TianwaitianContainerPreview.isAvailable(this) ? View.VISIBLE : View.GONE);
        content.addView(previewButton, GoGoUi.matchWrap());

        ScrollView deckScroll = new ScrollView(this);
        deckScroll.setFillViewport(false);
        deckScroll.setVerticalScrollBarEnabled(false);
        deckScroll.addView(content);
        int screenHeight = getResources().getConfiguration().screenHeightDp;
        int maxDeckHeight = Math.max(145, screenHeight - 215);
        FrameLayout.LayoutParams bottom = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                screenHeight < 540 ? dp(maxDeckHeight) : ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        bottom.setMargins(dp(12), 0, dp(12), dp(8));
        deck.addView(deckScroll);
        root.addView(deck, bottom);

        setContentView(root);
        TianwaitianBottomNav.attach(this, TianwaitianBottomNav.Tab.LOCATION);

        mapView.getMapAsync(mapLibreMap -> {
            map = mapLibreMap;
            map.addOnMapClickListener(point -> {
                selectPoint(point, "地图选点", false);
                return true;
            });
            map.setCameraPosition(new CameraPosition.Builder()
                    .target(selectedPoint).zoom(12.2).build());
            map.setStyle(MAP_STYLE, style -> {
                styleReady = true;
                updateMarker();
            });
        });
    }

    private void acceptCoordinates(double longitude, double latitude,
                                   String label, boolean moveCamera) {
        try {
            LabHomeCoordinates.Point p = LabHomeCoordinates.parse(
                    Double.toString(longitude), Double.toString(latitude));
            selectPoint(new LatLng(p.latitude, p.longitude), label, moveCamera);
            Toast.makeText(this,
                    "已选择：" + selectedLabel + " · 未自动改变正在运行的模拟",
                    Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException invalid) {
            Toast.makeText(this, "坐标无效，已保持原位置", Toast.LENGTH_SHORT).show();
        }
    }

    private void selectPoint(LatLng point, String label, boolean moveCamera) {
        // Only explicit user map taps / picker/search callbacks reach here.
        // Persist the selection for tabs and Activity recreation; do NOT
        // publish it to ServiceGo until the user starts simulation.
        TianwaitianSelectedTarget.Target stored = TianwaitianSelectedTarget.save(
                this, point.getLongitude(), point.getLatitude(), label);
        applySelectedTarget(stored, moveCamera);
    }

    private void applySelectedTarget(TianwaitianSelectedTarget.Target target,
                                     boolean moveCamera) {
        selectedPoint = new LatLng(target.latitude, target.longitude);
        selectedLabel = target.label;
        updateTargetText();
        updateMarker();
        if (moveCamera && map != null) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(selectedPoint, 12.2));
        }
    }

    private void syncSelectedTarget() {
        TianwaitianSelectedTarget.Target target = TianwaitianSelectedTarget.read(this);
        if (target == null) return;
        boolean changed = selectedPoint == null
                || Double.compare(selectedPoint.getLongitude(), target.longitude) != 0
                || Double.compare(selectedPoint.getLatitude(), target.latitude) != 0;
        if (changed || !target.label.equals(selectedLabel)) {
            applySelectedTarget(target, changed);
        }
    }

    private void updateTargetText() {
        if (targetView != null && selectedPoint != null) {
            targetView.setText(String.format(Locale.US,
                    "WGS-84  经度 %.6f  ·  纬度 %.6f",
                    selectedPoint.getLongitude(), selectedPoint.getLatitude()));
        }
        if (labelView != null) labelView.setText("已选目标 · " + selectedLabel);
    }

    private void updateMarker() {
        if (map == null || !styleReady || selectedPoint == null) return;
        if (selectedMarker == null) {
            selectedMarker = map.addMarker(new MarkerOptions()
                    .position(selectedPoint).title("天外天 · 当前目标"));
        } else {
            selectedMarker.setPosition(selectedPoint);
            map.updateMarker(selectedMarker);
        }
    }

    private void openMapTools() {
        Intent intent = new Intent(this, LabMapActivity.class);
        intent.putExtra(LabMapActivity.EXTRA_START_LONGITUDE, selectedPoint.getLongitude());
        intent.putExtra(LabMapActivity.EXTRA_START_LATITUDE, selectedPoint.getLatitude());
        mapPicker.launch(intent);
    }

    private void openSimulationConsole() {
        Intent intent = new Intent(this, SimpleMockActivity.class);
        intent.putExtra(EXTRA_PREFILL_LONGITUDE, selectedPoint.getLongitude());
        intent.putExtra(EXTRA_PREFILL_LATITUDE, selectedPoint.getLatitude());
        intent.putExtra(EXTRA_PREFILL_LABEL, selectedLabel);
        startActivity(intent);
    }

    @Override protected void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStart();
    }

    @Override protected void onResume() {
        super.onResume();
        TianwaitianBottomNav.syncSelection(this, TianwaitianBottomNav.Tab.LOCATION);
        syncSelectedTarget();
        if (previewButton != null) previewButton.setVisibility(
                TianwaitianContainerPreview.isAvailable(this) ? View.VISIBLE : View.GONE);
        if (mapView != null) mapView.onResume();
    }

    @Override protected void onPause() {
        if (mapView != null) mapView.onPause();
        super.onPause();
    }

    @Override protected void onStop() {
        if (mapView != null) mapView.onStop();
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (mapView != null) mapView.onDestroy();
        super.onDestroy();
    }

    @Override public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) mapView.onLowMemory();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putDouble(STATE_LONGITUDE, selectedPoint.getLongitude());
        outState.putDouble(STATE_LATITUDE, selectedPoint.getLatitude());
        outState.putString(STATE_LABEL, selectedLabel);
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }
}
