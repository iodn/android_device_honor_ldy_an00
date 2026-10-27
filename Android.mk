# SPDX-License-Identifier: Apache-2.0
# Track fingerprint contents when generating native AVB metadata.
ifeq ($(TARGET_PRODUCT),lineage_ldy_an00_native)
$(PRODUCT_OUT)/misc_info.txt: \
    $(PRODUCT_OUT)/build_fingerprint-$(TARGET_PRODUCT).txt \
    $(PRODUCT_OUT)/build_system_fingerprint-$(TARGET_PRODUCT).txt
endif
