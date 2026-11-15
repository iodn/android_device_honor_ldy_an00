# SPDX-License-Identifier: Apache-2.0
# Explicit kernel/vendor migration cohort; LK and preloader are never included.
_LDY_STOCK11_DYNAMIC_PARTITIONS := system system_dlkm vendor vendor_dlkm odm odm_dlkm
_LDY_STOCK11_OTA_PARTITIONS := boot init_boot vendor_boot dtbo system system_dlkm vendor vendor_dlkm odm odm_dlkm vbmeta vbmeta_system vbmeta_system_dlkm vbmeta_vendor vbmeta_vendor_dlkm vbmeta_odm vbmeta_odm_dlkm recovery_vbmeta
