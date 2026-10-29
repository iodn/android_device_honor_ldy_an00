// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;

/** Atomic raw acquisition boundary. Does not invent age/invalid-input cap policy. */
record ThermalPolicyBatch(long sequence, long startedMilliseconds, long completedMilliseconds,
        Boolean anyReporterInvalid, Integer back, Integer usb, Integer battery) {
    static final String PROPERTY = "sys.lineage.ldy_an00.thermal_batch";

    static ThermalPolicyBatch parse(String encoded, long nowMilliseconds) {
        if (encoded == null || encoded.length() > 91 || nowMilliseconds < 0) return null;
        String[] f = encoded.split(":", -1);
        if (f.length != 8 || !f[0].equals("4")) return null;
        try {
            long sequence = unsigned(f[1]);
            if (sequence > 0xffffffffL) return null;
            long start = unsigned(f[2]);
            long end = unsigned(f[3]);
            if (end < start || end > nowMilliseconds) return null;
            Boolean invalid = switch (f[4]) {
                case "0" -> false;
                case "1" -> true;
                case "x" -> null;
                default -> throw new NumberFormatException();
            };
            return new ThermalPolicyBatch(sequence, start, end, invalid,
                    field(f[5]), field(f[6]), field(f[7]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long unsigned(String value) {
        if (!value.matches("[0-9]+")) throw new NumberFormatException();
        return Long.parseLong(value);
    }

    private static Integer field(String value) {
        if (value.equals("x")) return null;
        if (!value.matches("-?[0-9]+")) throw new NumberFormatException();
        return Integer.parseInt(value);
    }
}
