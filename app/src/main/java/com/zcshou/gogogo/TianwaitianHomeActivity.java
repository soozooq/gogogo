package com.zcshou.gogogo;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
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
    private MaterialButton consoleButton;
    private TextView mapStatus;
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

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding(dp(16), dp(10), dp(8), dp(10));
        ImageView emblem = new ImageView(this);
        emblem.setImageResource(R.drawable.twt_constellation_motif);
        emblem.setImageTintList(ColorStateList.valueOf(
                GoGoUi.color(this, R.color.gogogo_primary)));
        emblem.setContentDescription(null);
        brand.addView(emblem, new LinearLayout.LayoutParams(dp(36), dp(36)));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wordLayout = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wordLayout.leftMargin = dp(10);
        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(GoGoUi.color(this, R.color.gogogo_text));
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        words.addView(title);
        TextView tagline = new TextView(this);
        tagline.setText("选一个地点，开启下一程");
        tagline.setTextColor(GoGoUi.color(this, R.color.gogogo_text_muted));
        tagline.setTextSize(12);
        words.addView(tagline);
        brand.addView(words, wordLayout);
        MaterialButton tools = GoGoUi.iconButton(this, R.drawable.twt_more,
                "打开地图工具", v -> openMapTools());
        brand.addView(tools, new LinearLayout.LayoutParams(
                dp(48), ViewGroup.LayoutParams.WRAP_CONTENT));
        brandCard.addView(brand);

        // Compact map search surface sits UNDER the original constellation
        // brand, not in the bottom control deck. No network call happens here.
        LinearLayout topStack = new LinearLayout(this);
        topStack.setOrientation(LinearLayout.VERTICAL);
        topStack.addView(brandCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialCardView searchCard = GoGoUi.card(this);
        searchCard.setRadius(dp(16));
        searchCard.setContentDescription("搜索城市、地址或经纬度，打开统一搜索页面");
        searchCard.setId(R.id.twt_home_search);
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(dp(16), dp(12), dp(16), dp(12));
        searchRow.setMinimumHeight(dp(52));
        ImageView searchIcon = new ImageView(this);
        searchIcon.setImageResource(R.drawable.ic_search);
        searchIcon.setImageTintList(ColorStateList.valueOf(
                GoGoUi.color(this, R.color.gogogo_primary)));
        searchIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        searchRow.addView(searchIcon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView searchHint = new TextView(this);
        searchHint.setText("搜索城市、街道、建筑或经纬度");
        searchHint.setPadding(dp(12), 0, dp(4), 0);
        searchHint.setSingleLine(true);
        searchHint.setEllipsize(android.text.TextUtils.TruncateAt.END);
        searchHint.setTextSize(14);
        searchHint.setTextColor(GoGoUi.color(this, R.color.gogogo_text_muted));
        searchRow.addView(searchHint, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_gogogo_chevron);
        arrow.setImageTintList(ColorStateList.valueOf(
                GoGoUi.color(this, R.color.gogogo_text_muted)));
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        searchRow.addView(arrow, new LinearLayout.LayoutParams(dp(18), dp(18)));
        searchCard.addView(searchRow);
        searchCard.setClickable(true);
        searchCard.setFocusable(true);
        searchCard.setOnClickListener(v -> unifiedSearch.launch(
                new Intent(this, TianwaitianSearchActivity.class)));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.topMargin = dp(9);
        topStack.addView(searchCard, searchLp);
        mapStatus = GoGoUi.muted(this, "地图加载中，可先搜索地点");
        mapStatus.setGravity(Gravity.CENTER);
        mapStatus.setPadding(dp(8), dp(8), dp(8), dp(8));
        mapStatus.setBackgroundResource(R.drawable.twt_settings_header_bg);
        mapStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams mapStatusLp = GoGoUi.matchWrap();
        mapStatusLp.topMargin = dp(6);
        topStack.addView(mapStatus, mapStatusLp);
        mapStatus.setOnClickListener(v -> { if (map != null) loadMapStyle(); });
        mapStatus.setClickable(false);

        FrameLayout.LayoutParams top = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        top.setMargins(dp(12), dp(10), dp(12), 0);
        root.addView(topStack, top);

        // One target and one primary action; secondary controls share a quiet row.
        MaterialCardView deck = GoGoUi.card(this);
        deck.setRadius(dp(24));
        deck.setCardElevation(dp(3));
        LinearLayout content = GoGoUi.cardContent(this);
        content.setPadding(dp(18), dp(16), dp(18), dp(12));

        TextView targetCaption = GoGoUi.muted(this, "已选目标 · 地图选点");
        targetCaption.setTextSize(12);
        content.addView(targetCaption, GoGoUi.matchWrap());
        content.addView(GoGoUi.gap(this, 5));

        labelView = GoGoUi.sectionTitle(this, selectedLabel);
        labelView.setId(R.id.twt_home_target);
        labelView.setMaxLines(2);
        labelView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labelView.setTextSize(21);
        labelView.setPadding(0, 0, 0, dp(3));
        content.addView(labelView, GoGoUi.matchWrap());

        targetView = GoGoUi.muted(this, "");
        targetView.setTextIsSelectable(true);
        targetView.setPadding(0, dp(3), 0, 0);
        content.addView(targetView, GoGoUi.matchWrap());
        updateTargetText();

        content.addView(GoGoUi.gap(this, 5));
        TextView note = GoGoUi.muted(this,
                "点按地图更换地点，选点后由你确认打开");
        note.setTextSize(12);
        content.addView(note, GoGoUi.matchWrap());
        content.addView(GoGoUi.gap(this, 8));

        // The native bottom navigation owns Diagnostics, Audit and Settings.
        // Keep only the two location-specific actions in this lightweight deck.
        previewButton = GoGoUi.primaryButton(this, "打开微信 / 抖音预览",
                v -> TianwaitianContainerPreview.chooseApp(this,
                        selectedPoint.getLongitude(), selectedPoint.getLatitude()));
        previewButton.setVisibility(TianwaitianContainerPreview.isAvailable(this)
                ? View.VISIBLE : View.GONE);
        previewButton.setId(R.id.twt_home_preview);
        content.addView(previewButton, GoGoUi.matchWrap());

        MaterialButton console = GoGoUi.secondaryButton(this,
                "系统模拟控制", v -> openSimulationConsole());
        consoleButton = console;
        console.setId(R.id.twt_home_controls);
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
        // Cap to actual available height after bottom navigation. Large text and
        // landscape can scroll inside the card without covering the search bar.
        ScrollView deckScroll = new ScrollView(this) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int available = root.getHeight();
                if (available > 0) {
                    int cap = Math.max(dp(80), Math.min(available / 2,
                            available - topStack.getHeight() - dp(100)));
                    heightSpec = View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST);
                }
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        deckScroll.setFillViewport(false);
        deckScroll.setVerticalScrollBarEnabled(false);
        deckScroll.addView(content);
        FrameLayout.LayoutParams bottom = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        bottom.setMargins(dp(12), 0, dp(12), dp(8));
        deck.addView(deckScroll);
        root.addView(deck, bottom);
        root.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if (b - t != oldB - oldT || r - l != oldR - oldL) deckScroll.requestLayout();
        });
        deck.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if (map != null) {
                // Keep the renderer/data attribution visible above the command card.
                map.getUiSettings().setAttributionMargins(dp(12), 0, 0, b - t + dp(18));
                map.getUiSettings().setLogoMargins(dp(12), 0, 0, b - t + dp(42));
            }
        });

        setContentView(root);
        TianwaitianBottomNav.attach(this, TianwaitianBottomNav.Tab.LOCATION);

        mapView.getMapAsync(mapLibreMap -> {
            map = mapLibreMap;
            map.getUiSettings().setAttributionMargins(dp(12), 0, 0, deck.getHeight() + dp(18));
            map.getUiSettings().setLogoMargins(dp(12), 0, 0, deck.getHeight() + dp(42));
            map.addOnMapClickListener(point -> {
                selectPoint(point, "地图选点", false);
                return true;
            });
            map.setCameraPosition(new CameraPosition.Builder()
                    .target(selectedPoint).zoom(12.2).build());
            loadMapStyle();
        });
        mapView.addOnDidFailLoadingMapListener(error -> {
            mapStatus.setText("地图暂未加载 · 点此重试，或先搜索地点");
            mapStatus.setMinHeight(dp(48));
            mapStatus.setVisibility(View.VISIBLE);
            mapStatus.setClickable(true);
            mapStatus.setFocusable(true);
        });
    }

    private void loadMapStyle() {
        mapStatus.setText("地图加载中，可先搜索地点");
        mapStatus.setClickable(false);
        mapStatus.setVisibility(View.VISIBLE);
        map.setStyle(MAP_STYLE, style -> {
            styleReady = true;
            updateMarker();
            mapStatus.setVisibility(View.GONE);
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
                    "经度 %.5f  /  纬度 %.5f  ·  WGS-84",
                    selectedPoint.getLongitude(), selectedPoint.getLatitude()));
        }
        if (labelView != null) labelView.setText(selectedLabel);
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
        if (previewButton != null) {
            boolean available = TianwaitianContainerPreview.isAvailable(this);
            previewButton.setVisibility(available ? View.VISIBLE : View.GONE);
            GoGoUi.setActionEmphasis(consoleButton, !available);
        }
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
