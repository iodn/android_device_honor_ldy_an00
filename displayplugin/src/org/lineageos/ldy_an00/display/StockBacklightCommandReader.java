package org.lineageos.ldy_an00.display;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads the selected driver's last accepted hardware command, not physical luminance.
 * The caller must qualify the selected firmware/module identities and own this reader
 * on its existing worker. No device operation or alternate source is attempted on error.
 */
public final class StockBacklightCommandReader {
    private static final Path COMMAND =
            Path.of("/sys/class/leds/lcd-backlight/mt_brightness_hw_changed");
    private static final int MAXIMUM = 16383;

    /** Null means unavailable; zero is an accepted off command, not an unknown read. */
    public Integer readRawCommand() {
        try {
            return parseRawCommand(Files.readString(COMMAND, StandardCharsets.US_ASCII));
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    // Exact selected sysfs producer prints an unsigned decimal followed by one newline.
    static Integer parseRawCommand(String value) {
        if (value == null || value.length() < 2 || value.length() > 6
                || value.charAt(value.length() - 1) != '\n') {
            return null;
        }
        int raw = 0;
        for (int i = 0; i < value.length() - 1; i++) {
            char ch = value.charAt(i);
            if (ch < '0' || ch > '9') return null;
            raw = raw * 10 + ch - '0';
        }
        return raw <= MAXIMUM ? raw : null;
    }
}
