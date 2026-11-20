# Opt in for native stock11 builds until the zRAM handoff is tested on hardware.
ifeq ($(LDY_AN00_MMD_ZRAM),true)
PRODUCT_SYSTEM_PROPERTIES += \
    mmd.zram.enabled=true \
    mmd.zram.size=100% \
    mmd.zram.writeback.enabled=false

PRODUCT_COPY_FILES += \
    device/honor/ldy_an00/rootdir/etc/init/ldy-an00-mmd.rc:$(TARGET_COPY_OUT_SYSTEM)/etc/init/ldy-an00-mmd.rc
endif
