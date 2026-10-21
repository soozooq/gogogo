#!/usr/bin/env python3
"""Static V3.1 allowlist/independent-demo invariants; not a device PASS."""

from pathlib import Path
import re
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parent.parent
ANDROID = "{http://schemas.android.com/apk/res/android}"
HOST = ROOT / "app/src/main/java/com/zcshou/gogogo"
V31 = HOST / "virtualv0"
DEMO = ROOT / "externalDemo"
DEMO_PACKAGE = "com.soozooq.tianwaitian.externaldemo"


def require(ok, detail):
    if not ok:
        raise SystemExit("V3.1 contract FAILED: " + detail)


def read(path):
    require(path.is_file(), "missing file: " + str(path.relative_to(ROOT)))
    return path.read_text(encoding="utf-8")


def static_method_body(source, name):
    """Read one Java helper body while ignoring braces in comments/strings."""
    declaration = re.search(r"(?:public|private)\s+static\s+\S+\s+" + re.escape(name)
                            + r"\s*\([^)]*\)[^{]*\{", source)
    require(declaration is not None, "missing static helper " + name)
    start = declaration.end()
    depth = 1
    token_pattern = r'''"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|//[^\n]*|/\*[\s\S]*?\*/|[{}]'''
    for token in re.finditer(token_pattern, source[start:]):
        if token.group() == "{":
            depth += 1
        elif token.group() == "}":
            depth -= 1
            if depth == 0:
                return source[start:start + token.start()]
    require(False, "unclosed static helper " + name)


settings = read(ROOT / "settings.gradle")
host_gradle = read(ROOT / "app/build.gradle")
demo_gradle = read(DEMO / "build.gradle")
require("':externalDemo'" in settings, "external APK must have its own Gradle module")
require("com.android.application" in demo_gradle and DEMO_PACKAGE in demo_gradle,
        "demo must build an Android application with a distinct package")
require("project(':app')" not in demo_gradle and "project(':containerFixture')" not in demo_gradle,
        "external demo cannot compile against host or historical fixture")
require(re.search(r"(?:implementation|api|compileOnly|runtimeOnly)\s*\(?\s*project\s*\(\s*['\"]:externalDemo",
                  host_gradle) is None,
        "host cannot directly depend on compiled demo classes")
require('applicationId "com.soozooq.gogogo.test"' in host_gradle,
        "existing host application ID changed")
version = re.search(r"versionCode\s+(\d+)", host_gradle)
require(version is not None and int(version.group(1)) > 2400093,
        "host versionCode must increase for in-place upgrade")
require("generateContainerV31Trust" in host_gradle
        and "ContainerV31BuildTrust.java" in host_gradle
        and "external-demo-trust.json" in host_gradle
        and "SHA-256" in host_gradle
        and ":externalDemo:assembleDebug" in host_gradle,
        "host must build an exact-SHA allowlist from the independently built demo")

demo_manifest = ET.parse(DEMO / "src/main/AndroidManifest.xml").getroot()
require(not demo_manifest.findall("uses-permission"), "controlled demo must remain permissionless")
demo_app = demo_manifest.find("application")
require(demo_app is not None, "demo application missing")
components = list(demo_app)
require(len(components) == 1 and components[0].tag == "activity"
        and components[0].get(ANDROID + "name") in (".DemoActivity", DEMO_PACKAGE + ".DemoActivity"),
        "standalone demo must declare exactly its own DemoActivity")
host_manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
host_app = host_manifest.find("application")
require(host_app is not None, "host application missing")
activities = {item.get(ANDROID + "name"): item for item in host_app.findall("activity")}
proxy = activities.get(".VirtualContainerV31ProxyActivity")
require(proxy is not None and proxy.get(ANDROID + "exported") == "false"
        and not proxy.findall("intent-filter"),
        "V3.1 must use a private Host Activity without external intent filters")
demo_entry = read(DEMO / "src/main/java/com/soozooq/tianwaitian/externaldemo/DemoEntry.java")
demo_layout = read(DEMO / "src/main/res/layout/demo_panel.xml")
strings = ET.parse(DEMO / "src/main/res/values/strings.xml").getroot()
resource_values = {item.get("name"): item.text for item in strings.findall("string")}
require(resource_values.get("demo_resource_marker") == "TWT_EXTERNAL_DEMO_V31_RESOURCE_1",
        "external resource provenance marker missing")
for widget in ("Button", "TextView"):
    require("<android.widget." + widget in demo_layout,
            "demo XML must use framework widgets, not host AppCompat inflation")
for method in ("createView()", "getClickCount()", "onHostLifecycle(String", "dispose()",
               "saveState()", "restoreState(Bundle", "handleBack()", "getInteractionCount()"):
    require(method in demo_entry, "demo contract missing " + method)
require("setOnClickListener" in demo_entry
        and "R.layout.demo_panel" in demo_entry
        and "R.string.demo_resource_marker" in demo_entry
        and "LayoutInflater.from(" in demo_entry,
        "guest must create an interactive View from external APK resources")

activity = read(HOST / "VirtualContainerV31ProxyActivity.java")
loader = read(V31 / "ContainerV31TrustedDemo.java")
policy = read(V31 / "ContainerV31AdmissionPolicy.java")
resources = read(V31 / "ContainerV31GuestResources.java")
for token in ("Intent.ACTION_OPEN_DOCUMENT", "Intent.CATEGORY_OPENABLE",
              "ContainerV31TrustedDemo", "LAST_RESULT", "PREFS", "onDestroy"):
    require(token in activity, "V3.1 explicit-selection/lifecycle report missing " + token)
for token in ("openInputStream", "MAX_BYTES", "ZipFile", "SHA-256", "setReadOnly()",
              "getPackageArchiveInfo", "ContainerV31AdmissionPolicy", "DexClassLoader"):
    require(token in loader, "trusted importer is missing " + token)
require("getAssets().open" not in loader and "twt-fixture-v0.apk" not in loader,
        "V3.1 must import selected external bytes, never reuse an embedded APK")
require("ContainerV31BuildTrust.APK_SHA256" in loader + policy,
        "generated exact-content SHA must be enforced before code execution")
require(loader.index("matchesDigest") < loader.index("new DexClassLoader"),
        "exact-content admission must happen before constructing executable loader")
reopen = static_method_body(loader, "openImported")
prepare = static_method_body(loader, "prepareAndOpen")
approved_source = static_method_body(loader, "approvedSource")
require("hasImportedDemo(" in reopen and "approvedSource(" in reopen
        and "prepareAndOpen(" in reopen and "new FileInputStream(" in reopen
        and "DexClassLoader" not in reopen,
        "reopen must use the authorized private source and the shared full verifier")
require("matchesDigest" in prepare and "verifyArchive(" in prepare
        and "verifyPackage(" in prepare
        and loader.count("new DexClassLoader") == 1
        and prepare.index("matchesDigest") < prepare.index("new DexClassLoader")
        and prepare.index("verifyArchive(") < prepare.index("new DexClassLoader")
        and prepare.index("verifyPackage(") < prepare.index("new DexClassLoader"),
        "every executable copy must match exact pin and archive identity before DEX loading")
require("getFilesDir()" in approved_source and '"approved.apk"' in approved_source
        and "Bundle" not in loader and "APPROVED_SHA" in loader
        and "forgetImportedDemo" in loader,
        "persistent import must use a fixed private location and revocable SHA-bound approval")
require(DEMO_PACKAGE in policy and "DemoEntry" in policy,
        "admission must be restricted to the expected demo package and entry")
require("ResourcesProvider.loadFromApk" in resources
        and "ResourcesLoader" in resources
        and "Resources.getSystem().getAssets()" in resources
        and "removeLoaders" in resources
        and "clearProviders" in resources
        and "provider.close()" in resources,
        "V3.1 needs an external-only ResourcesLoader with explicit cleanup")
for forbidden in ("PackageInstaller", "ACTION_INSTALL_PACKAGE", "ACTION_MANAGE_UNKNOWN_APP_SOURCES",
                  "canRequestPackageInstalls", "REQUEST_INSTALL_PACKAGES", "Runtime.getRuntime",
                  "ProcessBuilder", "System.loadLibrary", "ServiceGo.class"):
    require(forbidden not in activity + loader + resources + demo_entry,
            "execution bridge must not install, elevate or change location: " + forbidden)

session_policy = read(V31 / "ContainerV32SessionPolicy.java")
require("ContainerV32SessionPolicy.isValid" in activity
        and "getExpectedSha256()" in activity
        and '"restoreState"' in activity and '"saveState"' in activity
        and '"handleBack"' in activity,
        "Host must restore bounded state bound to the current demo SHA and delegate guest back")
require('"getInteractionCount"' in activity and "newUserActions > 0" in activity
        and "clicks > baselineClicks" not in activity,
        "ordinary real edits/navigation must count as interaction, not require a counter-only ritual")
require("MAX_NOTE_LENGTH" in session_policy and "MAX_CLICKS" in session_policy
        and "SCHEMA" in session_policy and "instanceof Integer" in session_policy
        and "instanceof String" in session_policy,
        "saved session values must have an explicit schema, types and size bounds")
for forbidden in ("new DexClassLoader", "new FileInputStream", "Uri.parse(", "getParcelable(",
                  "putParcelable(", "Intent.parseUri("):
    require(forbidden not in activity,
            "saved UI state must not become a URI/path-based execution grant: " + forbidden)

v0 = read(HOST / "VirtualContainerV0Activity.java")
require("VirtualContainerV31ProxyActivity.PREFS" in v0
        and "VirtualContainerV31ProxyActivity.LAST_RESULT" in v0,
        "V3.1 must append to existing one-copy report")
require("advancedPanel.setVisibility(View.GONE)" in v0
        and "collectAllReports(Context context)" in v0
        and "collectRecentResults(context)" in v0,
        "daily runtime must use one copy/export while historical verification remains folded")
v3 = read(V31 / "ContainerV3ArchiveProbe.java")
for forbidden in ("ContainerV31TrustedDemo", "DexClassLoader", "startActivity("):
    require(forbidden not in v3, "arbitrary V3 imports must remain read-only: " + forbidden)

for workflow in ("build-check.yml", "build-release.yml"):
    source = read(ROOT / ".github/workflows" / workflow)
    require("ci/verify_container_fixture_contract.py" in source
            and "ci/verify_container_v31_contract.py" in source
            and "ci/verify_container_v31_artifacts.py" in source,
            workflow + " must retain old gates and add V3.1 source/artifact gates")
    require("ci/stage_phone_update.py" in source and "test_phone_update_package.py" in source,
            workflow + " must verify one-download pair packaging")
preview = read(ROOT / ".github/workflows/build-release.yml")
require("tianwaitian-phone-update" in preview
        and "steps.stage_phone_update.outcome == 'success'" in preview,
        "the simple phone package must be gated by verified staging")
print("Controlled runtime contract PASS: explicit import, reverified persistent demo, bounded "
      "SHA-bound session restore, folded historical tools and one phone download; "
      "arbitrary V3 APK inspection remains read-only.")
