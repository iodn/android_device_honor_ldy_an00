// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;

import java.util.List;

/** Immutable selected-panel metadata. Calibration values are loaded from private payloads. */
public record StockLcdPanel(List<Point> hdrThermalRatioPoints, float manualMaxRaw,
        float seekbarMaxRaw, long switchPackageMillis, long powerUpdateMillis,
        float outdoorDiscountRaw, long outdoorDiscountDelayMillis, float outdoorLuxThreshold,
        float aplLuxThreshold, boolean thermalDragStrategyEnable,
        boolean thermalModeBrightnessMappingEnable, boolean fullSceneApl,
        boolean fullSceneAplThermalEnable, boolean hdrNoLimit, boolean outdoorDiscountEnable,
        float minValidThermalRaw, HdrModes hdrModes, StockAplPanel apl) {
    public record Point(float x, float y) {}

    public record HdrModes(List<Point> local, List<Point> hlg, List<Point> preview,
            List<Point> low, List<Point> gallery, List<Point> sdrVideoPlus, List<Point> thirdPartySdr,
            boolean localEnabled, boolean whiteEnabled, boolean galleryEnabled,
            boolean previewEnabled, boolean hlgEnabled, boolean sdrVideoPlusEnabled,
            boolean thirdPartySdrEnabled, boolean ehdrAplEnabled, float ehdrAplLuxThreshold, float maxHdrSdrRatio,
            boolean seekbarBasedLuxEnabled, float seekbarLuxThreshold, boolean careerModeEnabled,
            boolean fingerprintOffUnlockEnable) {
        public HdrModes {
            local = validatedCurve(local); hlg = validatedCurve(hlg);
            preview = validatedCurve(preview); low = validatedCurve(low);
            gallery = validatedCurve(gallery); sdrVideoPlus = validatedCurve(sdrVideoPlus);
            thirdPartySdr = validatedCurve(thirdPartySdr);
            if (!Float.isFinite(seekbarLuxThreshold) || seekbarLuxThreshold < 0f
                    || careerModeEnabled || !Float.isFinite(ehdrAplLuxThreshold) || ehdrAplLuxThreshold < 0f
                    || !Float.isFinite(maxHdrSdrRatio) || maxHdrSdrRatio < 1f) {
                throw new IllegalArgumentException("Invalid HDR mode scalars");
            }
        }
    }

    public float configuredMaxHdrBacklight() { return seekbarMaxRaw / 255f; }

    public StockLcdPanel {
        java.util.Objects.requireNonNull(hdrModes);
        java.util.Objects.requireNonNull(apl);
        hdrThermalRatioPoints = validatedCurve(hdrThermalRatioPoints);
        if (!raw(manualMaxRaw) || !raw(seekbarMaxRaw) || !raw(outdoorDiscountRaw)
                || !Float.isFinite(minValidThermalRaw) || minValidThermalRaw < 0f
                || minValidThermalRaw >= 255f || switchPackageMillis < 0
                || powerUpdateMillis < 0 || outdoorDiscountDelayMillis < 0
                || !Float.isFinite(outdoorLuxThreshold) || outdoorLuxThreshold < 0f
                || !Float.isFinite(aplLuxThreshold) || aplLuxThreshold < 0f) {
            throw new IllegalArgumentException("Invalid panel scalars");
        }
    }

    private static List<Point> validatedCurve(List<Point> points) {
        points = List.copyOf(points);
        if (points.size() < 2
                || points.get(0).x() != 0f
                || points.get(points.size() - 1).x() != 1f) {
            throw new IllegalArgumentException("Invalid HDR thermal curve endpoints");
        }
        float previousX = -1f, previousY = -1f;
        for (Point point : points) {
            if (!unit(point.x()) || !unit(point.y())
                    || point.x() <= previousX || point.y() < previousY) {
                throw new IllegalArgumentException("Invalid HDR thermal curve");
            }
            previousX = point.x();
            previousY = point.y();
        }
        return points;
    }

    private static boolean unit(float v) { return Float.isFinite(v) && v >= 0f && v <= 1f; }
    private static boolean raw(float v) { return Float.isFinite(v) && v > 0f && v <= 255f; }
}
