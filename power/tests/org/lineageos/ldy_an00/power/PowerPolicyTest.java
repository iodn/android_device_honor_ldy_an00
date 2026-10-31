// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.power;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class PowerPolicyTest {
    private static void equal(int[] actual, int... expected) {
        if (!Arrays.equals(actual, expected)) {
            throw new AssertionError(Arrays.toString(actual));
        }
    }

    private static void parsed(String text, int expected) {
        if (PowerPolicy.parseCpuCeiling(text.getBytes(StandardCharsets.US_ASCII)) != expected) {
            throw new AssertionError("Wrong CPU ceiling");
        }
    }

    private static void malformed(byte[] bytes) {
        try {
            PowerPolicy.parseCpuCeiling(bytes);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Malformed CPU ceiling accepted");
    }

    public static void main(String[] args) {
        equal(PowerPolicy.select(false, true, false), 0, 0, 0, -1);
        equal(PowerPolicy.select(false, false, false), 1800000, 1200000, 600000, -1);
        equal(PowerPolicy.select(true, true, false), 1300000, 1500000, 1550000, -1);
        equal(PowerPolicy.select(true, false, false), 1300000, 1500000, 1550000, -1);
        for (boolean saver : new boolean[]{false, true}) {
            for (boolean interactive : new boolean[]{false, true}) {
                // Call/camera protection overrides both saver and screen-off policies.
                equal(PowerPolicy.select(saver, interactive, true), 0, 0, 0, -1);
            }
        }
        int[] saver = PowerPolicy.select(true, true, false);
        if (!PowerPolicy.matches(saver, new int[]{1200000, 1400000, 1500000})) {
            throw new AssertionError("Stricter thermal constraints must remain valid");
        }
        for (int[] bad : new int[][]{
                {1400000, 1500000, 1550000},
                {1300000, 1600000, 1550000},
                {1300000, 1500000, 1700000},
                {0, 1500000, 1550000},
                {-1, 1500000, 1550000},
                {2800000, 1500000, 1550000},
                {1300000, 1500000},
                {1300000, 1500000, 1550000, 0}
        }) {
            if (PowerPolicy.matches(saver, bad)) throw new AssertionError("Invalid readback accepted");
        }
        if (!PowerPolicy.validReadback(new int[]{2700000, 3500000, 4210000})) {
            throw new AssertionError("CPU maximum endpoints rejected");
        }
        for (int[] badRange : new int[][]{
                {2700001, 3500000, 4210000}, {2700000, 3500001, 4210000},
                {2700000, 3500000, 4210001}, {2700000, 0, 4210000},
                {2700000, 3500000, -1}
        }) {
            if (PowerPolicy.validReadback(badRange)) {
                throw new AssertionError("Invalid CPU range accepted");
            }
        }
        if (PowerPolicy.validReadback(null)) throw new AssertionError("Null readback accepted");
        if (!PowerPolicy.validReadback(new int[]{2600000, 2600000, 3200000})) {
            throw new AssertionError("Captured CPU ceilings rejected");
        }
        for (boolean saverMode : new boolean[]{false, true}) {
            for (boolean interactive : new boolean[]{false, true}) {
                for (boolean protectedWorkload : new boolean[]{false, true}) {
                    int[] request = PowerPolicy.select(saverMode, interactive, protectedWorkload);
                    if (request.length != 4 || request[3] != -1) {
                        throw new AssertionError("GPU release request invariant");
                    }
                }
            }
        }
        for (int[] badRequest : new int[][]{
                null, {1300000, 1500000, 1550000}, {1300000, 1500000, 1550000, -1, 0},
                {1300000, 1500000, 1550000, 10}, {1300000, 1500000, 1550000, 12}
        }) {
            if (PowerPolicy.validRequest(badRequest)
                    || PowerPolicy.matches(badRequest, new int[]{1200000, 1400000, 1500000})) {
                throw new AssertionError("Unsupported request accepted");
            }
        }
        saver[0] = 1;
        equal(PowerPolicy.select(true, true, false), 1300000, 1500000, 1550000, -1);
        parsed("2600000\n", 2600000);
        parsed("2600000", 2600000);
        parsed("3200000\n", 3200000);
        parsed("1\n", 1);
        parsed("0000000000000000000000002600000\n", 2600000);
        for (String text : new String[]{"", "\n", "0", "-1", "+1", " 1", "1 ",
                "1\r\n", "1\n\n", "1x", "1\n2", "2147483648",
                "99999999999999999999999999999999",
                "00000000000000000000000002600000\n"}) {
            malformed(text.getBytes(StandardCharsets.US_ASCII));
        }
        malformed(null);
        malformed(new byte[]{(byte) 0xff, '1'});
        malformed(new byte[]{'1', 0});
        System.out.println("POWER_POLICY_TESTS_OK");
    }
}
