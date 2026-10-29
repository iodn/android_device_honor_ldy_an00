// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;

import java.util.List;

/** Immutable source-selected APL calibration; values remain in their original units. */
public record StockAplPanel(boolean enabled, boolean sunEnabled, boolean esdrEnabled,
        boolean galleryEnabled, boolean specialEnabled, float ehdrMaxRatio,
        int batteryThreshold, float brightenAmount, float darkenAmount,
        float fullBrightenAmount, float fullDarkenAmount, int fullStartLevel,
        float hdrDividedBySdrRatio, float hdrDividedBySdrAplRatio,
        float animationBrightenSeconds, float animationDarkenSeconds,
        List<StockLcdPanel.Point> sunRatioPoints, List<StockLcdPanel.Point> sdrRatioPoints,
        List<Float> indexThresholds, List<LuxRow> luxRows) {
    public record LuxRow(int lux, List<Float> brightnessLevels) {
        public LuxRow {
            brightnessLevels = List.copyOf(brightnessLevels);
            if (lux < 0) throw new IllegalArgumentException("APL lux");
            for (float v : brightnessLevels) {
                if (!Float.isFinite(v) || v < 0f || v > 255f)
                    throw new IllegalArgumentException("APL raw brightness");
            }
        }
    }
    public StockAplPanel {
        sunRatioPoints = ratios(sunRatioPoints);
        sdrRatioPoints = ratios(sdrRatioPoints);
        indexThresholds = List.copyOf(indexThresholds);
        luxRows = List.copyOf(luxRows);
        if (!Float.isFinite(ehdrMaxRatio) || batteryThreshold < -1 || batteryThreshold > 100
                || !nonnegative(brightenAmount) || !nonnegative(darkenAmount)
                || !nonnegative(fullBrightenAmount) || !nonnegative(fullDarkenAmount)
                || fullStartLevel < 0 || !positive(hdrDividedBySdrRatio)
                || !positive(hdrDividedBySdrAplRatio) || !nonnegative(animationBrightenSeconds)
                || !nonnegative(animationDarkenSeconds)
                || indexThresholds.isEmpty() || indexThresholds.size() > 256
                || luxRows.isEmpty() || luxRows.size() > 256)
            throw new IllegalArgumentException("APL scalars/table size");
        float previousIndex = -1f;
        for (float index : indexThresholds) {
            if (!Float.isFinite(index) || index < 0f || index > 255f || index <= previousIndex)
                throw new IllegalArgumentException("APL index order");
            previousIndex = index;
        }
        int previousLux = -1;
        for (LuxRow row : luxRows) {
            if (row.lux() <= previousLux || row.brightnessLevels().size() != indexThresholds.size())
                throw new IllegalArgumentException("APL lux order/row width");
            previousLux = row.lux();
        }
    }
    private static List<StockLcdPanel.Point> ratios(List<StockLcdPanel.Point> points) {
        points = List.copyOf(points);
        if (points.size() < 2 || points.size() > 256) throw new IllegalArgumentException("APL curve");
        float previous = -1f;
        for (var p : points) {
            if (!Float.isFinite(p.x()) || p.x() <= previous || p.x() < 0f
                    || !Float.isFinite(p.y()) || p.y() < 1f || p.y() > 1.5f)
                throw new IllegalArgumentException("APL ratio point");
            previous = p.x();
        }
        return points;
    }
    private static boolean nonnegative(float v) { return Float.isFinite(v) && v >= 0f; }
    private static boolean positive(float v) { return Float.isFinite(v) && v > 0f; }
}
