# Recovery-only hardware integration.
$(call inherit-product, vendor/honor/ldy_an00/recovery/recovery.mk)

PRODUCT_COPY_FILES += \
    device/honor/ldy_an00/recovery/init.recovery.mt6993.rc:recovery/root/init.recovery.mt6993.rc \
    device/honor/ldy_an00/recovery/init.ldy_an00.recovery.rc:recovery/root/system/etc/init/hw/init.ldy_an00.recovery.rc \
    device/honor/ldy_an00/recovery/product.prop:recovery/root/system/product/etc/build.prop

# The compiler uses the upstream trust store. Release packaging replaces it
# with recovery/security/ota.x509.pem before owner AVB signing.
