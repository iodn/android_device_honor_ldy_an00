// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Hash-pinned selected-panel loader. No source defaults or mutable global configuration. */
final class StockLcdPanelLoader {
    private static final int SIZE = 4239;
    private static final String SHA =
            "a69df5666d65e23f3f421516ee263dcfc0b551886c0810cb17f17f61841d26bc";
    private static final String SOURCE_XML =
            "15385bc3829a53ee78c5f47feae7a45f10b55ffac2421aa80ae2bb9d640dbbdd";

    static StockLcdPanel load(byte[] input) throws JSONException {
        check(input != null && input.length == SIZE, "payload length");
        byte[] bytes = input.clone();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >>> 4) & 15, 16));
                hex.append(Character.forDigit(b & 15, 16));
            }
            check(SHA.contentEquals(hex), "payload SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
        return parse(new JSONObject(new String(bytes, StandardCharsets.US_ASCII)));
    }

    // Schema tests use this path; production callers use hash-pinned load().
    static StockLcdPanel parse(JSONObject root) throws JSONException {
        keys(root, Set.of("schemaVersion", "device", "sourceXmlSha256", "semanticDefault",
                "panel", "hdrThermalRatioPoints", "hdrModes", "apl"));
        check(integer(root, "schemaVersion") == 6, "schema version");
        check("LDY-AN00".equals(root.get("device")), "device");
        check(SOURCE_XML.equals(root.get("sourceXmlSha256")), "source XML");
        JSONObject provenance = root.getJSONObject("semanticDefault");
        keys(provenance, Set.of("field", "floatBits", "owner", "dexStore"));
        check("minValidThermalBrightness".equals(provenance.get("field"))
                && "40800000".equals(provenance.get("floatBits"))
                && "HwBrightnessXmlLoader.Data".equals(provenance.get("owner"))
                && "250b58".equals(provenance.get("dexStore")), "semantic default provenance");
        JSONObject p = root.getJSONObject("panel");
        keys(p, Set.of("manualMaxRaw", "seekbarMaxRaw", "switchPackageMillis",
                "powerUpdateMillis", "outdoorDiscountRaw", "outdoorDiscountDelayMillis",
                "outdoorLuxThreshold", "aplLuxThreshold", "thermalDragStrategyEnable",
                "thermalModeBrightnessMappingEnable", "fullSceneApl",
                "fullSceneAplThermalEnable", "hdrNoLimit", "outdoorDiscountEnable",
                "minValidThermalRaw"));
        check(Float.floatToIntBits(number(p.get("minValidThermalRaw")))
                == 0x40800000, "source-qualified semantic default");
        JSONArray rawPoints = root.getJSONArray("hdrThermalRatioPoints");
        ArrayList<StockLcdPanel.Point> points = new ArrayList<>(rawPoints.length());
        for (int i = 0; i < rawPoints.length(); i++) {
            JSONArray pair = rawPoints.getJSONArray(i);
            check(pair.length() == 2, "curve point");
            points.add(new StockLcdPanel.Point(number(pair.get(0)), number(pair.get(1))));
        }
        try {
            StockLcdPanel panel = new StockLcdPanel(points,
                    number(p.get("manualMaxRaw")), number(p.get("seekbarMaxRaw")),
                    integer(p, "switchPackageMillis"), integer(p, "powerUpdateMillis"),
                    number(p.get("outdoorDiscountRaw")), integer(p, "outdoorDiscountDelayMillis"),
                    number(p.get("outdoorLuxThreshold")), number(p.get("aplLuxThreshold")),
                    bool(p, "thermalDragStrategyEnable"), bool(p, "thermalModeBrightnessMappingEnable"),
                    bool(p, "fullSceneApl"), bool(p, "fullSceneAplThermalEnable"),
                    bool(p, "hdrNoLimit"), bool(p, "outdoorDiscountEnable"),
                    number(p.get("minValidThermalRaw")), hdrModes(root.getJSONObject("hdrModes")),
                    apl(root.getJSONObject("apl")));
            check(!panel.thermalModeBrightnessMappingEnable() && panel.thermalDragStrategyEnable(),
                    "unsupported thermal strategy");
            return panel;
        } catch (IllegalArgumentException bad) {
            throw new JSONException(bad.getMessage());
        }
    }

    private static StockAplPanel apl(JSONObject a) throws JSONException {
        keys(a, Set.of("enabled","sunEnabled","esdrEnabled","galleryEnabled","specialEnabled",
                "ehdrMaxRatio","batteryThreshold","brightenAmount","darkenAmount",
                "fullBrightenAmount","fullDarkenAmount","fullStartLevel","hdrDividedBySdrRatio",
                "hdrDividedBySdrAplRatio","animationBrightenSeconds","animationDarkenSeconds",
                "sunRatioPoints","sdrRatioPoints","indexThresholds","luxRows","defaults"));
        JSONArray defaults = a.getJSONArray("defaults");
        check(defaults.length() == 3,"APL source defaults");
        JSONObject gallery = defaults.getJSONObject(0), special = defaults.getJSONObject(1);
        keys(gallery,Set.of("field","value","dexStore"));
        keys(special,Set.of("field","value","dexStore"));
        check("GalleryAplModeEnable".equals(gallery.get("field")) && !bool(gallery,"value")
                && "24fee8".equals(gallery.get("dexStore")) && !bool(a,"galleryEnabled"),
                "gallery APL default");
        check("specialAplEnable".equals(special.get("field")) && !bool(special,"value")
                && "24ff66".equals(special.get("dexStore")) && !bool(a,"specialEnabled"),
                "special APL default");
        JSONObject max = defaults.getJSONObject(2);
        keys(max,Set.of("field","value","floatBits","dexStore","constant"));
        check("ehdrAplMaxRatio".equals(max.get("field"))
                && Float.floatToIntBits(number(max.get("value"))) == 0xbf800000
                && "bf800000".equals(max.get("floatBits"))
                && "24ff6a".equals(max.get("dexStore"))
                && "24fdb8".equals(max.get("constant"))
                && Float.floatToIntBits(number(a.get("ehdrMaxRatio"))) == 0xbf800000,
                "HDR APL maximum default");
        JSONArray index = a.getJSONArray("indexThresholds"), rows = a.getJSONArray("luxRows");
        check(index.length() <= 256 && rows.length() <= 256,"APL table bounds");
        ArrayList<Float> indices = new ArrayList<>(index.length());
        for (int i = 0; i < index.length(); i++) indices.add(number(index.get(i)));
        ArrayList<StockAplPanel.LuxRow> table = new ArrayList<>(rows.length());
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            keys(row,Set.of("lux","brightnessLevels"));
            JSONArray raw = row.getJSONArray("brightnessLevels");
            check(raw.length() == index.length(),"APL row width");
            ArrayList<Float> brightness = new ArrayList<>(raw.length());
            for (int j = 0; j < raw.length(); j++) brightness.add(number(raw.get(j)));
            long lux = integer(row,"lux");
            check(lux >= 0 && lux <= Integer.MAX_VALUE,"APL lux range");
            table.add(new StockAplPanel.LuxRow((int)lux,brightness));
        }
        long battery = integer(a,"batteryThreshold"), start = integer(a,"fullStartLevel");
        check(battery >= -1 && battery <= 100 && start >= 0 && start <= Integer.MAX_VALUE,
                "APL integer range");
        return new StockAplPanel(bool(a,"enabled"),bool(a,"sunEnabled"),bool(a,"esdrEnabled"),
                bool(a,"galleryEnabled"),bool(a,"specialEnabled"),number(a.get("ehdrMaxRatio")),
                (int)battery,number(a.get("brightenAmount")),number(a.get("darkenAmount")),
                number(a.get("fullBrightenAmount")),number(a.get("fullDarkenAmount")),
                (int)start,number(a.get("hdrDividedBySdrRatio")),number(a.get("hdrDividedBySdrAplRatio")),
                number(a.get("animationBrightenSeconds")),number(a.get("animationDarkenSeconds")),
                curve(a,"sunRatioPoints"),curve(a,"sdrRatioPoints"),indices,table);
    }

    private static StockLcdPanel.HdrModes hdrModes(JSONObject h) throws JSONException {
        keys(h, Set.of("curves", "flags", "ehdrAplLuxThreshold", "maxHdrSdrRatio", "ratioDefault",
                "seekbarBasedLuxEnabled", "seekbarLuxThreshold", "careerModeEnabled", "careerDefault",
                "fingerprintOffUnlockEnable"));
        JSONObject career = h.getJSONObject("careerDefault");
        keys(career, Set.of("field", "value", "owner", "dexStore"));
        check("careerModeEnable".equals(career.get("field")) && !bool(career,"value")
                && "HwBrightnessXmlLoader.Data".equals(career.get("owner"))
                && "24fe5c".equals(career.get("dexStore")), "career default provenance");
        check(!bool(h,"careerModeEnabled"), "unsupported career profile");
        JSONObject d = h.getJSONObject("ratioDefault");
        keys(d, Set.of("field", "floatBits", "owner", "dexStore"));
        check("maxHdrSdrRatio".equals(d.get("field")) && "40a00000".equals(d.get("floatBits"))
                && "HwBrightnessXmlLoader.Data".equals(d.get("owner"))
                && "24ffde".equals(d.get("dexStore")), "HDR ratio provenance");
        check(Float.floatToIntBits(number(h.get("maxHdrSdrRatio"))) == 0x40a00000,
                "source-qualified HDR ratio default");
        JSONObject c = h.getJSONObject("curves"), f = h.getJSONObject("flags");
        keys(c, Set.of("LocalHdrBrightnessPoints", "HlgHdrBrightnessPoints",
                "HdrPreviewBrightnessPoints", "LowHdrBrightnessPoints",
                "BrightnessMappingPointsForHdrGalleryMode", "SdrVideoPlusBrightnessPoints",
                "ThirdPartSdrBrightnessPoints"));
        keys(f, Set.of("LocalHdrModeEnable", "WhiteHdrModeEnable", "HdrGalleryModeEnable",
                "HdrPreviewModeEnable", "HlgHdrModeEnable", "SdrVideoPlusModeEnable", "ThirdPartSdrModeEnable", "EhdrAplEnable"));
        return new StockLcdPanel.HdrModes(curve(c,"LocalHdrBrightnessPoints"),
                curve(c,"HlgHdrBrightnessPoints"), curve(c,"HdrPreviewBrightnessPoints"),
                curve(c,"LowHdrBrightnessPoints"), curve(c,"BrightnessMappingPointsForHdrGalleryMode"),
                curve(c,"SdrVideoPlusBrightnessPoints"), curve(c,"ThirdPartSdrBrightnessPoints"),
                bool(f,"LocalHdrModeEnable"),
                bool(f,"WhiteHdrModeEnable"), bool(f,"HdrGalleryModeEnable"),
                bool(f,"HdrPreviewModeEnable"), bool(f,"HlgHdrModeEnable"),
                bool(f,"SdrVideoPlusModeEnable"), bool(f,"ThirdPartSdrModeEnable"), bool(f,"EhdrAplEnable"),
                number(h.get("ehdrAplLuxThreshold")), number(h.get("maxHdrSdrRatio")),
                bool(h,"seekbarBasedLuxEnabled"), number(h.get("seekbarLuxThreshold")),
                bool(h,"careerModeEnabled"), bool(h,"fingerprintOffUnlockEnable"));
    }
    private static java.util.List<StockLcdPanel.Point> curve(JSONObject object, String key)
            throws JSONException {
        JSONArray raw = object.getJSONArray(key);
        ArrayList<StockLcdPanel.Point> result = new ArrayList<>(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            JSONArray point = raw.getJSONArray(i);
            check(point.length() == 2, "HDR point");
            result.add(new StockLcdPanel.Point(number(point.get(0)),number(point.get(1))));
        }
        return result;
    }

    private static float number(Object value) throws JSONException {
        check(value instanceof Number, "numeric type");
        float result = ((Number) value).floatValue();
        check(Float.isFinite(result), "finite number");
        return result;
    }
    private static long integer(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        check(value instanceof Integer || value instanceof Long, "integer " + key);
        return ((Number) value).longValue();
    }
    private static boolean bool(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        check(value instanceof Boolean, "boolean " + key);
        return (Boolean) value;
    }
    private static void keys(JSONObject object, Set<String> expected) throws JSONException {
        Set<String> actual = new HashSet<>();
        object.keys().forEachRemaining(actual::add);
        check(actual.equals(expected), "object keys");
    }
    private static void check(boolean condition, String message) throws JSONException {
        if (!condition) throw new JSONException(message);
    }
}
