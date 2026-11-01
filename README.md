# HONOR LDY-AN00 device tree

Device configuration for unofficial LineageOS 24.0 / Android 17.

| Item | Value |
| --- | --- |
| Device/model | `ldy_an00` / `LDY-AN00` |
| Android source path | `device/honor/ldy_an00` |
| Native product | `lineage_ldy_an00_native` |
| Branch | `lineage-24.0` |
| Tested firmware baseline | `10.0.0.113(C00E100R101P1)` |

See the [GhostLock firmware table](https://github.com/iodn/honor-ldy-an00-ghostlock#firmware-downloads-and-ghostlock-status) for vulnerable versions.
This device configuration uses the tested **10.0.0.113** inputs.

## Layout

- Root makefiles: product, board, AVB and Virtual A/B configuration.
- `overlay*`, `displayconfig`, `keylayout`: framework/app configuration and input mappings.
- `rootdir`, `sepolicy`: init integration and SELinux policy.
- `ims`: device-specific IMS compatibility source, tests and reconstruction tools.
- `proprietary-inputs.json`: required excluded build inputs and hashes.
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

The retained Honor performance HAL also requires its original
`product_h/etc/xml/uniperf_config_cust.xml` at
`power/proprietary/uniperf_config_cust.xml`. Its expected size and hash are
listed in `proprietary-inputs.json`; the build installs it under `/product/etc/xml`.
The proprietary policy is excluded from Git.

The power model corrects battery accounting; it does not establish a battery-life
improvement. A device service separately connects Battery Saver and screen-off
state to stock CPU/GPU ceilings, releasing them for Android-reported camera use or non-normal
audio modes. Existing MediaTek interaction, sustained-performance and thermal
controls remain in place. Device validation of this integration is pending.

The standard HAL hint table is generated from
`power/proprietary/powerscntbl-stock113.xml`. Init stages both power tables in RAM with a
vendor-data label before the HALs start. The interaction hint reuses
the stock touch resources. The candidate sustained profile uses stock balanced
CPU ceilings; capability advertising remains disabled pending hardware tests.
The vendor partition is unchanged.

Run the power-policy tests with JDK 17 or newer and Python 3:

```sh
./power/test.sh
```

Validate or regenerate the profile:

```sh
python3 tools/generate_power_profile.py --check
python3 -m unittest discover -s tools -p 'test_power_profile.py'
# After updating the verified source data:
python3 tools/generate_power_profile.py
```

## Private display inputs

The source files and four generated payloads are listed in
`display-policy-inputs.json`. Place the verified extracted sources at their
listed `proprietary_path` locations, then generate and verify the payloads:

```sh
python3 tools/prepare_display_policy.py
python3 tools/verify_display_policy_inputs.py
python3 tools/test_display_policy_inputs.py
```

Decoded policy XML is an offline firmware-extraction prerequisite. Source XML,
decoded policies and generated payloads remain excluded from Git.

The personal DSU test profile requires `LDY_AN00_REVIEWED_DSU_CANDIDATE=true`.
It is accepted only for the `lineage_ldy_an00` product with the `userdebug` variant;
native release targets reject this opt-in.

Select the product before enabling the personal test profile:

```sh
unset LDY_AN00_REVIEWED_DSU_CANDIDATE
source build/envsetup.sh
lunch lineage_ldy_an00 cp2a userdebug
export LDY_AN00_REVIEWED_DSU_CANDIDATE=true
```

Installation and releases:
[android_manifest_ldy_an00](https://github.com/iodn/android_manifest_ldy_an00).
