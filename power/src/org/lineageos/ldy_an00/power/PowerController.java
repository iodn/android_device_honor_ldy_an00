// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.power;

import java.util.Arrays;
import java.util.function.BiConsumer;

/** Serialized request/readback lifecycle, independent of Android event delivery. */
final class PowerController {
    interface Backend {
        int[] readLimits() throws Exception;
        int gpuLevelCount() throws Exception;
        void notifyInteractive(boolean interactive) throws Exception;
        void request(int[] limits) throws Exception;
    }
    interface Scheduler {
        void post(Runnable task, long delayMillis);
    }

    private final Backend mBackend;
    private final Scheduler mScheduler;
    private final BiConsumer<String, Exception> mLog;
    private int[] mRequested;
    private int[] mBlocked;
    private int mGeneration;
    private int mRetries;
    private Boolean mInteractive;
    private int mScreenGeneration;

    PowerController(Backend backend, Scheduler scheduler, BiConsumer<String, Exception> log) {
        mBackend = backend;
        mScheduler = scheduler;
        mLog = log;
    }

    void invalidate() {
        mScreenGeneration++;
        mInteractive = null;
        mGeneration++;
        mRequested = null;
        mBlocked = null;
        mRetries = 0;
    }

    /** Release our votes without inventing an interactive state before observers are ready. */
    void release() {
        apply(PowerPolicy.select(false, true, true));
    }

    void update(boolean saver, boolean interactive, boolean protectedWorkload) {
        if (mInteractive == null || mInteractive != interactive) {
            mInteractive = interactive;
            reportInteractive(++mScreenGeneration, interactive, 0);
        }
        int gpuLevelCount = -1;
        if (saver && !protectedWorkload) {
            try {
                gpuLevelCount = mBackend.gpuLevelCount();
            } catch (Exception e) {
                mLog.accept("GPU level count unavailable; releasing GPU vote", e);
            }
        }
        int[] desired = PowerPolicy.select(saver, interactive, protectedWorkload, gpuLevelCount);
        apply(desired);
    }

    private void reportInteractive(int generation, boolean interactive, int attempt) {
        if (generation != mScreenGeneration) return;
        try {
            mBackend.notifyInteractive(interactive);
        } catch (Exception e) {
            mLog.accept("UniPerf screen-state delivery failed", e);
            if (attempt < 3) {
                mScheduler.post(() -> reportInteractive(generation, interactive, attempt + 1), 5000);
            } else {
                mInteractive = null; // A later state observation can retry after recovery.
            }
        }
    }

    private void apply(int[] desired) {
        if (Arrays.equals(desired, mRequested) || Arrays.equals(desired, mBlocked)) return;
        int generation = ++mGeneration;
        boolean requestAttempted = false;
        try {
            // Never let a failed getter prevent an attempt to release our own limits.
            if (!PowerPolicy.released(desired)) mBackend.readLimits();
            requestAttempted = true;
            mBackend.request(desired.clone());
            mRequested = desired;
            mBlocked = null;
            mRetries = 0;
            mScheduler.post(() -> verify(generation, desired, 0), 250);
        } catch (Exception e) {
            mLog.accept("CPU ceiling policy unavailable", e);
            if (requestAttempted) {
                // A transport failure is not proof that a one-way request was not delivered.
                releaseAfterFailure(desired);
            } else if (++mRetries <= 3) {
                mScheduler.post(() -> {
                    if (generation == mGeneration) apply(desired);
                }, 5000);
            }
        }
    }

    private void verify(int generation, int[] desired, int attempt) {
        if (generation != mGeneration) return;
        try {
            int[] observed = mBackend.readLimits();
            if (PowerPolicy.matches(desired, observed)) {
                mLog.accept((PowerPolicy.released(desired) ? "Release requested; observed CPU ceilings "
                        : "Aggregate CPU ceilings observed ") + Arrays.toString(observed), null);
                return;
            }
            // A synchronous getter can overtake the one-way setter.
            if (attempt < 2) {
                mScheduler.post(() -> verify(generation, desired, attempt + 1), 250);
                return;
            }
            mLog.accept("Requested CPU ceilings not observed", null);
        } catch (Exception e) {
            mLog.accept("Cannot verify CPU ceilings", e);
        }
        if (!PowerPolicy.released(desired)) releaseAfterFailure(desired);
    }

    private void releaseAfterFailure(int[] desired) {
        // A failed release must not suppress later attempts to release this client's limits.
        mBlocked = PowerPolicy.released(desired) ? null : desired;
        mRequested = null;
        requestRelease(++mGeneration, 0);
    }

    private void requestRelease(int generation, int attempt) {
        if (generation != mGeneration) return;
        try {
            int[] release = PowerPolicy.select(false, true, true);
            mBackend.request(release.clone());
            mRequested = release;
            mLog.accept("Frequency release requested after failure", null);
        } catch (Exception e) {
            mLog.accept("Frequency release delivery failed; outcome unverified", e);
            if (attempt < 3) {
                mScheduler.post(() -> requestRelease(generation, attempt + 1), 5000);
            }
        }
    }
}
