package org.lineageos.ldy_an00.display;

import java.util.List;
import java.util.Objects;

/** Selected-panel private HDR arithmetic. Call only with a qualified stock scene. */
public final class StockHdrCurve {
    public enum Scene { NONE, LOCAL, GALLERY, PREVIEW, WHITE, SDR_VIDEO, THIRD_PARTY, HLG, ULTRA }
    /** Direct backlight-domain DDC maps, NOT brightness-domain ratio/spline methods. */
    public interface BacklightMapper {
        float nitsFromBacklight(float backlight);
        float backlightFromNits(float nits);
    }
    public record AutomaticResult(float beforeRecovery, float storedNoLimit,
            Boolean updatedGalleryAplEnable, float computedHdr) {}

    private final StockLcdPanel mPanel;
    public StockHdrCurve(StockLcdPanel panel) { mPanel = Objects.requireNonNull(panel); }

    /** DEX2456c8 exact operation order; no endpoint resampling or ratio approximation. */
    static float interpolate(List<StockLcdPanel.Point> points, float input) {
        if (Float.isNaN(input) || input <= 0) return input;
        StockLcdPanel.Point previous = points.get(0);
        float result = input;
        for (StockLcdPanel.Point point : points) {
            if (input < point.x()) {
                if (point.x() <= previous.x()) return input;
                return ((point.y() - previous.y()) / (point.x() - previous.x()))
                        * (input - previous.x()) + previous.y();
            }
            result = point.y();
            previous = point;
        }
        return result;
    }

    /** DEX273b54 exact direct-map helper, including original-input lower bound. */
    public float ratioLimit(float input, float desiredRatio, boolean ultra,
            BacklightMapper mapper) {
        if (mapper == null || input <= 0 || Float.isNaN(input) || desiredRatio < 0)
            return input;
        float nits = mapper.nitsFromBacklight(input);
        if (nits == -1) return input;
        float ratio = ultra ? Math.min(mPanel.hdrModes().maxHdrSdrRatio(), desiredRatio) : desiredRatio;
        float backlight = mapper.backlightFromNits(nits * ratio);
        return Math.max(input, Math.min(mPanel.configuredMaxHdrBacklight(), backlight));
    }

    /** Seven positively enabled stock curves; SDRPLUS is disabled by selected XML+ctor. */
    public float compute(Scene scene, float input, float desiredRatio, BacklightMapper mapper) {
        return switch (scene) {
            case NONE -> input;
            case LOCAL -> Math.min(interpolate(mPanel.hdrModes().local(), input),
                    ratioLimit(input, desiredRatio, false, mapper));
            case HLG -> Math.min(interpolate(mPanel.hdrModes().hlg(), input),
                    ratioLimit(input, desiredRatio, false, mapper));
            case WHITE -> Math.min(interpolate(mPanel.hdrModes().low(), input),
                    ratioLimit(input, desiredRatio, false, mapper));
            case ULTRA -> Math.min(interpolate(mPanel.hdrModes().gallery(), input),
                    ratioLimit(input, desiredRatio, true, mapper));
            case PREVIEW -> interpolate(mPanel.hdrModes().preview(), input);
            case SDR_VIDEO -> interpolate(mPanel.hdrModes().sdrVideoPlus(), input);
            case THIRD_PARTY -> interpolate(mPanel.hdrModes().thirdPartySdr(), input);
            case GALLERY -> interpolate(mPanel.hdrModes().gallery(), input);
        };
    }

    /** DEX292200: gallery state updates only in gallery branch; null means retain prior state. */
    public AutomaticResult automatic(Scene scene, boolean brightenScreenFirst, float input,
            float desiredRatio, float thermalMaximum, Float effectiveGalleryAplThreshold,
            BacklightMapper mapper) {
        if (brightenScreenFirst || scene == Scene.NONE)
            return new AutomaticResult(input, -1, null, input);
        if (scene == Scene.GALLERY && (effectiveGalleryAplThreshold == null
                || !Float.isFinite(effectiveGalleryAplThreshold)
                || effectiveGalleryAplThreshold < 0f || effectiveGalleryAplThreshold > 1f)) {
            throw new IllegalArgumentException("Missing qualified gallery getter limit");
        }
        float hdr = compute(scene, input, desiredRatio, mapper);
        Boolean galleryUpdate = scene == Scene.GALLERY
                ? mPanel.hdrModes().ehdrAplEnabled() && input >= effectiveGalleryAplThreshold : null;
        return hdr > thermalMaximum
                ? new AutomaticResult(thermalMaximum, hdr, galleryUpdate, hdr)
                : new AutomaticResult(hdr, -1, galleryUpdate, hdr);
    }
}
