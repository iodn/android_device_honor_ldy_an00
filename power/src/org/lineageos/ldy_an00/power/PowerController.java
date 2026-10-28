// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.power;

import java.util.Arrays;
import java.util.function.BiConsumer;

/** Serialized request/readback lifecycle, independent of Android event delivery. */
final class PowerController {
    interface Backend {
        int[] readLimits() throws Exception;
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

    PowerController(Backend backend, Scheduler scheduler, BiConsumer<String, Exception> log) {
        mBackend = backend;
        mScheduler = scheduler;
        mLog = log;
    }

    void invalidate() {
        mGeneration++;
        mRequested = null;
        mBlocked = null;
        mRetries = 0;
    }

    void update(boolean saver, boolean interactive, boolean protectedWorkload) {
        int[] desired = PowerPolicy.select(saver, interactive, protectedWorkload);
        apply(desired);
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
            mLog.accept("Frequency policy unavailable", e);
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
                mLog.accept((PowerPolicy.released(desired) ? "Release requested; observed "
                        : "Aggregate limits observed ") + Arrays.toString(observed), null);
                return;
            }
            // A synchronous getter can overtake the one-way setter.
            if (attempt < 2) {
                mScheduler.post(() -> verify(generation, desired, attempt + 1), 250);
                return;
            }
            mLog.accept("Requested limits not observed", null);
        } catch (Exception e) {
            mLog.accept("Cannot verify frequency policy", e);
        }
        if (!PowerPolicy.released(desired)) releaseAfterFailure(desired);
    }

    private void releaseAfterFailure(int[] desired) {
        mBlocked = desired;
        mRequested = null;
        mGeneration++;
        try {
            int[] release = PowerPolicy.select(false, true, true);
            mBackend.request(release.clone());
            mRequested = release;
            mLog.accept("Frequency release requested after failure", null);
        } catch (Exception e) {
            mLog.accept("Frequency release delivery failed; outcome unverified", e);
        }
    }
}
