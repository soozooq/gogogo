#!/usr/bin/env python3
"""Verify independently built V3.1 APKs, never install or execute either APK.

Reads DEX class_defs instead of searching DEX strings: the host legitimately
contains the demo's class name as a reflection allowlist string.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import zipfile


DEMO_PACKAGE = "com.soozooq.tianwaitian.externaldemo"
DEMO_ENTRY = DEMO_PACKAGE + ".DemoEntry"
DEMO_DESCRIPTOR = "L" + DEMO_ENTRY.replace(".", "/") + ";"
DEMO_PREFIX = "L" + DEMO_PACKAGE.replace(".", "/") + "/"
HOST_PACKAGE = "com.soozooq.gogogo.test"
HOST_ACTIVITY = "com.zcshou.gogogo.VirtualContainerV31ProxyActivity"


def require(ok, detail):
    if not ok:
        raise SystemExit("V3.1 artifact verification FAILED: " + detail)


def one_apk(directory):
    candidates = sorted(Path(directory).glob("*.apk"))
    require(len(candidates) == 1, f"expected exactly one APK in {directory}")
    return candidates[0]


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def u32(data, offset):
    require(0 <= offset <= len(data) - 4, "DEX uint32 is out of bounds")
    return struct.unpack_from("<I", data, offset)[0]


def bounded_table(data, count, offset, width):
    require(0 <= count <= len(data) // width
            and 0 <= offset <= len(data) - count * width,
            "DEX table is out of bounds")


def dex_defined_classes(data):
    require(len(data) >= 112 and re.fullmatch(rb"dex\n[0-9]{3}\x00", data[:8]),
            "unsupported or invalid DEX header")
    require(u32(data, 32) == len(data) and u32(data, 40) == 0x12345678,
            "DEX size/endian mismatch")
    strings_size, strings_offset = u32(data, 56), u32(data, 60)
    types_size, types_offset = u32(data, 64), u32(data, 68)
    classes_size, classes_offset = u32(data, 96), u32(data, 100)
    bounded_table(data, strings_size, strings_offset, 4)
    bounded_table(data, types_size, types_offset, 4)
    bounded_table(data, classes_size, classes_offset, 32)
    result = set()
    for index in range(classes_size):
        type_index = u32(data, classes_offset + index * 32)
        require(type_index < types_size, "DEX class type index out of bounds")
        string_index = u32(data, types_offset + type_index * 4)
        require(string_index < strings_size, "DEX descriptor string index out of bounds")
        start = u32(data, strings_offset + string_index * 4)
        require(start < len(data), "DEX string data out of bounds")
        # string_data_item begins with uleb128 UTF-16 length. Descriptor bytes
        # below need only delimiter-safe UTF-8, not the length for other strings.
        for _ in range(5):
            require(start < len(data), "truncated DEX string length")
            value = data[start]
            start += 1
            if value < 128:
                break
        else:
            require(False, "oversized DEX string length")
        end = data.find(b"\x00", start)
        require(end != -1, "unterminated DEX class descriptor")
        descriptor = data[start:end].decode("utf-8", errors="strict")
        require(descriptor.startswith("L") and descriptor.endswith(";"),
                "invalid DEX class descriptor")
        result.add(descriptor)
    return result


def apk_defined_classes(archive):
    dex_names = [name for name in archive.namelist()
                 if re.fullmatch(r"classes(?:[2-9]|[1-9][0-9]+)?\.dex", name)]
    require(bool(dex_names), "APK has no primary DEX")
    result = set()
    for name in dex_names:
        result.update(dex_defined_classes(archive.read(name)))
    return result


def check_zip(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), "APK has duplicate ZIP names")
        require(archive.testzip() is None, "APK ZIP CRC failure")
        require({"AndroidManifest.xml", "classes.dex", "resources.arsc"}.issubset(names),
                "APK is missing manifest, DEX or resources")
        return names, apk_defined_classes(archive)


def tool(sdk, name):
    candidates = list((Path(sdk) / "build-tools").glob("*/" + name))
    require(bool(candidates), name + " is not available in Android build-tools")
    # Prefer the most recently installed version; every candidate supports the
    # fixed dump/verify commands used here.
    return sorted(candidates, key=lambda item: item.parent.name)[-1]


def run(command):
    completed = subprocess.run([str(arg) for arg in command], check=True,
                               capture_output=True, text=True, timeout=90)
    return completed.stdout


def package_info(aapt, apk, expected):
    badging = run([aapt, "dump", "badging", apk])
    match = re.search(r"^package: name='([^']+)' versionCode='([^']+)'", badging, re.M)
    require(match is not None and match.group(1) == expected,
            "built APK package mismatch for " + apk.name)
    return int(match.group(2))


def private_host_activity(manifest):
    lines = manifest.splitlines()
    for index, line in enumerate(lines):
        if not re.search(r"E: activity(?:\s|$)", line):
            continue
        indent = len(line) - len(line.lstrip())
        block = []
        for candidate in lines[index + 1:]:
            if "E:" in candidate and len(candidate) - len(candidate.lstrip()) <= indent:
                break
            block.append(candidate)
        text = "\n".join(block)
        if HOST_ACTIVITY in text or ".VirtualContainerV31ProxyActivity\"" in text:
            return re.search(r"android:exported[^\n]*\(type 0x12\)0x0(?:\s|$)", text) is not None
    return False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host-dir", required=True)
    parser.add_argument("--demo-dir", required=True)
    parser.add_argument("--trust", required=True)
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    require(re.fullmatch(r"[0-9a-f]{40}", args.commit) is not None,
            "source commit must be a full SHA")
    host, demo = one_apk(args.host_dir), one_apk(args.demo_dir)
    host_names, host_classes = check_zip(host)
    _, demo_classes = check_zip(demo)
    require(DEMO_DESCRIPTOR in demo_classes, "demo entry class is not defined by standalone APK")
    require(not any(item.startswith(DEMO_PREFIX) for item in host_classes),
            "host APK contains a compiled copy of the demo code")
    embedded_apks = [name for name in host_names if name.lower().endswith(".apk")]
    require(embedded_apks == ["assets/twt-fixture-v0.apk"],
            "host must embed only the historical V0 fixture, never the external demo")
    digest = sha256(demo)
    trust = json.loads(Path(args.trust).read_text(encoding="utf-8"))
    require(trust.get("apk_sha256") == digest
            and trust.get("package_name") == DEMO_PACKAGE
            and trust.get("version_code") == 1
            and trust.get("entry_class") == DEMO_ENTRY,
            "generated allowlist does not match the standalone demo APK")
    # The exact SHA must actually reach the compiled host, not merely an unused
    # build-side JSON file. String presence is appropriate for this digest only.
    with zipfile.ZipFile(host) as archive:
        host_dex = [archive.read(name) for name in host_names if name.endswith(".dex")]
    require(any(digest.encode("ascii") in data for data in host_dex),
            "standalone APK SHA is missing from compiled host DEX")
    aapt, apksigner = tool(args.sdk, "aapt"), tool(args.sdk, "apksigner")
    host_version = package_info(aapt, host, HOST_PACKAGE)
    require(host_version > 2400092, "host versionCode did not advance")
    require(package_info(aapt, demo, DEMO_PACKAGE) == 1, "standalone demo versionCode mismatch")
    host_manifest = run([aapt, "dump", "xmltree", host, "AndroidManifest.xml"])
    require(private_host_activity(host_manifest), "built V3.1 Host proxy must be registered and private")
    demo_manifest = run([aapt, "dump", "xmltree", demo, "AndroidManifest.xml"])
    require("DemoActivity" in demo_manifest and "E: uses-permission" not in demo_manifest,
            "standalone demo must declare its own Activity and no permissions")
    demo_resources = run([aapt, "dump", "resources", demo])
    for marker in ("layout/demo_panel", "string/demo_resource_marker", "color/demo_background"):
        require(marker in demo_resources, "standalone compiled resource missing: " + marker)
    for apk in (host, demo):
        run([apksigner, "verify", "--min-sdk-version", "30", str(apk)])
    output = {
        "scope": "built APK archive verification; no install, DEX execution or phone validation",
        "source_commit": args.commit,
        "host": {"file": host.name, "sha256": sha256(host), "bytes": host.stat().st_size,
                 "package": HOST_PACKAGE, "version_code": host_version},
        "external_demo": {"file": demo.name, "sha256": digest, "bytes": demo.stat().st_size,
                          "package": DEMO_PACKAGE, "version_code": 1, "entry_class": DEMO_ENTRY},
        "demo_is_embedded": False,
        "demo_classes_in_host": False,
        "sha_pin_in_host": True,
    }
    destination = Path(args.output)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(output, ensure_ascii=False, indent=2))
    print("V3.1 independently built APK and exact-SHA admission artifacts: PASS")


if __name__ == "__main__":
    main()
