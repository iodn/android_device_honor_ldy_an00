# Included only for lineage_ldy_an00_recovery, after existing board defaults.
TARGET_NO_KERNEL := true
TARGET_NO_BOOTLOADER := true
TARGET_NO_RECOVERY := false
BOARD_EXCLUDE_KERNEL_FROM_RECOVERY_IMAGE := true
BOARD_BOOT_HEADER_VERSION := 4
BOARD_KERNEL_PAGESIZE := 4096
BOARD_RECOVERYIMAGE_PARTITION_SIZE := 67108864
BOARD_USES_FULL_RECOVERY_IMAGE := true
BOARD_USES_RECOVERY_AS_BOOT :=
BOARD_MOVE_RECOVERY_RESOURCES_TO_VENDOR_BOOT :=
BOARD_INCLUDE_RECOVERY_RAMDISK_IN_VENDOR_BOOT :=
BOARD_INCLUDE_DTB_IN_BOOTIMG :=
BOARD_INCLUDE_RECOVERY_DTBO :=
BOARD_RECOVERY_KERNEL_MODULES :=
BOARD_RAMDISK_USE_LZ4 := true
BOARD_RECOVERY_MKBOOTIMG_ARGS := --header_version 4 --os_version 16.0.0 --os_patch_level 2025-12-01
TARGET_RECOVERY_FSTAB := $(DEVICE_PATH)/recovery/recovery.fstab
TARGET_RECOVERY_UI_SCREEN_WIDTH := 1216
TARGET_RECOVERY_UI_SCREEN_HEIGHT := 2640
TARGET_RECOVERY_DENSITY := xxxhdpi

# Produce an unsigned staging wrapper only. The source rule hardcodes the wrong
# AVB partition name "recovery"; final host packaging uses recovery_ramdisk.
# This does not set a device verification-disable flag or deploy an unsigned image.
BOARD_AVB_ENABLE := false

BOARD_VENDOR_SEPOLICY_DIRS += $(DEVICE_PATH)/recovery/sepolicy
