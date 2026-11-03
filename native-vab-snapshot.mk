# Kernel dm-snapshot supports writable addon.d postinstall mounts.
PRODUCT_PRODUCT_PROPERTIES += \
    ro.virtual_ab.enabled=true \
    ro.virtual_ab.retrofit=false \
    ro.virtual_ab.compression.enabled=false \
    ro.virtual_ab.userspace.snapshots.enabled=false \
    ro.virtual_ab.ublk.enabled=false
