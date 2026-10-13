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
check("savedInstanceState == null) acceptHomeLauncherCoordinates" in console,
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
print("Tianwaitian shared bottom navigation PASS: four pages, ordered tabs, "
      "back-safe switching, existing advanced controls available.")
