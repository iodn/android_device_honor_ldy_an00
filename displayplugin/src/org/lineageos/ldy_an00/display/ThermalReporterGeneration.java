// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;

import java.util.List;

/** Port-owned stock-math epochs over complete, sequenced, source-qualified V4 cycles. */
final class ThermalReporterGeneration {
    enum Status { NOT_READY, UNCHANGED, ADVANCED }
    record Result(long epoch, Status status, List<StockTemperatureReporter.Report> reports) {}

    private long mEpoch;
    private long mEndFence = -1;
    private ThermalDisplayBatch mLast;
    private StockTemperatureReporter mReporter;

    Result observe(ThermalDisplayBatch batch) {
        if (batch != null && batch.equals(mLast)) return empty(Status.UNCHANGED);
        if (batch == null || batch.sequence() < 0 || batch.sequence() > 0xffff_ffffL
                || batch.startedMilliseconds() < 0
                || batch.completedMilliseconds() < batch.startedMilliseconds()) {
            mReporter = null;
            return empty(Status.NOT_READY);
        }
        if (batch.startedMilliseconds() <= mEndFence) {
            mEndFence = Math.max(mEndFence, batch.completedMilliseconds());
            mLast = batch;
            mReporter = null;
            return empty(Status.NOT_READY);
        }
        boolean consecutive = mLast != null && mLast.sequence() != 0xffff_ffffL
                && batch.sequence() == mLast.sequence() + 1;
        boolean restart = mReporter == null || batch.sequence() == 0 || !consecutive;
        mEndFence = batch.completedMilliseconds();
        boolean conflictingDuplicate = mLast != null && batch.sequence() != 0
                && batch.sequence() == mLast.sequence();
        mLast = batch;
        if (conflictingDuplicate || batch.battery() == null
                || batch.back() == null || batch.usb() == null || batch.front() == null
                || (Boolean.FALSE.equals(batch.anyReporterInvalid()) && selectedInvalid(batch))) {
            mReporter = null;
            return empty(Status.NOT_READY);
        }
        if (restart) {
            if (batch.anyReporterInvalid() == null) {
                mReporter = null;
                return empty(Status.NOT_READY);
            }
            mReporter = StockTemperatureReporter.forPinnedLdyDisplayInputs();
            mEpoch++;
        }
        long seconds = batch.completedMilliseconds() / 1000;
        var reports = mReporter.processCycle(List.of(
                new StockTemperatureReporter.Reading(batch.battery(), seconds),
                new StockTemperatureReporter.Reading(batch.front(), seconds),
                new StockTemperatureReporter.Reading(batch.back(), seconds),
                new StockTemperatureReporter.Reading(batch.usb(), seconds)));
        if (restart && batch.anyReporterInvalid()) {
            mReporter.includeOtherFirstCycleInvalidPossibility();
        }
        return new Result(mEpoch, Status.ADVANCED, reports);
    }

    private static boolean selectedInvalid(ThermalDisplayBatch batch) {
        int battery = (int) ((double) batch.battery() / 1000);
        int front = (int) ((double) batch.front() / 1000);
        int back = (int) ((double) batch.back() / 1000);
        int usb = (int) ((double) batch.usb() / 1000);
        return battery < -20 || battery > 75 || front < 0 || front > 80 || back < 0 || back > 80
                || usb < -30 || usb > 80;
    }

    private Result empty(Status status) {
        // Caller holds accepted caps but discards level/filter generation on NOT_READY.
        return new Result(mEpoch, status, null);
    }
}
