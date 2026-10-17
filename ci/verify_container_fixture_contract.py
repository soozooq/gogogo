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
require(len(list(fixture_app)) == 0, "fixture should not register Android components yet")

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

print("V0/V1 static contract PASS: controlled APK, five-step one-click report, "
      "two private PIDs, trusted lifecycle proxy and stable main package.")
