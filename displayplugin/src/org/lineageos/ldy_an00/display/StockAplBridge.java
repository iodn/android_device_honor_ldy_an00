package org.lineageos.ldy_an00.display;

import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.SystemProperties;

import java.lang.ref.WeakReference;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

import vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineCallback;
import vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineWrapper;

/**
 * Private, unselected candidate. A single deployment owner is a prerequisite:
 * the HAL replaces its previous callback and offers no atomic ownership query.
 * It does not enable the histogram until a caller supplies a qualified stock snapshot.
 */
public final class StockAplBridge implements AutoCloseable {
    static final String SERVICE =
            "vendor.honor.hardware.hwdisplay.displayengine.IDisplayEngineWrapper/default";
    static final String INTERFACE_HASH = "1b12fa2f43d0511e7bd32246d36bddfe533ae419";
    static final String OPT_IN = "ro.display.apl_bridge";
    private final Handler mOwner;
    private final Consumer<StockAplEvent> mSink;
    private final Runnable mInvalidated;
    private final Consumer<StockDisplayAuxNotice> mAuxiliary;
    private Session mSession;
    private long mGeneration;
    private byte[] mLastAcceptedSnapshot;
    public enum CollectorOutcome {
        NEVER_REQUESTED,
        REQUEST_RETURNED_OK,
        STOP_REQUEST_RETURNED_OK,
        SERVICE_ERROR_STATE_UNVERIFIED,
        OWNER_LOST_STATE_UNVERIFIED,
        TRANSPORT_COMPLETION_UNKNOWN
    }
    private CollectorOutcome mCollectorOutcome = CollectorOutcome.NEVER_REQUESTED;

    /** Request-path result only, never a physical collector acknowledgement. */
    /** Captured atomic lifetime only, safe for the downstream display-owner queued guard. */
    public BooleanSupplier lifetimeGuard(long generation) {
        requireOwner();
        Session session=mSession;
        return session==null || session.generation!=generation ? () -> false : session.alive::get;
    }

    public CollectorOutcome collectorOutcome() {
        requireOwner();
        return mCollectorOutcome;
    }

    /** Owner-thread snapshot; request receipts and callback delivery are distinct evidence. */
    String diagnostics() {
        requireOwner();
        Session session = mSession;
        return "requestOutcome=" + mCollectorOutcome
                + " boundGeneration=" + (session == null ? "none" : session.generation)
                + " alive=" + (session != null && session.alive.get())
                + " lastRequestSnapshotBytes=" + (mLastAcceptedSnapshot == null ? 0 : mLastAcceptedSnapshot.length)
                + " event8Delivered=" + (session == null ? 0 : session.event8Delivered)
                + " lastEvent8ElapsedNs=" + (session == null ? 0 : session.lastEvent8ElapsedNs);
    }

    public StockAplBridge(Handler owner, Consumer<StockAplEvent> sink, Runnable invalidated) {
        this(owner, sink, ignored -> {}, invalidated);
    }

    /** Production owner uses this overload so native optional context cannot be silently ignored. */
    public StockAplBridge(Handler owner, Consumer<StockAplEvent> sink,
            Consumer<StockDisplayAuxNotice> auxiliary, Runnable invalidated) {
        mOwner = owner;
        mSink = sink;
        mAuxiliary = auxiliary;
        mInvalidated = invalidated;
    }

    /** Caller establishes exclusive deployment ownership; this API cannot discover it atomically. */
    public boolean bind(boolean exclusiveDeploymentQualified) {
        requireOwner();
        boolean optedIn = SystemProperties.getBoolean(OPT_IN, false);
        IBinder binder = optedIn && exclusiveDeploymentQualified
                ? ServiceManager.checkService(SERVICE) : null;
        return bindEndpoint(binder, optedIn, exclusiveDeploymentQualified);
    }

    // Tests exercise the same admission, metadata, and lifetime path without a live HAL.
    boolean bindEndpoint(IBinder binder, boolean optedIn, boolean exclusiveDeploymentQualified) {
        requireOwner();
        if (mCollectorOutcome != CollectorOutcome.NEVER_REQUESTED
                && mCollectorOutcome != CollectorOutcome.STOP_REQUEST_RETURNED_OK) {
            return false; // No replacement registration after active or uncertain hardware state.
        }
        close();
        if (!optedIn || !exclusiveDeploymentQualified
                || binder == null || !binder.isBinderAlive()) {
            return false;
        }
        IDisplayEngineWrapper service = IDisplayEngineWrapper.Stub.asInterface(binder);
        Session session = new Session(this, binder, service, ++mGeneration);
        mSession = session;
        try {
            binder.linkToDeath(session, 0);
            if (service.getInterfaceVersion() != 1
                    || !INTERFACE_HASH.equals(service.getInterfaceHash())
                    || !session.alive.get()) {
                close();
                return false;
            }
            mAuxiliary.accept(new StockDisplayAuxNotice.SourceReset(session.generation,
                    SystemClock.elapsedRealtimeNanos()));
            service.registerCallback(session.callback);
            if (!session.alive.get()) {
                close();
                return false;
            }
            return true;
        } catch (RemoteException | SecurityException e) {
            close();
            return false;
        }
    }

    /**
     * Applies only a complete, externally qualified stock HistogramParam.
     * The native HAL overwrites algorithm support fields; this is not an APL-only switch.
     */
    public boolean applyHistogram(int mode, byte[] snapshot) {
        requireOwner();
        Session session = mSession;
        if (session == null || !session.alive.get()
                || (mode != 0x30 && mode != 0x31 && mode != 0x7b)
                || snapshot == null || snapshot.length != 0xb0
                || littleInt(snapshot, 0) != mode) {
            return false;
        }
        try {
            int result = session.service.setHardwareEffect(29, mode, snapshot.clone());
            if (!session.alive.get()) {
                mCollectorOutcome = CollectorOutcome.TRANSPORT_COMPLETION_UNKNOWN;
                invalidate(session);
                return false;
            }
            if (result != 0) {
                mCollectorOutcome = CollectorOutcome.SERVICE_ERROR_STATE_UNVERIFIED;
                invalidate(session);
                return false;
            }
            mLastAcceptedSnapshot = snapshot.clone();
            mCollectorOutcome = mode == 0x31 ? CollectorOutcome.STOP_REQUEST_RETURNED_OK
                    : CollectorOutcome.REQUEST_RETURNED_OK;
            return true;
        } catch (RemoteException | SecurityException e) {
            // No compensating setter or automatic retry on unknown transport completion.
            mCollectorOutcome = CollectorOutcome.TRANSPORT_COMPLETION_UNKNOWN;
            invalidate(session);
            return false;
        }
    }

    /**
     * Requests the selected vendor stop path using the last complete accepted snapshot.
     * This is a request receipt, not a hardware drain or durable-state acknowledgement.
     * Unknown completion never authorizes a second setter.
     */
    public boolean requestStop() {
        requireOwner();
        if (mCollectorOutcome == CollectorOutcome.NEVER_REQUESTED
                || mCollectorOutcome == CollectorOutcome.STOP_REQUEST_RETURNED_OK) {
            return true;
        }
        if (mCollectorOutcome != CollectorOutcome.REQUEST_RETURNED_OK
                || mLastAcceptedSnapshot == null || mSession == null
                || !mSession.alive.get()) {
            return false;
        }
        byte[] stop = mLastAcceptedSnapshot.clone();
        stop[0] = 0x31;
        stop[1] = stop[2] = stop[3] = 0;
        return applyHistogram(0x31, stop);
    }

    /**
     * Normal known-state shutdown first requests native mode31, then detaches callbacks.
     * Uncertain/failing setters cause no compensating setter; collectorOutcome remains explicit.
     * Closing does not prove physical collector quiescence.
     */
    @Override
    public void close() {
        requireOwner();
        if (mCollectorOutcome == CollectorOutcome.REQUEST_RETURNED_OK && !requestStop()
                && mCollectorOutcome == CollectorOutcome.REQUEST_RETURNED_OK) {
            mCollectorOutcome = CollectorOutcome.OWNER_LOST_STATE_UNVERIFIED;
        }
        Session session = mSession;
        if (session != null) {
            mSession = null;
            session.alive.set(false);
            try {
                session.binder.unlinkToDeath(session, 0);
            } catch (NoSuchElementException ignored) {
                // A death notification or failed link can have already removed this recipient.
            }
            mInvalidated.run();
        }
        // No unregister exists. The HAL may retain this callback, whose session is now invalid.
    }

    private void invalidate(Session session) {
        requireOwner();
        if (mSession == session) {
            close();
        }
    }

    private void requireOwner() {
        if (Looper.myLooper() != mOwner.getLooper()) {
            throw new IllegalStateException("APL owner thread required");
        }
    }

    private static int littleInt(byte[] data, int offset) {
        return (data[offset] & 255) | ((data[offset + 1] & 255) << 8)
                | ((data[offset + 2] & 255) << 16) | ((data[offset + 3] & 255) << 24);
    }

    private static final class Session implements IBinder.DeathRecipient {
        final WeakReference<StockAplBridge> bridge;
        final IBinder binder;
        final IDisplayEngineWrapper service;
        final long generation;
        final AtomicBoolean alive = new AtomicBoolean(true);
        final Callback callback;
        long event8Delivered;
        long lastEvent8ElapsedNs;

        Session(StockAplBridge owner, IBinder binder, IDisplayEngineWrapper service,
                long generation) {
            bridge = new WeakReference<>(owner);
            this.binder = binder;
            this.service = service;
            this.generation = generation;
            callback = new Callback(this);
        }

        @Override
        public void binderDied() {
            alive.set(false);
            StockAplBridge owner = bridge.get();
            if (owner != null) {
                owner.mOwner.post(() -> owner.invalidate(this));
            }
        }
    }

    private static final class Callback extends IDisplayEngineCallback.Stub {
        private final WeakReference<Session> mSession;

        Callback(Session session) {
            mSession = new WeakReference<>(session);
        }

        @Override public int getInterfaceVersion() { return 1; }
        @Override public String getInterfaceHash() { return INTERFACE_HASH; }

        @Override
        public void onEvent(int event, int extra) {
            // Event8 carries bytes. Other stock callback families are outside this owner scope.
        }

        @Override
        public void onEventWithData(int event, byte[] data) {
            if (android.os.Binder.getCallingPid() != android.os.Process.myPid()
                    && android.os.Binder.getCallingUid() != android.os.Process.SYSTEM_UID) {
                throw new SecurityException("Display engine callbacks require the system UID");
            }
            Session session = mSession.get();
            if (session == null || !session.alive.get()) {
                return;
            }
            long elapsed = SystemClock.elapsedRealtimeNanos();
            StockAplEvent decoded = StockAplEvent.decode(event, data, session.generation, elapsed);
            StockDisplayAuxNotice auxiliary = StockDisplayAuxNotice.decode(
                    event, data, session.generation, elapsed);
            StockAplBridge owner = session.bridge.get();
            if ((decoded == null && auxiliary == null) || owner == null) {
                return;
            }
            owner.mOwner.post(() -> {
                if (session.alive.get() && owner.mSession == session) {
                    if (decoded != null) {
                        session.event8Delivered++;
                        session.lastEvent8ElapsedNs = elapsed;
                        owner.mSink.accept(decoded);
                    }
                    if (auxiliary != null) owner.mAuxiliary.accept(auxiliary);
                }
            });
        }
    }
}
