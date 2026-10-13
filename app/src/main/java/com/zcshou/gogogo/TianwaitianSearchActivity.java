package com.zcshou.gogogo;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;
import java.util.Locale;

/**
 * One clear search route for offline city presets, WGS-84 input and manual
 * online OpenStreetMap geocoding. No results ever start the mock service.
 */
public final class TianwaitianSearchActivity extends AppCompatActivity {
    private static final String STATE_QUERY = "twt.search.query";
    private static final int MAX_PRESETS_SHOWN = 12;

    private EditText queryField;
    private TextView offlineLabel;
    private TextView onlineStatus;
    private LinearLayout offlineResults;
    private LinearLayout onlineResults;
    private MaterialButton onlineButton;
    private boolean searching;
    private long queryRevision;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String query = state == null ? "" : state.getString(STATE_QUERY, "");
        createUi(query);
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (queryField != null) outState.putString(STATE_QUERY,
                queryField.getText().toString());
        super.onSaveInstanceState(outState);
    }

    private int dp(int n) { return GoGoUi.dp(this, n); }

    private void createUi(String initialQuery) {
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(10), dp(16), dp(10));
        GoGoUi.applyScreenBackground(root);
        root.setFocusableInTouchMode(true);

        LinearLayout heading = GoGoUi.row(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        MaterialButton back = GoGoUi.textButton(this, "‹", v -> finish());
        back.setTextSize(23);
        back.setContentDescription("返回地图首页");
        heading.addView(back, new LinearLayout.LayoutParams(
                dp(43), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout headingText = new LinearLayout(this);
        headingText.setOrientation(LinearLayout.VERTICAL);
        headingText.addView(GoGoUi.heroTitle(this, "搜索地点"), GoGoUi.matchWrap());
        TextView subtitle = GoGoUi.muted(this, "离线城市 · 全球地名 · WGS-84 坐标");
        subtitle.setTextSize(12);
        headingText.addView(subtitle, GoGoUi.matchWrap());
        heading.addView(headingText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialButton service = GoGoUi.textButton(this, "服务", v -> serviceOptions());
        service.setContentDescription("在线搜索服务设置及缓存");
        heading.addView(service, new LinearLayout.LayoutParams(
                dp(69), ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(heading, GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 12));

        LinearLayout inputRow = GoGoUi.row(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        queryField = new EditText(this);
        queryField.setSingleLine(true);
        queryField.setTextSize(15);
        queryField.setHint("城市、街道、建筑或 98.5,16.7");
        queryField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        queryField.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        queryField.setContentDescription("输入地点名称、地址或经纬度");
        GoGoUi.styleInput(queryField);
        inputRow.addView(queryField, GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, inputRow, 5);
        MaterialButton clear = GoGoUi.textButton(this, "×", v -> queryField.setText(""));
        clear.setContentDescription("清空搜索词");
        inputRow.addView(clear, new LinearLayout.LayoutParams(
                dp(46), ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(inputRow, GoGoUi.matchWrap());

        root.addView(GoGoUi.gap(this, 8));
        onlineButton = GoGoUi.primaryButton(this, "搜索全球地图（联网）",
                v -> startOnlineSearch());
        onlineButton.setContentDescription("手动发起一次全球地点搜索，会发送输入的关键词");
        root.addView(onlineButton, GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 6));
        TextView note = GoGoUi.muted(this,
                "离线结果即时筛选；联网查询仅在点击按钮后发送关键词，不自动补全。");
        note.setTextSize(11);
        root.addView(note, GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 8));

        ScrollView scrolling = new ScrollView(this);
        scrolling.setFillViewport(false);
        scrolling.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        offlineLabel = GoGoUi.sectionTitle(this, "离线预设");
        body.addView(offlineLabel, GoGoUi.matchWrap());
        body.addView(GoGoUi.gap(this, 5));
        offlineResults = new LinearLayout(this);
        offlineResults.setOrientation(LinearLayout.VERTICAL);
        body.addView(offlineResults, GoGoUi.matchWrap());

        body.addView(GoGoUi.gap(this, 14));
        body.addView(GoGoUi.sectionTitle(this, "全球地图搜索结果"), GoGoUi.matchWrap());
        body.addView(GoGoUi.gap(this, 5));
        onlineStatus = GoGoUi.muted(this,
                "输入地址后点击「搜索全球地图」；默认不会向网络发送文字。");
        body.addView(onlineStatus, GoGoUi.matchWrap());
        body.addView(GoGoUi.gap(this, 7));
        onlineResults = new LinearLayout(this);
        onlineResults.setOrientation(LinearLayout.VERTICAL);
        body.addView(onlineResults, GoGoUi.matchWrap());
        scrolling.addView(body);
        root.addView(scrolling, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(GoGoUi.gap(this, 6));
        TextView attribution = GoGoUi.muted(this,
                "搜索数据 © OpenStreetMap contributors · ODbL · 查看署名与许可");
        attribution.setTextSize(11);
        attribution.setTextColor(GoGoUi.color(this, R.color.gogogo_primary_dark));
        attribution.setGravity(Gravity.CENTER);
        attribution.setOnClickListener(v -> startActivity(new Intent(
                Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"))));
        attribution.setContentDescription("查看 OpenStreetMap 数据署名与许可");
        root.addView(attribution, GoGoUi.matchWrap());
        setContentView(root);

        queryField.setText(initialQuery == null ? "" : initialQuery);
        queryField.setSelection(queryField.length());
        queryField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start,
                                                    int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start,
                                                int before, int count) {
                queryRevision++;
                renderOffline(s.toString());
                onlineResults.removeAllViews();
                onlineStatus.setText("关键词已变化，请点击联网搜索查看新结果。");
                onlineButton.setEnabled(!searching &&
                        TianwaitianSearchQuery.validOnlineQuery(s.toString()));
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        queryField.setOnEditorActionListener((view, id, event) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH) {
                // Keyboard Search is a deliberate submission, not autocomplete.
                if (TianwaitianSearchQuery.looksLikeCoordinates(
                        queryField.getText().toString())) {
                    returnCoordinatesFromInput();
                } else {
                    startOnlineSearch();
                }
                return true;
            }
            return false;
        });
        renderOffline(queryField.getText().toString());
        onlineButton.setEnabled(TianwaitianSearchQuery.validOnlineQuery(
                queryField.getText().toString()));
    }

    private void renderOffline(String raw) {
        if (offlineResults == null) return;
        offlineResults.removeAllViews();
        String query = raw == null ? "" : raw.trim();

        if (TianwaitianSearchQuery.looksLikeCoordinates(query)) {
            try {
                LabHomeCoordinates.Point p =
                        TianwaitianSearchQuery.coordinatesOrNull(query);
                offlineLabel.setText("坐标直达 · WGS-84");
                addResult(offlineResults, "使用输入的经纬度",
                        String.format(Locale.US, "经度 %.6f · 纬度 %.6f",
                                p.longitude, p.latitude),
                        p.longitude, p.latitude);
            } catch (IllegalArgumentException error) {
                offlineLabel.setText("坐标输入");
                offlineResults.addView(GoGoUi.muted(this, error.getMessage()),
                        GoGoUi.matchWrap());
            }
            return;
        }

        int total = 0;
        int shown = 0;
        for (int i = 0; i < LabLocationPresets.size(); i++) {
            LabLocationPresets.Preset preset = LabLocationPresets.get(i);
            if (!LabPlaceSearch.matches(preset.name, query)) continue;
            total++;
            if (shown < MAX_PRESETS_SHOWN) {
                addResult(offlineResults, preset.name,
                        String.format(Locale.US, "%.5f, %.5f · 离线预设",
                                preset.longitude, preset.latitude),
                        preset.longitude, preset.latitude);
                shown++;
            }
        }
        offlineLabel.setText("离线预设 · 匹配 " + total + " 个地点");
        if (total == 0) {
            offlineResults.addView(GoGoUi.muted(this,
                    "本地预设中没有这个地点，可点击上方按钮联网搜索。"),
                    GoGoUi.matchWrap());
        }
        if (total > shown) {
            TextView more = GoGoUi.muted(this,
                    "仅显示前 " + shown + " 个，继续输入关键词缩小范围。");
            offlineResults.addView(more, GoGoUi.matchWrap());
        }
        offlineResults.addView(GoGoUi.gap(this, 5));
        offlineResults.addView(GoGoUi.secondaryButton(this,
                "查看完整地点库 · " + LabLocationPresets.size() + " 个预设",
                v -> startActivity(new Intent(this, LabPlacePickerActivity.class))),
                GoGoUi.matchWrap());
    }

    private void addResult(LinearLayout parent, String name, String detail,
                           double longitude, double latitude) {
        MaterialCardView tile = GoGoUi.navigationTile(this, R.drawable.twt_nav_location,
                name, detail + " · 点击填入地图",
                v -> deliverResult(name, longitude, latitude));
        LinearLayout.LayoutParams lp = GoGoUi.matchWrap();
        lp.bottomMargin = dp(6);
        parent.addView(tile, lp);
    }

    private void deliverResult(String label, double longitude, double latitude) {
        try {
            LabHomeCoordinates.Point point = LabHomeCoordinates.parse(
                    Double.toString(longitude), Double.toString(latitude));
            Intent answer = new Intent()
                    .putExtra(LabPlacePickerActivity.EXTRA_LONGITUDE, point.longitude)
                    .putExtra(LabPlacePickerActivity.EXTRA_LATITUDE, point.latitude)
                    .putExtra(LabPlacePickerActivity.EXTRA_LABEL, label);
            setResult(Activity.RESULT_OK, answer);
            finish();
        } catch (IllegalArgumentException error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void returnCoordinatesFromInput() {
        try {
            LabHomeCoordinates.Point point = TianwaitianSearchQuery.coordinatesOrNull(
                    queryField.getText().toString());
            if (point == null) {
                Toast.makeText(this, "请输入 经度,纬度", Toast.LENGTH_SHORT).show();
                return;
            }
            deliverResult("坐标直达", point.longitude, point.latitude);
        } catch (IllegalArgumentException error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startOnlineSearch() {
        if (searching) return;
        String query = queryField.getText().toString().trim();
        if (TianwaitianSearchQuery.looksLikeCoordinates(query)) {
            returnCoordinatesFromInput();
            return;
        }
        if (!TianwaitianSearchQuery.validOnlineQuery(query)) {
            Toast.makeText(this, "请输入 2–120 字的地点或地址",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        searching = true;
        onlineButton.setEnabled(false);
        onlineButton.setText("正在搜索…");
        onlineResults.removeAllViews();
        onlineStatus.setText("正在获取全球地点，可能需要几秒钟…");
        long requestedRevision = queryRevision;
        TianwaitianGeocoder.WORKER.execute(() -> {
            List<TianwaitianGeocoder.Result> results = null;
            String error = null;
            boolean cached = false;
            try {
                results = TianwaitianGeocoder.cached(getApplicationContext(), query);
                if (results != null) cached = true;
                else results = TianwaitianGeocoder.search(getApplicationContext(), query);
            } catch (Exception failure) {
                error = failure.getMessage();
            }
            final List<TianwaitianGeocoder.Result> result = results;
            final String message = error;
            final boolean fromCache = cached;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                searching = false;
                onlineButton.setText("搜索全球地图（联网）");
                onlineButton.setEnabled(TianwaitianSearchQuery.validOnlineQuery(
                        queryField.getText().toString()));
                // Do not paint stale answers after user edits the input.
                if (requestedRevision != queryRevision) return;
                onlineResults.removeAllViews();
                if (message != null) {
                    onlineStatus.setText("搜索失败：" + message
                            + "。离线预设和坐标输入仍可使用。");
                } else if (result == null || result.isEmpty()) {
                    onlineStatus.setText("没有找到匹配结果，试试完整地址或其他语言。");
                } else {
                    onlineStatus.setText(result.size() + " 个在线结果"
                            + (fromCache ? " · 本地缓存" : " · 已完成查询")
                            + " · 点击只会选择地图目标");
                    for (TianwaitianGeocoder.Result item : result) {
                        addResult(onlineResults, item.label,
                                String.format(Locale.US, "WGS-84  %.6f, %.6f",
                                        item.longitude, item.latitude),
                                item.longitude, item.latitude);
                    }
                }
            });
        });
    }

    private void serviceOptions() {
        String[] options = {"更换 Nominatim 兼容搜索服务", "清除本地在线结果缓存",
                "查看 OpenStreetMap 署名与使用规则"};
        new AlertDialog.Builder(this).setTitle("在线搜索设置")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) editEndpoint();
                    else if (which == 1) {
                        TianwaitianGeocoder.clearCache(this);
                        Toast.makeText(this, "已清除在线搜索本地缓存",
                                Toast.LENGTH_SHORT).show();
                    } else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                            "https://operations.osmfoundation.org/policies/nominatim/")));
                }).setNegativeButton("取消", null).show();
    }

    private void editEndpoint() {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setText(TianwaitianGeocoder.endpoint(this));
        field.setSelectAllOnFocus(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        field.setPadding(dp(16), dp(10), dp(16), dp(10));
        new AlertDialog.Builder(this)
                .setTitle("Nominatim 兼容服务地址")
                .setMessage("仅使用可信的 HTTPS 服务。切换后会将输入的搜索词发送给新服务，且清空旧缓存。")
                .setView(field)
                .setPositiveButton("保存", (dialog, which) -> {
                    try {
                        TianwaitianGeocoder.setEndpoint(this,
                                field.getText().toString());
                        onlineResults.removeAllViews();
                        onlineStatus.setText("搜索服务已切换，请手动重新搜索。");
                        Toast.makeText(this, "搜索服务已保存", Toast.LENGTH_SHORT).show();
                    } catch (IllegalArgumentException invalid) {
                        Toast.makeText(this, invalid.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNeutralButton("恢复默认", (dialog, which) -> {
                    TianwaitianGeocoder.setEndpoint(this,
                            TianwaitianGeocoder.DEFAULT_ENDPOINT);
                    onlineResults.removeAllViews();
                    onlineStatus.setText("已恢复默认搜索服务。");
                }).setNegativeButton("取消", null).show();
    }
}
