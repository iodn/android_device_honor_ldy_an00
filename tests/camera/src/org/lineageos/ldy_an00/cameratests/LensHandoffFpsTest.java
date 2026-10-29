// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.cameratests;

import static org.junit.Assert.*;

import org.junit.Test;

public class LensHandoffFpsTest {
    @Test public void explicitCadenceAcceptsOnlyOrdinary30Or60() {
        assertNull(LensHandoffSamples.fixedFps(null));
        assertEquals(Integer.valueOf(30), LensHandoffSamples.fixedFps("30"));
        assertEquals(Integer.valueOf(60), LensHandoffSamples.fixedFps("60"));
        for (String value : new String[] {"", "0", "29", "120", "240", " 30", "60 ", "-30"}) {
            try {
                LensHandoffSamples.fixedFps(value);
                fail("Accepted unsupported cadence: " + value);
            } catch (IllegalArgumentException expected) {
                // High-speed sessions and malformed arguments require a different path.
            }
        }
    }

    @Test public void missingOrInvalidTimingCannotQualifyCadence() {
        for (int fps : new int[] {30, 60}) {
            assertFalse(LensHandoffSamples.supportsFixedFps(0, fps));
            assertFalse(LensHandoffSamples.supportsFixedFps(-1, fps));
        }
    }

    @Test public void nanosecondRoundingIsBoundedAtOneNanosecond() {
        assertTrue(LensHandoffSamples.supportsFixedFps(16_666_666L, 60));
        assertTrue(LensHandoffSamples.supportsFixedFps(16_666_667L, 60));
        assertFalse(LensHandoffSamples.supportsFixedFps(16_666_668L, 60));
        assertTrue(LensHandoffSamples.supportsFixedFps(33_333_333L, 30));
        assertTrue(LensHandoffSamples.supportsFixedFps(33_333_334L, 30));
        assertFalse(LensHandoffSamples.supportsFixedFps(33_333_335L, 30));
    }

    @Test public void thirtyFpsStreamDoesNotQualifySixtyFps() {
        assertTrue(LensHandoffSamples.supportsFixedFps(33_333_333L, 30));
        assertFalse(LensHandoffSamples.supportsFixedFps(33_333_333L, 60));
    }
}
