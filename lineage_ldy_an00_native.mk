# C6-derived native Virtual A/B. Inventory artifacts must never be installed.
# Retained stock113 reader/handoff compatibility is source-controlled.
include device/honor/ldy_an00/native-vab-snapshot.mk

ifeq ($(LDY_AN00_NATIVE_STAGE),unsigned-inventory)
# Public AOSP test keys are host-only inventory inputs, not production keys.
PRODUCT_DEFAULT_DEV_CERTIFICATE := build/make/target/product/security/testkey
else ifeq ($(LDY_AN00_NATIVE_STAGE),production)
ifeq ($(strip $(LDY_AN00_NATIVE_RELEASE_CONFIG)),)
$(error Production native VAB requires a reviewed release configuration)
endif
include $(LDY_AN00_NATIVE_RELEASE_CONFIG)
ifneq ($(LDY_AN00_NATIVE_VAB_CONFIG_REVIEWED),true)
$(error Production signing and feed configuration has not been reviewed)
endif
ifeq ($(filter https://%,$(LDY_AN00_NATIVE_UPDATER_URI)),)
$(error Production native VAB requires its actual HTTPS updater endpoint)
endif
else
$(error Select explicit unsigned-inventory or production native VAB stage)
endif

# Preserve the C6 features and userdebug root-debug opt-in.
$(call inherit-product, device/honor/ldy_an00/lineage_ldy_an00.mk)
PRODUCT_NAME := lineage_ldy_an00_native
# Distribution must not silently authorize the owner's personal ADB public key.
PRODUCT_ADB_KEYS :=

AB_OTA_UPDATER := true
AB_OTA_PARTITIONS := system vbmeta_system
PRODUCT_VIRTUAL_AB_OTA := true
PRODUCT_VIRTUAL_AB_OTA_RETROFIT := false
PRODUCT_VIRTUAL_AB_COMPRESSION := true

# Permit host child/top metadata construction, never stock top-vbmeta installation.
PRODUCT_BUILD_VBMETA_IMAGE := true
# Stock first-stage ramdisk/kernel/vendor are retained, not rebuilt outputs.
PRODUCT_PACKAGES += snapuserd
# Android partial-update synthesis reads this VENDOR-named property first.
# It must override retained vendor firmware lists in the later product props map.
PRODUCT_PRODUCT_PROPERTIES += \
    ro.vendor.build.ab_ota_partitions=system,vbmeta_system
ifeq ($(LDY_AN00_NATIVE_STAGE),production)
PRODUCT_PRODUCT_PROPERTIES += lineage.updater.uri=$(LDY_AN00_NATIVE_UPDATER_URI)
endif
