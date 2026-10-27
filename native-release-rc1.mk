# RC1 offline-signing INPUT configuration, not a privately signed release.
# Public AOSP intermediate keys only; private material is never mounted in compiler.
LDY_AN00_NATIVE_VAB_CONFIG_REVIEWED := true
PRODUCT_DEFAULT_DEV_CERTIFICATE := build/make/target/product/security/testkey
LDY_AN00_NATIVE_VBMETA_SYSTEM_KEY_PATH := external/avb/test/data/testkey_rsa4096.pem
# Owned future endpoint: repository currently private/empty; NOT live/OTA-ready.
# Publication, direct HTTPS tests and signed asset availability remain ON HOLD.
LDY_AN00_NATIVE_UPDATER_URI := https://raw.githubusercontent.com/iodn/android_manifest_ldy_an00/main/ota/ldy_an00.json
