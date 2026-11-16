#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Recovery scope and late property override regressions; no image build required."""
import json
import hashlib
import ssl
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]

def properties(text):
    return dict(line.split('=', 1) for line in text.splitlines()
                if '=' in line and not line.startswith('#'))

def scope():
    return properties((ROOT / 'recovery/product.prop').read_text())

class RecoveryOtaScopeTest(unittest.TestCase):
    def test_exact_twenty_partition_contract(self):
        contract = json.loads((ROOT / 'kernel/stock11-inputs.json').read_text())
        expected = {row['partition'] for row in contract['inputs']} | {'system', 'vbmeta_system'}
        actual = scope()
        vendor = actual['ro.vendor.build.ab_ota_partitions'].split(',')
        self.assertEqual(len(vendor), 20)
        self.assertEqual(set(vendor), expected)
        self.assertEqual(actual['ro.product.ab_ota_partitions'], ','.join(vendor))
        self.assertTrue(set(vendor).isdisjoint({'lk', 'preloader_raw', 'tee', 'logo', 'nvram', 'nvdata', 'nvcfg'}))

    def test_product_make_scope_matches_runtime_properties(self):
        text = (ROOT / 'lineage_ldy_an00_recovery.mk').read_text()
        actual = re.findall(r'^AB_OTA_PARTITIONS := (.+)$', text, re.M)
        self.assertEqual(actual, [scope()['ro.vendor.build.ab_ota_partitions'].replace(',', ' ')])
        self.assertIn('TARGET_PRODUCT),lineage_ldy_an00_recovery)', (ROOT / 'BoardConfig.mk').read_text())
        self.assertIn('lineage_ldy_an00_recovery-cp2a-userdebug', (ROOT / 'AndroidProducts.mk').read_text())

    def test_vendor_defaults_reproduce_missing_logo_error_without_late_override(self):
        early = {'ro.vendor.build.ab_ota_partitions': 'system,vbmeta_system'}
        retained_vendor = {'ro.vendor.build.ab_ota_partitions': 'boot,lk,logo,preloader_raw,system,vbmeta_system'}
        old = dict(early, **retained_vendor)
        self.assertIn('logo', old['ro.vendor.build.ab_ota_partitions'].split(','))
        admitted = dict(old, **scope())
        self.assertEqual(len(admitted['ro.vendor.build.ab_ota_partitions'].split(',')), 20)
        self.assertNotIn('logo', admitted['ro.vendor.build.ab_ota_partitions'].split(','))

    def test_copy_uses_retained_product_symlink_destination(self):
        text = (ROOT / 'recovery/device-recovery.mk').read_text()
        self.assertIn('recovery/product.prop:recovery/root/system/product/etc/build.prop', text)
        self.assertNotIn(':recovery/root/product/etc/build.prop', text)
        # Retained Recovery has /product -> /system/product. Resolve inside image,
        # without following an absolute image symlink onto the host filesystem.
        image = {'system/product/etc/build.prop': (ROOT / 'recovery/product.prop').read_text()}
        canonical = '/product/etc/build.prop'.replace('/product/', '/system/product/', 1).lstrip('/')
        self.assertEqual(properties(image[canonical]), scope())

    def test_owner_certificate_is_release_packaging_input_only(self):
        self.assertFalse((ROOT / 'recovery/Android.bp').exists())
        product = (ROOT / 'recovery/device-recovery.mk').read_text()
        self.assertNotIn('PRODUCT_PACKAGES', product)
        self.assertNotIn('external_otacerts', product)
        cert = (ROOT / 'recovery/security/ota.x509.pem').read_text()
        self.assertNotIn('PRIVATE KEY', cert)
        der = ssl.PEM_cert_to_DER_cert(cert)
        self.assertEqual(hashlib.sha256(der).hexdigest(),
                         '46fc26697072341a414473390b5a313b1385d09d058562985d8d1acbc8a3d06c')

    def test_recovery_keeps_transport_and_does_not_build_kernel(self):
        board = (ROOT / 'recovery/BoardConfigRecovery.mk').read_text()
        self.assertIn('TARGET_NO_KERNEL := true', board)
        self.assertIn('BOARD_RECOVERYIMAGE_PARTITION_SIZE := 67108864', board)
        product = (ROOT / 'lineage_ldy_an00_recovery.mk').read_text()
        self.assertIn('fastbootd update_engine_sideload snapuserd.recovery', product)
        self.assertIn('PRODUCT_BUILD_USERDATA_IMAGE := false', product)
        self.assertNotIn('mount_all', (ROOT / 'recovery/init.ldy_an00.recovery.rc').read_text())

if __name__ == '__main__':
    unittest.main()
