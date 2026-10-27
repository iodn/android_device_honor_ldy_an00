# Source candidate only. Include only after the core bridge/display gate and policy qualify.
PRODUCT_PACKAGES += LdyAn00FrameworkFingerprintOverlay
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.fingerprint.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/permissions/android.hardware.fingerprint.xml
