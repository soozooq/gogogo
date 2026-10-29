import copy
import hashlib
import unittest

from restore_preview_signature import restore


class RestorePreviewSignatureTest(unittest.TestCase):
    def setUp(self):
        self.base = b'unchanged-owned-apk-payload'
        self.target = b'public-signature' + self.base[3:]
        self.recipe = {'schema': 'GOGOGO_PUBLIC_APK_SIGNATURE_RECIPE_V1',
                       'base_size': len(self.base), 'base_sha256': hashlib.sha256(self.base).hexdigest(),
                       'target_size': len(self.target), 'target_sha256': hashlib.sha256(self.target).hexdigest(),
                       'operations': [{'insert_base64': 'cHVibGljLXNpZ25hdHVyZQ=='},
                                      {'copy': [3, len(self.base) - 3]}]}

    def test_exact_signed_bytes_are_restored_without_modifying_the_base(self):
        self.assertEqual(self.target, restore(self.base, self.recipe))
        self.assertEqual(b'unchanged-owned-apk-payload', self.base)

    def test_wrong_base_or_target_digest_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'base APK'):
            restore(self.base + b'wrong', self.recipe)
        recipe = copy.deepcopy(self.recipe)
        recipe['target_sha256'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'signed candidate'):
            restore(self.base, recipe)

    def test_unbounded_or_negative_copy_is_rejected(self):
        for span in [[-1, 1], [0, len(self.base) + 1], [0, -1], [True, 1], [0]]:
            with self.subTest(span=span):
                recipe = copy.deepcopy(self.recipe)
                recipe['operations'] = [{'copy': span}]
                with self.assertRaises(ValueError):
                    restore(self.base, recipe)

    def test_invalid_base64_unknown_operation_and_oversized_output_are_rejected(self):
        for operation in [{'insert_base64': '!!!'}, {'path': '/not-a-file-input'},
                          {'copy': [0, 1], 'insert_base64': ''}]:
            recipe = copy.deepcopy(self.recipe)
            recipe['operations'] = [operation]
            with self.assertRaises(ValueError):
                restore(self.base, recipe)
        recipe = copy.deepcopy(self.recipe)
        recipe['target_size'] = 1
        with self.assertRaisesRegex(ValueError, 'pinned APK size'):
            restore(self.base, recipe)

    def test_operation_and_apk_size_caps_are_enforced(self):
        for change in [{'operations': []}, {'operations': [{'copy': [0, 1]}] * 2001},
                       {'target_size': 17 * 1024 * 1024}]:
            recipe = dict(self.recipe, **change)
            with self.assertRaises(ValueError):
                restore(self.base, recipe)


if __name__ == '__main__':
    unittest.main()
