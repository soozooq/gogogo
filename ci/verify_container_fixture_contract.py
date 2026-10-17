#!/usr/bin/env python3
"""Static V0 fixture/IPC invariants; does not replace Android build or phone tests."""

from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
ANDROID = "{http://schemas.android.com/apk/res/android}"


def require(ok: bool, detail: str) -> None:
    if not ok:
        raise SystemExit("V0 fixture contract FAILED: " + detail)


fixture_manifest = ET.parse(ROOT / "containerFixture/src/main/AndroidManifest.xml").getroot()
require(not fixture_manifest.findall("uses-permission"), "fixture unexpectedly asks for permission")
fixture_app = fixture_manifest.find("application")
require(fixture_app is not None, "no fixture application")
# V2 fixture declares exactly ONE private Activity, for offline archive inspection;
# no guest component may be exported, have an intent filter or be auto-started.
fixture_children = list(fixture_app)
require(len(fixture_children) == 1 and fixture_children[0].tag == "activity",
        "V2 fixture must declare exactly one private Activity and no other components")
guest_declared = fixture_children[0]
require(guest_declared.get(ANDROID + "name") == ".GuestV2DeclaredActivity"
        and guest_declared.get(ANDROID + "exported") == "false"
        and guest_declared.get(ANDROID + "permission") is None
        and len(list(guest_declared)) == 0,
        "guest archive Activity must be private, permissionless and without intent filters")

main_manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
app = main_manifest.find("application")
require(app is not None, "host application missing")
services = {item.get(ANDROID + "name"): item
            for item in app.findall("service")}
for name, process in [
    (".virtualv0.VirtualContainerV0IpcService", ":virtualv0"),
    (".virtualv0.VirtualContainerV0IpcServiceB", ":virtualv0b"),
]:
    item = services.get(name)
    require(item is not None, "missing private service " + name)
    require(item.get(ANDROID + "exported") == "false",
            name + " must remain non-exported")
    require(item.get(ANDROID + "process") == process,
            name + " must use distinct sub-process")

settings = (ROOT / "settings.gradle").read_text(encoding="utf-8")
require("':containerFixture'" in settings, "separate APK module missing")
host_gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
fixture_gradle = (ROOT / "containerFixture/build.gradle").read_text(encoding="utf-8")
require('applicationId "com.soozooq.gogogo.test"' in host_gradle,
        "host package changed unexpectedly")
require("applicationId 'com.soozooq.tianwaitian.fixture'" in fixture_gradle,
        "fixture package changed unexpectedly")
require("stageContainerV0Fixture" in host_gradle
        and "twt-fixture-v0.apk" in host_gradle,
        "controlled asset staging disappeared")
require("applicationIdSuffix" not in host_gradle,
        "temporary parallel-install suffix must not return to main")

guest_entry = (ROOT / "containerFixture/src/main/java/com/soozooq/tianwaitian/fixture/GuestEntry.java").read_text(encoding="utf-8")
require("String record(String value)" in guest_entry and "String read()" in guest_entry,
        "fixture entrypoint API changed")
host_source = ROOT / "app/src/main/java/com/zcshou/gogogo/virtualv0"
loader = (host_source / "ContainerV0TrustedGuest.java").read_text(encoding="utf-8")
require('ASSET = "twt-fixture-v0.apk"' in loader
        and 'PACKAGE = "com.soozooq.tianwaitian.fixture"' in loader,
        "loader/fixture identity mismatch")
require("setReadOnly()" in loader and "DexClassLoader" in loader,
        "dynamic code read-only guard or loader missing")
service = (host_source / "VirtualContainerV0IpcService.java").read_text(encoding="utf-8")
require("MSG_GUEST_WRITE" in service and "MSG_GUEST_READ" in service
        and "ContainerV0TrustedGuest.open(this)" in service,
        "remote fixture handling missing")
require("HandlerThread" in service, "guest loading may block service main thread")

screen = (ROOT / "app/src/main/java/com/zcshou/gogogo/VirtualContainerV0Activity.java").read_text(encoding="utf-8")
require("验证独立 APK 在双进程执行" in screen,
        "remote APK experiment entry missing")
require("runRemoteGuestProbe()" in screen,
        "remote APK test button disconnected")
require("ServiceGo" not in screen, "test page must not drive mock-location service")
require("一键诊断 V0 五项" in screen and "runOneClick()" in screen,
        "one-click diagnosis must remain the default route")
require("v0_last_complete" in screen and "一键复制总报告" in screen,
        "user must be able to copy a single persisted report")
batch = (host_source / "VirtualContainerV0OneClickDiagnostic.java").read_text(encoding="utf-8")
require("VirtualContainerV0TwoSlotProbe" in batch
        and "ContainerV0ExternalApkProbe.run(app)" in batch
        and "ContainerV0Probe.run(app)" in batch
        and "TIMEOUTS_MS" in batch
        and "cleanupConnections()" in batch,
        "batch probe missing individual timeout/cleanup/one of five tests")

v1name = ".VirtualContainerV1ProxyActivity"
v1_activities = {item.get(ANDROID + "name"): item
                 for item in app.findall("activity")}
require(v1name in v1_activities and
        v1_activities[v1name].get(ANDROID + "exported") == "false",
        "V1 proxy must be declared and non-exported")
v1 = (ROOT / "app/src/main/java/com/zcshou/gogogo/VirtualContainerV1ProxyActivity.java").read_text(encoding="utf-8")
require("ContainerV0TrustedGuest.open(context)" in v1
        and "GuestLifecycleV1" in v1
        and 'forward("onPause")' in v1
        and 'forward("onDestroy")' in v1,
        "host proxy must forward real lifecycle events to own fixture APK")
fixture_v1 = (ROOT / "containerFixture/src/main/java/com/soozooq/tianwaitian/fixture/GuestLifecycleV1.java").read_text(encoding="utf-8")
require("class GuestLifecycleV1" in fixture_v1 and "onEvent(String event)" in fixture_v1,
        "guest lifecycle state machine missing from separate APK")

# V1 UI must be backed by code in the independently compiled fixture APK;
# host proxy may only receive the View and attach it to a private container.
fixture_ui = (ROOT / "containerFixture/src/main/java/com/soozooq/tianwaitian/fixture/GuestUiV1.java").read_text(encoding="utf-8")
require("class GuestUiV1" in fixture_ui
        and "View createView()" in fixture_ui
        and "setOnClickListener" in fixture_ui
        and "getClickCount()" in fixture_ui and "void dispose()" in fixture_ui,
        "independently packaged interactive guest View contract missing")
require("GuestUiV1" in v1 and "guestFrame.addView(guestView)" in v1,
        "host proxy must render the actual returned guest View")
require("onSaveInstanceState(Bundle outState)" in v1
        and "KEY_CLICKS" in v1 and "recreate()" in v1
        and "restoreVerified" in v1 and "guestDispose.invoke(guestUi)" in v1,
        "guest UI recreation and cleanup evidence missing")
require("VirtualContainerV0Activity" in screen
        or "VirtualContainerV1ProxyActivity" in screen,
        "V0/V1 navigation missing")
# API33 phone evidence (2026-10-17 21:34) proved that short XML <Button>
# was upgraded to AppCompatButton by the HOST Activity LayoutInflater.Factory2,
# which then rejected the deliberately framework-themed guest Context.
# Never route this controlled APK's XML through an Activity AppCompat factory.
guest_layout = (ROOT / "containerFixture/src/main/res/layout/guest_panel.xml").read_text(encoding="utf-8")
for widget in ("Button", "TextView", "ImageView"):
    require("<android.widget." + widget in guest_layout,
            "fixture XML must use fully-qualified framework " + widget)
require("<Button" not in guest_layout and "<TextView" not in guest_layout
        and "<ImageView" not in guest_layout,
        "AppCompat-inflatable shorthand XML widget was reintroduced")
require("LayoutInflater.from(hostContext.getApplicationContext())" in fixture_ui
        and "cloneInContext(this)" in fixture_ui
        and "AppCompat View leaked into guest XML" in fixture_ui,
        "guest inflater must not inherit host Activity AppCompat Factory2")

# Separate resources must live inside the fixture module, not host res/.
fixture_res = ROOT / "containerFixture/src/main/res"
for rel in ["values/strings.xml", "values/colors.xml",
            "layout/guest_panel.xml", "drawable/guest_mark.xml"]:
    require((fixture_res / rel).is_file(), "missing separate fixture resource: " + rel)
strings = ET.parse(fixture_res / "values/strings.xml").getroot()
markers = {item.get("name"): item.text for item in strings.findall("string")}
require(markers.get("guest_resource_marker") == "TWT_GUEST_ONLY_2_4_17",
        "missing explicit guest-only resource source marker")
require("com.soozooq.tianwaitian.fixture" in fixture_ui
        and "guestResources.getString(R.string.guest_resource_marker)" in fixture_ui
        and "inflate(R.layout.guest_panel, null, false)" in fixture_ui
        and "getDrawable(R.drawable.guest_mark" in fixture_ui
        and "fixture guest UI stage=" in fixture_ui,
        "guest must resolve own XML/string/drawable from scoped resource table")
resource_loader = (host_source / "ContainerV1GuestResources.java").read_text(encoding="utf-8")
require("ResourcesProvider.loadFromApk(fd)" in resource_loader
        and "new ResourcesLoader()" in resource_loader
        and "isolated.addLoaders(loader)" in resource_loader
        and "Resources.getSystem().getAssets()" in resource_loader
        and "host.getAssets()" not in resource_loader
        and "resources.removeLoaders(loader)" in resource_loader
        and "loader.clearProviders()" in resource_loader
        and resource_loader.index("loader.clearProviders();\n            provider.close()") > 0
        and "getVerifiedResourceApk()" in resource_loader,
        "missing API 30+ guest-only ResourcesLoader and cleanup")
require("ContainerV1GuestResources.open(this, readyGuest)" in v1
        and "guestResourcesVerified" in v1
        and "guestResourceEvidence" in v1
        and "resourceScope.close()" in v1
        and "describeFailure(uiFailure)" in v1
        and "viewCreated=false" in v1,
        "V1 resource verification, report or loader cleanup disconnected")



# V2: installed host proxy must not turn into an exported IPC endpoint.
v2_names = (".VirtualContainerV2Activity", ".VirtualContainerV2ProxyActivity")
for name in v2_names:
    require(name in v1_activities and
            v1_activities[name].get(ANDROID + "exported") == "false",
            "V2 host Activity must be registered and non-exported: " + name)
v2_guest_route = (ROOT / "containerFixture/src/main/java/com/soozooq/tianwaitian/fixture/GuestV2Route.java").read_text(encoding="utf-8")
v2_guest_activity = (ROOT / "containerFixture/src/main/java/com/soozooq/tianwaitian/fixture/GuestV2DeclaredActivity.java").read_text(encoding="utf-8")
v2_probe = (host_source / "ContainerV2ManifestRouteProbe.java").read_text(encoding="utf-8")
v2_proxy = (ROOT / "app/src/main/java/com/zcshou/gogogo/VirtualContainerV2ProxyActivity.java").read_text(encoding="utf-8")
v2_controller = (ROOT / "app/src/main/java/com/zcshou/gogogo/VirtualContainerV2Activity.java").read_text(encoding="utf-8")
require("extends Activity" in v2_guest_activity and "class GuestV2DeclaredActivity" in v2_guest_activity,
        "V2 fixture manifest-declared class missing")
require("String handle(String action, String value)" in v2_guest_route
        and "GUEST_V2_RESULT:" in v2_guest_route,
        "bounded guest DEX response contract missing")
require("PackageManager.GET_ACTIVITIES" in v2_probe
        and "getPackageArchiveInfo(" in v2_probe
        and "activities.length != 1" in v2_probe
        and "activity.exported" in v2_probe
        and "Activity.class.isAssignableFrom(declared)" in v2_probe
        and "guest.getVerifiedResourceApk()" in v2_probe,
        "guest component archive introspection and DEX checks missing")
require("new Intent(this, VirtualContainerV2ProxyActivity.class)" in v2_controller
        and "startActivityForResult(request, REQUEST)" in v2_controller
        and "nonceOk && responseOk && pidOk && uidOk && digestOk" in v2_controller
        and "showAndSave(output)" in v2_controller,
        "V2 controller must verify real host proxy response and save one report")
require("ContainerV2ManifestRouteProbe.run(getApplicationContext()" in v2_proxy
        and "setResult(check.passed ? RESULT_OK : RESULT_CANCELED, outbound)" in v2_proxy,
        "V2 proxy not wired to guest manifest/DEX route")
require("VirtualContainerV2Activity.PREFS" in screen
        and "VirtualContainerV2Activity.LAST_RESULT" in screen
        and "Tianwaitian V0+V1+V2" in screen
        and "ServiceGo" not in v2_controller + v2_proxy + v2_probe,
        "V2 output must be in single-copy diagnostic without location service calls")

print("V0/V1/V2 static contract PASS: one-copy report, controlled fixture Manifest, "
      "private host proxy, safe Intent handler and unchanged app identity.")

