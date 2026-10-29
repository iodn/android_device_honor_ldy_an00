package org.lineageos.ldy_an00.display;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Typed selected-native observations. Receipt time is not a hardware sample timestamp. */
public sealed interface StockDisplayAuxNotice {
    long generation();
    long receivedElapsedNanos();

    enum Domain { FULLSCREEN, ENHANCEMENT }
    enum Problem { MISSING_PAYLOAD, WRONG_LENGTH, UNTERMINATED_STRING, INVALID_UTF8,
        UNSUPPORTED_SDR2HDR_ACTION }

    record SourceReset(long generation, long receivedElapsedNanos) implements StockDisplayAuxNotice {}
    record Fullscreen(long generation, long receivedElapsedNanos, boolean active)
            implements StockDisplayAuxNotice {}
    record Sdr2HdrAction(long generation, long receivedElapsedNanos, int action, int sceneAction)
            implements StockDisplayAuxNotice {}
    record Capsule(long generation, long receivedElapsedNanos, int capsuleType,
            String featureName, String packageName) implements StockDisplayAuxNotice {}
    record Unknown(long generation, long receivedElapsedNanos, Domain domain,
            Problem problem, Integer rawAction) implements StockDisplayAuxNotice {}

    static StockDisplayAuxNotice decode(int event, byte[] data, long generation, long elapsed) {
        if ((event != 3 && event != 4) || generation <= 0 || elapsed < 0) return null;
        Domain domain = event == 3 ? Domain.FULLSCREEN : Domain.ENHANCEMENT;
        if (data == null || data.length == 0) {
            return new Unknown(generation, elapsed, domain, Problem.MISSING_PAYLOAD, null);
        }
        if (event == 3) {
            // Stock accepts every nonempty payload and compares only the first unsigned byte to0.
            return new Fullscreen(generation, elapsed, data[0] != 0);
        }
        if (data.length == 1) {
            int action = (data[0] & 255) ^ 1;
            int scene = switch (action) {
                case 0 -> 0xf0;
                case 1 -> 0xf1;
                case 14 -> 0x106;
                case 15 -> 0x107;
                case 17 -> 0xdf;
                case 59 -> 0xde;
                case 66 -> 0xe0;
                case 67 -> 0xe1;
                default -> -1;
            };
            return scene < 0
                    ? new Unknown(generation, elapsed, domain,
                            Problem.UNSUPPORTED_SDR2HDR_ACTION, action)
                    : new Sdr2HdrAction(generation, elapsed, action, scene);
        }
        if (data.length != 260) {
            return new Unknown(generation, elapsed, domain, Problem.WRONG_LENGTH, null);
        }
        int featureEnd = endOfString(data, 4);
        int packageEnd = endOfString(data, 132);
        if (featureEnd < 0 || packageEnd < 0) {
            return new Unknown(generation, elapsed, domain, Problem.UNTERMINATED_STRING, null);
        }
        try {
            String feature = utf8(data, 4, featureEnd);
            String pkg = utf8(data, 132, packageEnd);
            int type = (data[0] & 255) | ((data[1] & 255) << 8)
                    | ((data[2] & 255) << 16) | ((data[3] & 255) << 24);
            return new Capsule(generation, elapsed, type, feature, pkg);
        } catch (CharacterCodingException e) {
            // Safe bounded adaptation: stock passes these fields to a native C-string constructor.
            return new Unknown(generation, elapsed, domain, Problem.INVALID_UTF8, null);
        }
    }

    private static int endOfString(byte[] data, int offset) {
        for (int i = offset; i < offset + 128; i++) if (data[i] == 0) return i;
        return -1;
    }

    private static String utf8(byte[] data, int offset, int end) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(data, offset, end - offset)).toString();
    }
}
