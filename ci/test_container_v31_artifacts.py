"""Regression checks for the artifact gate's class-definition distinction."""

import io
import struct
import unittest
import zipfile

from verify_container_v31_artifacts import (
    DEMO_DESCRIPTOR, HOST_ACTIVITY, apk_defined_classes,
    dex_defined_classes, private_host_activity,
)


def minimal_dex(defined="Lexample/Host;", extra=DEMO_DESCRIPTOR):
    """Small table fixture, sufficient for a metadata reader, not runnable DEX."""
    strings = [defined, extra]
    data = bytearray(156)
    data[:8] = b"dex\n035\x00"
    for offset, value in ((40, 0x12345678), (56, 2), (60, 112),
                          (64, 1), (68, 120), (96, 1), (100, 124)):
        struct.pack_into("<I", data, offset, value)
    for index, text in enumerate(strings):
        encoded = text.encode("utf-8")
        assert len(encoded) < 128
        struct.pack_into("<I", data, 112 + index * 4, len(data))
        data.extend(bytes([len(text)]) + encoded + b"\x00")
    struct.pack_into("<I", data, 32, len(data))
    return bytes(data)


class ArtifactGateTest(unittest.TestCase):
    def test_reflection_string_does_not_define_demo_in_host(self):
        data = minimal_dex()
        self.assertIn(DEMO_DESCRIPTOR.encode(), data)
        self.assertEqual({"Lexample/Host;"}, dex_defined_classes(data))

    def test_real_demo_class_definition_is_found(self):
        self.assertEqual({DEMO_DESCRIPTOR}, dex_defined_classes(minimal_dex(DEMO_DESCRIPTOR)))

    def test_invalid_class_index_fails_closed(self):
        data = bytearray(minimal_dex())
        struct.pack_into("<I", data, 124, 100)
        with self.assertRaises(SystemExit):
            dex_defined_classes(data)

    def test_later_multidex_entries_are_checked(self):
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w") as archive:
            archive.writestr("classes.dex", minimal_dex())
            archive.writestr("classes10.dex", minimal_dex(DEMO_DESCRIPTOR))
        buffer.seek(0)
        with zipfile.ZipFile(buffer) as archive:
            self.assertIn(DEMO_DESCRIPTOR, apk_defined_classes(archive))

    def test_exported_attribute_is_matched_to_proxy_component(self):
        manifest = ('E: manifest\n  E: application\n'
                    '    E: activity\n      A: android:name="example.Other"\n'
                    '      A: android:exported(0x01010010)=(type 0x12)0x0\n'
                    '    E: activity\n      A: android:name="' + HOST_ACTIVITY + '"\n'
                    '      A: android:exported(0x01010010)=(type 0x12)0xffffffff\n')
        self.assertFalse(private_host_activity(manifest))
        self.assertTrue(private_host_activity(manifest.replace('0xffffffff', '0x0')))


if __name__ == "__main__":
    unittest.main()
