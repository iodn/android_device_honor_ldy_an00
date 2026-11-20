# C6-derived native Virtual A/B. Inventory artifacts must never be installed.
# Kernel snapshot policy is source-controlled for writable add-on postinstall.
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

ifeq ($(LDY_AN00_NATIVE_HARDWARE_BASELINE),stock11)
LDY_AN00_RESET_GUARD ?= 1
endif

# Preserve the C6 features and userdebug root-debug opt-in.
$(call inherit-product, device/honor/ldy_an00/lineage_ldy_an00.mk)
PRODUCT_NAME := lineage_ldy_an00_native
# Distribution must not silently authorize the owner's personal ADB public key.
PRODUCT_ADB_KEYS :=

# Normal Android has no serial-console user; retain Recovery and ADB defaults.
PRODUCT_SYSTEM_PROPERTIES += ro.boot.serialconsole=0

AB_OTA_UPDATER := true
AB_OTA_PARTITIONS := boot system system_dlkm vbmeta_system vbmeta_system_dlkm
ifeq ($(LDY_AN00_NATIVE_HARDWARE_BASELINE),stock11)
include device/honor/ldy_an00/stock11-hardware.mk
include device/honor/ldy_an00/memory-management.mk
AB_OTA_PARTITIONS := $(_LDY_STOCK11_OTA_PARTITIONS)
# Upstream caps this at 600 seconds; diagnostics stop the daemon during the trial.
PRODUCT_SYSTEM_PROPERTIES += ro.virtual_ab.merge_delay_seconds=600
endif
# Lineage update_engine runs addon.d backup/restore before mounting postinstall read-only.
AB_OTA_POSTINSTALL_CONFIG += \
    RUN_POSTINSTALL_system=true \
    POSTINSTALL_PATH_system=system/bin/otapreopt_script \
    FILESYSTEM_TYPE_system=ext4 \
    POSTINSTALL_OPTIONAL_system=true
PRODUCT_VIRTUAL_AB_OTA := true
PRODUCT_VIRTUAL_AB_OTA_RETROFIT := false
PRODUCT_VIRTUAL_AB_COMPRESSION := false

# Permit host child/top metadata construction, never stock top-vbmeta installation.
PRODUCT_BUILD_VBMETA_IMAGE := true
# Stock first-stage ramdisk/kernel/vendor are retained, not rebuilt outputs.
PRODUCT_PACKAGES += snapuserd
# Android partial-update synthesis reads this VENDOR-named property first.
# It must override retained vendor firmware lists in the later product props map.
ifeq ($(LDY_AN00_NATIVE_HARDWARE_BASELINE),stock11)
PRODUCT_PRODUCT_PROPERTIES += \
    ro.vendor.build.ab_ota_partitions=boot,init_boot,vendor_boot,dtbo,system,system_dlkm,vendor,vendor_dlkm,odm,odm_dlkm,vbmeta,vbmeta_system,vbmeta_system_dlkm,vbmeta_vendor,vbmeta_vendor_dlkm,vbmeta_odm,vbmeta_odm_dlkm,recovery_vbmeta,modem,mcf_ota
else
PRODUCT_PRODUCT_PROPERTIES += \
    ro.vendor.build.ab_ota_partitions=boot,system,system_dlkm,vbmeta_system,vbmeta_system_dlkm
endif
ifeq ($(LDY_AN00_NATIVE_STAGE),production)
PRODUCT_PRODUCT_PROPERTIES += lineage.updater.uri=$(LDY_AN00_NATIVE_UPDATER_URI)
endif
