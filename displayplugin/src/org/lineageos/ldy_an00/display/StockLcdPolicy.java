// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;

import com.android.server.display.plugin.types.BrightnessThermalPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Private glue prototype. Only an actual Sender dispatch is a logical caps command. */
public final class StockLcdPolicy implements BrightnessThermalPolicy {
    public enum HdrSceneState {
        BASELINE, GALLERY, PREVIEW, WHITE, SDR_VIDEO, THIRD_PARTY, NOT_READY
    }
    public record StockContext(Boolean earlyBatteryLimitInactive,
            StockLcdCapController.HdrContext hdr, Boolean manualOutdoor,
            Integer manualOutdoorMaximumRaw, HdrSceneState hdrSceneState,
            StockAplPolicy.SceneFlags aplSceneFlags) {
        public StockContext(Boolean early,StockLcdCapController.HdrContext hdr,Boolean outdoor,
                Integer maximum,HdrSceneState scene) {
            this(early,hdr,outdoor,maximum,scene,null);
        }
        public StockContext(Boolean earlyBatteryLimitInactive, StockLcdCapController.HdrContext hdr,
                Boolean manualOutdoor, Integer manualOutdoorMaximumRaw) {
            this(earlyBatteryLimitInactive,hdr,manualOutdoor,manualOutdoorMaximumRaw,
                    HdrSceneState.NOT_READY,null);
        }
    }
    private record Command(long revision, StockLcdCapController.RawCaps caps) {}
    private record Publication(long revision, Command command, long contextRevision,
            StockContext context, long outdoorRevision, Integer outdoorCommand) {}
    /** Must enqueue to the existing control worker, never Binder/I/O inline on DPC. */
    public interface AplControlOwner {
        void requestFullScene(boolean enabled);
        /** Cached qualification only; no Binder/file access on the display thread. */
        default boolean canDispatchFullScene() { return false; }
        default void setManualSensorActive(boolean enabled) {}
        default void onDisplayPower(int state,int policy) {}
        default void onDisplayPower(CompositionSource source,int state,int policy) {
            onDisplayPower(state,policy);
        }
        default void observeNativeHdr(Boolean hdr) {}
        default void onCompositionSource(CompositionSource source,boolean active) {}
    }
    private record AplCheckpoint(StockAplPolicy.Checkpoint state,boolean automaticKnown,
            boolean manualKnown,StockAplPolicy.SceneFlags flags,Float automaticLux,Float manualLux,
            boolean galleryAplInstance,BooleanSupplier lifetime) {}
    private AplCheckpoint mAplCheckpoint;
    private long mAplEpoch, mAplServiceGeneration = -1, mAplEventRevision;
    private AplControlOwner mAplControlOwner;
    private Float mLatestManualLux;
    private Integer mLatestAplBattery;
    private boolean mAplUnobservedEvent;

    private final String mDisplayId;
    private final StockLcdPanel mPanel;
    private final Object mLock = new Object();
    private final List<Bound> mBindings = new ArrayList<>();
    private long mCommandRevision, mContextRevision, mPublicationRevision, mOutdoorRevision;
    private Publication mLatest = new Publication(0,null,0,
            new StockContext(null,null,null,null),0,null);

    public StockLcdPolicy(String displayId, StockLcdPanel panel) {
        mDisplayId = Objects.requireNonNull(displayId);
        mPanel = Objects.requireNonNull(panel);
    }
    /** One policy cycle: null dispatch means Sender dedup, not release or a fresh command. */
    public void offerPolicy(StockLcdCapAggregator.Dispatch dispatch, StockContext context,
            Integer outdoorCommand) {
        Objects.requireNonNull(context);
        if (dispatch != null && dispatch.caps().size() != 5) {
            throw new IllegalArgumentException("Five caps required");
        }
        Publication publication;
        List<Bound> bindings;
        synchronized (mLock) {
            boolean contextChanged = !Objects.equals(context,mLatest.context());
            boolean outdoorEvent = outdoorCommand != null
                    && (outdoorCommand == 0 || outdoorCommand == 1);
            if (dispatch == null && !contextChanged && !outdoorEvent) return;
            Command command = mLatest.command();
            if (dispatch != null) {
                var c = dispatch.caps();
                command = new Command(++mCommandRevision, new StockLcdCapController.RawCaps(
                        c.get(0),c.get(1),c.get(2),c.get(3),c.get(4),dispatch.temperature()));
            }
            if (contextChanged || outdoorEvent) ++mContextRevision;
            if (outdoorEvent) ++mOutdoorRevision;
            publication = new Publication(++mPublicationRevision,command,mContextRevision,
                    context,mOutdoorRevision,
                    outdoorEvent ? outdoorCommand : mLatest.outdoorCommand());
            mLatest = publication;
            bindings = List.copyOf(mBindings);
        }
        for (Bound binding : bindings) binding.enqueue(publication);
    }
    public void setAplControlOwner(AplControlOwner control) {
        List<Bound> bindings;
        synchronized (mLock) { mAplControlOwner=control; bindings=List.copyOf(mBindings); }
        for (Bound binding:bindings) binding.mOwner.post(() -> {
            binding.requireOwner();
            if (!binding.mClosed) {
                if(binding.mApl.lowBattery())binding.requestFullScene(false);
                if(control!=null && binding.mCompositionSource!=null)
                    control.onCompositionSource(binding.mCompositionSource,true);
                if(control!=null && binding.mManualSensorActive!=null)
                    control.setManualSensorActive(binding.mManualSensorActive);
                if(control!=null && binding.mNativeHdrObserved)
                    control.observeNativeHdr(binding.mNativeHdr);
                binding.mOwner.invalidate();
            }
        });
    }
    /** Worker delivers actual samples in its Handler order, before any following APL event. */
    public void offerManualHistoryLux(Float lux) {
        if (lux != null && (!Float.isFinite(lux) || lux<0f)) throw new IllegalArgumentException("lux");
        List<Bound> bindings;
        synchronized(mLock) { mLatestManualLux=lux; bindings=List.copyOf(mBindings); }
        for(Bound binding:bindings) binding.mOwner.post(() -> {
            binding.requireOwner();
            if(!binding.mClosed) binding.mAplManualLux=lux;
        });
    }
    /** No cached event replay to later sessions or scenes. Every source event remains ordered. */
    public void onPackedApl(long serviceGeneration,int packed,BooleanSupplier lifetime) {
        Objects.requireNonNull(lifetime);
        List<Bound> bindings;long epoch,revision;
        synchronized(mLock) {
            if(serviceGeneration<mAplServiceGeneration) return;
            mAplServiceGeneration=serviceGeneration;
            epoch=mAplEpoch;revision=++mAplEventRevision;bindings=List.copyOf(mBindings);
            if(bindings.isEmpty())mAplUnobservedEvent=true;
        }
        for(Bound binding:bindings) binding.mOwner.post(() -> {
            binding.requireOwner();
            synchronized(mLock) { if(binding.mClosed || epoch!=mAplEpoch) return; }
            if(!lifetime.getAsBoolean() || revision<=binding.mAppliedAplEvent) return;
            binding.mAppliedAplEvent=revision;
            binding.applyAplEvent(StockAplPolicy.Event.fromPacked(packed),lifetime);
        });
    }
    /** Invalidated callbacks cannot become a release or a fabricated ratio1. */
    public void invalidateApl() {
        List<Bound> bindings;
        synchronized(mLock) {
            ++mAplEpoch; mAplUnobservedEvent=true; bindings=List.copyOf(mBindings);
        }
        for(Bound binding:bindings) binding.mOwner.post(() -> {
            binding.requireOwner();
            if(binding.mClosed) return;
            binding.mAutomaticAplKnown=false;binding.mManualAplKnown=false;
            binding.mOwner.invalidate();
        });
    }
    public void offerAplBatteryLevel(int level) {
        List<Bound> bindings;
        synchronized(mLock) { mLatestAplBattery=level;bindings=List.copyOf(mBindings); }
        for(Bound binding:bindings) binding.mOwner.post(() -> {
            binding.requireOwner();if(binding.mClosed)return;
            var result=binding.mApl.onBatteryLevel(level);
            if(Boolean.FALSE.equals(result.fullSceneRequest())) binding.requestFullScene(false);
            if(result.changed())binding.mOwner.invalidate();
        });
    }
    @Override public Session bind(String displayId, Owner owner) {
        if (!owner.isCurrentThread()) throw new IllegalStateException("Wrong display owner");
        if (!mDisplayId.equals(displayId)) return null;
        Bound binding;
        Publication initial;
        synchronized (mLock) {
            if (!mBindings.isEmpty()) return null; // One primary owner for static stock APL state.
            binding=new Bound(owner);
            if(mAplCheckpoint!=null) binding.restoreApl(mAplCheckpoint);
            if(mAplUnobservedEvent) {
                binding.mAutomaticAplKnown=false;binding.mManualAplKnown=false;
            }
            binding.mAplManualLux=mLatestManualLux;
            if(mLatestAplBattery!=null)binding.mApl.onBatteryLevel(mLatestAplBattery);
            mBindings.add(binding);
            initial = mLatest;
        }
        // A concurrent newer publication may enqueue first; the owner rejects older replay.
        binding.enqueue(initial);
        owner.post(() -> {
            binding.requireOwner();
            if(!binding.mClosed) {
                AplControlOwner control;
                synchronized(mLock) { control=mAplControlOwner; }
                if(control!=null && binding.mCompositionSource!=null)
                    control.onCompositionSource(binding.mCompositionSource,true);
                if(binding.mApl.lowBattery())binding.requestFullScene(false);
            }
        });
        return binding;
    }

    private StockHdrCurve.Scene resolveHdrScene(StockContext stock,int flags) {
        if(stock.hdrSceneState()==null || stock.hdrSceneState()==HdrSceneState.NOT_READY)
            return StockHdrCurve.Scene.NONE;
        var s=stock.aplSceneFlags();
        if(s==null)return resolveHdrScene(stock.hdrSceneState(),flags);
        // Actual getLocalHdrBrightness2922aa..2923de priority, distinct from ratio/galleries.
        if(s.preview())return StockHdrCurve.Scene.PREVIEW;
        if(s.hlg())return StockHdrCurve.Scene.HLG;
        if(s.ultra())return StockHdrCurve.Scene.ULTRA;
        if(s.sdrPlus())return StockHdrCurve.Scene.NONE; // No guessed absent curve.
        if(s.local())return StockHdrCurve.Scene.LOCAL;
        if(s.white())return StockHdrCurve.Scene.WHITE;
        if(s.sdrVideo())return StockHdrCurve.Scene.SDR_VIDEO;
        if(s.thirdParty())return StockHdrCurve.Scene.THIRD_PARTY;
        return s.gallery()?StockHdrCurve.Scene.GALLERY:StockHdrCurve.Scene.NONE;
    }
    private StockHdrCurve.Scene resolveHdrScene(HdrSceneState state, int flags) {
        return switch (state) {
            case BASELINE -> {
                if ((flags & 0x400) != 0 && mPanel.hdrModes().hlgEnabled()) {
                    yield StockHdrCurve.Scene.HLG;
                }
                if ((flags & 0xc00) != 0) yield StockHdrCurve.Scene.LOCAL;
                yield (flags & 0x1000) != 0 ? StockHdrCurve.Scene.ULTRA : StockHdrCurve.Scene.NONE;
            }
            case GALLERY -> StockHdrCurve.Scene.GALLERY;
            case PREVIEW -> StockHdrCurve.Scene.PREVIEW;
            case WHITE -> StockHdrCurve.Scene.WHITE;
            case SDR_VIDEO -> StockHdrCurve.Scene.SDR_VIDEO;
            case THIRD_PARTY -> StockHdrCurve.Scene.THIRD_PARTY;
            case NOT_READY -> StockHdrCurve.Scene.NONE;
        };
    }

    private final class Bound implements Session {
        private final Owner mOwner;
        private final StockLcdCapController mController = new StockLcdCapController(mPanel);
        private boolean mClosed;
        private boolean mFirstHistoryKnown;
        private final StockFirstScreenDelay mFirst = new StockFirstScreenDelay(
                mPanel.hdrModes().localEnabled() || mPanel.hdrModes().galleryEnabled()
                || mPanel.hdrModes().previewEnabled() || mPanel.hdrModes().whiteEnabled()
                || mPanel.hdrModes().sdrVideoPlusEnabled() || mPanel.hdrModes().thirdPartySdrEnabled()
                || mPanel.hdrModes().hlgEnabled());
        private long mAppliedPublication = -1, mAppliedCommand = -1, mAppliedContext = -1;
        private long mOutdoorTimerEpoch, mAppliedOutdoor = -1;
        private final StockLcdCapController mArithmetic = new StockLcdCapController(mPanel);
        private final StockHdrCurve mHdrCurve = new StockHdrCurve(mPanel);
        private final StockAplPolicy mApl=new StockAplPolicy(mPanel);
        private boolean mAutomaticAplKnown=true,mManualAplKnown=true;
        private StockAplPolicy.SceneFlags mAplFlags;
        private Float mAplAutoLux,mAplManualLux;
        private Boolean mManualSensorActive;
        private int mLastDisplayState=-1,mLastDisplayPolicy=-1;
        private boolean mGalleryAplInstance;
        private boolean mNativeHdrObserved;
        private Boolean mNativeHdr;
        private BooleanSupplier mAplLifetime=() -> true; // Fresh source constructor state only.
        private long mAppliedAplEvent=-1;
        private void restoreApl(AplCheckpoint checkpoint) {
            mApl.restore(checkpoint.state());mAutomaticAplKnown=checkpoint.automaticKnown();
            mManualAplKnown=checkpoint.manualKnown();mAplFlags=checkpoint.flags();
            mAplAutoLux=checkpoint.automaticLux();mAplManualLux=checkpoint.manualLux();
            mGalleryAplInstance=checkpoint.galleryAplInstance();mAplLifetime=checkpoint.lifetime();
        }
        private void applyAplEvent(StockAplPolicy.Event event,BooleanSupplier lifetime) {
            mAplLifetime=lifetime;
            var auto=mApl.onAutomaticEvent(event,mAplFlags,true,mAplAutoLux,false);
            var manual=mApl.onManualEvent(event,mAplFlags,mAplManualLux,false);
            boolean knownChanged=mAutomaticAplKnown!=auto.isPresent()
                    || mManualAplKnown!=manual.isPresent();
            mAutomaticAplKnown=auto.isPresent();mManualAplKnown=manual.isPresent();
            synchronized(mLock) { if(auto.isPresent() || manual.isPresent()) mAplUnobservedEvent=false; }
            if(knownChanged || auto.isPresent() && auto.get().brightnessUpdate()
                    || manual.isPresent() && manual.get().brightnessUpdate())
                mOwner.invalidate();
        }
        private void requestFullScene(boolean enabled) {
            AplControlOwner control;
            synchronized(mLock) { control=mAplControlOwner; }
            if(control==null || !control.canDispatchFullScene())return;
            var action=mApl.executeFullSceneRequest(enabled,true);
            if(action.isPresent()) control.requestFullScene(enabled);
        }
        private StockAplPolicy.SceneFlags currentAplFlags(Context context) {
            if(mStock==null || mStock.hdrSceneState()==null)return null;
            if(mStock.aplSceneFlags()!=null)return mStock.aplSceneFlags();
            if(mStock.hdrSceneState()!=HdrSceneState.BASELINE)return null;
            var metadata=context.hdrMetadata();
            if(metadata==null || !metadata.active() && metadata.hdrLayersPresent()==null)return null;
            // Observed zero layers is distinct from unobserved/positive unclassified content.
            if(Boolean.FALSE.equals(metadata.hdrLayersPresent()))
                return StockAplPolicy.SceneFlags.of(StockAplPolicy.Scene.NONE);
            if((metadata.layerFlags()&0x1c00)==0)return null;
            var scene=resolveHdrScene(HdrSceneState.BASELINE,context.hdrMetadata().layerFlags());
            return StockAplPolicy.SceneFlags.of(switch(scene) {
                case HLG -> StockAplPolicy.Scene.HLG;
                case LOCAL -> StockAplPolicy.Scene.LOCAL;
                case ULTRA -> StockAplPolicy.Scene.ULTRA;
                default -> StockAplPolicy.Scene.NONE;
            });
        }
        private StockContext mStock;
        private final CompositionSource mCompositionSource;
        Bound(Owner owner) {
            mOwner = owner;
            mCompositionSource=owner.compositionSource();
            // A replaced owner may have had pending source12/17 clears. Until their400ms bound,
            // don't infer their old state. Ordinary framework HDR remains available meanwhile.
            mOwner.postDelayed(() -> {
                requireOwner();
                if (!mClosed) { mFirstHistoryKnown = true; mOwner.invalidate(); }
            },StockFirstScreenDelay.DELAY_MILLIS);
        }
        private void scheduleFirstTimeout(StockFirstScreenDelay.Timeout timeout) {
            if (timeout == null) return;
            mOwner.postDelayed(() -> {
                requireOwner();
                if (!mClosed && mFirst.onTimeout(timeout)) mOwner.invalidate();
            },StockFirstScreenDelay.DELAY_MILLIS);
        }
        @Override public Boolean firstScreen() {
            requireOwner();
            return mClosed || !mFirstHistoryKnown ? null : mFirst.isFirst();
        }
        @Override public Boolean onAnimation(float cs,float cm,float target,float rate) {
            requireOwner();
            if (mClosed) return null;
            var update = mFirst.update(cs,cm,target,rate);
            scheduleFirstTimeout(update.timeout());
            return firstScreen();
        }
        private void requireOwner() {
            if (!mOwner.isCurrentThread()) throw new IllegalStateException("Wrong display owner");
        }
        void enqueue(Publication publication) {
            mOwner.post(() -> {
                requireOwner();
                if (mClosed || publication.revision() <= mAppliedPublication) return;
                Command command = publication.command();
                if (command != null && command.revision() > mAppliedCommand) {
                    mController.onCaps(command.caps());
                    mAppliedCommand = command.revision();
                }
                var nextStock=publication.context();
                if(mStock==null || mStock.hdrSceneState()!=nextStock.hdrSceneState()
                        || !Objects.equals(mStock.aplSceneFlags(),nextStock.aplSceneFlags()))
                    mAplFlags=null;
                mStock = nextStock;
                mAppliedContext = publication.contextRevision();
                mAppliedPublication = publication.revision();
                Integer outdoor = publication.outdoorCommand();
                if (outdoor != null && publication.outdoorRevision() > mAppliedOutdoor) {
                    mAppliedOutdoor = publication.outdoorRevision();
                    mController.onOutdoorDiscountCommand(outdoor);
                    long epoch = ++mOutdoorTimerEpoch;
                    if (outdoor == 1) mOwner.postDelayed(() -> {
                        requireOwner();
                        if (mClosed || epoch != mOutdoorTimerEpoch) return;
                        mController.onOutdoorDiscountTimer();
                        mOwner.invalidate();
                    },mPanel.outdoorDiscountDelayMillis());
                }
                mOwner.invalidate();
            });
        }
        @Override public void onEvent(Event event) {
            requireOwner();
            if (mClosed || event == null || event.kind() == null) return;
            switch (event.kind()) {
                case PACKAGE_CHANGED -> mController.onPackageChanged(event.elapsedMs());
                case INTERVENED_AUTO -> mController.onIntervened();
                case ADJUSTMENT_COMMITTED -> mController.onAdjustmentCommitted(event.elapsedMs());
                case AUTO_BRIGHTNESS_SETUP -> {
                    // DPC supplies actual readiness+runtimeHDRscene, not just XMLgallery enable.
                    if (Boolean.TRUE.equals(event.hdrSceneActive())) {
                        scheduleFirstTimeout(mFirst.switchScreenHdrDelay());
                    }
                }
                case POWER -> {
                    if(event.displayState()!=mLastDisplayState || event.displayPolicy()!=mLastDisplayPolicy) {
                        mLastDisplayState=event.displayState();mLastDisplayPolicy=event.displayPolicy();
                        AplControlOwner control;
                        synchronized(mLock) { control=mAplControlOwner; }
                        if(control!=null)control.onDisplayPower(mCompositionSource,event.displayState(),event.displayPolicy());
                    }
                    // Stock29bde6 reads immutable XMLData, not a live fingerprint event.
                    mController.onPower(event.displayState(),event.displayPolicy(),
                            mPanel.hdrModes().fingerprintOffUnlockEnable(),event.elapsedMs());
                }
            }
            // POWER is supplied by the DPC while already computing this same frame.
            // Invalidating here would queue updatePowerState -> POWER -> invalidate forever.
            if (event.kind() != EventKind.POWER) mOwner.invalidate();
        }
        @Override public Frame beginFrame(Context context) {
            requireOwner();
            if(!mClosed && context!=null) {
                if(context.manualSensorActive()!=null
                        && !Objects.equals(mManualSensorActive,context.manualSensorActive())) {
                    mManualSensorActive=context.manualSensorActive();
                    AplControlOwner control;
                    synchronized(mLock) { control=mAplControlOwner; }
                    if(control!=null)control.setManualSensorActive(mManualSensorActive);
                }
                Boolean hdr=context.hdrMetadata()==null?null:context.hdrMetadata().nativeHdrPresent();
                if(!mNativeHdrObserved || !Objects.equals(mNativeHdr,hdr)) {
                    mNativeHdrObserved=true;mNativeHdr=hdr;
                    AplControlOwner control;
                    synchronized(mLock) { control=mAplControlOwner; }
                    if(control!=null)control.observeNativeHdr(hdr);
                }
                mAplFlags=currentAplFlags(context);
                if(context.historyLux()!=null && Float.isFinite(context.historyLux())
                        && context.historyLux()>=0f) mAplAutoLux=context.historyLux();
            }
            if (mClosed || mStock == null || mAppliedCommand < 0 || context == null
                    || !context.ordinaryEligible() || context.mode() == null
                    || context.hdrActive() == null || context.originalBrightness() == null
                    || !valid(context.originalBrightness())) return null;
            StockContext stock = mStock;
            if (context.hdrActive() && !qualifiedHdr(context,stock)) return null;
            boolean automatic = context.mode() == Mode.AUTOMATIC;
            var cap = mController.sdr(automatic ? StockLcdCapController.Mode.AUTOMATIC
                    : StockLcdCapController.Mode.MANUAL,context.ambientLux(),
                    stock.earlyBatteryLimitInactive());
            if (cap.isEmpty()) return null;
            var sdr = cap.get();
            float outputMaximum = sdr.maximum();
            var discount = mController.outdoorDiscountMaximum();
            if (automatic && discount.isPresent()) outputMaximum =
                    Math.min(outputMaximum,discount.get());
            final float capturedMaximum = outputMaximum;
            final var aplFlags=mAplFlags;
            final var aplState=mApl.snapshot();
            final boolean aplKnown=automatic?mAutomaticAplKnown:mManualAplKnown;
            final Float historyLux=automatic?mAplAutoLux:mAplManualLux;
            final long aplEventRevision=mAppliedAplEvent;
            final BooleanSupplier aplLifetime=mAplLifetime;
            final boolean capturedGalleryAplInstance=mGalleryAplInstance;
            Boolean desiredFullScene=null;
            if(automatic && stock.hdrSceneState()==HdrSceneState.BASELINE && aplFlags!=null) {
                var desired=mApl.desiredFullScene(new StockAplPolicy.FullSceneInputs(
                        context.ambientLux(),stock.hdr()==null?null:stock.hdr().thermalRestriction(),
                        mController.storedOutdoorApl(),false,false,aplFlags));
                desiredFullScene=desired.orElse(null);
                if(desired.isPresent()) requestFullScene(desired.get());
            }
            final Boolean capturedDesiredFullScene=desiredFullScene;
            final Float galleryThreshold = mController.galleryAplThreshold(context.ambientLux())
                    .orElse(null);
            long publicationRevision = mAppliedPublication;
            long commandRevision = mAppliedCommand, contextRevision = mAppliedContext;
            return new Frame() {
                private boolean manualCommitted,galleryCommitted;
                private boolean active() { requireOwner(); return !mClosed; }
                public long publicationRevision() { return publicationRevision; }
                public long commandRevision() { return commandRevision; }
                public long contextRevision() { return contextRevision; }
                public float outputMaximum(float original) {
                    return active() ? Math.min(original,capturedMaximum) : original;
                }
                public float beforeHdr(float original) {
                    if (!active() || !automatic) return original;
                    return Math.min(original,capturedMaximum);
                }
                public Output transformSdr(float ordinarySdr,float ordinaryMain,SdrRequest request) {
                    if(!active() || !automatic || !aplKnown || aplFlags==null || aplFlags.any() || request==null
                            || mAppliedAplEvent!=aplEventRevision || !aplLifetime.getAsBoolean())
                        return null;
                    var facts=new StockAplPolicy.BrightnessInputs(aplFlags,sdr.maximum(),historyLux,
                            false,context.noOffsetBrightness(),context.ambientLux());
                    var value=mApl.automaticBrightness(ordinaryMain,facts,aplState);
                    if(value.isEmpty())return null;
                    float main=value.get();
                    boolean both=Boolean.TRUE.equals(capturedDesiredFullScene)
                            && ordinarySdr==ordinaryMain;
                    return new Output(both?main:Math.min(ordinarySdr,capturedMaximum),main);
                }
                public Output transformHdr(float ordinarySdr, float ordinaryMain,
                        HdrRequest request) {
                    if (!active() || !aplLifetime.getAsBoolean()
                            || mAppliedAplEvent!=aplEventRevision || request == null || request.metadata() == null
                            || !Objects.equals(request.metadata(), context.hdrMetadata())
                            || !request.metadata().active() || request.mapper() == null
                            || stock.hdrSceneState() == null
                            || stock.hdrSceneState() == HdrSceneState.NOT_READY) return null;
                    StockHdrCurve.Scene scene = resolveHdrScene(
                            stock, request.metadata().layerFlags());
                    if (scene == StockHdrCurve.Scene.NONE
                            || scene == StockHdrCurve.Scene.GALLERY && galleryThreshold == null) {
                        return null;
                    }
                    var maps = new StockHdrCurve.BacklightMapper() {
                        public float nitsFromBacklight(float b) {
                            return request.mapper().nitsFromBacklight(b);
                        }
                        public float backlightFromNits(float n) {
                            return request.mapper().backlightFromNits(n);
                        }
                    };
                    float requested = request.metadata().desiredRatio();
                    // Stock listener229286..2292da normalizes private-scene headroom to1..16.
                    float privateDesiredRatio = Float.isNaN(requested) ? 1f
                            : Math.max(1f, Math.min(16f, requested));
                    boolean local = aplFlags!=null && (aplFlags.local() || aplFlags.hlg());
                    var source = stock.hdr();
                    // Group and lift come from actual resolved scene and same-frame getter.
                    boolean galleryInstance = !request.firstScreen() && scene==StockHdrCurve.Scene.GALLERY
                            && galleryThreshold!=null
                            ? mPanel.hdrModes().ehdrAplEnabled() && ordinaryMain>=galleryThreshold
                            : capturedGalleryAplInstance;
                    if(automatic && scene==StockHdrCurve.Scene.GALLERY && !request.firstScreen()
                            && !galleryCommitted && mGalleryAplInstance==capturedGalleryAplInstance) {
                        mGalleryAplInstance=galleryInstance;galleryCommitted=true;
                    }
                    boolean lift = aplFlags!=null && aplFlags.gallery()
                            && mPanel.hdrModes().ehdrAplEnabled() && galleryInstance;
                    var hdrContext = new StockLcdCapController.HdrContext(
                            local ? StockLcdCapController.Scene.LOCAL_OR_HLG
                                    : StockLcdCapController.Scene.GALLERY_GROUP,
                            source.thermalRestriction(), source.batteryRestriction(),
                            mPanel.hdrNoLimit(), true,
                            mPanel.apl().enabled(), aplKnown?aplState.automaticRatio():null, lift,
                            source.frontCameraLimitInactive());
                    java.util.Optional<Float> recovered;
                    if (automatic) {
                        var computed = mHdrCurve.automatic(scene, request.firstScreen(),
                                ordinaryMain, privateDesiredRatio, sdr.maximum(),
                                galleryThreshold, maps);
                        if(!aplKnown || aplFlags==null)return null;
                        var facts=new StockAplPolicy.BrightnessInputs(aplFlags,sdr.maximum(),historyLux,
                                galleryInstance,context.noOffsetBrightness(),context.ambientLux());
                        var apl=mApl.automaticBrightness(computed.beforeRecovery(),facts,aplState);
                        if(apl.isEmpty())return null;
                        recovered = mArithmetic.automaticHdr(sdr,hdrContext,
                                apl.get(),computed.storedNoLimit(),1f);
                    } else {
                        float computed = mHdrCurve.compute(scene,ordinaryMain,
                                privateDesiredRatio,maps);
                        recovered = mArithmetic.manualHdr(sdr,hdrContext,computed);
                        if(!aplKnown || aplFlags==null)return null;
                        if(recovered.isPresent()) {
                            var apl=mApl.manualBrightness(recovered.get(),aplFlags,historyLux,aplState);
                            if(apl.isEmpty())return null;
                            recovered=java.util.Optional.of(apl.get().output());
                            if(!manualCommitted && apl.get().nextTarget()!=null
                                    && mApl.manualTarget()==aplState.manualTarget()) {
                                mApl.commitManualTarget(apl.get().nextTarget());manualCommitted=true;
                            }
                        }
                    }
                    return recovered.isEmpty() ? null
                            : new Output(Math.min(ordinarySdr,capturedMaximum), recovered.get());
                }
                public Output finish(float ordinarySdr, float ordinaryMain, HdrOutputs hdr) {
                    if (!active()) return new Output(ordinarySdr,ordinaryMain);
                    float limitedSdr = Math.min(ordinarySdr,capturedMaximum);
                    if (!context.hdrActive()) {
                        return new Output(limitedSdr,Math.min(ordinaryMain,capturedMaximum));
                    }
                    java.util.Optional<Float> main;
                    if (automatic) {
                        if (hdr == null || hdr.currentBeforeRecovery() == null
                                || hdr.stockNoLimitCandidate() == null) return null;
                        if (stock.hdr().scene() == StockLcdCapController.Scene.SDR_PLUS) {
                            main = mArithmetic.automaticSdrPlus(sdr,stock.hdr(),
                                    context.originalBrightness(),hdr.currentBeforeRecovery(),
                                    hdr.stockNoLimitCandidate(),ordinaryMain);
                        } else {
                            main = mArithmetic.automaticHdr(sdr,stock.hdr(),
                                    hdr.currentBeforeRecovery(),hdr.stockNoLimitCandidate(),ordinaryMain);
                        }
                    } else if (stock.hdr().scene() == StockLcdCapController.Scene.SDR_PLUS) {
                        main = mArithmetic.manualSdrPlus(sdr,stock.hdr(),
                                context.originalBrightness(),ordinaryMain,stock.manualOutdoor(),
                                stock.manualOutdoorMaximumRaw());
                    } else {
                        main = mArithmetic.manualHdr(sdr,stock.hdr(),ordinaryMain);
                    }
                    return main.isPresent() ? new Output(limitedSdr,main.get()) : null;
                }
            };
        }
        private boolean qualifiedHdr(Context context, StockContext stock) {
            var hdr = stock.hdr();
            if (hdr == null || stock.hdrSceneState() == null
                    || stock.hdrSceneState() == HdrSceneState.NOT_READY
                    || hdr.thermalRestriction() == null || hdr.batteryRestriction() == null
                    || hdr.hdrNoLimit() == null || hdr.hdrNoLimit() != mPanel.hdrNoLimit()
                    || !Boolean.TRUE.equals(context.hdrMetadataAvailable())
                    || context.hdrMetadata() == null || !context.hdrMetadata().active()) return false;
            StockHdrCurve.Scene scene = resolveHdrScene(
                    stock,context.hdrMetadata().layerFlags());
            if (scene == StockHdrCurve.Scene.NONE) return false;
            if (context.mode() == Mode.MANUAL) {
                return Boolean.TRUE.equals(hdr.frontCameraLimitInactive());
            }
            if (hdr.hdrNoLimit() && !hdr.thermalRestriction()
                    && (scene == StockHdrCurve.Scene.LOCAL || scene == StockHdrCurve.Scene.HLG)) {
                return mAutomaticAplKnown && mAplLifetime.getAsBoolean();
            }
            return true; // Runtime scene OR and gallery APL lift are derived from this exact frame.
        }
        @Override public void close() {
            requireOwner();
            if (mClosed) return;
            mClosed = true;
            AplControlOwner control;
            synchronized(mLock) { control=mAplControlOwner; }
            if(control!=null && mCompositionSource!=null)
                control.onCompositionSource(mCompositionSource,false);
            if(control!=null && Boolean.TRUE.equals(mManualSensorActive))
                control.setManualSensorActive(false);
            mFirst.close();
            synchronized (mLock) {
                mAplCheckpoint=new AplCheckpoint(mApl.checkpoint(),mAutomaticAplKnown,
                        mManualAplKnown,mAplFlags,mAplAutoLux,mAplManualLux,mGalleryAplInstance,mAplLifetime);
                mBindings.remove(this);
            }
        }
    }
    private static boolean valid(float value) {
        return Float.isFinite(value) && value >= 0f && value <= 1f;
    }
}
