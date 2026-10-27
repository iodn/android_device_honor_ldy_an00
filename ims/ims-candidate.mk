# Isolated, UNSELECTED source proposal. Inherit only after all host gates pass.
# Native VT, service contexts, updated framework ABI and final-image qualification
# are separate mandatory prerequisites; this fragment alone is not a working IMS.
ifeq ($(TARGET_BUILD_VARIANT),userdebug)
PRODUCT_PACKAGES += \
    LdyAn00ImsService \
    LdyAn00ImsPrivappPermissions
endif
