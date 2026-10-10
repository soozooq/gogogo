package com.zcshou.gogogo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * Shared searchable, offline global preset picker for home and MapLibre.
 * Favorites are the SAME LabStore favorites used by map tools; choosing a
 * result only returns WGS-84 coordinates to the caller, never starts ServiceGo.
 */
public final class LabPlacePickerActivity extends AppCompatActivity {
    public static final String EXTRA_LONGITUDE = "gogogo.place.longitude";
    public static final String EXTRA_LATITUDE = "gogogo.place.latitude";
    public static final String EXTRA_LABEL = "gogogo.place.label";

    private static final String KEY_QUERY = "gogogo.place.query";
    private static final String KEY_FAVORITES_TAB = "gogogo.place.favoritesTab";
    private static final String KEY_EXPANDED = "gogogo.place.expandedCountries";

    // Accordion country headers and optional city rows share a single ListView.
    private final List<PickerRow> displayed = new ArrayList<>();
    private final Set<String> expandedCountries = new HashSet<>();
    private final Set<String> searchCollapsedCountries = new HashSet<>();
    private String lastQuery = "";
    private final PickerAdapter adapter = new PickerAdapter();
    private EditText searchField;
    private TextView resultsLabel;
    private TextView emptyLabel;
    private ListView resultsList;
    private MaterialButton allTab;
    private MaterialButton favoritesTab;
    private boolean favoritesOnly;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        favoritesOnly = state != null && state.getBoolean(KEY_FAVORITES_TAB, false);
        if (state != null) {
            ArrayList<String> restored = state.getStringArrayList(KEY_EXPANDED);
            if (restored != null) expandedCountries.addAll(restored);
        }
        buildUi(state == null ? "" : state.getString(KEY_QUERY, ""));
    }

    @Override protected void onResume() {
        super.onResume();
        // A map page can add favorites independently: always reread LabStore.
        refreshResults();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean(KEY_FAVORITES_TAB, favoritesOnly);
        out.putStringArrayList(KEY_EXPANDED, new ArrayList<>(expandedCountries));
        if (searchField != null) out.putString(KEY_QUERY, searchField.getText().toString());
        super.onSaveInstanceState(out);
    }

    private void buildUi(String initialSearch) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        GoGoUi.applyScreenBackground(root);
        int pad = GoGoUi.dp(this, 16);
        root.setPadding(pad, GoGoUi.dp(this, 12), pad, GoGoUi.dp(this, 14));

        root.addView(GoGoUi.backButton(this, v -> finish()), GoGoUi.matchWrap());
        root.addView(GoGoUi.eyebrow(this, "PLACES  /  OFFLINE SEARCH"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.heroTitle(this, "国家与城市"), GoGoUi.matchWrap());
        root.addView(GoGoUi.subtitle(this,
                "国旗代表国家，点击右侧 ＋ 展开城市；可搜索、选点和收藏。选点不会自动启动模拟。"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 12));

        searchField = new EditText(this);
        searchField.setSingleLine(true);
        searchField.setHint("搜索国家或城市，如：中国、东京、巴黎");
        searchField.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        GoGoUi.styleInput(searchField);
        searchField.setText(initialSearch);
        root.addView(searchField, GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 10));

        LinearLayout tabs = GoGoUi.row(this);
        allTab = GoGoUi.secondaryButton(this, "", v -> switchTab(false));
        favoritesTab = GoGoUi.secondaryButton(this, "", v -> switchTab(true));
        tabs.addView(allTab, GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, tabs, 8);
        tabs.addView(favoritesTab, GoGoUi.weighted());
        root.addView(tabs, GoGoUi.matchWrap());

        resultsLabel = GoGoUi.muted(this, "");
        resultsLabel.setPadding(0, GoGoUi.dp(this, 10), 0, GoGoUi.dp(this, 8));
        root.addView(resultsLabel, GoGoUi.matchWrap());

        emptyLabel = GoGoUi.muted(this, "");
        emptyLabel.setGravity(Gravity.CENTER);
        emptyLabel.setPadding(GoGoUi.dp(this, 10), GoGoUi.dp(this, 20),
                GoGoUi.dp(this, 10), GoGoUi.dp(this, 20));
        root.addView(emptyLabel, GoGoUi.matchWrap());

        resultsList = new ListView(this);
        resultsList.setDividerHeight(GoGoUi.dp(this, 8));
        resultsList.setDivider(null);
        resultsList.setCacheColorHint(android.graphics.Color.TRANSPARENT);
        resultsList.setAdapter(adapter);
        resultsList.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= displayed.size()) return;
            PickerRow row = displayed.get(position);
            if (row.isCountry()) toggleCountry(row.country);
            else choose(row.place);
        });
        root.addView(resultsList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView note = GoGoUi.muted(this,
                "收藏与地图收藏夹同步，最多 100 项。离线参考坐标不是在线地址搜索。");
        note.setPadding(0, GoGoUi.dp(this, 10), 0, 0);
        root.addView(note, GoGoUi.matchWrap());
        setContentView(root);

        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start,
                                                     int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start,
                                                int before, int count) {
                refreshResults();
            }
            @Override public void afterTextChanged(Editable editable) {}
        });
        refreshResults();
    }

    private void switchTab(boolean onlyFavorites) {
        favoritesOnly = onlyFavorites;
        searchCollapsedCountries.clear();
        refreshResults();
        if (resultsList != null) resultsList.setSelection(0);
    }

    private boolean isExpanded(String country, boolean searching) {
        return searching ? !searchCollapsedCountries.contains(country)
                : expandedCountries.contains(country);
    }

    private void toggleCountry(String country) {
        boolean searching = searchField != null
                && !searchField.getText().toString().trim().isEmpty();
        if (searching) {
            if (!searchCollapsedCountries.add(country)) searchCollapsedCountries.remove(country);
        } else {
            if (!expandedCountries.add(country)) expandedCountries.remove(country);
        }
        refreshResults();
    }

    private void refreshResults() {
        if (searchField == null || resultsLabel == null) return;
        String query = searchField.getText().toString();
        boolean searching = !query.trim().isEmpty();
        if (!query.equals(lastQuery)) {
            // A fresh search automatically reveals all matching cities;
            // tapping a country can still collapse it while searching.
            searchCollapsedCountries.clear();
            lastQuery = query;
        }
        displayed.clear();
        List<LabStore.SavedPoint> favorites = LabStore.getFavorites(this);
        Map<String, List<PlaceOption>> groups = new LinkedHashMap<>();
        int matches = 0;

        if (favoritesOnly) {
            for (LabStore.SavedPoint point : favorites) {
                if (!LabPlaceSearch.matches(point.name, query)) continue;
                if (!Double.isFinite(point.longitude) || !Double.isFinite(point.latitude)
                        || Math.abs(point.longitude) > 180.0
                        || Math.abs(point.latitude) > 90.0) continue;
                String country = LabPlaceCountries.countryOf(point.name);
                groups.computeIfAbsent(country, unused -> new ArrayList<>())
                        .add(new PlaceOption(point.name, point.longitude, point.latitude));
                matches++;
            }
        } else {
            for (int i = 0; i < LabLocationPresets.size(); i++) {
                LabLocationPresets.Preset preset = LabLocationPresets.get(i);
                if (!LabPlaceSearch.matches(preset.name, query)) continue;
                String country = LabPlaceCountries.countryOf(preset.name);
                groups.computeIfAbsent(country, unused -> new ArrayList<>())
                        .add(new PlaceOption(preset.name, preset.longitude, preset.latitude));
                matches++;
            }
        }

        for (Map.Entry<String, List<PlaceOption>> entry : groups.entrySet()) {
            String country = entry.getKey();
            List<PlaceOption> cities = entry.getValue();
            boolean expanded = isExpanded(country, searching);
            displayed.add(PickerRow.country(country, cities.size(), expanded));
            if (expanded) {
                for (PlaceOption option : cities) displayed.add(PickerRow.city(option));
            }
        }

        allTab.setText("全部预设 · " + LabLocationPresets.size()
                + (favoritesOnly ? "" : " ✓"));
        favoritesTab.setText("我的收藏 · " + favorites.size()
                + (favoritesOnly ? " ✓" : ""));
        allTab.setContentDescription("全部预设，" + LabLocationPresets.size()
                + " 个地点，" + (favoritesOnly ? "未选中" : "已选中"));
        favoritesTab.setContentDescription("我的收藏，" + favorites.size()
                + " 个地点，" + (favoritesOnly ? "已选中" : "未选中"));
        resultsLabel.setText("找到 " + groups.size() + " 个国家或分类 · " + matches
                + " 个" + (favoritesOnly ? "收藏地点" : "预设城市")
                + (searching ? " · 已自动展开匹配城市" : " · 点 ＋ 展开"));
        boolean isEmpty = groups.isEmpty();
        emptyLabel.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        emptyLabel.setText(favoritesOnly
                ? "还没有符合条件的收藏。切到「全部预设」点星号即可收藏。"
                : "没有匹配结果，试试输入国家名、城市名，或清空搜索。");
        adapter.notifyDataSetChanged();
    }

    private void choose(PlaceOption option) {
        try {
            LabHomeCoordinates.Point coordinate = LabHomeCoordinates.parse(
                    Double.toString(option.longitude), Double.toString(option.latitude));
            Intent result = new Intent();
            result.putExtra(EXTRA_LONGITUDE, coordinate.longitude);
            result.putExtra(EXTRA_LATITUDE, coordinate.latitude);
            result.putExtra(EXTRA_LABEL, option.label);
            setResult(Activity.RESULT_OK, result);
            finish();
        } catch (IllegalArgumentException invalid) {
            Toast.makeText(this, "该坐标无效，无法选择", Toast.LENGTH_SHORT).show();
        }
    }

    /** A header never represents a coordinate; only expanded city rows are selectable. */
    private static final class PickerRow {
        final String country;
        final PlaceOption place;
        final int count;
        final boolean expanded;

        private PickerRow(String country, PlaceOption place, int count, boolean expanded) {
            this.country = country;
            this.place = place;
            this.count = count;
            this.expanded = expanded;
        }

        static PickerRow country(String country, int count, boolean expanded) {
            return new PickerRow(country, null, count, expanded);
        }
        static PickerRow city(PlaceOption place) {
            return new PickerRow(null, place, 0, false);
        }
        boolean isCountry() { return place == null; }
    }

    private static final class PlaceOption {
        final String label;
        final double longitude;
        final double latitude;

        PlaceOption(String label, double longitude, double latitude) {
            this.label = label;
            this.longitude = longitude;
            this.latitude = latitude;
        }
    }

    private final class PickerAdapter extends BaseAdapter {
        @Override public int getCount() { return displayed.size(); }
        @Override public Object getItem(int position) { return displayed.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            PickerRow entry = displayed.get(position);
            if (entry.isCountry()) return countryHeader(entry);
            return cityRow(entry.place);
        }

        private View countryHeader(PickerRow entry) {
            LinearLayout row = GoGoUi.row(LabPlacePickerActivity.this);
            row.setPadding(GoGoUi.dp(LabPlacePickerActivity.this, 12),
                    GoGoUi.dp(LabPlacePickerActivity.this, 8),
                    GoGoUi.dp(LabPlacePickerActivity.this, 8),
                    GoGoUi.dp(LabPlacePickerActivity.this, 8));
            row.setBackground(GoGoUi.card(LabPlacePickerActivity.this).getBackground());

            TextView title = GoGoUi.sectionTitle(LabPlacePickerActivity.this,
                    LabPlaceCountries.flagOf(entry.country) + "  " + entry.country);
            title.setTextSize(16);
            title.setPadding(0, 0, 0, 0);
            row.addView(title, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView count = GoGoUi.muted(LabPlacePickerActivity.this,
                    entry.count + (favoritesOnly ? " 个收藏" : " 个城市"));
            row.addView(count, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            GoGoUi.addHorizontalGap(LabPlacePickerActivity.this, row, 8);

            MaterialButton toggle = GoGoUi.secondaryButton(LabPlacePickerActivity.this,
                    entry.expanded ? "−" : "+", v -> toggleCountry(entry.country));
            toggle.setTextSize(20);
            toggle.setContentDescription((entry.expanded ? "收起" : "展开") + entry.country
                    + "的" + entry.count + "个地点");
            row.addView(toggle, new LinearLayout.LayoutParams(
                    GoGoUi.dp(LabPlacePickerActivity.this, 52),
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            row.setClickable(true);
            row.setFocusable(true);
            row.setContentDescription(LabPlaceCountries.flagOf(entry.country) + " "
                    + entry.country + "，" + entry.count
                    + (favoritesOnly ? "个收藏" : "个城市") + "，"
                    + (entry.expanded ? "已展开" : "已收起"));
            row.setOnClickListener(v -> toggleCountry(entry.country));
            return row;
        }

        private View cityRow(PlaceOption option) {
            LinearLayout row = GoGoUi.row(LabPlacePickerActivity.this);
            row.setPadding(GoGoUi.dp(LabPlacePickerActivity.this, 24),
                    GoGoUi.dp(LabPlacePickerActivity.this, 4),
                    GoGoUi.dp(LabPlacePickerActivity.this, 8),
                    GoGoUi.dp(LabPlacePickerActivity.this, 4));
            row.setBackground(GoGoUi.card(LabPlacePickerActivity.this).getBackground());
            row.setClickable(true);
            row.setFocusable(true);
            row.setContentDescription("选择城市：" + option.label);
            row.setOnClickListener(v -> choose(option));

            LinearLayout info = new LinearLayout(LabPlacePickerActivity.this);
            info.setOrientation(LinearLayout.VERTICAL);
            TextView title = GoGoUi.sectionTitle(LabPlacePickerActivity.this,
                    LabPlaceCountries.cityOf(option.label));
            title.setTextSize(15);
            title.setPadding(0, 0, 0, 0);
            info.addView(title, GoGoUi.matchWrap());
            info.addView(GoGoUi.muted(LabPlacePickerActivity.this,
                    String.format(Locale.US, "%.5f, %.5f",
                            option.longitude, option.latitude)), GoGoUi.matchWrap());
            row.addView(info, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            boolean saved = LabStore.isFavorite(LabPlacePickerActivity.this,
                    option.longitude, option.latitude);
            MaterialButton star = GoGoUi.textButton(LabPlacePickerActivity.this,
                    saved ? "★" : "☆", v -> {
                        if (LabStore.isFavorite(LabPlacePickerActivity.this,
                                option.longitude, option.latitude)) {
                            LabStore.removeFavorite(LabPlacePickerActivity.this,
                                    option.longitude, option.latitude);
                            Toast.makeText(LabPlacePickerActivity.this,
                                    "已取消收藏", Toast.LENGTH_SHORT).show();
                        } else {
                            LabStore.addFavorite(LabPlacePickerActivity.this, option.label,
                                    option.longitude, option.latitude);
                            Toast.makeText(LabPlacePickerActivity.this,
                                    "已收藏：" + option.label, Toast.LENGTH_SHORT).show();
                        }
                        refreshResults();
                    });
            star.setContentDescription((saved ? "取消收藏" : "收藏") + option.label);
            row.addView(star, new LinearLayout.LayoutParams(
                    GoGoUi.dp(LabPlacePickerActivity.this, 56),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return row;
        }
    }
}
