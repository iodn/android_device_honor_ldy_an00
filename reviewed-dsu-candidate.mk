# SPDX-License-Identifier: Apache-2.0
# Explicit personal test profile. Dormant for normal builds and all native releases.
ifeq ($(LDY_AN00_REVIEWED_DSU_CANDIDATE),true)
ifneq ($(TARGET_PRODUCT),lineage_ldy_an00)
$(error Reviewed DSU candidate is only supported by lineage_ldy_an00)
endif
ifneq ($(TARGET_BUILD_VARIANT),userdebug)
$(error Reviewed DSU candidate requires userdebug)
endif

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
