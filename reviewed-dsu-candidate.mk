# SPDX-License-Identifier: Apache-2.0
# Explicit reviewed profiles. Dormant unless the matching product opts in.
_ldy_an00_reviewed_profile :=
ifeq ($(LDY_AN00_REVIEWED_DSU_CANDIDATE),true)
ifeq ($(LDY_AN00_REVIEWED_NATIVE_CANDIDATE),true)
$(error Select only one reviewed candidate profile)
endif
ifneq ($(TARGET_PRODUCT),lineage_ldy_an00)
$(error Reviewed DSU candidate is only supported by lineage_ldy_an00)
endif
ifneq ($(TARGET_BUILD_VARIANT),userdebug)
$(error Reviewed DSU candidate requires userdebug)
endif
_ldy_an00_reviewed_profile := true
endif

ifeq ($(LDY_AN00_REVIEWED_NATIVE_CANDIDATE),true)
ifneq ($(TARGET_PRODUCT),lineage_ldy_an00_native)
$(error Reviewed native candidate is only supported by lineage_ldy_an00_native)
endif
ifneq ($(TARGET_BUILD_VARIANT),userdebug)
$(error Reviewed native candidate requires userdebug)
endif
ifneq ($(LDY_AN00_NATIVE_STAGE),production)
$(error Reviewed native candidate requires production signing configuration)
endif
ifneq ($(LDY_AN00_NATIVE_VAB_CONFIG_REVIEWED),true)
$(error Reviewed native candidate requires reviewed release configuration)
endif
_ldy_an00_reviewed_profile := true
endif

ifeq ($(_ldy_an00_reviewed_profile),true)
PRODUCT_PACKAGES += \
    LdyAn00DisplayPlugin \
    LdyAn00FrameworkDisplayPolicyOverlay \
    ldy-an00-shell-temperature \
    ldy_an00_display_policy_test_init \
    ldy_an00_stock_lcd_panel \
    ldy_an00_stock_manual_lux \
    ldy_an00_thermal_display_policy \
    ldy_an00_stock_aps_fullscreen

PRODUCT_SYSTEM_EXT_PROPERTIES += \
    ro.display.stock_thermal_policy=true \
    ro.display.apl_bridge=true \
    ro.audio.honor_sws_output=true
endif
_ldy_an00_reviewed_profile :=
