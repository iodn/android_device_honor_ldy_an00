# Userspace-only bring-up with the existing Android 16 vendor and GKI kernel.
PRODUCT_SHIPPING_API_LEVEL := 36
PRODUCT_USE_DYNAMIC_PARTITIONS := true
PRODUCT_NO_BIONIC_PAGE_SIZE_MACRO := true
PRODUCT_MAX_PAGE_SIZE_SUPPORTED := 4096

# The retained RadioConfig advertises V4 but its SIM-type methods do not dispatch.
PRODUCT_RELEASE_CONFIG_MAPS += \
    device/honor/ldy_an00/config/release/release_config_map.textproto

# No OTA payload or replacement boot chain is produced at this stage.
ifneq ($(TARGET_PRODUCT),lineage_ldy_an00_native)
AB_OTA_UPDATER := false
endif
PRODUCT_BUILD_BOOT_IMAGE := false
PRODUCT_BUILD_INIT_BOOT_IMAGE := false
PRODUCT_BUILD_VENDOR_BOOT_IMAGE := false
PRODUCT_BUILD_VENDOR_IMAGE := false
PRODUCT_BUILD_ODM_IMAGE := false
PRODUCT_BUILD_VENDOR_DLKM_IMAGE := false
PRODUCT_BUILD_ODM_DLKM_IMAGE := false
PRODUCT_BUILD_SYSTEM_DLKM_IMAGE := false
PRODUCT_BUILD_RECOVERY_IMAGE := false
PRODUCT_BUILD_SUPER_PARTITION := false
PRODUCT_BUILD_SUPER_EMPTY_IMAGE := false
PRODUCT_BUILD_USERDATA_IMAGE := false
PRODUCT_BUILD_CACHE_IMAGE := false
PRODUCT_BUILD_VBMETA_IMAGE := false
PRODUCT_BUILD_PVMFW_IMAGE := false

PRODUCT_SYSTEM_PROPERTIES += ro.zygote=zygote64
# Default both SIM slots to the reviewed NR/LTE/GSM/WCDMA mode.
PRODUCT_SYSTEM_PROPERTIES += ro.telephony.default_network=26,26
# Stock hardware capability read by the IMS OEM plugin before WFO startup.
PRODUCT_SYSTEM_PROPERTIES += ro.vendor.config.vowifi=true
# Preserve Honor legacy audio parameter transport through the retained HAL.
PRODUCT_SYSTEM_PROPERTIES += ro.audio.honor_legacy_parameters=true

# Stock logical density; TARGET_SCREEN_DENSITY only affects the unbuilt vendor image.
PRODUCT_PRODUCT_PROPERTIES += ro.sf.lcd_density=560

# Select first-stage mount exclusions for embedded product/system_ext.
PRODUCT_PACKAGES += gsi_skip_mount.cfg

# Stock Honor exposes software navigation; the navigation mode remains selectable.
PRODUCT_PACKAGES += LdyAn00FrameworkNavigationBarOverlay
# Restore the measured stock ambient-light curve and availability.
PRODUCT_PACKAGES += LdyAn00FrameworkAutoBrightnessOverlay
# Device power estimates and CPU scaling-policy buckets for the retained kernel.
PRODUCT_PACKAGES += LdyAn00FrameworkPowerProfileOverlay
# The retained Honor performance HAL requires its stock policy to initialize.
PRODUCT_PACKAGES += LdyAn00UniPerfConfig
# Populate standard hints through a read-only userspace configuration override.
PRODUCT_PACKAGES += LdyAn00MtkPowerConfig
PRODUCT_COPY_FILES += \
    device/honor/ldy_an00/rootdir/etc/init/ldy-an00-power-config.rc:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/init/ldy-an00-power-config.rc
PRODUCT_PACKAGES += LdyAn00PowerService LdyAn00FrameworkPowerServiceOverlay
PRODUCT_SYSTEM_SERVER_JARS_EXTRA += LdyAn00PowerService
# Restore measured native display geometry and device-level IMS capability gates.
PRODUCT_PACKAGES += LdyAn00FrameworkDisplayGeometryOverlay
ifeq ($(TARGET_BUILD_VARIANT),userdebug)
PRODUCT_PACKAGES += LdyAn00FrameworkImsAvailabilityOverlay
# Match stock 44011 carrier VoLTE availability.
PRODUCT_PACKAGES += LdyAn00CarrierConfigOverlay
endif
# Keep the status-bar clock inside the physical display edge.
PRODUCT_PACKAGES += LdyAn00SystemUIClockPaddingOverlay

# Expose only the firm stage of the stock pressure button to Lineage key settings.
PRODUCT_PACKAGES += LdyAn00LineageHardwareKeysOverlay
PRODUCT_COPY_FILES += \
    device/honor/ldy_an00/keylayout/input_spb_press.kl:$(TARGET_COPY_OUT_PRODUCT)/usr/keylayout/input_spb_press.kl

# Exact stock LDY-AN00 panel calibration; product config takes precedence over vendor.
PRODUCT_COPY_FILES += \
    device/honor/ldy_an00/displayconfig/display_id_4627039422300187648.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/displayconfig/display_id_4627039422300187648.xml

# Stock TEE binaries/configs qualified against the retained vendor policy.
$(call inherit-product, vendor/honor/ldy_an00/tee-framework.mk)

# Stock public MediaTek NPU clients for ordinary applications.
$(call inherit-product, vendor/honor/ldy_an00/npu-framework.mk)

# Stock audio algorithm engine and deterministic tuning.
$(call inherit-product, vendor/honor/ldy_an00/audio-sws-framework.mk)

# Personal userdebug diagnostics; no capture package in user builds.
ifeq ($(TARGET_BUILD_VARIANT),userdebug)
PRODUCT_PACKAGES += ldy-guest-capture LdyAn00HnRadioMetadataProbe
# Corrected local LPA fork: no global startup logical-channel cleanup sweep.
PRODUCT_PACKAGES += OpenEUICC
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.telephony.euicc.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/permissions/android.hardware.telephony.euicc.xml
# Reviewed experimental integrations for the next personal userdebug test.
$(call inherit-product, device/honor/ldy_an00/fingerprint-feature.mk)
$(call inherit-product, device/honor/ldy_an00/ims/ims-candidate.mk)
$(call inherit-product, device/honor/ldy_an00/ims-phone-selection.mk)
$(call inherit-product, vendor/honor/ldy_an00/ims-native-modules.mk)
endif

# The OEM plugin uses the IMS app parent classloader at runtime.
$(call add-product-dex-preopt-module-config,LdyAn00ImsOemPlugin,disable)

# Retained provider otherwise truncates its list to cameras 0/1 for CTS.
# Match stock system/build.prop so the secure face camera is enumerated.
PRODUCT_SYSTEM_PROPERTIES += ro.vendor.qti.va_aosp.support=1

# Reviewed personal userdebug trial with the retained stock secure 2D face mode.
PRODUCT_SYSTEM_EXT_PROPERTIES += ro.config.support_face_mode=1
# The entry opens Phone consent; it does not select a SIM path automatically.
PRODUCT_SYSTEM_EXT_PROPERTIES += ro.lineage.ldy_an00.esim_bridge=true
# Explicit consent-based rooted eSIM diagnostics in personal userdebug builds only.
ifeq ($(TARGET_BUILD_VARIANT),userdebug)
PRODUCT_SYSTEM_EXT_PROPERTIES += ro.lineage.ldy_an00.esim_test_support=true
else
PRODUCT_SYSTEM_EXT_PROPERTIES += ro.lineage.ldy_an00.esim_test_support=false
endif
PRODUCT_COPY_FILES += \
    device/honor/ldy_an00/rootdir/etc/init/ldy-an00-face-mode.rc:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/init/ldy-an00-face-mode.rc

# Opt in only in this isolated face/eSIM trial source branch.
PRODUCT_SYSTEM_EXT_PROPERTIES += ro.lineage.ldy_an00.face_bridge=true
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.biometrics.face.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/permissions/android.hardware.biometrics.face.xml

# The private HAL owns the RGB camera and renders into the enrollment SurfaceView.
PRODUCT_PACKAGE_OVERLAYS += device/honor/ldy_an00/overlay-face-enroll
PRODUCT_ENFORCE_RRO_EXCLUDED_OVERLAYS += device/honor/ldy_an00/overlay-face-enroll

# Optional measured-metadata lens shortcut trial; optical handoff still needs validation.
PRODUCT_PACKAGE_OVERLAYS += device/honor/ldy_an00/overlay-aperture

# Exact stock CPU policy plugin for the complete typed display engine.
PRODUCT_PACKAGES += libhonor_ldy_an00_sf_cpupolicy

# Native Settings controls use the modes advertised by the display HAL.
PRODUCT_PACKAGES += LdyAn00SettingsRefreshRateOverlay


# Explicit personal DSU qualification only; native release targets cannot opt in.
$(call inherit-product, device/honor/ldy_an00/reviewed-dsu-candidate.mk)
