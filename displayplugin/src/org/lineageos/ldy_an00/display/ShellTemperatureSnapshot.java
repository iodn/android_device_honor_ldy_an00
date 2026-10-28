// Copyright (C) 2026 The LineageOS Project
// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;

/** Candidate reader contract for the HDR integration; pure host-testable Java.
 * Producer timestamps use CLOCK_BOOTTIME milliseconds. Android callers must use
 * SystemClock.elapsedRealtime(), including time spent suspended.
 */
final class ShellTemperatureSnapshot {
    static final String PROPERTY = "sys.lineage.ldy_an00.shell_temp";
    static final long MAX_AGE_MILLIS = 10_000; // Port policy: two 5-second sample intervals.
    final int front, back, frame;

    private ShellTemperatureSnapshot(int front, int back, int frame) {
        this.front = front;
        this.back = back;
        this.frame = frame;
    }

    private static boolean decimal(String value, boolean signed) {
        int first = signed && value.startsWith("-") ? 1 : 0;
        if (first == value.length()) return false;
        for (int i = first; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    static ShellTemperatureSnapshot parse(String value, long nowMillis) {
        if (value == null || value.isEmpty() || value.length() > 91 || nowMillis < 0) return null;
        String[] fields = value.split(":", -1);
        if (fields.length != 5 || !fields[0].equals("1") || !decimal(fields[1], false)) return null;
        try {
            long timestamp = Long.parseLong(fields[1]);
            if (timestamp > nowMillis || nowMillis - timestamp > MAX_AGE_MILLIS) return null;
            int[] temperatures = new int[3];
            for (int i = 0; i < temperatures.length; i++) {
                if (!decimal(fields[i + 2], true)) return null;
                temperatures[i] = Integer.parseInt(fields[i + 2]);
                if (temperatures[i] <= -40000 || temperatures[i] > 145000) return null;
            }
            return new ShellTemperatureSnapshot(temperatures[0], temperatures[1], temperatures[2]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
