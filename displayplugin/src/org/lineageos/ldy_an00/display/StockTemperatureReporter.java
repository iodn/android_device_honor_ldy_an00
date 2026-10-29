// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;

import java.util.ArrayList;
import java.util.List;

/** Stock ReportTemperature ordered full-cycle math, separate from source acquisition. */
final class StockTemperatureReporter {
    record Config(int id, int minimumCelsius, int maximumCelsius, int step) {}
    record Reading(int rawMilliCelsius, long observedBoottimeSeconds) {}
    record Report(int id, int wholeCelsius, int remainder, long observedBoottimeSeconds) {
        int milliCelsius() { return wholeCelsius * 1000 + remainder; }
    }
    private static final class State {
        int whole;
        int remainder;
        long seconds;
    }
    private final List<Config> mConfigs;
    private final State[] mStates;
    private boolean mFirstCycle = true;
    // Native this+0x50 belongs to the whole reporter, not one sensor.
    private boolean mRecovery;
    private boolean mPinnedLcdInputs;

    static StockTemperatureReporter forPinnedLdyDisplayInputs() {
        var result = new StockTemperatureReporter(List.of(
                new Config(9, -20, 75, 5), new Config(28, 0, 80, 5),
                new Config(29, 0, 80, 5),
                new Config(55, -30, 80, 5)));
        result.mPinnedLcdInputs = true;
        return result;
    }

    void includeOtherFirstCycleInvalidPossibility() { mRecovery = true; }


    StockTemperatureReporter(List<Config> orderedConfigs) {
        mConfigs = List.copyOf(orderedConfigs);
        mStates = new State[mConfigs.size()];
        for (int i = 0; i < mStates.length; i++) mStates[i] = new State();
    }

    List<Report> processCycle(List<Reading> orderedReadings) {
        if (orderedReadings.size() != mConfigs.size()) {
            throw new IllegalArgumentException("A complete configured sensor cycle is required");
        }
        // Missing samples cannot be converted to an invented native error/default value.
        for (Reading reading : orderedReadings) {
            if (reading == null) throw new IllegalArgumentException("Missing physical sample");
        }
        List<Report> result = new ArrayList<>(mStates.length);
        for (int i = 0; i < mStates.length; i++) {
            Config config = mConfigs.get(i);
            Reading reading = orderedReadings.get(i);
            // Pinned LDY debug0 ambient24 is always valid and precedes front28 (then back29).
            if (mPinnedLcdInputs && !mFirstCycle && config.id() == 28) mRecovery = false;
            State prior = mStates[i];
            double divided = (double) reading.rawMilliCelsius() / 1000.0;
            int whole = (int) divided;
            int remainder = (int) (divided * 1000.0) % 1000;
            boolean valid = whole >= config.minimumCelsius()
                    && whole <= config.maximumCelsius();
            if (mFirstCycle) {
                if (!valid) {
                    whole = 25;
                    remainder = 0;
                    mRecovery = true;
                }
            } else if (!valid) {
                whole = prior.whole;
                remainder = prior.remainder;
            } else {
                int delta = Math.abs(whole - prior.whole);
                long elapsed = reading.observedBoottimeSeconds() - prior.seconds;
                // Exact ARM64 zero-delta branch retains absolute delta (not zero).
                int rate = elapsed == 0 ? delta : (int) ((long) delta * 5 / elapsed);
                if (rate > config.step()) {
                    if (mRecovery) mRecovery = false;
                    else whole = prior.whole + (whole > prior.whole
                            ? config.step() : -config.step());
                } else {
                    mRecovery = false;
                }
            }
            // Native repeats validity after default/debounce, restoring old fields.
            if (whole < config.minimumCelsius() || whole > config.maximumCelsius()) {
                whole = prior.whole;
                remainder = prior.remainder;
            }
            prior.whole = whole;
            prior.remainder = remainder;
            prior.seconds = reading.observedBoottimeSeconds();
            result.add(new Report(config.id(), whole, remainder, prior.seconds));
        }
        mFirstCycle = false;
        return List.copyOf(result);
    }
}
