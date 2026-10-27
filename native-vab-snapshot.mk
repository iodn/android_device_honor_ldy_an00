# Stock113 snapuserd accepts COW3.0 (78-byte header, 16-byte operations)
# and raw/none data; the retained first-stage dm-user socket handoff matches.
# Reviewed c8d13e1758123d38e5654b2b92d6665a4f6d1f0153f1ef2cd852569834cea1ba;
# this is static format/protocol evidence, not a hardware merge qualification.
PRODUCT_VIRTUAL_AB_COW_VERSION := 3
PRODUCT_VIRTUAL_AB_COMPRESSION_METHOD := none
# Preserve retained vendor tuning, but explicitly keep the proven dm-user route.
PRODUCT_PRODUCT_PROPERTIES += \
    ro.virtual_ab.enabled=true \
    ro.virtual_ab.retrofit=false \
    ro.virtual_ab.compression.enabled=true \
    ro.virtual_ab.userspace.snapshots.enabled=true \
    ro.virtual_ab.ublk.enabled=false
