#!/usr/bin/env python3
"""Static launcher regression guard (also checked in debug-preview CI).

This intentionally validates the SOURCE manifest before AAPT merges it. It is
not a substitute for verifying the installed APK or Android device behavior.
"""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
ANDROID = "{http://schemas.android.com/apk/res/android}"

def check(condition, message):
    if not condition:
        raise SystemExit("Tianwaitian launcher verification FAILED: " + message)

manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
application = manifest.find("application")
check(application is not None, "no application")
activities = application.findall("activity")
launchers = []
activity_names = []
for activity in activities:
    name = activity.get(ANDROID + "name")
    activity_names.append(name)
    for filter_tag in activity.findall("intent-filter"):
        actions = [a.get(ANDROID + "name") for a in filter_tag.findall("action")]
        categories = [c.get(ANDROID + "name") for c in filter_tag.findall("category")]
        if ("android.intent.action.MAIN" in actions
                and "android.intent.category.LAUNCHER" in categories):
            launchers.append(name)

check(launchers == [".TianwaitianHomeActivity"],
      "expected ONLY TianwaitianHomeActivity launcher, got " + repr(launchers))
check(".SimpleMockActivity" in activity_names, "simulation console missing")
check(".LabMapActivity" in activity_names, "MapLibre map tools missing")
check(".LabHubActivity" in activity_names, "diagnostics hub missing")

base = ROOT / "app/src/main/java/com/zcshou/gogogo"
home = (base / "TianwaitianHomeActivity.java").read_text(encoding="utf-8")
console = (base / "SimpleMockActivity.java").read_text(encoding="utf-8")
check("MapLibre.getInstance(this)" in home, "launcher must use MapLibre")
check("tiles.openfreemap.org/styles/liberty" in home,
      "launcher must use key-free OpenFreeMap; CI Tencent key is DUMMY")
check("new Intent(this, SimpleMockActivity.class)" in home,
      "no route from map launcher to simulation console")
check("EXTRA_PREFILL_LONGITUDE" in console and "EXTRA_PREFILL_LATITUDE" in console,
      "console is not wired to selected map coordinates")
check("if (savedInstanceState == null) {" in console
      and "acceptHomeLauncherCoordinates(getIntent())" in console
      and "outState.putString(HOME_LONGITUDE_TEXT" in console
      and "outState.putString(HOME_LATITUDE_TEXT" in console,
      "console may overwrite in-progress edits after rotation")
print("Tianwaitian launcher static verification PASS: "
      "single MapLibre home, console and diagnostics accessible.")

# The primary bottom navigation must be REAL, shared, and ordered as designed.
tab_menu = ET.parse(ROOT / "app/src/main/res/menu/twt_bottom_nav.xml").getroot()
tab_ids = [item.get(ANDROID + "id") for item in tab_menu.findall("item")]
expected_tabs = [
    "@+id/twt_tab_location",
    "@+id/twt_tab_diagnostics",
    "@+id/twt_tab_audit",
    "@+id/twt_tab_settings",
]
check(tab_ids == expected_tabs,
      "bottom tab order changed or destination missing: " + repr(tab_ids))
nav_source = (base / "TianwaitianBottomNav.java").read_text(encoding="utf-8")
check("BottomNavigationView" in nav_source and "LABEL_VISIBILITY_LABELED" in nav_source,
      "bottom navigation must be a native four-item labeled control")
check("FLAG_ACTIVITY_REORDER_TO_FRONT" in nav_source,
      "tab navigation must not stack duplicate activity instances")
check("host.removeView(page)" in nav_source and "shell.addView(page" in nav_source,
      "bottom navigation must reserve real viewport space, not overlay the map")
for cls, tab in [
        ("TianwaitianHomeActivity", "LOCATION"),
        ("LabHubActivity", "DIAGNOSTICS"),
        ("LabPrivacyAuditActivity", "AUDIT"),
        ("SettingsActivity", "SETTINGS")]:
    src = (base / (cls + ".java")).read_text(encoding="utf-8")
    check("TianwaitianBottomNav.attach(this, TianwaitianBottomNav.Tab." + tab + ")" in src,
          cls + " is missing its fixed bottom navigation")
check("全球地点库" in home and "模拟控制" in home,
      "home must retain its primary location-specific actions")
settings = (base / "SettingsActivity.java").read_text(encoding="utf-8")
check("SimpleMockActivity.class" in settings and "Shizuku" in settings,
      "settings must retain a direct route to VPN and Shizuku controls")

# Real device regression: a MenuItem's checked bit is not sufficient for
# Material NavigationBarView; its internal selectedItemId must also be reset.
check("nav.setSelectedItemId(selected.menuId)" in nav_source,
      "initial active tab must synchronize NavigationBarView selectedItemId")
check("nav.setSelectedItemId(current.menuId)" in nav_source,
      "resumed/reordered tabs must synchronize selectedItemId")
check("return false;" in nav_source.split("activity.startActivity(intent);", 1)[1],
      "outgoing tab must not briefly highlight its destination")
for cls, tab in [
        ("TianwaitianHomeActivity", "LOCATION"),
        ("LabHubActivity", "DIAGNOSTICS"),
        ("LabPrivacyAuditActivity", "AUDIT"),
        ("SettingsActivity", "SETTINGS")]:
    src = (base / (cls + ".java")).read_text(encoding="utf-8")
    check("TianwaitianBottomNav.syncSelection(this, TianwaitianBottomNav.Tab." + tab + ")" in src,
          cls + " must refresh its active tab when resumed")
check("stackTianwaitianLauncherActions" in home,
      "compact map launcher must use its own short-action sizing policy")
for activity in activities:
    if activity.get(ANDROID + "name") == ".SettingsActivity":
        check(activity.get(ANDROID + "theme") == "@style/AppTheme.NoActionBar",
              "Settings primary tab must not reintroduce the legacy blue ActionBar")
        break
else:
    check(False, "SettingsActivity is missing from the manifest")

print("Tianwaitian shared bottom navigation PASS: four pages, ordered tabs, "
      "back-safe switching, existing advanced controls available.")

# Map screen hierarchy: raw telemetry may still be read for debugging, but
# must not be a giant always-visible widget covering the actual map viewport.
map_ui = (base / "LabMapActivity.java").read_text(encoding="utf-8")
check("MapView(this)" in map_ui, "MapLibre route map must remain available")
check("0, 1f" in map_ui, "the map must retain weighted free space")
deck_start = map_ui.index("// Minimal command deck")
deck_end = map_ui.index("root.addView(controlCard", deck_start)
deck_code = map_ui[deck_start:deck_end]
check("control.addView(liveView" not in deck_code,
      "raw per-tick logs must not consume the default map deck")
check("liveView.setText(sb.toString())" in map_ui
      and "查看实时原始数据" in map_ui,
      "raw telemetry must remain available on demand")
check("模拟这里" in deck_code and "填入首页" in deck_code
      and "路线设置" in deck_code and "漫游设置" in deck_code,
      "must retain map mock, selection and motion controls")

# The simulation console retains its unique controls but does not duplicate
# the app's primary map, diagnostics or network-audit navigation destinations.
check('GoGoUi.sectionTitle(this, "常用入口")' not in console,
      "sim console must not duplicate the four primary tabs")
check("VPN_DETAILS_EXPANDED" in console
      and "vpnDetailsPanel.addView(vpnSyncToggle" in console
      and "vpnDetailsPanel.addView(vpnFreezeToggle" in console
      and "vpnDetailsPanel.addView(systemTimeZoneToggle" in console,
      "VPN/lock/Shizuku settings must remain reachable behind disclosure")
check("LabSimAttributionActivity.class" in console,
      "non-primary SIM attribution utility must remain accessible")
check("LabSupportLog.copy(this," not in console,
      "diagnostic export must live in Diagnostics, not every screen")

# A single-open accordion prevents every diagnostics group displaying at once.
hub = (base / "LabHubActivity.java").read_text(encoding="utf-8")
check("if (opening) collapseAllAdvanced();" in hub
      and "收起全部高级工具" in hub
      and "groupTitles[i], groupCounts[i], false" in hub,
      "diagnostic advanced accordion or collapse-all is missing")
# Daily feedback needs no manual experiment or prior saved result. Keep the
# cache-only export off the UI thread and specialist tools behind one disclosure.
daily_hub = hub[hub.index("private void buildUi() {"):hub.index("private void setAdvancedExpanded(")]
for specialist in ("LabQuickCheckActivity.class", "ConsumerLocationProbeActivity.class",
                   "LabAutoDiagnosticActivity.class", "VirtualContainerV0Activity.class"):
    check(specialist not in daily_hub, "daily diagnostics must not require specialist navigation")
check("advancedPanel.getChildCount() == 0" in hub and "buildAdvancedTools(advancedPanel)" in hub
      and "LabSupportLog.generateForFeedback(app)" in hub
      and "resumed && hasWindowFocus()" in hub and "exportWorker.shutdownNow()" in hub,
      "simple diagnostics must lazily retain tools and export with foreground clipboard ownership")
support_snapshot = (base / "LabSupportSnapshot.java").read_text(encoding="utf-8")
for mutation in ("requestLocationUpdates(", "getCurrentLocation(", "requestPermissions(",
                 "setMockMode(", "setMockLocation(", "startService(", "stopService("):
    check(mutation not in support_snapshot, "support export must not mutate location/service: " + mutation)
check("getLastKnownLocation(provider)" in support_snapshot
      and ".getLastLocation(), 2, TimeUnit.SECONDS" in support_snapshot
      and "real_coordinates=REDACTED" in support_snapshot,
      "one-copy feedback must read bounded existing caches without exposing real coordinates")
print("Tianwaitian streamlined layout checks PASS: map retains space, "
      "simulation controls preserved, advanced views de-duplicated.")

# The desktop launcher icon must not silently regress to the inherited orange
# GoGoGo pin. Verify standard/round densities AND modern adaptive/themed icons.
from struct import unpack
app_icon = application.get(ANDROID + "icon")
app_round_icon = application.get(ANDROID + "roundIcon")
check(app_icon == "@mipmap/ic_launcher" and
      app_round_icon == "@mipmap/ic_launcher_round",
      "manifest must point at both Tianwaitian launcher icon resources")
for name in ("ic_launcher", "ic_launcher_round"):
    xml = ET.parse(ROOT / ("app/src/main/res/mipmap-anydpi-v26/" + name + ".xml"))
    drawables = {elem.tag: elem.get(ANDROID + "drawable") for elem in xml.getroot()}
    check(drawables.get("foreground") == "@drawable/ic_launcher_foreground" and
          drawables.get("background") == "@drawable/ic_launcher_background" and
          drawables.get("monochrome") == "@drawable/ic_launcher_monochrome",
          name + " must include new foreground, gradient and monochrome")
    for density, dimension in (("mdpi", 48), ("hdpi", 72),
                               ("xhdpi", 96), ("xxhdpi", 144),
                               ("xxxhdpi", 192)):
        image = ROOT / ("app/src/main/res/mipmap-" + density + "/" + name + ".png")
        data = image.read_bytes()
        check(len(data) > 1000 and data[:8] ==
              bytes([137, 80, 78, 71, 13, 10, 26, 10]),
              str(image) + " missing or not valid PNG signature")
        check(unpack(">II", data[16:24]) == (dimension, dimension),
              str(image) + " incorrect density/dimensions")
foreground = (ROOT / "app/src/main/res/drawable/ic_launcher_foreground.xml"
              ).read_text(encoding="utf-8")
check("#FC9637" not in foreground and "M30,74 L43,47" in foreground,
      "old orange-pin foreground reintroduced")
check((ROOT / "app/src/main/res/drawable/ic_launcher_monochrome.xml").exists(),
      "Android 13 themed icon missing")

# Country/city expansion is additive, with documented source and preserved
# default coordinates. Unit tests also check uniqueness and flag availability.
source = (ROOT / "docs/data/tianwaitian-natural-earth-places-2026.tsv"
          ).read_text(encoding="utf-8")
new_places = [ln for ln in source.splitlines() if ln.strip() and not ln.startswith("#")]
check(len(new_places) == 106, "Natural Earth expansion should have 106 points")
presets_java = (base / "LabLocationPresets.java").read_text(encoding="utf-8")
check(presets_java.count("points.add(new Preset(") == 263,
      "expected 263 offline city presets")
check("缅甸 · 妙瓦底（默认）" in presets_java,
      "original default Myawaddy position must be preserved")
# Every new country must also be indexable by its Chinese Hanyu Pinyin
# initial, otherwise the offline A-Z country rail silently drops its groups.
import re
labels = re.findall(r'points\.add\(new Preset\("([^"]+)"', presets_java)
countries = {label.split(" · ", 1)[0] for label in labels}
index_source = (base / "LabPlaceAlphabet.java").read_text(encoding="utf-8")
indexed = set(re.findall(r'values\.put\("([^"]+)", "[a-z]+"\)', index_source))
check(len(countries) == 193 and countries.issubset(indexed),
      "missing country Pinyin A-Z index: " + repr(sorted(countries - indexed)))
print("Tianwaitian icon/country regression checks PASS: "
      "10 legacy PNGs, 2 adaptive XMLs, themed monochrome, 263 locations.")

# Unified search: the launcher field must open an internal explicit-submit
# screen, results are passed back to the map and NEVER start mock injection.
search_ui = (base / "TianwaitianSearchActivity.java").read_text(encoding="utf-8")
geocoder = (base / "TianwaitianGeocoder.java").read_text(encoding="utf-8")
query_parser = (base / "TianwaitianSearchQuery.java").read_text(encoding="utf-8")
check("unifiedSearch.launch(" in home
      and "new Intent(this, TianwaitianSearchActivity.class)" in home,
      "map launcher missing new unified search field")
check("registerForActivityResult(" in home
      and "data.getStringExtra(LabPlacePickerActivity.EXTRA_LABEL)" in home,
      "searched coordinates must be returned to the map without launching mock")
check(".TianwaitianSearchActivity" in activity_names,
      "unified search Activity must be declared in AndroidManifest")
check("new ActivityResultContracts.StartActivityForResult()" in search_ui
      and "presetPicker.launch(" in search_ui,
      "full offline place catalog must return a valid selected point")
check("queryField.addTextChangedListener" in search_ui
      and "renderOffline(s.toString())" in search_ui,
      "offline suggestions must be local and immediate")
watcher = search_ui.split("queryField.addTextChangedListener", 1)[1].split(
    "queryField.setOnEditorActionListener", 1)[0]
check("TianwaitianGeocoder.search" not in watcher
      and "startOnlineSearch()" not in watcher,
      "Nominatim autocomplete/online typing requests are prohibited")
check("TianwaitianGeocoder.search" in search_ui
      and 'GoGoUi.primaryButton(this, "搜索全球地图（联网）"' in search_ui,
      "online geocoding must be accessible only via explicit submission")
check("EXTRA_LONGITUDE" in search_ui and "setResult(Activity.RESULT_OK" in search_ui
      and "ServiceGo" not in search_ui,
      "search results should fill WGS-84 target, not start mock simulation")
check("1200" in geocoder and "RATE_LOCK" in geocoder
      and "User-Agent" in geocoder and "Accept-Language" in geocoder,
      "geocoder must identify requests and limit request frequency")
check('7L * 24 * 60 * 60 * 1000' in geocoder
      and "MAX_CACHE_ENTRIES" in geocoder and "clearCache" in geocoder,
      "geocoder must avoid redundant repeated calls and offer cache clearing")
check("DEFAULT_ENDPOINT" in geocoder and "setEndpoint" in geocoder
      and '"https".equalsIgnoreCase' in geocoder,
      "geocoding provider must be switchable to a trusted HTTPS service")
check("OpenStreetMap contributors" in search_ui
      and "openstreetmap.org/copyright" in search_ui,
      "search results must provide visible map-data attribution")
check("LabHomeCoordinates.parse" in query_parser
      and "coordinatesOrNull" in query_parser,
      "typed coordinates must use the same strict boundary as home")
print("Tianwaitian unified search checks PASS: offline/coordinates/local callback, "
      "explicit online queries, cache, switchable HTTPS provider and attribution.")

# A real vivo test surfaced excessive technical UI in the cross-app evidence
# tool. The ordinary workflow must show three steps and a cautious Chinese
# summary while preserving the opt-in expert form and historical raw logs.
cross_app = (base / "LabCrossAppLocationActivity.java").read_text(encoding="utf-8")
readable = (base / "LabCrossAppReadableSummary.java").read_text(encoding="utf-8")
check("三步完成一次对比" in cross_app
      and "startQuickRound()" in cross_app
      and "saveQuickObservation()" in cross_app,
      "cross-app quick comparison must have the 3-step workflow")
check("advancedPanel.addView(card" in cross_app
      and "advancedExpanded" in cross_app
      and 'passive("BEFORE")' in cross_app
      and 'passive("AFTER")' in cross_app,
      "advanced controls and before/after passive evidence must be preserved")
check("rawLogExpanded" in cross_app
      and "reportView.setVisibility" in cross_app
      and 'getString(KEY_LOG' in cross_app,
      "technical logs must be preserved but folded by default")
check("LabCrossAppReadableSummary.summarize" in cross_app
      and "不能据此断言" in readable
      and "微信实际使用的定位来源仍未知" in readable,
      "readable outcome must avoid unproved WeChat SDK or cache attribution")
check("ServiceGo" not in cross_app, "UI simplification must not touch the mock engine")
print("Tianwaitian cross-app compact mode PASS: three-step flow, "
      "readable evidence summary and preserved raw diagnostics.")

# The Android 13 device exposed a real bug: picking Beijing in the console
# left the launcher map at its hardcoded Myawaddy start location. A shared
# selection store must be written at explicit selection boundaries and read
# both after launcher process recreation and after returning from other tabs.
target_store = (base / "TianwaitianSelectedTarget.java").read_text(encoding="utf-8")
console = (base / "SimpleMockActivity.java").read_text(encoding="utf-8")
map_tools = (base / "LabMapActivity.java").read_text(encoding="utf-8")
check("SharedPreferences" in target_store
      and 'PREFS = "twt_selected_target_v1"' in target_store
      and "LabHomeCoordinates.parse" in target_store
      and "ServiceGo" not in target_store.split("public final class", 1)[1],
      "selected target must be persisted and strictly separate from ServiceGo")
check("TianwaitianSelectedTarget.read(this)" in home
      and "TianwaitianSelectedTarget.save(" in home
      and "syncSelectedTarget();" in home
      and "applySelectedTarget(target, changed)" in home,
      "launcher must persist and rehydrate chosen map target")
check("EXTRA_PREFILL_LABEL" in home
      and "TianwaitianSelectedTarget.save(" in console
      and "TianwaitianSelectedTarget.Target shared" in console
      and "selected.longitude, selected.latitude, place" in console,
      "console picker must synchronize selected Beijing label and WGS-84 coordinates")
check("TianwaitianSelectedTarget.save(" in map_tools,
      "map tools should preserve explicit city selections across Activities")
check("pauseMockCameraFollowForManualSelection();" in map_tools
      and "followMock = false;" in map_tools
      and "followHeading = false;" in map_tools,
      "manually selected Beijing must not be hidden by previous live mock camera follow")
check("EXTRA_RESULT_LABEL" in map_tools
      and "data.getStringExtra(LabMapActivity.EXTRA_RESULT_LABEL)" in home
      and "data.getStringExtra(LabMapActivity.EXTRA_RESULT_LABEL)" in console,
      "map return must preserve the selected Beijing label on both screens")
check("已选目标 · " in home
      and "未自动改变正在运行的模拟" in home,
      "map must visibly distinguish selected destination from active simulation")
print("Tianwaitian selected-target persistence checks PASS: Beijing across "
      "home/console/map tools, Activity restore, mock publishing still explicit.")
