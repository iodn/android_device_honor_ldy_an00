// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;

/** Four selected inputs from one existing sampler cycle. No age/TTL or new protocol. */
record ThermalDisplayBatch(long sequence, long startedMilliseconds, long completedMilliseconds,
        Boolean anyReporterInvalid, Integer back, Integer usb, Integer battery, Integer front,
        long shellPublishedMilliseconds) {
    static final String SHELL_PROPERTY = "sys.lineage.ldy_an00.shell_temp";
    static ThermalDisplayBatch read(String before,String shell,String after,long nowMilliseconds) {
        return java.util.Objects.equals(before,after)
                ? join(ThermalPolicyBatch.parse(before,nowMilliseconds),shell) : null;
    }
    static ThermalDisplayBatch join(ThermalPolicyBatch batch,String shell) {
        if (batch == null || shell == null || shell.length() > 91) return null;
        String[] f = shell.split(":",-1);
        if (f.length != 5 || !f[0].equals("1") || !f[1].matches("[0-9]+")) return null;
        try {
            long timestamp = Long.parseLong(f[1]);
            if (timestamp < batch.startedMilliseconds()
                    || timestamp > batch.completedMilliseconds()) return null;
            for (int i=2;i<5;i++) if (!f[i].matches("-?[0-9]+")) return null;
            int front=Integer.parseInt(f[2]),back=Integer.parseInt(f[3]);
            Integer.parseInt(f[4]); // Complete legacy shape; frame is not a selected reporter input.
            if (batch.back()==null || back!=batch.back()) return null;
            return new ThermalDisplayBatch(batch.sequence(),batch.startedMilliseconds(),
                    batch.completedMilliseconds(),batch.anyReporterInvalid(),back,batch.usb(),
                    batch.battery(),front,timestamp);
        } catch (NumberFormatException malformed) { return null; }
    }
}
