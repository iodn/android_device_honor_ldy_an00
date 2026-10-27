# Existing shared-super Virtual A/B geometry; owned partial OTA context only.
BOARD_SUPER_PARTITION_SIZE := 17574133760
BOARD_SUPER_PARTITION_METADATA_DEVICE := super
BOARD_SUPER_PARTITION_GROUPS := qti_dynamic_partitions
BOARD_QTI_DYNAMIC_PARTITIONS_SIZE := 17569939456
BOARD_QTI_DYNAMIC_PARTITIONS_PARTITION_LIST := system
BOARD_PARTIAL_OTA_UPDATE_PARTITIONS_LIST := system vbmeta_system

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
