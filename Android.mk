# SPDX-License-Identifier: Apache-2.0
# Track fingerprint contents when generating native AVB metadata.
ifeq ($(TARGET_PRODUCT),lineage_ldy_an00_native)
$(PRODUCT_OUT)/misc_info.txt: \
    $(PRODUCT_OUT)/build_fingerprint-$(TARGET_PRODUCT).txt \
    $(PRODUCT_OUT)/build_system_fingerprint-$(TARGET_PRODUCT).txt
endif

# Authenticated hardware child metadata supplied by the reviewed migration cohort.
ifeq ($(LDY_AN00_NATIVE_HARDWARE_BASELINE),stock11)
LOCAL_PATH := $(LDY_AN00_NATIVE_KERNEL_COHORT_DIR)
$(foreach image,vbmeta_vendor.img vbmeta_vendor_dlkm.img vbmeta_odm.img vbmeta_odm_dlkm.img recovery_vbmeta.img,$(call add-radio-file,$(image)))
endif
