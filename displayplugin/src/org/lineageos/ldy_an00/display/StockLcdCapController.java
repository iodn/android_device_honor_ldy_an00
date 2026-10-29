// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;

import java.util.Optional;

/**
 * Selected-panel stock cap arithmetic. Research candidate; no Android caller is wired.
 * Every event is ordered by one caller thread. This class never computes an HDR boost.
 */
public final class StockLcdCapController {
    public enum Mode { AUTOMATIC, MANUAL }
    public enum Scene { LOCAL_OR_HLG, GALLERY_GROUP, SDR_PLUS }
    public record RawCaps(float lcd, float manual, float outdoor, float outdoorManual,
            float apl, int temperature) {}
    public record Sdr(Mode mode, float maximum, float manualRaw) {
        public Sdr {
            if (mode == null || !valid(maximum)) throw new IllegalArgumentException("Invalid normalized cap");
        }
    }
    // galleryAplLimitLiftQualified is ehdrAplEnable && actual HDR-gallery scene
    // && mEhdrAplEnable. A raw APL setting for the grouped scene is insufficient.
    public record HdrContext(Scene scene, Boolean thermalRestriction, Boolean batteryRestriction,
            Boolean hdrNoLimit, Boolean galleryModeEnabled, Boolean localAplEnabled,
            Float localAplRatio, Boolean galleryAplLimitLiftQualified, Boolean frontCameraLimitInactive) {}

    private final StockLcdPanel mPanel;

    public StockLcdCapController(StockLcdPanel panel) {
        mPanel = java.util.Objects.requireNonNull(panel);
        if (panel.thermalModeBrightnessMappingEnable() || !panel.thermalDragStrategyEnable()) {
            throw new IllegalArgumentException("Unqualified panel thermal strategy");
        }
    }

    private RawCaps mRaw;
    private int mCount;
    private boolean mIntervened, mPackageSwitched, mPowerOn;
    private long mPackageTime, mPowerOffTime;
    private boolean mOutdoorDiscount, mOutdoorDiscountPending;

    public void onCaps(RawCaps caps) {
        mRaw = caps;
        resetDrag();
    }
    public Float storedOutdoorApl() { return mRaw==null?null:mRaw.apl(); }
    private void resetDrag() {
        mCount = 0;
        mIntervened = false;
        mPackageSwitched = false;
    }
    public void onPackageChanged(long elapsedMs) {
        mPackageTime = elapsedMs;
        mPackageSwitched = true;
        mIntervened = false;
    }
    public void onIntervened() {
        mIntervened = true;
        mPackageSwitched = false;
    }
    // Caller must establish stock spline+handler readiness before dispatching this event.
    public void onAdjustmentCommitted(long elapsedMs) {
        // Stock299c50 clears the active discount but leaves pending message14 intact.
        mOutdoorDiscount = false;
        mCount = (float) elapsedMs - (float) mPackageTime < (float) mPanel.switchPackageMillis() && mCount == 1 ? 2 : 1;
        mPackageSwitched = false;
    }
    public void onPower(int state, int policy, boolean fingerprintOffUnlock, long elapsedMs) {
        boolean on = policy == 2 || policy == 3
                || state == 2 && policy == 1 && fingerprintOffUnlock;
        if (on != mPowerOn) {
            if (!on) mPowerOffTime = elapsedMs;
            else if (elapsedMs - mPowerOffTime > mPanel.powerUpdateMillis()) resetDrag();
            mPowerOn = on;
        }
    }
    // Caller owns message14 scheduling/cancellation on the same serial controller thread.
    public void onOutdoorDiscountCommand(int value) {
        if (!mPanel.outdoorDiscountEnable()) return;
        if (value == 0) {
            mOutdoorDiscountPending = false;
            mOutdoorDiscount = false;
        } else if (value == 1) {
            mOutdoorDiscountPending = true;
        }
    }
    public void onOutdoorDiscountTimer() {
        if (mOutdoorDiscountPending) {
            mOutdoorDiscountPending = false;
            mOutdoorDiscount = true;
        }
    }
    public Optional<Float> outdoorDiscountMaximum() {
        return mOutdoorDiscount ? Optional.of(mPanel.outdoorDiscountRaw() / 255f) : Optional.empty();
    }
    public boolean usesDrag() {
        return mIntervened || (!mPackageSwitched && mCount >= 1)
                || (mPackageSwitched && mCount > 1);
    }
    /** Selected career-disabled DEX2914f8 getter, normalized exactly at its gallery caller. */
    public Optional<Float> galleryAplThreshold(Float ambientLux) {
        if (mRaw == null || ambientLux == null || !Float.isFinite(ambientLux)
                || mPanel.hdrModes().careerModeEnabled()) return Optional.empty();
        boolean outdoor = mPanel.hdrModes().seekbarBasedLuxEnabled()
                && ambientLux > mPanel.hdrModes().seekbarLuxThreshold();
        float rawDrag = outdoor ? mRaw.outdoorManual() : mRaw.manual();
        boolean fallbackBoth = mRaw.manual() < 0f || mRaw.outdoorManual() < 0f;
        float normalizedDrag = fallbackBoth
                ? (outdoor ? mPanel.seekbarMaxRaw() : mPanel.manualMaxRaw()) : normalize(rawDrag);
        // brightnessFloatToFloat255 returns negative sentinel unchanged; positive map constrains.
        float rawConverted = normalizedDrag < 0f ? normalizedDrag
                : Math.max(0f, Math.min(1f, normalizedDrag)) * 255f;
        float rawLimit = Math.min(outdoor ? mPanel.seekbarMaxRaw() : mPanel.manualMaxRaw(),
                rawConverted);
        float limit = normalize(rawLimit);
        return valid(limit) ? Optional.of(limit) : Optional.empty();
    }

    public int dragCount() { return mCount; }

    /** Null/unknown early-battery eligibility refuses qualification, as does unsupported raw state. */
    public Optional<Sdr> sdr(Mode mode, Float ambientLux, Boolean earlyBatteryLimitInactive) {
        if (mRaw == null || mode == null || !Float.isFinite(mRaw.lcd())
                || !Float.isFinite(mRaw.manual()) || !Float.isFinite(mRaw.outdoor())
                || !Float.isFinite(mRaw.outdoorManual()) || !Float.isFinite(mRaw.apl())
                || !Boolean.TRUE.equals(earlyBatteryLimitInactive)) {
            return Optional.empty();
        }
        if (mode == Mode.MANUAL) {
            float raw = mPanel.thermalDragStrategyEnable() && mRaw.manual() > 0f
                    ? mRaw.temperature() == 40 ? (mRaw.lcd() + mRaw.manual()) / 2f
                            : mRaw.manual()
                    : mRaw.lcd();
            if (!(raw > mPanel.minValidThermalRaw())) raw = 255f; // mapping disabled on this selected panel.
            float maximum = normalize(raw);
            return valid(maximum) ? Optional.of(new Sdr(Mode.MANUAL, maximum, raw)) : Optional.empty();
        }
        if (ambientLux == null || !Float.isFinite(ambientLux) || ambientLux < 0f) {
            return Optional.empty();
        }
        float normal = normalize(mRaw.lcd());
        float drag = normalize(mRaw.manual());
        float outdoor = normalize(mRaw.outdoor());
        float outdoorDrag = normalize(mRaw.outdoorManual());
        float apl = normalize(mRaw.apl());
        if (mRaw.manual() < 0f || mRaw.outdoorManual() < 0f) {
            // Preserve the stock setter's unnormalized fallback. It cannot be published as [0,1].
            drag = mPanel.manualMaxRaw();
            outdoorDrag = mPanel.seekbarMaxRaw();
        }
        if (mRaw.apl() < 0f) apl = mRaw.apl(); // updateThermalAplBrightness.
        float maximum;
        if (ambientLux > mPanel.outdoorLuxThreshold() && outdoor > 0f) {
            maximum = mPanel.fullSceneApl() && mPanel.fullSceneAplThermalEnable()
                    && ambientLux > mPanel.aplLuxThreshold() && apl > 0f
                    ? apl : usesDrag() ? outdoorDrag : outdoor;
        } else {
            maximum = usesDrag() ? drag : normal;
        }
        return valid(maximum) ? Optional.of(new Sdr(Mode.AUTOMATIC, maximum, Float.NaN)) : Optional.empty();
    }

    /**
     * Automatic stock recovery, capped by the ordinary pipeline's output. current is the
     * stock-equivalent pre-recovery value, not an arbitrary SDR substitute. Missing context refuses.
     */
    public Optional<Float> automaticHdr(Sdr sdr, HdrContext context, float current,
            float unlimited, float ordinaryMaximum) {
        if (context == null || context.scene() == Scene.SDR_PLUS) return Optional.empty();
        return recoverAutomaticHdr(sdr, context, current, unlimited, ordinaryMaximum);
    }
    public Optional<Float> automaticSdrPlus(Sdr sdr, HdrContext context, float originalInput,
            float current, float curveCandidate, float ordinaryMaximum) {
        if (context == null || context.scene() != Scene.SDR_PLUS
                || context.thermalRestriction() == null || !valid(originalInput)
                || !valid(curveCandidate)) return Optional.empty();
        return recoverAutomaticHdr(sdr, context, current,
                context.thermalRestriction() ? originalInput : curveCandidate, ordinaryMaximum);
    }
    private Optional<Float> recoverAutomaticHdr(Sdr sdr, HdrContext context, float current,
            float unlimited, float ordinaryMaximum) {
        if (sdr == null || sdr.mode() != Mode.AUTOMATIC
                || context == null || context.scene() == null
                || context.thermalRestriction() == null || context.batteryRestriction() == null
                || context.hdrNoLimit() == null || context.galleryModeEnabled() == null
                || !valid(current) || !valid(ordinaryMaximum) || !Float.isFinite(unlimited)) {
            return Optional.empty();
        }
        float result = current;
        if (!context.galleryModeEnabled() || !(unlimited > 0f)) {
            return Optional.of(Math.min(result, ordinaryMaximum));
        }
        if (context.hdrNoLimit() && !context.thermalRestriction()) {
            if (context.scene() == Scene.LOCAL_OR_HLG) {
                if (context.localAplEnabled() == null) return Optional.empty();
                if (context.localAplEnabled()) {
                    if (context.localAplRatio() == null
                            || !Float.isFinite(context.localAplRatio())
                            || context.localAplRatio() < 0f) return Optional.empty();
                    result = Math.min(unlimited * context.localAplRatio(), 1f);
                    return Optional.of(Math.min(result, ordinaryMaximum));
                }
            } else {
                if (context.galleryAplLimitLiftQualified() == null) return Optional.empty();
                result = Math.max(current, unlimited);
                result = Math.min(result, context.galleryAplLimitLiftQualified() ? 1f : mPanel.seekbarMaxRaw() / 255f);
                return Optional.of(Math.min(result, ordinaryMaximum));
            }
        }
        if (!context.batteryRestriction()) {
            float mapped = hdrThermal(sdr.maximum());
            if (mapped > 0f) {
                result = Math.max(current, Math.min(unlimited, mapped));
                result = Math.min(result, mPanel.seekbarMaxRaw() / 255f);
            }
        }
        return Optional.of(Math.min(result, ordinaryMaximum));
    }

    /** Normal manual HDR only; SDR-plus requires its separate branch and is refused here. */
    public Optional<Float> manualHdr(Sdr sdr, HdrContext context, float ordinaryHdr) {
        if (sdr == null || sdr.mode() != Mode.MANUAL
                || context == null || context.scene() == null || !valid(ordinaryHdr)
                || context.thermalRestriction() == null || context.batteryRestriction() == null
                || context.hdrNoLimit() == null || context.scene() == Scene.SDR_PLUS
                || !Boolean.TRUE.equals(context.frontCameraLimitInactive())) {
            return Optional.empty();
        }
        // This fast-path is before the thermal/battery flag in the original method.
        if (equals255(sdr.manualRaw(), 255f)) return Optional.of(ordinaryHdr);
        float mapped = hdrThermal(sdr.maximum());
        if (!(mapped > 0f)) return Optional.empty();
        float result = Math.min(ordinaryHdr, mPanel.seekbarMaxRaw() / 255f);
        if (context.batteryRestriction()) result = Math.min(result, sdr.maximum());
        else if (!context.hdrNoLimit() || context.thermalRestriction()) {
            result = Math.min(result, mapped);
        }
        return Optional.of(result);
    }

    /** Selected SDR-plus suppression uses original input, not the already-boosted HDR value. */
    public Optional<Float> manualSdrPlus(Sdr sdr, HdrContext context, float originalInput,
            float ordinaryHdr, Boolean isOutdoor, Integer outdoorMaximumRaw) {
        if (sdr == null || sdr.mode() != Mode.MANUAL
                || context == null || context.scene() != Scene.SDR_PLUS
                || context.thermalRestriction() == null || context.batteryRestriction() == null
                || context.hdrNoLimit() == null || !valid(originalInput) || !valid(ordinaryHdr)
                || !Boolean.TRUE.equals(context.frontCameraLimitInactive())) {
            return Optional.empty();
        }
        if (equals255(sdr.manualRaw(), 255f)) return Optional.of(ordinaryHdr);
        if (!context.thermalRestriction() && !context.batteryRestriction()) {
            float output = Math.min(ordinaryHdr, mPanel.seekbarMaxRaw() / 255f);
            return Optional.of(context.hdrNoLimit()
                    ? output : Math.min(output, hdrThermal(sdr.maximum())));
        }
        if (isOutdoor == null || outdoorMaximumRaw == null) return Optional.empty();
        float cap = isOutdoor && outdoorMaximumRaw > 0 && !context.batteryRestriction()
                ? normalize(outdoorMaximumRaw) : sdr.maximum();
        return Optional.of(Math.min(ordinaryHdr, Math.min(originalInput, cap)));
    }

    public static float normalize(float raw) {
        if (equals(raw, 0f)) return -1f;
        if (equals(raw, -1f)) return Float.NaN;
        return Math.max(0f, Math.min(1f, raw / 255f));
    }
    private static boolean equals(float a, float b) {
        if (a == b || Float.isNaN(a) && Float.isNaN(b)) return true;
        if (Float.isNaN(a) || Float.isNaN(b)) return false;
        if (a > .05f) return Math.abs(a - b) < .0001f;
        return epsilonHigh(a) == epsilonHigh(b);
    }
    private static float epsilonHigh(float value) {
        return (float) Math.round(value < 0f ? value : value * 100000f);
    }
    private static boolean equals255(float a, float b) {
        return a == b || Float.isNaN(a) && Float.isNaN(b) || Math.abs(a - b) < .01f;
    }
    private static boolean valid(float value) {
        return Float.isFinite(value) && value >= 0f && value <= 1f;
    }
    public float hdrThermal(float value) {
        var points = mPanel.hdrThermalRatioPoints();
        if (!(value > 0f)) return value;
        for (int i = 1; i < points.size(); i++) {
            StockLcdPanel.Point previous = points.get(i - 1), next = points.get(i);
            if (value < next.x()) {
                return (next.y() - previous.y()) / (next.x() - previous.x())
                        * (value - previous.x()) + previous.y();
            }
        }
        return points.get(points.size() - 1).y();
    }
}
