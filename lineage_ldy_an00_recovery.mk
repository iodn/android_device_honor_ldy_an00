# Recovery-only product; does not inherit the full phone userspace product.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/base_vendor.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_ramdisk.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/virtual_ab_ota.mk)

LINEAGE_BUILD := ldy_an00
include vendor/lineage/config/version.mk

PRODUCT_NAME := lineage_ldy_an00_recovery
PRODUCT_DEVICE := ldy_an00
PRODUCT_BRAND := HONOR
PRODUCT_MANUFACTURER := HONOR
PRODUCT_MODEL := LDY-AN00
PRODUCT_SHIPPING_API_LEVEL := 36
PRODUCT_MAX_PAGE_SIZE_SUPPORTED := 4096
PRODUCT_NO_BIONIC_PAGE_SIZE_MACRO := true
PRODUCT_USE_DYNAMIC_PARTITIONS := true
PRODUCT_VIRTUAL_AB_COMPRESSION := true
PRODUCT_VIRTUAL_AB_COMPRESSION_METHOD := none
PRODUCT_VIRTUAL_AB_COW_VERSION := 3
AB_OTA_UPDATER := true
AB_OTA_PARTITIONS := boot init_boot vendor_boot dtbo system system_dlkm vendor vendor_dlkm odm odm_dlkm vbmeta vbmeta_system vbmeta_system_dlkm vbmeta_vendor vbmeta_vendor_dlkm vbmeta_odm vbmeta_odm_dlkm recovery_vbmeta modem mcf_ota

PRODUCT_BUILD_RECOVERY_IMAGE := true
PRODUCT_BUILD_BOOT_IMAGE := false
PRODUCT_BUILD_INIT_BOOT_IMAGE := false
PRODUCT_BUILD_VENDOR_BOOT_IMAGE := false
PRODUCT_BUILD_VENDOR_KERNEL_BOOT_IMAGE := false
PRODUCT_BUILD_SYSTEM_IMAGE := false
PRODUCT_BUILD_SYSTEM_EXT_IMAGE := false
PRODUCT_BUILD_PRODUCT_IMAGE := false
PRODUCT_BUILD_VENDOR_IMAGE := false
PRODUCT_BUILD_ODM_IMAGE := false
PRODUCT_BUILD_SYSTEM_DLKM_IMAGE := false
PRODUCT_BUILD_VENDOR_DLKM_IMAGE := false
PRODUCT_BUILD_ODM_DLKM_IMAGE := false
PRODUCT_BUILD_VBMETA_IMAGE := false
PRODUCT_BUILD_SUPER_PARTITION := false
PRODUCT_BUILD_SUPER_EMPTY_IMAGE := false
PRODUCT_BUILD_USERDATA_IMAGE := false
PRODUCT_BUILD_CACHE_IMAGE := false
PRODUCT_BUILD_PVMFW_IMAGE := false

# The legacy root staging tree is required by recovery image assembly.
PRODUCT_PACKAGES += init.environ.rc fastbootd update_engine_sideload snapuserd.recovery
PRODUCT_VENDOR_PROPERTIES += ro.virtual_ab.compression.enabled=true
PRODUCT_VENDOR_PROPERTIES += ro.virtual_ab.userspace.snapshots.enabled=true
PRODUCT_ADB_KEYS :=
PRODUCT_SYSTEM_PROPERTIES += ro.boot.init_rc=/system/etc/init/hw/init.ldy_an00.recovery.rc
# Retained vendor ramdisk supplies the stock recovery USB IDs.

# Recovery-only hardware and boot-control integration.
$(call inherit-product, device/honor/ldy_an00/recovery/device-recovery.mk)
