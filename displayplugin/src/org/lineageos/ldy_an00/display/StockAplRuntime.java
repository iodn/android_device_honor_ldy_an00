// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;
import android.os.Handler;
import android.os.Looper;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** Existing worker owns transport. Source-derived fullscene can qualify the exact native OR branch. */
final class StockAplRuntime {
    private final Handler mOwner;
    private final StockAplBridge mBridge;
    private final ThermalPolicyPublisher mPublisher;
    private final AtomicBoolean mReady=new AtomicBoolean();
    private Boolean mOwnedFullscreen,mVendorFullscreen,mScreenOn,mHdr;
    private final Runnable mCompositionReset;
    private final StockBacklightThreshold mThreshold=new StockBacklightThreshold();
    private final StockBacklightCommandReader mBacklight=new StockBacklightCommandReader();
    private boolean mOnPacketNeeded=true;
    private boolean mFullScene;
    private boolean mOwnedCollectorActive; // Accepted healthy-owner request, not physical drain proof.
    // Fresh replacement SYSTEM Histogram owner: ctor 17ac70 clears offsets 41/42/49.
    // These are our logical state, not a statement or reset of a running HAL cache.
    // SourceReset preserves these; future private scene senders must implement their own FSM.
    private final boolean mThirdPartyHdr=false,mEhdrApl=false,mSdrApl=false;
    private byte[] mLast;
    private long mPublisherCalls;
    private long mLastPublisherGeneration = -1;
    StockAplRuntime(Handler owner,ThermalPolicyPublisher publisher) {
        this(owner,publisher,()->{});
    }
    StockAplRuntime(Handler owner,ThermalPolicyPublisher publisher,Runnable compositionReset) {
        mOwner=owner;mPublisher=publisher;mCompositionReset=compositionReset;
        mBridge=new StockAplBridge(owner,
                this::onApl,this::auxiliary,this::invalidated);
    }
    private void onApl(StockAplEvent event) {
        requireOwner();
        var lifetime=mBridge.lifetimeGuard(event.generation());
        if(!mReady.get() || !mOwnedCollectorActive || !lifetime.getAsBoolean())return;
        refreshBacklight();
        if(mReady.get() && mOwnedCollectorActive && lifetime.getAsBoolean()) {
            mPublisher.packedApl(event.generation(),event.packedExtra(),lifetime);
            mPublisherCalls++;
            mLastPublisherGeneration = event.generation();
        }
    }
    /** Counts publisher calls only; downstream display acceptance is a separate boundary. */
    String diagnostics() {
        requireOwner();
        return "observedAtElapsedMs=" + android.os.SystemClock.elapsedRealtime()
                + " ready=" + mReady.get() + " collectorRequestOwned=" + mOwnedCollectorActive
                + " screenOn=" + mScreenOn + " hdr=" + mHdr
                + " ownedFullscreen=" + mOwnedFullscreen + " backlightAbove=" + mThreshold.above()
                + " publisherCalls=" + mPublisherCalls
                + " lastPublisherGeneration=" + mLastPublisherGeneration
                + " " + mBridge.diagnostics();
    }
    boolean ready() { return mReady.get(); }
    boolean bind(boolean exclusiveQualified) {
        requireOwner();
        boolean bound=mBridge.bind(exclusiveQualified);mReady.set(bound);
        return bound;
    }
    void refreshBacklight() {
        requireOwner();
        // Exact stock setBacklight ignores raw0; no invented false threshold event.
        if(mThreshold.observe(mBacklight.readRawCommand()))
            update(StockAplHistogramSnapshotBuilder.ScreenEvent.NONE);
    }
    private void requireOwner() {
        if(Looper.myLooper()!=mOwner.getLooper())throw new IllegalStateException("Wrong APL worker");
    }
    void hdr(Boolean value) {
        requireOwner();
        if(java.util.Objects.equals(mHdr,value))return;
        mHdr=value;refreshBacklight();update(StockAplHistogramSnapshotBuilder.ScreenEvent.NONE);
    }
    void ownedFullscreen(Boolean value) {
        requireOwner();
        if(java.util.Objects.equals(mOwnedFullscreen,value))return;
        mOwnedFullscreen=value;refreshBacklight();
        update(StockAplHistogramSnapshotBuilder.ScreenEvent.NONE);
    }
    void fullScene(boolean enabled) {
        requireOwner();
        mFullScene=enabled;refreshBacklight();update(StockAplHistogramSnapshotBuilder.ScreenEvent.NONE);
    }
    void displayPower(int state) {
        requireOwner();
        boolean on=state==2;
        if(mScreenOn!=null && mScreenOn==on)return;
        mScreenOn=on;
        if(!on) { mOnPacketNeeded=true;stop(); }
        else { mOnPacketNeeded=true;refreshBacklight();update(StockAplHistogramSnapshotBuilder.ScreenEvent.ON); }
    }
    /** Explicit display-owner retirement, distinct from unknown input or physical screen OFF. */
    void retireOwner() {
        requireOwner();
        mScreenOn=null;
        mOwnedFullscreen=null;
        mOnPacketNeeded=true;
        stop(); // Only our accepted healthy epoch may issue the existing explicit stop.
    }
    private void auxiliary(StockDisplayAuxNotice notice) {
        requireOwner();
        if(notice instanceof StockDisplayAuxNotice.SourceReset) {
            mOwnedFullscreen=null;mVendorFullscreen=null;mLast=null;mOnPacketNeeded=true;
            mCompositionReset.run();
            // Preserve our independently owned Histogram flags; reset is not a HAL state snapshot.
        } else if(notice instanceof StockDisplayAuxNotice.Fullscreen full) {
            mVendorFullscreen=full.active(); // Diagnostic: not APS SYSTEM68 ed/ee authority.
        } else if(notice instanceof StockDisplayAuxNotice.Unknown unknown
                && unknown.domain()==StockDisplayAuxNotice.Domain.FULLSCREEN) {
            mVendorFullscreen=null;
        } else {
            // Exact retained data4 consumer cone is native SDR2HDR/UI-only, with no LCD flag
            // writer. Preserve the LCD authority; do not invent a boolean mode or new UI path.
            // Invalid data4 is likewise a source no-op. This does not heal other UNKNOWN inputs.
        }
    }
    private void update(StockAplHistogramSnapshotBuilder.ScreenEvent screen) {
        if(!mReady.get() || !Boolean.TRUE.equals(mScreenOn))return;
        // HDR/fullscreen/backlight require actual observations. The three mode defaults are
        // source-qualified new SYSTEM-owner state, independent of vendor collector cached state.
        var scene=new StockAplHistogramSnapshotBuilder.Scene(
                mHdr,mThirdPartyHdr,mOwnedFullscreen,mThreshold.above(),mEhdrApl,mSdrApl,mFullScene);
        var result=StockAplHistogramSnapshotBuilder.build(scene,
                mOnPacketNeeded?StockAplHistogramSnapshotBuilder.ScreenEvent.ON:screen);
        if(Boolean.FALSE.equals(result.requested())) {
            // Dedicated APL projection has no other stock operation to keep the collector alive.
            // Stock MODE0 is not a supported stop; use the existing owned explicit stop instead.
            // This is a deliberate port lifecycle adaptation, not a stock NONE->49 assertion.
            mOnPacketNeeded=true;
            stop();
            return;
        }
        var snapshot=result.snapshot();
        if(snapshot==null)return; // UNKNOWN is not a disabled/native zero-parameter packet.
        byte[] data=snapshot.data();
        if(Arrays.equals(mLast,data))return;
        if(mBridge.applyHistogram(snapshot.mode(),data)) {
            mLast=data;mOnPacketNeeded=false;mOwnedCollectorActive=true;
        }
        else invalidated();
    }
    private void stop() {
        if(!mReady.get() || !mOwnedCollectorActive)return;
        if(mBridge.requestStop()) {
            mOwnedCollectorActive=false;mLast=null;mOnPacketNeeded=true;
        }
        else invalidated();
    }
    private void invalidated() {
        requireOwner();mReady.set(false);mOwnedCollectorActive=false;
        mOwnedFullscreen=null;mVendorFullscreen=null;mLast=null;mPublisher.invalidateApl();
        mCompositionReset.run();
        // A transport-unknown outcome never triggers a compensating setter or automatic rebind.
    }
}
