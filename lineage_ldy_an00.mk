# The stock device is arm64-only; do not introduce a 32-bit zygote.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_system.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/handheld_system_ext.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/telephony_system_ext.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_product.mk)
$(call inherit-product, vendor/lineage/config/common_full_phone.mk)
$(call inherit-product, device/honor/ldy_an00/device.mk)

PRODUCT_NAME := lineage_ldy_an00
# Select canonical AIDL sensor payloads in the retained Honor HAL.
PRODUCT_SYSTEM_NAME := mainline
PRODUCT_DEVICE := ldy_an00
PRODUCT_BRAND := HONOR
PRODUCT_MANUFACTURER := HONOR
PRODUCT_MODEL := LDY-AN00
PRODUCT_CHARACTERISTICS := nosdcard

# Development guest: authenticated ADB before the UI authorization prompt.
ifeq ($(TARGET_BUILD_VARIANT),userdebug)
# The build serializes any nonempty value, including false, as true.
PRODUCT_NOT_DEBUGGABLE_IN_USERDEBUG :=
PRODUCT_ADB_KEYS := vendor/honor/ldy_an00/proprietary/debug/adbkey.pub
# Product defaults load after the retained vendor manufacturing defaults.
PRODUCT_PRODUCT_PROPERTIES += persist.sys.usb.config=adb ro.logd.kernel=true
endif
