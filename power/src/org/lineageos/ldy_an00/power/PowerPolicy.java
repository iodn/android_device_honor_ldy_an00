// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.power;

import java.util.Arrays;

/** Stock113 PowerGenie policy values, without app classifications or benchmark exceptions. */
final class PowerPolicy {
    static final int[] TAGS = {1, 4, 7, 10};
    // CPU kHz; GPU OPP level. Zero CPU and negative GPU restore the HAL defaults.
    private static final int[] RELEASE = {0, 0, 0, -1};
    private static final int[] NORMAL_ASLEEP = {1800000, 1200000, 600000, -1};
    private static final int[] SAVER_AWAKE = {1300000, 1500000, 1550000, 10};
    private static final int[] SAVER_ASLEEP = {1300000, 1500000, 1550000, 12};

    static int[] select(boolean saver, boolean interactive, boolean protectedWorkload) {
        if (protectedWorkload || (!saver && interactive)) return RELEASE.clone();
        return (saver ? (interactive ? SAVER_AWAKE : SAVER_ASLEEP) : NORMAL_ASLEEP).clone();
    }

    static boolean released(int[] limits) {
        return Arrays.equals(limits, RELEASE);
    }

    static boolean validReadback(int[] observed) {
        if (observed == null || observed.length != TAGS.length) return false;
        int[] maximum = {2700000, 3500000, 4210000};
        for (int i = 0; i < 3; i++) {
            if (observed[i] <= 0 || observed[i] > maximum[i]) return false;
        }
        return observed[3] >= 0 && observed[3] <= 56;
    }

    /** Aggregate readback may be stricter because thermal and other clients still own limits. */
    static boolean matches(int[] requested, int[] observed) {
        if (!validReadback(observed)) return false;
        for (int i = 0; i < 3; i++) {
            if (observed[i] <= 0 || (requested[i] > 0 && observed[i] > requested[i])) return false;
        }
        return observed[3] >= 0 && (requested[3] < 0 || observed[3] >= requested[3]);
    }
}
