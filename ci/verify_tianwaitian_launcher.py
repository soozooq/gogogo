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
