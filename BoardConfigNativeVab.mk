# Existing shared-super Virtual A/B geometry; owned partial OTA context only.
BOARD_SUPER_PARTITION_SIZE := 17574133760
BOARD_SUPER_PARTITION_METADATA_DEVICE := super
BOARD_SUPER_PARTITION_GROUPS := qti_dynamic_partitions
BOARD_QTI_DYNAMIC_PARTITIONS_SIZE := 17569939456
BOARD_QTI_DYNAMIC_PARTITIONS_PARTITION_LIST := system system_dlkm
BOARD_PARTIAL_OTA_UPDATE_PARTITIONS_LIST := boot system system_dlkm vbmeta_system vbmeta_system_dlkm

# Reserve space for optional add-on variants before image signing.
BOARD_SYSTEMIMAGE_PARTITION_RESERVED_SIZE := 1073741824

# Keep native OTA compatibility independent of the retained vendor board alias.
TARGET_OTA_ASSERT_DEVICE := ldy_an00

# System is owned by the child, not a second independent chained partition.
BOARD_AVB_SYSTEM_KEY_PATH :=
BOARD_AVB_SYSTEM_ALGORITHM :=
BOARD_AVB_SYSTEM_ROLLBACK_INDEX_LOCATION :=
BOARD_AVB_VBMETA_SYSTEM := system
BOARD_AVB_VBMETA_SYSTEM_ALGORITHM := SHA256_RSA4096
BOARD_AVB_VBMETA_SYSTEM_ROLLBACK_INDEX := 0
BOARD_AVB_VBMETA_SYSTEM_ROLLBACK_INDEX_LOCATION := 2
BOARD_AVB_MAKE_VBMETA_SYSTEM_IMAGE_ARGS := --flags 0
ifeq ($(LDY_AN00_NATIVE_STAGE),unsigned-inventory)
BOARD_AVB_VBMETA_SYSTEM_KEY_PATH := external/avb/test/data/testkey_rsa4096.pem
else ifeq ($(LDY_AN00_NATIVE_STAGE),production)
BOARD_AVB_VBMETA_SYSTEM_KEY_PATH := $(LDY_AN00_NATIVE_VBMETA_SYSTEM_KEY_PATH)
ifeq ($(strip $(BOARD_AVB_VBMETA_SYSTEM_KEY_PATH)),)
$(error Production native VAB requires its reviewed vbmeta_system key)
endif
endif

# Generated host top-level vbmeta/fastboot-info is NOT a phone write manifest.
# Never flash super_empty, update-super, fastboot update, or generated vbmeta.img.

# Resolve block-device links labelled by the retained vendor policy during DSU installs.
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += $(DEVICE_PATH)/sepolicy/native/system_ext/private

# External, separately qualified kernel cohort; never generate it in Android.
ifeq ($(strip $(LDY_AN00_NATIVE_KERNEL_COHORT_DIR)),)
$(error Native update requires its reviewed external kernel cohort directory)
endif
BOARD_PREBUILT_BOOTIMAGE := $(LDY_AN00_NATIVE_KERNEL_COHORT_DIR)/boot.img
BOARD_PREBUILT_SYSTEM_DLKMIMAGE := $(LDY_AN00_NATIVE_KERNEL_COHORT_DIR)/system_dlkm.img
BOARD_SYSTEM_DLKMIMAGE_PARTITION_SIZE := 33554432
BOARD_SYSTEM_DLKMIMAGE_FILE_SYSTEM_TYPE := erofs
# Compiler keys are public intermediate inputs; final owner signing is offline.
BOARD_AVB_BOOT_KEY_PATH := $(BOARD_AVB_VBMETA_SYSTEM_KEY_PATH)
BOARD_AVB_BOOT_ALGORITHM := SHA256_RSA4096
BOARD_AVB_BOOT_ROLLBACK_INDEX_LOCATION := 1
BOARD_AVB_BOOT_ROLLBACK_INDEX := 0
BOARD_AVB_VBMETA_CUSTOM_PARTITIONS += system_dlkm
BOARD_AVB_VBMETA_SYSTEM_DLKM := system_dlkm
BOARD_AVB_VBMETA_SYSTEM_DLKM_KEY_PATH := $(BOARD_AVB_VBMETA_SYSTEM_KEY_PATH)
BOARD_AVB_VBMETA_SYSTEM_DLKM_ALGORITHM := SHA256_RSA4096
BOARD_AVB_VBMETA_SYSTEM_DLKM_ROLLBACK_INDEX := 0
BOARD_AVB_VBMETA_SYSTEM_DLKM_ROLLBACK_INDEX_LOCATION := 28
BOARD_AVB_MAKE_VBMETA_SYSTEM_DLKM_IMAGE_ARGS := --flags 0
