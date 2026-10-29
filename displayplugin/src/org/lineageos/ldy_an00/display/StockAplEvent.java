package org.lineageos.ldy_an00.display;

/** Native event data only. No inferred freshness, panel curve, or active APL default. */
public record StockAplEvent(long generation, long receivedElapsedNanos,
        int rawRatio, boolean active, int packedExtra) {
    public static StockAplEvent decode(int event, byte[] data, long generation,
            long receivedElapsedNanos) {
        if (event != 8 || data == null || data.length < 8 || generation <= 0
                || receivedElapsedNanos < 0) {
            return null;
        }
        // Exact ARM32 ORR and subsequent stock Java arithmetic, including overflow/sign bits.
        int packed = int32(data, 0) | (int32(data, 4) << 8);
        return new StockAplEvent(generation, receivedElapsedNanos,
                packed & 255, (packed >> 8) == 1, packed);
    }

    private static int int32(byte[] data, int offset) {
        return (data[offset] & 255) | ((data[offset + 1] & 255) << 8)
                | ((data[offset + 2] & 255) << 16) | ((data[offset + 3] & 255) << 24);
    }
}
