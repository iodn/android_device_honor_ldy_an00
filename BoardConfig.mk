# Honor LDY-AN00: stock-kernel/vendor userspace bring-up.
DEVICE_PATH := device/honor/ldy_an00
DEVICE_FRAMEWORK_COMPATIBILITY_MATRIX_FILE += $(DEVICE_PATH)/compatibility_matrix.xml
TARGET_ARCH := arm64
TARGET_ARCH_VARIANT := armv8-a
TARGET_CPU_ABI := arm64-v8a
TARGET_CPU_VARIANT := generic
TARGET_CPU_VARIANT_RUNTIME := generic
TARGET_BOARD_PLATFORM := mt6993
TARGET_BOOTLOADER_BOARD_NAME := k6993v1_64
BOARD_SHIPPING_API_LEVEL := 202504
TARGET_SCREEN_WIDTH := 1216
TARGET_SCREEN_HEIGHT := 2640
TARGET_SCREEN_DENSITY := 560
TARGET_BOARD_PLATFORM_GPU :=

# The initial target builds userspace only; stock boot/vendor images stay intact.
include build/make/target/board/BoardConfigGsiCommon.mk
TARGET_NO_KERNEL := true
TARGET_NO_BOOTLOADER := true
TARGET_NO_RECOVERY := true
TARGET_KERNEL_VERSION := 6.12
TARGET_KERNEL_ARCH := arm64
TARGET_PREBUILT_KERNEL := vendor/honor/ldy_an00/proprietary/boot/Image.lz4
TARGET_MAX_PAGE_SIZE_SUPPORTED := 4096
BOARD_KERNEL_PAGESIZE := 4096
# Retained vendor init mounts persist at this canonical root directory.
BOARD_ROOT_EXTRA_FOLDERS += sec_storage
BOARD_BOOT_HEADER_VERSION := 4
BOARD_INIT_BOOT_HEADER_VERSION := 4
BOARD_BOOTIMAGE_PARTITION_SIZE := 67108864
BOARD_INIT_BOOT_IMAGE_PARTITION_SIZE := 8388608
BOARD_VENDOR_BOOTIMAGE_PARTITION_SIZE := 100663296
BOARD_USES_SYSTEM_DLKMIMAGE := false
BOARD_AVB_ROLLBACK_INDEX := 0
BOARD_AVB_SYSTEM_ROLLBACK_INDEX := 0

# Staged framework-owned stock-vendor contract; policy qualification pending.
SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS += $(DEVICE_PATH)/sepolicy/system_ext/public
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(DEVICE_PATH)/sepolicy/system_ext/private

include vendor/lineage/config/BoardConfigLineage.mk

ifeq ($(TARGET_BUILD_VARIANT),userdebug)
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(DEVICE_PATH)/diagnostic-capture/sepolicy
endif

ifeq ($(TARGET_PRODUCT),lineage_ldy_an00_native)
include $(DEVICE_PATH)/BoardConfigNativeVab.mk
endif
