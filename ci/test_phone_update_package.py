"""The one-download package must preserve a verified pair without nested ZIPs."""

import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from stage_phone_update import stage_phone_update


class PhoneUpdatePackageTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.host = self.root / "host"
        self.demo = self.root / "demo"
        self.host.mkdir()
        self.demo.mkdir()
        (self.host / "Go_update.apk").write_bytes(b"self-authored host fixture")
        (self.demo / "externalDemo-debug.apk").write_bytes(b"paired independent demo fixture")
        self.evidence = self.root / "evidence.json"
        self.record = {
            "demo_is_embedded": False, "demo_classes_in_host": False, "sha_pin_in_host": True,
            "host": self.describe(self.host / "Go_update.apk"),
            "external_demo": self.describe(self.demo / "externalDemo-debug.apk"),
        }
        self.output = self.root / "phone"

    @staticmethod
    def describe(path):
        data = path.read_bytes()
        return {"file": path.name, "sha256": hashlib.sha256(data).hexdigest(), "bytes": len(data)}

    def stage(self):
        self.evidence.write_text(json.dumps(self.record), encoding="utf-8")
        return stage_phone_update(self.host, self.demo, self.evidence, self.output)

    def test_one_flat_folder_contains_the_exact_pair_and_simple_instructions(self):
        result = self.stage()
        self.assertEqual({"Go_update.apk", "externalDemo-debug.apk", "先读我.txt",
                          "SHA256SUMS", "build-evidence.json"}, {p.name for p in result.iterdir()})
        self.assertEqual((self.demo / "externalDemo-debug.apk").read_bytes(),
                         (result / "externalDemo-debug.apk").read_bytes())
        self.assertIn(self.record["host"]["sha256"], (result / "SHA256SUMS").read_text())

    def test_other_run_demo_bytes_are_rejected_without_a_partial_package(self):
        (self.demo / "externalDemo-debug.apk").write_bytes(b"different build")
        with self.assertRaisesRegex(ValueError, "do not match"):
            self.stage()
        self.assertFalse(self.output.exists())
        self.assertFalse(list(self.root.glob(".phone-update-*")))

    def test_unverified_or_embedded_pair_is_rejected(self):
        self.record["demo_is_embedded"] = True
        with self.assertRaisesRegex(ValueError, "verification did not pass"):
            self.stage()

    def test_existing_output_cannot_mix_previous_and_current_apks(self):
        self.output.mkdir()
        (self.output / "old.apk").write_bytes(b"old")
        with self.assertRaisesRegex(ValueError, "already exists"):
            self.stage()
        self.assertEqual(b"old", (self.output / "old.apk").read_bytes())

    def test_evidence_cannot_escape_the_apk_directory(self):
        self.record["host"]["file"] = "../other.apk"
        with self.assertRaisesRegex(ValueError, "filename"):
            self.stage()


if __name__ == "__main__":
    unittest.main()
