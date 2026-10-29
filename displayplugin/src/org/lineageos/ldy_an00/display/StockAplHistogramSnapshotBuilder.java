package org.lineageos.ldy_an00.display;

/**
 * Exact stock HistogramProcessor APL predicate and its constructor-baseline packet projection.
 * Other stock features (ELVSS, black-screen/content-load/splash requests) are not requested here.
 * The vendor independently overwrites support bits and may run its Splash observer.
 */
public final class StockAplHistogramSnapshotBuilder {
    public enum ScreenEvent { NONE, ON, OFF }
    public record Scene(Boolean hdr, Boolean thirdPartyHdr, Boolean fullscreen,
            Boolean backlightAboveThreshold, Boolean ehdrApl, Boolean sdrApl,
            Boolean fullSceneApl) {}
    public record Snapshot(int mode, byte[] data) {
        public Snapshot { data = data.clone(); }
        @Override public byte[] data() { return data.clone(); }
    }
    /** requested=null means missing context; a false requested with no snapshot issues no command. */
    public record Result(Boolean requested, Snapshot snapshot) {}

    private StockAplHistogramSnapshotBuilder() {}

    public static Result build(Scene scene, ScreenEvent screenEvent) {
        if (scene == null || screenEvent == null) return new Result(null, null);
        Boolean[] inputs = {scene.hdr(), scene.thirdPartyHdr(), scene.fullscreen(),
                scene.backlightAboveThreshold(), scene.ehdrApl(), scene.sdrApl(),
                scene.fullSceneApl()};
        Boolean requested = null;
        boolean seen = false;
        // Only seven fixed stock flags. Qualify partial input iff every completion agrees.
        for (int bits = 0; bits < 128; bits++) {
            boolean matches = true;
            for (int i = 0; i < 7; i++) {
                if (inputs[i] != null && inputs[i] != ((bits & (1 << i)) != 0)) {
                    matches = false;
                    break;
                }
            }
            if (!matches) continue;
            boolean hdr = (bits & 1) != 0;
            boolean thirdParty = (bits & 2) != 0;
            boolean fullscreen = (bits & 4) != 0;
            boolean backlight = (bits & 8) != 0;
            boolean ehdr = (bits & 16) != 0;
            boolean sdr = (bits & 32) != 0;
            boolean fullScene = (bits & 64) != 0;
            boolean current = ((hdr || thirdParty) && fullscreen && backlight)
                    || (ehdr && !hdr && backlight && !thirdParty && !sdr)
                    || (sdr && backlight && fullscreen) || fullScene;
            if (seen && requested != current) return new Result(null, null);
            requested = current;
            seen = true;
        }
        int mode = screenEvent == ScreenEvent.ON ? 0x30
                : screenEvent == ScreenEvent.OFF ? 0x31 : requested ? 0x7b : 0;
        if (mode == 0) return new Result(false, null);
        byte[] data = new byte[176]; // Exact memset baseline, not unknown scene flags as false.
        data[0] = (byte) mode;
        data[12] = (byte) (requested ? 1 : 0);
        return new Result(requested, new Snapshot(mode, data));
    }
}
