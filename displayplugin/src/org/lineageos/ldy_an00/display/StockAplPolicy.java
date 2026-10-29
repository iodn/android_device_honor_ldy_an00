// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Owner-thread source arithmetic/state only. Native callbacks and scene78 dispatch are caller-owned. */
public final class StockAplPolicy {
    public enum Scene { NONE, LOCAL, HLG, GALLERY, SDR_VIDEO, THIRD_PARTY, PREVIEW, WHITE, ULTRA }
    /** Independent source booleans: curve priority is not the APL/gallery predicate. */
    public record SceneFlags(boolean local,boolean hlg,boolean gallery,boolean sdrVideo,
            boolean thirdParty,boolean preview,boolean white,boolean ultra,boolean sdrPlus) {
        public static SceneFlags of(Scene s) {
            if (s == null) return null;
            return new SceneFlags(s==Scene.LOCAL,s==Scene.HLG,s==Scene.GALLERY,
                    s==Scene.SDR_VIDEO,s==Scene.THIRD_PARTY,s==Scene.PREVIEW,
                    s==Scene.WHITE,s==Scene.ULTRA,false);
        }
        public boolean any() {
            return local || hlg || gallery || sdrVideo || thirdParty || preview || white
                    || ultra || sdrPlus;
        }
        public boolean ratioScene() { return local || hlg || gallery || sdrVideo || thirdParty; }
    }
    public record Event(boolean enabled, float ratio, float rawIndex) {
        public static Event fromPacked(int packed) {
            int raw = packed & 255;
            return new Event((packed >> 8) == 1, 1f + ((raw - 100f) * Float.intBitsToFloat(0x3cc565c0)) / 50f, raw);
        }
    }
    public record Effect(boolean brightnessUpdate, boolean animationRequested) {}
    public record BatteryEffect(boolean changed, Boolean fullSceneRequest) {}
    public record SceneAction(int scene, int value) {}
    public record FullSceneInputs(Float ambientLux, Boolean hdrThermal, Float storedOutdoorApl,
            Boolean game, Boolean camera, SceneFlags sceneFlags) {
        public FullSceneInputs(Float lux,Boolean thermal,Float outdoor,Boolean game,
                Boolean camera,Scene scene) { this(lux,thermal,outdoor,game,camera,SceneFlags.of(scene)); }
    }
    /** noOffsetBrightness is already framework-normalized; current mapper calibration is an
     * explicit port adaptation, not proof of OEM private spline equivalence. */
    public record BrightnessInputs(SceneFlags sceneFlags, Float thermalMaximum, Float historyLux,
            Boolean galleryAplInstanceEnabled, Float noOffsetBrightness, Float ambientLux) {
        public BrightnessInputs(Scene scene,Float thermal,Float history,Boolean gallery,
                Float noOffset,Float ambient) {
            this(SceneFlags.of(scene),thermal,history,gallery,noOffset,ambient);
        }
    }
    public record ManualRatio(float target, float ratio) {}
    public record Snapshot(float automaticRatio, float manualRatio, float manualTarget, int index,
            boolean fullSceneEnabled, boolean lowBattery) {}
    public Snapshot snapshot() {
        return new Snapshot(mAutoRatio,mManualRatio,mManualTarget,mIndex,mFullSceneEnabled,mLowBattery);
    }

    record Checkpoint(Snapshot state,boolean animation) {}
    Checkpoint checkpoint() { return new Checkpoint(snapshot(),mAutoAnimation); }
    void restore(Checkpoint saved) {
        var s = saved.state();
        mAutoRatio=s.automaticRatio(); mManualRatio=s.manualRatio(); mManualTarget=s.manualTarget();
        mIndex=s.index(); mFullSceneEnabled=s.fullSceneEnabled(); mLowBattery=s.lowBattery();
        mAutoAnimation=saved.animation();
    }

    private static final float EPSILON = 0.000001f;
    private final StockLcdPanel mPanel;
    private final StockAplPanel mData;
    private float mAutoRatio = 1f, mManualRatio = 1f, mManualTarget = 1f;
    private int mIndex;
    private boolean mLowBattery, mFullSceneEnabled, mAutoAnimation;

    public StockAplPolicy(StockLcdPanel panel) {
        mPanel = Objects.requireNonNull(panel);
        mData = panel.apl();
    }

    public float automaticRatio() { return mAutoRatio; }
    public float manualRatio() { return mManualRatio; }
    public float manualTarget() { return mManualTarget; }
    public int index() { return mIndex; }
    public boolean fullSceneEnabled() { return mFullSceneEnabled; }
    public boolean lowBattery() { return mLowBattery; }

    /** Source receiver compares raw level extra directly; there is no scale normalization. */
    public BatteryEffect onBatteryLevel(int level) {
        boolean low = mData.batteryThreshold() > 0 && level <= mData.batteryThreshold();
        if (low == mLowBattery) return new BatteryEffect(false,null);
        mLowBattery = low;
        // Source only requests disable on the falling transition; recovery does not auto-enable.
        return new BatteryEffect(true,low ? Boolean.FALSE : null);
    }

    /** Source getFullSceneAplEnable; no thermal inference from an ordinary brightness cap. */
    public Optional<Boolean> desiredFullScene(FullSceneInputs input) {
        if (!mPanel.fullSceneApl()) return Optional.of(false);
        if (input == null || !finite(input.ambientLux())) return Optional.empty();
        if (Math.round(input.ambientLux()) <= mPanel.aplLuxThreshold() || mLowBattery)
            return Optional.of(false);
        if (mPanel.fullSceneAplThermalEnable()) {
            if (input.hdrThermal() == null || !finite(input.storedOutdoorApl()))
                return Optional.empty();
            if (input.hdrThermal() || input.storedOutdoorApl() < 0f) return Optional.of(false);
        }
        if (input.game() == null || input.camera() == null || input.sceneFlags() == null)
            return Optional.empty();
        return Optional.of(!input.game() && !input.camera() && !input.sceneFlags().any());
    }

    /** Call when the source-equivalent posted owner task executes with handler+manager ready. */
    public Optional<SceneAction> executeFullSceneRequest(boolean enabled, boolean dispatchReady) {
        if (!dispatchReady || !mPanel.fullSceneApl() || mFullSceneEnabled == enabled)
            return Optional.empty();
        mFullSceneEnabled = enabled; // Stock stores before invoking manager.setScene.
        return Optional.of(new SceneAction(78,enabled ? 14 : 15));
    }

    public boolean consumeAutomaticDimming() {
        if (!mAutoAnimation || !mData.enabled()) return false;
        mAutoAnimation = false;
        return true;
    }

    /** No-input callback means unavailable, never a manufactured disabled event. */
    public Optional<Effect> onAutomaticEvent(Event event, Scene scene, Boolean controllerPresent,
            Float historyLux, Boolean specialActive) {
        return onAutomaticEvent(event,SceneFlags.of(scene),controllerPresent,historyLux,specialActive);
    }
    public Optional<Effect> onAutomaticEvent(Event event, SceneFlags scene, Boolean controllerPresent,
            Float historyLux, Boolean specialActive) {
        if (event == null || controllerPresent == null
                || controllerPresent && scene == null) return Optional.empty();
        int nextIndex = mPanel.fullSceneApl() ? getIndex(event.rawIndex()) : 0;
        boolean rangeStrict = event.ratio() - 1f > EPSILON && 1.5f - event.ratio() > EPSILON;
        if (mData.galleryEnabled() && rangeStrict) {
            return updateAutomaticRatio(event.ratio(),true,scene,controllerPresent,
                    historyLux,specialActive);
        }
        boolean ratioScene = !controllerPresent || scene.ratioScene();
        boolean enabled = event.enabled() && ratioScene;
        if (enabled && rangeStrict) {
            return updateAutomaticRatio(event.ratio(),event.enabled(),scene,controllerPresent,
                    historyLux,specialActive);
        }
        if (mPanel.fullSceneApl() && event.enabled()) {
            boolean changed = mIndex != nextIndex;
            mIndex = nextIndex;
            return Optional.of(new Effect(changed,false));
        }
        if (enabled && event.ratio() - 1f >= EPSILON && 1.5f - event.ratio() >= EPSILON) {
            mAutoRatio = 1f;
            mIndex = 0;
            return Optional.of(new Effect(false,false));
        }
        boolean changed = mAutoRatio != 1f || mIndex != 0;
        mAutoRatio = 1f;
        mIndex = 0;
        return Optional.of(new Effect(changed,false));
    }

    public Optional<Effect> onManualEvent(Event event, Scene scene, Float historyLux,
            Boolean specialActive) {
        return onManualEvent(event,SceneFlags.of(scene),historyLux,specialActive);
    }
    public Optional<Effect> onManualEvent(Event event, SceneFlags scene, Float historyLux,
            Boolean specialActive) {
        if (event == null || scene == null) return Optional.empty();
        boolean strict = event.ratio() - 1f > EPSILON && 1.5f - event.ratio() > EPSILON;
        if (mData.galleryEnabled() && strict)
            return updateManualRatio(event.ratio(),true,scene,historyLux,specialActive);
        boolean enabled = event.enabled() && scene.ratioScene();
        if (enabled && strict)
            return updateManualRatio(event.ratio(),true,scene,historyLux,specialActive);
        if (enabled && event.ratio() - 1f >= EPSILON && 1.5f - event.ratio() >= EPSILON)
            return Optional.of(new Effect(false,false));
        boolean changed = mManualRatio != 1f;
        mManualRatio = 1f;
        return Optional.of(new Effect(changed,false));
    }

    private Optional<Effect> updateAutomaticRatio(float ratio, boolean enabled, SceneFlags scene,
            boolean controllerPresent, Float lux, Boolean specialActive) {
        if (!controllerPresent || !mData.enabled()) return Optional.of(new Effect(false,false));
        Optional<Float> result = resolveRatio(ratio,enabled,scene,lux,specialActive);
        if (result.isEmpty()) return Optional.empty();
        boolean changed = result.get() != mAutoRatio;
        mAutoRatio = result.get();
        if (changed) mAutoAnimation = true;
        return Optional.of(new Effect(changed,changed));
    }

    private Optional<Effect> updateManualRatio(float ratio, boolean enabled, SceneFlags scene,
            Float lux, Boolean specialActive) {
        if (!mData.enabled()) return Optional.of(new Effect(false,false));
        Optional<Float> result = resolveRatio(ratio,enabled,scene,lux,specialActive);
        if (result.isEmpty()) return Optional.empty();
        boolean changed = result.get() != mManualRatio;
        mManualRatio = result.get();
        return Optional.of(new Effect(changed,false));
    }

    private Optional<Float> resolveRatio(float incoming, boolean enabled, SceneFlags scene, Float lux,
            Boolean specialActive) {
        if (!enabled || mLowBattery || !(incoming - 1f >= EPSILON)
                || !(1.5f - incoming >= EPSILON)) return Optional.of(1f);
        if (mData.specialEnabled()) {
            if (specialActive == null) return Optional.empty();
            if (specialActive) return Optional.of(1f);
        }
        if (scene.local() || scene.hlg()) {
            if (!mData.sunEnabled()) return Optional.of(incoming);
            if (lux == null) return Optional.empty();
            return Optional.of(Math.min(incoming,sunRatio(mData.sunRatioPoints(),lux)));
        }
        if (scene.sdrVideo() || scene.thirdParty()) {
            if (!mData.esdrEnabled()) return Optional.of(1f);
            if (lux == null) return Optional.empty();
            return Optional.of(Math.min(incoming,sunRatio(mData.sdrRatioPoints(),lux)));
        }
        if (scene.gallery()) {
            if (!mPanel.hdrModes().ehdrAplEnabled()) return Optional.of(1f);
            return Optional.of(mData.ehdrMaxRatio() > 0f
                    ? Math.min(mData.ehdrMaxRatio(),incoming) : incoming);
        }
        return Optional.of(mData.galleryEnabled() ? incoming : 1f);
    }


    /** Exact2459ac: invalid/nonpositive lux=>1, firstpoint lower tail=>1, final y upper tail. */
    public static float sunRatio(List<StockLcdPanel.Point> points, float lux) {
        if (Float.isNaN(lux) || lux <= 0f || points == null || points.isEmpty()) return 1f;
        var previous = points.get(0);
        float out = 1f;
        for (var point : points) {
            if (lux < point.x()) {
                if (point.x() <= previous.x()) return 1f;
                return (point.y() - previous.y()) / (point.x() - previous.x())
                        * (lux - previous.x()) + previous.y();
            }
            out = point.y();
            previous = point;
        }
        return out;
    }

    /** Exact293594: first threshold>=raw; aboveall thresholds returns0. */
    public int getIndex(float raw) {
        for (int i = 0; i < mData.indexThresholds().size(); i++)
            if (!(raw > mData.indexThresholds().get(i))) return i;
        return 0;
    }

    public Optional<ManualRatio> getManualRatio(float input) {
        if (!unit(input)) return Optional.empty();
        mManualTarget = Math.min(input,mPanel.seekbarMaxRaw()/255f);
        return Optional.of(new ManualRatio(mManualTarget,mManualTarget > 0f ? mManualRatio : 1f));
    }

    public record ManualBrightness(float output,Float nextTarget) {}
    /** Exact29d6a8: old target is loaded before getAplRatio updates the stored target. */
    public Optional<ManualBrightness> manualBrightness(float input, SceneFlags flags,
            Float historyLux, Snapshot state) {
        if (!unit(input) || flags == null || state == null) return Optional.empty();
        float maximum = mPanel.seekbarMaxRaw()/255f;
        boolean eligible = (flags.local() || flags.hlg()) && input >= maximum;
        if (!eligible && mPanel.hdrModes().ehdrAplEnabled() && flags.gallery()) {
            if (!finite(historyLux)) return Optional.empty();
            eligible = historyLux > mPanel.hdrModes().ehdrAplLuxThreshold() && input >= maximum;
        }
        if (!eligible && mData.esdrEnabled() && input >= maximum)
            eligible = flags.sdrVideo() || flags.thirdParty();
        if (!eligible) return Optional.of(new ManualBrightness(input,null));
        float target=Math.min(input,maximum);
        float ratio=target>0f?state.manualRatio():1f;
        return Optional.of(new ManualBrightness(
                Math.min(state.manualTarget()*ratio,1f),target));
    }
    void commitManualTarget(float target) { mManualTarget=target; }

    /** Exact291394, with source ratio/index state frozen for this frame. */
    public Optional<Float> automaticBrightness(float input, BrightnessInputs facts, Snapshot state) {
        if (!unit(input) || state == null) return Optional.empty();
        if (!mData.enabled() || input <= 0f) return Optional.of(input);
        if (facts == null || facts.sceneFlags() == null || !finite(facts.thermalMaximum()))
            return Optional.empty();
        float target = Math.min(input,mPanel.seekbarMaxRaw()/255f);
        float ratio = state.automaticRatio();
        if (ratio * target > facts.thermalMaximum()) ratio = facts.thermalMaximum() / input;
        if (mPanel.hdrModes().ehdrAplEnabled() && facts.sceneFlags().gallery()) {
            if (facts.galleryAplInstanceEnabled() == null) return Optional.empty();
            if (!facts.galleryAplInstanceEnabled()) return Optional.of(target);
            if (!finite(facts.historyLux())) return Optional.empty();
            if (facts.historyLux() < mPanel.hdrModes().ehdrAplLuxThreshold()
                    || input < mPanel.seekbarMaxRaw()/255f) return Optional.of(target);
        }
        if (state.fullSceneEnabled() && !facts.sceneFlags().any()) {
            return fullSceneBrightness(input,facts,state.index());
        }
        return Optional.of(target * ratio);
    }

    private Optional<Float> fullSceneBrightness(float input, BrightnessInputs facts, int index) {
        if (!finite(facts.noOffsetBrightness()) || !finite(facts.ambientLux()))
            return Optional.empty();
        float noOffset = facts.noOffsetBrightness();
        if (input * 1.2f < noOffset) return Optional.of(input);
        int lux = Math.round(facts.ambientLux());
        // Source producer getAplIndex guarantees0..size-1. Refuse impossible corruptedsize index.
        if (lux <= mPanel.aplLuxThreshold() || index < 0
                || index >= mData.indexThresholds().size()) return Optional.of(input);
        return Optional.of(Math.min(interpolateFullSceneLux(lux,index),facts.thermalMaximum()));
    }

    /** Exact292988 raw-lux fraction-first operation order; normalization only at the end. */
    public float interpolateFullSceneLux(int lux, int index) {
        if (index < 0 || index >= mData.indexThresholds().size())
            throw new IllegalArgumentException("APL index");
        var rows = mData.luxRows();
        int lower = 0, upper = rows.size()-1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).lux() <= lux) lower = i;
            else break;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).lux() >= lux) { upper = i; break; }
        }
        var low = rows.get(lower);
        var high = rows.get(upper);
        float value = low.brightnessLevels().get(index);
        if (lower != upper) {
            float highValue = high.brightnessLevels().get(index);
            if (!(Math.abs(value-highValue) < 0.01f)) {
                float fraction = ((float)lux - (float)low.lux())
                        / ((float)high.lux() - (float)low.lux());
                value += fraction * (highValue-value);
            }
        }
        return rawToNormalized(value);
    }

    private static float rawToNormalized(float raw) {
        if (floatEquals(raw,0f)) return -1f;
        if (floatEquals(raw,-1f)) return Float.NaN;
        return Math.max(0f,Math.min(1f,raw/255f));
    }
    private static boolean floatEquals(float a,float b) {
        if (a == b || Float.isNaN(a) && Float.isNaN(b)) return true;
        if (Float.isNaN(a) || Float.isNaN(b)) return false;
        if (a > .05f) return Math.abs(a-b) < .0001f;
        return (float)Math.round(a < 0f ? a : a*100000f)
                == (float)Math.round(b < 0f ? b : b*100000f);
    }

    private static boolean finite(Float f) { return f != null && Float.isFinite(f); }
    private static boolean unit(float f) { return Float.isFinite(f) && f >= 0f && f <= 1f; }
}
