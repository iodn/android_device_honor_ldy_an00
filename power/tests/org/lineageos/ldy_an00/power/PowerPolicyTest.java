// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.power;

import java.util.Arrays;

public final class PowerPolicyTest {
    private static void equal(int[] actual, int... expected) {
        if (!Arrays.equals(actual, expected)) {
            throw new AssertionError(Arrays.toString(actual));
        }
    }

    public static void main(String[] args) {
        equal(PowerPolicy.select(false, true, false), 0, 0, 0, -1);
        equal(PowerPolicy.select(false, false, false), 1800000, 1200000, 600000, -1);
        equal(PowerPolicy.select(true, true, false), 1300000, 1500000, 1550000, 10);
        equal(PowerPolicy.select(true, false, false), 1300000, 1500000, 1550000, 12);
        for (boolean saver : new boolean[]{false, true}) {
            for (boolean interactive : new boolean[]{false, true}) {
                // Call/camera protection overrides both saver and screen-off policies.
                equal(PowerPolicy.select(saver, interactive, true), 0, 0, 0, -1);
            }
        }
        int[] saver = PowerPolicy.select(true, true, false);
        if (!PowerPolicy.matches(saver, new int[]{1200000, 1400000, 1500000, 12})) {
            throw new AssertionError("Stricter thermal constraints must remain valid");
        }
        for (int[] bad : new int[][]{
                {1400000, 1500000, 1550000, 10},
                {1300000, 1600000, 1550000, 10},
                {1300000, 1500000, 1700000, 10},
                {1300000, 1500000, 1550000, 9},
                {0, 1500000, 1550000, 10},
                {1300000, 1500000, 1550000, -1},
                {1300000, 1500000, 1550000, 57},
                {2800000, 1500000, 1550000, 10},
                {1300000, 1500000, 1550000}
        }) {
            if (PowerPolicy.matches(saver, bad)) throw new AssertionError("Invalid readback accepted");
        }
        saver[0] = 1;
        equal(PowerPolicy.select(true, true, false), 1300000, 1500000, 1550000, 10);
        System.out.println("POWER_POLICY_TESTS_OK");
    }
}
