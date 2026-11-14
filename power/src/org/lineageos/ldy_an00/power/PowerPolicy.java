// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.power;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Stock113 PowerGenie policy values, without app classifications or benchmark exceptions. */
final class PowerPolicy {
    static final int[] TAGS = {1, 4, 7, 10};
    // CPU requests use kHz; GPU requests use driver level indices. Observations contain CPUs.
    private static final int[] RELEASE = {0, 0, 0, -1};
    private static final int[] NORMAL_ASLEEP = {1800000, 1200000, 600000, -1};
    private static final int[] SAVER_AWAKE = {1300000, 1500000, 1550000, -1};
    private static final int[] SAVER_ASLEEP = {1300000, 1500000, 1550000, -1};

    static int[] select(boolean saver, boolean interactive, boolean protectedWorkload) {
        if (protectedWorkload || (!saver && interactive)) return RELEASE.clone();
        return (saver ? (interactive ? SAVER_AWAKE : SAVER_ASLEEP) : NORMAL_ASLEEP).clone();
    }

    /** Stock super-mode defaults: GPU maximum 10 awake, 12 asleep. */
    static int[] select(boolean saver, boolean interactive, boolean protectedWorkload,
            int gpuLevelCount) {
        int[] limits = select(saver, interactive, protectedWorkload);
        int gpuMaximum = interactive ? 10 : 12;
        if (saver && !protectedWorkload && gpuLevelCount > gpuMaximum) {
            limits[3] = gpuMaximum;
        }
        return limits;
    }

    static boolean released(int[] limits) {
        return Arrays.equals(limits, RELEASE);
    }

    /** Only the audited CPU ranges and stock GPU policy indices are accepted. */
    static boolean validRequest(int[] requested) {
        if (requested == null || requested.length != TAGS.length) return false;
        int[] maximum = {2700000, 3500000, 4210000};
        for (int i = 0; i < 3; i++) {
            if (requested[i] < 0 || requested[i] > maximum[i]) return false;
        }
        return requested[3] == -1 || requested[3] == 10 || requested[3] == 12;
    }

    static int parseCpuCeiling(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > 32) {
            throw new IllegalArgumentException("Invalid CPU ceiling length");
        }
        String text = new String(bytes, StandardCharsets.US_ASCII);
        if (!text.matches("[0-9]+\\n?")) throw new IllegalArgumentException("Invalid CPU ceiling text");
        if (text.endsWith("\n")) text = text.substring(0, text.length() - 1);
        try {
            int value = Integer.parseInt(text);
            if (value <= 0) throw new IllegalArgumentException("Invalid CPU ceiling value");
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("CPU ceiling overflow", e);
        }
    }

    static boolean validReadback(int[] observed) {
        if (observed == null || observed.length != 3) return false;
        int[] maximum = {2700000, 3500000, 4210000};
        for (int i = 0; i < 3; i++) {
            if (observed[i] <= 0 || observed[i] > maximum[i]) return false;
        }
        return true;
    }

    /** Aggregate readback may be stricter because thermal and other clients still own limits. */
    static boolean matches(int[] requested, int[] observed) {
        if (!validRequest(requested) || !validReadback(observed)) return false;
        for (int i = 0; i < 3; i++) {
            if (observed[i] <= 0 || (requested[i] > 0 && observed[i] > requested[i])) return false;
        }
        return true;
    }
}
