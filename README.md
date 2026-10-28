# HONOR LDY-AN00 device tree

Device configuration for unofficial LineageOS 24.0 / Android 17.

| Item | Value |
| --- | --- |
| Device/model | `ldy_an00` / `LDY-AN00` |
| Android source path | `device/honor/ldy_an00` |
| Native product | `lineage_ldy_an00_native` |
| Branch | `lineage-24.0` |
| Firmware baseline | `10.0.0.113(C00E100R101P1)` |

## Layout

- Root makefiles: product, board, AVB and Virtual A/B configuration.
- `overlay*`, `displayconfig`, `keylayout`: framework/app configuration and input mappings.
- `rootdir`, `sepolicy`: init integration and SELinux policy.
- `ims`: device-specific IMS compatibility source, tests and reconstruction tools.
- `proprietary-inputs.json`: required excluded binary inputs and hashes.
- `EXPORT-PROVENANCE.json`: original source identities and component mapping.

Use this tree with the pinned manifest and its upstream patch series. The stock
kernel and vendor firmware are retained. Proprietary files and personal ADB keys
are excluded. Preserve existing source licenses and copyright notices.

IMS is included in this device tree rather than a separate hardware repository.
The reorganized tree requires a clean-build qualification before a new release.
Existing RC3 release images are unchanged by this source reorganization.

## Power profile

The framework overlay uses the stock power estimates and the verified 5,500 mAh
design capacity. Android 17 CPU scaling-step arrays follow the retained kernel's
frequency order. Values between stock points are interpolated; values outside
the stock range use its nearest endpoint and can underestimate boost consumption.

This corrects battery accounting. It does not change CPU limits or establish
a battery-life improvement.

Validate or regenerate the profile:

```sh
python3 tools/generate_power_profile.py --check
python3 -m unittest discover -s tools -p 'test_power_profile.py'
# After updating the verified source data:
python3 tools/generate_power_profile.py
```

Installation and releases:
[android_manifest_ldy_an00](https://github.com/iodn/android_manifest_ldy_an00).
