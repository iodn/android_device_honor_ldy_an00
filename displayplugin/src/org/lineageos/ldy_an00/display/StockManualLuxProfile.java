package org.lineageos.ldy_an00.display;

import java.security.MessageDigest;
import java.util.HexFormat;
import org.json.JSONException;
import org.json.JSONObject;

/** Fixed private firmware-profile payload, loaded only on the existing plugin worker. */
final class StockManualLuxProfile {
    private static final String PAYLOAD_SHA256 =
            "3eb3b502bb48ebff0c7c293a36c14e5cbca04e44e3909baf2fe8701d3effff6d";

    static StockManualLuxFusion.Parameters load(byte[] payload) throws Exception {
        if (!PAYLOAD_SHA256.equals(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(payload)))) {
            throw new IllegalArgumentException("Unqualified manual lux profile");
        }
        JSONObject root = new JSONObject(new String(payload, java.nio.charset.StandardCharsets.UTF_8));
        if (root.getInt("schema") != 1) throw new JSONException("Manual lux schema");
        JSONObject p = root.getJSONObject("parameters");
        return new StockManualLuxFusion.Parameters(
                (float) p.getDouble("HubFloorThresh"), (float) p.getDouble("HubDarkRoomThresh"),
                (float) p.getDouble("HubDarkRoomThresh1"), (float) p.getDouble("HubDarkRoomThresh2"),
                (float) p.getDouble("HubDarkRoomThresh3"), (float) p.getDouble("HubDarkRoomThresh4"),
                (float) p.getDouble("HubDarkRoomDelta"), (float) p.getDouble("HubDarkRoomDelta1"),
                (float) p.getDouble("HubDarkRoomDelta2"), (float) p.getDouble("HubDarkRoomDelta3"),
                (float) p.getDouble("HubFusedRatio"));
    }
}
