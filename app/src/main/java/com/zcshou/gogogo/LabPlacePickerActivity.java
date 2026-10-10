package com.zcshou.gogogo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import android.view.WindowManager;

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
    private static final String KEY_SEARCH_COLLAPSED = "gogogo.place.searchCollapsedCountries";

    // Accordion country headers and optional city rows share a single ListView.
    private final List<PickerRow> displayed = new ArrayList<>();
    private final Set<String> expandedCountries = new HashSet<>();
    private final Set<String> searchCollapsedCountries = new HashSet<>();
    private String lastQuery = "";
    private final PickerAdapter adapter = new PickerAdapter();
    private List<LabStore.SavedPoint> favoriteSnapshot = new ArrayList<>();
    private MaterialButton clearSearchButton;
    private EditText searchField;
    private TextView resultsLabel;
    private TextView emptyLabel;
    private ListView resultsList;
    private MaterialButton allTab;
    private MaterialButton favoritesTab;
    private boolean favoritesOnly;
    private ScrollView alphabetScroll;
    private LinearLayout alphabetColumn;
    private final Map<String, Integer> letterAnchors = new LinkedHashMap<>();
    private String renderedAlphabet = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        favoritesOnly = state != null && state.getBoolean(KEY_FAVORITES_TAB, false);
        if (state != null) {
            ArrayList<String> restored = state.getStringArrayList(KEY_EXPANDED);
            if (restored != null) expandedCountries.addAll(restored);
            ArrayList<String> collapsed = state.getStringArrayList(KEY_SEARCH_COLLAPSED);
            if (collapsed != null) searchCollapsedCountries.addAll(collapsed);
        }
        String query = state == null ? "" : state.getString(KEY_QUERY, "");
        lastQuery = query == null ? "" : query;
        buildUi(lastQuery);
    }

    @Override protected void onResume() {
        super.onResume();
        // A map page can add favorites independently: always reread LabStore.
        refreshResults();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean(KEY_FAVORITES_TAB, favoritesOnly);
        out.putStringArrayList(KEY_EXPANDED, new ArrayList<>(expandedCountries));
        out.putStringArrayList(KEY_SEARCH_COLLAPSED,
                new ArrayList<>(searchCollapsedCountries));
        if (searchField != null) out.putString(KEY_QUERY, searchField.getText().toString());
        super.onSaveInstanceState(out);
    }

    private void buildUi(String initialSearch) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        GoGoUi.applyScreenBackground(root);
        int pad = GoGoUi.dp(this, 16);
        root.setPadding(pad, GoGoUi.dp(this, 12), pad, GoGoUi.dp(this, 14));
        root.setFocusableInTouchMode(true);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        root.addView(GoGoUi.backButton(this, v -> finish()), GoGoUi.matchWrap());
        root.addView(GoGoUi.eyebrow(this, "PLACES  /  OFFLINE SEARCH"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.heroTitle(this, "国家与城市"), GoGoUi.matchWrap());
        root.addView(GoGoUi.subtitle(this,
                "国旗代表国家，点击右侧 ＋ 展开城市；可搜索、选点和收藏。选点不会自动启动模拟。"),
                GoGoUi.matchWrap());
        root.addView(GoGoUi.gap(this, 12));

        LinearLayout searchRow = GoGoUi.row(this);
        searchField = new EditText(this);
        searchField.setSingleLine(true);
        searchField.setHint("搜索国家或城市");
        searchField.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        GoGoUi.styleInput(searchField);
        searchField.setText(initialSearch);
        searchField.setContentDescription("搜索国家与城市");
        searchRow.addView(searchField, GoGoUi.weighted());
        GoGoUi.addHorizontalGap(this, searchRow, 8);
        clearSearchButton = GoGoUi.textButton(this, "×", v -> {
            searchField.setText("");
            searchField.clearFocus();
            root.requestFocus();
        });
        clearSearchButton.setContentDescription("清空搜索关键词");
        clearSearchButton.setVisibility(initialSearch.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        searchRow.addView(clearSearchButton, new LinearLayout.LayoutParams(
                GoGoUi.dp(this, 48), ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(searchRow, GoGoUi.matchWrap());
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
        // Thin right-hand A-Z rail stays visible independently of the list
        // scroll. On compact phones the rail itself scrolls instead of
        // shrinking 20+ tap targets below usable size.
        LinearLayout listWithIndex = GoGoUi.row(this);
        listWithIndex.setGravity(Gravity.TOP);
        listWithIndex.addView(resultsList, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        alphabetColumn = new LinearLayout(this);
        alphabetColumn.setOrientation(LinearLayout.VERTICAL);
        alphabetScroll = new ScrollView(this);
        alphabetScroll.setFillViewport(false);
        alphabetScroll.setVerticalScrollBarEnabled(false);
        alphabetScroll.setContentDescription("按国家拼音首字母快速跳转");
        alphabetScroll.addView(alphabetColumn);
        listWithIndex.addView(alphabetScroll, new LinearLayout.LayoutParams(
                GoGoUi.dp(this, 36), ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(listWithIndex, new LinearLayout.LayoutParams(
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
                clearSearchButton.setVisibility(s.length() == 0
                        ? View.INVISIBLE : View.VISIBLE);
                refreshResults();
                if (resultsList != null) resultsList.setSelection(0);
            }
            @Override public void afterTextChanged(Editable editable) {}
        });
        root.requestFocus();
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
        favoriteSnapshot = LabStore.getFavorites(this);
        Map<String, List<PlaceOption>> groups = new LinkedHashMap<>();
        int matches = 0;

        if (favoritesOnly) {
            for (LabStore.SavedPoint point : favoriteSnapshot) {
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

        // Country ordering is stable Hanyu Pinyin, rather than the old
        // source-data insertion order. Track the actual header position so
        // the rail remains correct after expanding/collapsing city sections.
        List<String> orderedCountries = new ArrayList<>(groups.keySet());
        Collections.sort(orderedCountries, LabPlaceAlphabet.COUNTRY_ORDER);
        letterAnchors.clear();
        for (String country : orderedCountries) {
            String letter = LabPlaceAlphabet.initialOf(country);
            if (!letterAnchors.containsKey(letter)) {
                letterAnchors.put(letter, displayed.size());
            }
            List<PlaceOption> cities = groups.get(country);
            boolean expanded = isExpanded(country, searching);
            displayed.add(PickerRow.country(country, cities.size(), expanded));
            if (expanded) {
                for (PlaceOption option : cities) displayed.add(PickerRow.city(option));
            }
        }

        allTab.setText("全部预设 · " + LabLocationPresets.size()
                + (favoritesOnly ? "" : " ✓"));
        favoritesTab.setText("我的收藏 · " + favoriteSnapshot.size()
                + (favoritesOnly ? " ✓" : ""));
        allTab.setContentDescription("全部预设，" + LabLocationPresets.size()
                + " 个地点，" + (favoritesOnly ? "未选中" : "已选中"));
        favoritesTab.setContentDescription("我的收藏，" + favoriteSnapshot.size()
                + " 个地点，" + (favoritesOnly ? "已选中" : "未选中"));
        resultsLabel.setText(groups.size() + " 个国家 / 分类 · " + matches
                + " 个地点" + (searching ? " · 已展开搜索结果" : " · 拼音 A–Z · 点＋看城市"));
        updateAlphabetRail(searching);
        boolean isEmpty = groups.isEmpty();
        emptyLabel.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        emptyLabel.setText(favoritesOnly
                ? "还没有符合条件的收藏。切到「全部预设」点星号即可收藏。"
                : "没有匹配结果，试试输入国家名、城市名，或清空搜索。");
        adapter.notifyDataSetChanged();
    }

    private void updateAlphabetRail(boolean searching) {
        if (alphabetScroll == null || alphabetColumn == null) return;
        // Search results already narrow to matching cities. Do not waste
        // screen width with an index when typing or viewing only 1 category.
        boolean show = !searching && letterAnchors.size() > 1;
        alphabetScroll.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;

        StringBuilder signature = new StringBuilder();
        for (String letter : letterAnchors.keySet()) signature.append(letter).append(',');
        String keys = signature.toString();
        if (keys.equals(renderedAlphabet)) return;
        renderedAlphabet = keys;
        alphabetColumn.removeAllViews();
        for (String letter : letterAnchors.keySet()) {
            TextView shortcut = GoGoUi.muted(this, letter);
            shortcut.setGravity(Gravity.CENTER);
            shortcut.setTextSize(12);
            shortcut.setMinimumHeight(GoGoUi.dp(this, 24));
            shortcut.setContentDescription("跳转到拼音首字母 " + letter + " 的国家");
            shortcut.setOnClickListener(v -> {
                Integer headerPosition = letterAnchors.get(letter);
                if (headerPosition != null && resultsList != null) {
                    resultsList.setSelectionFromTop(headerPosition, 0);
                }
            });
            alphabetColumn.addView(shortcut, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    GoGoUi.dp(this, 24)));
        }
        alphabetScroll.scrollTo(0, 0);
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

    // Cache favorites once per filter/update. Loading SharedPreferences JSON
    // inside every ListView.getView() causes repeated disk-backed parses.
    private boolean isFavoriteInSnapshot(PlaceOption option) {
        for (LabStore.SavedPoint saved : favoriteSnapshot) {
            if (LabPlaceCoordinateMatch.same(saved.longitude, saved.latitude,
                    option.longitude, option.latitude)) {
                return true;
            }
        }
        return false;
    }

    private static final class CountryHolder {
        final LinearLayout row;
        final TextView title;
        final TextView count;
        final MaterialButton toggle;
        CountryHolder(LinearLayout row, TextView title, TextView count, MaterialButton toggle) {
            this.row = row;
            this.title = title;
            this.count = count;
            this.toggle = toggle;
        }
    }

    private static final class CityHolder {
        final LinearLayout row;
        final TextView title;
        final TextView coordinates;
        final MaterialButton star;
        CityHolder(LinearLayout row, TextView title, TextView coordinates, MaterialButton star) {
            this.row = row;
            this.title = title;
            this.coordinates = coordinates;
            this.star = star;
        }
    }

    private final class PickerAdapter extends BaseAdapter {
        private static final int TYPE_COUNTRY = 0;
        private static final int TYPE_CITY = 1;

        @Override public int getCount() { return displayed.size(); }
        @Override public Object getItem(int position) { return displayed.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int position) {
            return displayed.get(position).isCountry() ? TYPE_COUNTRY : TYPE_CITY;
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            PickerRow entry = displayed.get(position);
            return entry.isCountry() ? bindCountry(entry, convertView)
                    : bindCity(entry.place, convertView);
        }

        private View bindCountry(PickerRow entry, View recycled) {
            CountryHolder holder;
            if (recycled == null) {
                LinearLayout row = GoGoUi.row(LabPlacePickerActivity.this);
                row.setMinimumHeight(GoGoUi.dp(LabPlacePickerActivity.this, 60));
                row.setPadding(GoGoUi.dp(LabPlacePickerActivity.this, 12),
                        GoGoUi.dp(LabPlacePickerActivity.this, 6),
                        GoGoUi.dp(LabPlacePickerActivity.this, 8),
                        GoGoUi.dp(LabPlacePickerActivity.this, 6));
                row.setBackground(GoGoUi.card(LabPlacePickerActivity.this).getBackground());
                TextView title = GoGoUi.sectionTitle(LabPlacePickerActivity.this, "");
                title.setTextSize(15);
                title.setPadding(0, 0, 0, 0);
                title.setSingleLine(true);
                title.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(title, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView count = GoGoUi.muted(LabPlacePickerActivity.this, "");
                count.setSingleLine(true);
                row.addView(count, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                GoGoUi.addHorizontalGap(LabPlacePickerActivity.this, row, 8);
                MaterialButton toggle = GoGoUi.secondaryButton(LabPlacePickerActivity.this,
                        "+", v -> {});
                toggle.setTextSize(19);
                row.addView(toggle, new LinearLayout.LayoutParams(
                        GoGoUi.dp(LabPlacePickerActivity.this, 48),
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                holder = new CountryHolder(row, title, count, toggle);
                row.setTag(holder);
            } else {
                holder = (CountryHolder) recycled.getTag();
            }

            holder.title.setText(LabPlaceCountries.flagOf(entry.country) + "  " + entry.country);
            holder.count.setText(entry.count + " 个");
            holder.toggle.setText(entry.expanded ? "−" : "+");
            holder.toggle.setContentDescription((entry.expanded ? "收起" : "展开")
                    + entry.country + "的" + entry.count + "个地点");
            holder.toggle.setOnClickListener(v -> toggleCountry(entry.country));
            holder.row.setContentDescription(entry.country + "，" + entry.count
                    + "个地点，" + (entry.expanded ? "已展开" : "已收起"));
            holder.row.setOnClickListener(v -> toggleCountry(entry.country));
            return holder.row;
        }

        private View bindCity(PlaceOption option, View recycled) {
            CityHolder holder;
            if (recycled == null) {
                LinearLayout row = GoGoUi.row(LabPlacePickerActivity.this);
                row.setMinimumHeight(GoGoUi.dp(LabPlacePickerActivity.this, 58));
                row.setPadding(GoGoUi.dp(LabPlacePickerActivity.this, 24),
                        GoGoUi.dp(LabPlacePickerActivity.this, 4),
                        GoGoUi.dp(LabPlacePickerActivity.this, 8),
                        GoGoUi.dp(LabPlacePickerActivity.this, 4));
                row.setBackground(GoGoUi.card(LabPlacePickerActivity.this).getBackground());
                LinearLayout info = new LinearLayout(LabPlacePickerActivity.this);
                info.setOrientation(LinearLayout.VERTICAL);
                TextView title = GoGoUi.sectionTitle(LabPlacePickerActivity.this, "");
                title.setTextSize(15);
                title.setPadding(0, 0, 0, 0);
                title.setSingleLine(true);
                title.setEllipsize(TextUtils.TruncateAt.END);
                info.addView(title, GoGoUi.matchWrap());
                TextView coordinate = GoGoUi.muted(LabPlacePickerActivity.this, "");
                coordinate.setSingleLine(true);
                coordinate.setEllipsize(TextUtils.TruncateAt.END);
                info.addView(coordinate, GoGoUi.matchWrap());
                row.addView(info, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                MaterialButton star = GoGoUi.textButton(LabPlacePickerActivity.this,
                        "☆", v -> {});
                row.addView(star, new LinearLayout.LayoutParams(
                        GoGoUi.dp(LabPlacePickerActivity.this, 52),
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                holder = new CityHolder(row, title, coordinate, star);
                row.setTag(holder);
            } else {
                holder = (CityHolder) recycled.getTag();
            }

            holder.title.setText(LabPlaceCountries.cityOf(option.label));
            holder.coordinates.setText(String.format(Locale.US, "%.5f, %.5f",
                    option.longitude, option.latitude));
            boolean saved = isFavoriteInSnapshot(option);
            holder.star.setText(saved ? "★" : "☆");
            holder.star.setContentDescription((saved ? "取消收藏" : "收藏") + option.label);
            holder.star.setOnClickListener(v -> {
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
            holder.row.setContentDescription("选择城市：" + option.label);
            holder.row.setOnClickListener(v -> choose(option));
            return holder.row;
        }
    }
}
