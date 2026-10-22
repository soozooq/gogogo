#!/usr/bin/env python3
"""Stage one flat phone download containing an already-verified APK pair.

GitHub upload-artifact creates the outer ZIP. Do not make a nested ZIP or
silently combine APKs from different runs. This script never installs code.
"""

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import tempfile


PHONE_README = """天外天手机更新包

1. 覆盖安装 Go_ 开头的主程序 APK，不要卸载原程序。
2. 按平常方式选择目标地点，开始模拟；无需逐项重跑旧实验。
3. 使用后在诊断中心复制最近诊断日志，下次一起回传即可；该出口已汇总定位与已保存的应用运行记录。

本次修复定位启动前误发默认坐标，以及空命令/无效路线错误重置坐标。
这属于模拟发布正确性修复，不代表微信、抖音或海外位置功能已兼容。
无需 Root 或电脑；不会改变物理网络、VPN出口或第三方应用内部行为。

externalDemo-debug.apk 是保留给高级“应用运行”工具的配套文件，本轮正常定位使用可忽略，不要安装。
如使用高级运行工具，主程序和 Demo 应保持同一构建配对。
SHA256SUMS 为本包实际 APK 摘要；build-evidence.json 保存构建来源。
"""


def require(condition, detail):
    if not condition:
        raise ValueError("phone update package rejected: " + detail)


def checked_file(directory, item):
    require(isinstance(item, dict), "missing APK evidence")
    name = item.get("file")
    require(isinstance(name, str) and Path(name).name == name
            and name.endswith(".apk") and "\\" not in name,
            "invalid APK evidence filename")
    path = Path(directory) / name
    require(path.is_file() and not path.is_symlink(), "APK file missing or symlinked")
    content = path.read_bytes()
    digest = hashlib.sha256(content).hexdigest()
    require(digest == item.get("sha256") and len(content) == item.get("bytes"),
            "APK bytes do not match this build's evidence")
    return path, digest


def stage_phone_update(host_directory, demo_directory, evidence_path, output_directory):
    evidence = json.loads(Path(evidence_path).read_text(encoding="utf-8"))
    require(evidence.get("demo_is_embedded") is False
            and evidence.get("demo_classes_in_host") is False
            and evidence.get("sha_pin_in_host") is True,
            "independent APK verification did not pass")
    host, host_digest = checked_file(host_directory, evidence.get("host"))
    demo, demo_digest = checked_file(demo_directory, evidence.get("external_demo"))
    require(host.name != demo.name, "APK filenames collide")
    destination = Path(output_directory)
    require(not destination.exists(), "output directory already exists; do not mix old files")
    destination.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix=".phone-update-", dir=destination.parent))
    try:
        shutil.copyfile(host, staging / host.name)
        shutil.copyfile(demo, staging / demo.name)
        (staging / "先读我.txt").write_text(PHONE_README, encoding="utf-8")
        (staging / "SHA256SUMS").write_text(
            host_digest + "  " + host.name + "\n"
            + demo_digest + "  " + demo.name + "\n", encoding="utf-8")
        (staging / "build-evidence.json").write_text(
            json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        # Check copied bytes as well: the source must not change during staging.
        for source, digest in ((host, host_digest), (demo, demo_digest)):
            require(hashlib.sha256((staging / source.name).read_bytes()).hexdigest() == digest,
                    "APK changed while staging")
        staging.rename(destination)
    except BaseException:
        shutil.rmtree(staging, ignore_errors=True)
        raise
    return destination


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host-dir", required=True)
    parser.add_argument("--demo-dir", required=True)
    parser.add_argument("--evidence", required=True)
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()
    result = stage_phone_update(args.host_dir, args.demo_dir, args.evidence, args.output_dir)
    print("Phone update package staged: " + str(result))


if __name__ == "__main__":
    main()
