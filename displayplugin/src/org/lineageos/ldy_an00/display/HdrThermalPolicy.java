// Copyright (C) 2026 The LineageOS Project
// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;

/** Recovered shell_back hysteresis plus the port's missing-data guard. */
final class HdrThermalPolicy {
    private boolean mThermallyRestricted;

    boolean restricted(ShellTemperatureSnapshot sample) {
        // Missing data must not release a previously established thermal limit.
        if (sample == null) return true;
        if (sample.back >= 40000) mThermallyRestricted = true;
        else if (sample.back <= 38000) mThermallyRestricted = false;
        return mThermallyRestricted;
    }
}
