// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Fixed, hash-qualified stock metadata. No acquisition, control execution or publication. */
final class ThermalMetadataLoader {
    private static final String SHA =
            "3a82ca45508b254259344da82aae5de7c36f7b5c55e821d17d3b896cb0439572";
    private static final int SIZE = 100253;
    private static final List<String> ORDER =
            List.of("warm_policy", "cold_policy", "safe_policy");

    record ContextPackages(String packageH, String packageI, String packageJ) {}
    record OpaqueLeaf(String tag, Map<String, String> attributes, String value) {
        OpaqueLeaf { attributes = immutableMap(attributes); }
    }
    record OpaqueItem(String cluster, int level, List<OpaqueLeaf> leaves) {
        OpaqueItem { leaves = List.copyOf(leaves); }
    }
    record SafetyObligation(boolean present, boolean executionImplemented,
            List<OpaqueItem> items) {
        SafetyObligation { items = List.copyOf(items); }
    }
    record SafetyEffects(boolean executionImplemented, List<OpaqueItem> customItems,
            List<OpaqueItem> assetAppendItems) {
        SafetyEffects {
            customItems = List.copyOf(customItems);
            assetAppendItems = List.copyOf(assetAppendItems);
        }
    }
    record SceneMaps(Map<Integer, String> appTypes,
            List<ThermalSceneResolver.PackageRule> packageRules,
            Set<String> unmappedMarkers, boolean scene70003Exists) {
        SceneMaps {
            appTypes = immutableMap(appTypes);
            packageRules = List.copyOf(packageRules);
            unmappedMarkers = immutableSet(unmappedMarkers);
        }
    }
    record Metadata(List<StockThermalLevelEngine.Cluster> levelTables,
            Map<String, Map<String, StockLcdCapAggregator.Policy>> policyConfigs,
            Map<String, Set<String>> configPresence, List<String> pluginOrder,
            SceneMaps sceneMaps, SafetyObligation safe99998, SafetyEffects safe99999) {
        Metadata {
            levelTables = List.copyOf(levelTables);
            var policies = new LinkedHashMap<String, Map<String, StockLcdCapAggregator.Policy>>();
            policyConfigs.forEach((key, value) -> policies.put(key, immutableMap(value)));
            policyConfigs = immutableMap(policies);
            var presence = new LinkedHashMap<String, Set<String>>();
            configPresence.forEach((key, value) -> presence.put(key, immutableSet(value)));
            configPresence = immutableMap(presence);
            pluginOrder = List.copyOf(pluginOrder);
        }

        ThermalSceneResolver.Policy scenePolicy(ContextPackages context) {
            Objects.requireNonNull(context, "Caller must qualify H/I/J context");
            return new ThermalSceneResolver.Policy(sceneMaps.appTypes(), sceneMaps.packageRules(),
                    sceneMaps.scene70003Exists(), context.packageH(), context.packageI(),
                    context.packageJ(), configPresence.get("warm_policy"),
                    configPresence.get("cold_policy"), configPresence.get("safe_policy"));
        }
    }

    static Metadata load(byte[] input) throws JSONException {
        check(input != null && input.length == SIZE, "payload length");
        byte[] bytes = input.clone();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            var hex = new StringBuilder(64);
            for (byte value : digest) {
                hex.append(Character.forDigit((value >>> 4) & 15, 16));
                hex.append(Character.forDigit(value & 15, 16));
            }
            check(SHA.contentEquals(hex), "payload SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
        return parse(new JSONObject(new String(bytes, StandardCharsets.US_ASCII)));
    }

    private static Metadata parse(JSONObject root) throws JSONException {
        keys(root, "schemaVersion", "device", "firmware", "sources", "levelTables",
                "policyConfigs", "configPresence", "pluginOrder", "scenePolicy",
                "safe99998Obligation", "safe99999SideEffects", "counts");
        check(number(root, "schemaVersion") == 2 && text(root, "device").equals("LDY-AN00")
                && text(root, "firmware").equals("10.0.0.113"), "fixed identity");
        JSONObject sources = object(root, "sources");
        keys(sources, "customSha256", "assetSha256", "customVersion", "assetVersion");
        check(text(sources, "customSha256").equals(
                "a5c896d295125f1ceac621acdbe3c3ced712884be60f7f8a693a245c3ac025c4")
                && text(sources, "assetSha256").equals(
                "3dd3d73cad59d6dd2f7520e5b157755d15db4017fa9f29c2e5cd131d3b864e09")
                && text(sources, "customVersion").equals("5.10.24.100")
                && text(sources, "assetVersion").equals("5.10.22.100"), "source identity");
        List<String> order = strings(array(root, "pluginOrder"));
        check(order.equals(ORDER), "plugin order");

        var levels = new ArrayList<StockThermalLevelEngine.Cluster>();
        var levelNames = new HashSet<String>();
        for (JSONObject cluster : objects(array(root, "levelTables"))) {
            keys(cluster, "name", "scene", "sensors");
            String name = text(cluster, "name");
            check(levelNames.add(name) && cluster.get("scene") == JSONObject.NULL,
                    "duplicate level or unsupported scene predicate");
            var sensors = new ArrayList<StockThermalLevelEngine.SensorTable>();
            var ids = new HashSet<Integer>();
            for (JSONObject sensor : objects(array(cluster, "sensors"))) {
                keys(sensor, "sensorId", "rows");
                int id = number(sensor, "sensorId");
                check((id == 29 || id == 55 || id == 9 || id == 28) && ids.add(id), "sensor identity");
                var rows = new ArrayList<StockThermalLevelEngine.Row>();
                for (JSONObject row : objects(array(sensor, "rows"))) {
                    keys(row, "level", "enter", "clear");
                    rows.add(new StockThermalLevelEngine.Row(number(row, "level"),
                            number(row, "enter"), number(row, "clear")));
                }
                check(!rows.isEmpty(), "empty sensor table");
                sensors.add(new StockThermalLevelEngine.SensorTable(id, rows));
            }
            check(!sensors.isEmpty(), "empty cluster");
            levels.add(new StockThermalLevelEngine.Cluster(name, null, sensors));
        }
        check(levels.size() == 7, "level count");

        var presence = new LinkedHashMap<String, Set<String>>();
        for (JSONObject feature : objects(array(root, "configPresence"))) {
            keys(feature, "feature", "scenes");
            String name = text(feature, "feature");
            check(ORDER.contains(name) && !presence.containsKey(name), "feature identity");
            List<String> scenes = strings(array(feature, "scenes"));
            check(new HashSet<>(scenes).size() == scenes.size(), "duplicate presence");
            presence.put(name, new LinkedHashSet<>(scenes));
        }
        check(presence.keySet().equals(new HashSet<>(ORDER))
                && presence.values().stream().mapToInt(Set::size).sum() == 31,
                "complete presence");
        check(presence.get("safe_policy").equals(Set.of("99998", "99999")),
                "safe presence");

        var policies = new LinkedHashMap<String, Map<String, StockLcdCapAggregator.Policy>>();
        for (String feature : ORDER) policies.put(feature, new LinkedHashMap<>());
        var dependencies = new HashSet<String>();
        int itemCount = 0;
        int leafCount = 0;
        int policyCount = 0;
        for (JSONObject config : objects(array(root, "policyConfigs"))) {
            keys(config, "feature", "scene", "items");
            String feature = text(config, "feature");
            String scene = text(config, "scene");
            check(presence.containsKey(feature) && presence.get(feature).contains(scene),
                    "unregistered config");
            Map<String, StockLcdCapAggregator.Policy> target = policies.get(feature);
            check(!target.containsKey(scene), "duplicate config");
            var items = new ArrayList<StockLcdCapAggregator.Item>();
            for (JSONObject item : objects(array(config, "items"))) {
                keys(item, "cluster", "level", "leaves");
                String cluster = text(item, "cluster");
                var leaves = new ArrayList<StockLcdCapAggregator.Leaf>();
                for (JSONObject leaf : objects(array(item, "leaves"))) {
                    keys(leaf, "channel", "value", "batteryRange");
                    String channel = text(leaf, "channel");
                    String value = text(leaf, "value");
                    check(StockLcdCapAggregator.DISPLAY_CHANNELS.contains(channel), "unknown LCD channel");
                    decimal(value, -999999999, 999999999);
                    Object rawRange = leaf.get("batteryRange");
                    check(rawRange == JSONObject.NULL || rawRange instanceof String,
                            "battery range type");
                    String range = rawRange == JSONObject.NULL ? null : (String) rawRange;
                    if (range != null) {
                        String[] bounds = range.split(",", -1);
                        check(bounds.length == 2, "battery range");
                        for (String bound : bounds) decimal(bound, -999999, 999999);
                    }
                    leaves.add(new StockLcdCapAggregator.Leaf(channel, value, range));
                }
                if (!leaves.isEmpty()) dependencies.add(cluster);
                leafCount += leaves.size();
                items.add(new StockLcdCapAggregator.Item(cluster, number(item, "level"), leaves));
            }
            itemCount += items.size();
            policyCount++;
            target.put(scene, new StockLcdCapAggregator.Policy(scene, items));
        }
        check(policyCount == 30 && itemCount == 316 && leafCount == 1142, "projection count");
        check(levelNames.equals(dependencies), "LCD level dependency closure");
        for (String feature : ORDER) {
            Set<String> expected = new HashSet<>(presence.get(feature));
            if (feature.equals("safe_policy")) expected.remove("99998");
            check(policies.get(feature).keySet().equals(expected), "missing config projection");
        }
        var neutral = policies.get("safe_policy").get("99999");
        check(neutral != null && neutral.items().size() == 34
                && neutral.items().stream().mapToInt(i -> i.leaves().size()).sum() == 8,
                "complete safe display config");
        JSONObject counts = object(root, "counts");
        keys(counts, "customPolicies", "customItems", "assetSafeAppendItems", "mergedItems", "lcdLeaves", "controlLeaves", "displayLeaves");
        check(number(counts, "customPolicies") == 30 && number(counts, "customItems") == 294
                && number(counts, "assetSafeAppendItems") == 22
                && number(counts, "mergedItems") == itemCount
                && number(counts, "lcdLeaves") == 1134
                && number(counts, "controlLeaves") == 8
                && number(counts, "displayLeaves") == leafCount, "declared counts");

        JSONObject scene = object(root, "scenePolicy");
        keys(scene, "appTypeScenes", "packageScenes", "unmappedSceneMarkers", "scene70003Exists");
        var types = new LinkedHashMap<Integer, String>();
        Integer previousType = null;
        for (JSONObject entry : objects(array(scene, "appTypeScenes"))) {
            keys(entry, "appType", "scene");
            int type = number(entry, "appType");
            check(previousType == null || previousType < type, "duplicate/unordered appType");
            previousType = type;
            types.put(type, text(entry, "scene"));
        }
        var rules = new ArrayList<ThermalSceneResolver.PackageRule>();
        var packages = new HashSet<String>();
        Integer previousHash = null;
        for (JSONObject entry : objects(array(scene, "packageScenes"))) {
            keys(entry, "key", "scene");
            String name = text(entry, "key");
            check(packages.add(name) && (previousHash == null || previousHash <= name.hashCode()),
                    "duplicate/unordered package");
            previousHash = name.hashCode();
            rules.add(new ThermalSceneResolver.PackageRule(name, text(entry, "scene")));
        }
        List<String> markers = strings(array(scene, "unmappedSceneMarkers"));
        check(new HashSet<>(markers).size() == markers.size(), "duplicate markers");
        boolean special = bool(scene, "scene70003Exists");
        check(special == markers.contains("70003"), "marker provenance");
        SceneMaps sceneMaps = new SceneMaps(types, rules, new LinkedHashSet<>(markers), special);
        JSONObject safe = object(root, "safe99998Obligation");
        keys(safe, "present", "executionImplemented", "items");
        check(bool(safe, "present") && !bool(safe, "executionImplemented"), "safe obligation");
        List<OpaqueItem> safeItems = opaque(array(safe, "items"));
        check(safeItems.size() == 1, "safe99998 count");
        JSONObject effects = object(root, "safe99999SideEffects");
        keys(effects, "executionImplemented", "customItems", "assetAppendItems");
        check(!bool(effects, "executionImplemented"), "no sideeffect executor");
        List<OpaqueItem> customItems = opaque(array(effects, "customItems"));
        List<OpaqueItem> assetItems = opaque(array(effects, "assetAppendItems"));
        check(customItems.size() == 15 && assetItems.size() == 22, "raw safety count");
        return new Metadata(levels, policies, presence, order, sceneMaps,
                new SafetyObligation(true, false, safeItems),
                new SafetyEffects(false, customItems, assetItems));
    }

    private static List<OpaqueItem> opaque(JSONArray input) throws JSONException {
        var items = new ArrayList<OpaqueItem>();
        for (JSONObject item : objects(input)) {
            keys(item, "cluster", "level", "leaves");
            var leaves = new ArrayList<OpaqueLeaf>();
            for (JSONObject leaf : objects(array(item, "leaves"))) {
                keys(leaf, "tag", "attributes", "value");
                JSONObject attributes = object(leaf, "attributes");
                var copied = new LinkedHashMap<String, String>();
                for (String key : attributes.keySet()) copied.put(key, text(attributes, key));
                leaves.add(new OpaqueLeaf(text(leaf, "tag"), copied, text(leaf, "value")));
            }
            items.add(new OpaqueItem(text(item, "cluster"), number(item, "level"), leaves));
        }
        return List.copyOf(items);
    }

    private static List<JSONObject> objects(JSONArray input) throws JSONException {
        var result = new ArrayList<JSONObject>();
        for (int i = 0; i < input.length(); i++) {
            Object value = input.get(i);
            check(value instanceof JSONObject, "object array type");
            result.add((JSONObject) value);
        }
        return result;
    }
    private static List<String> strings(JSONArray input) throws JSONException {
        var result = new ArrayList<String>();
        for (int i = 0; i < input.length(); i++) {
            Object value = input.get(i);
            check(value instanceof String, "string array type");
            result.add((String) value);
        }
        return List.copyOf(result);
    }
    private static void keys(JSONObject object, String... expected) throws JSONException {
        check(object.keySet().equals(Set.of(expected)), "unknown/missing keys");
    }
    private static JSONObject object(JSONObject parent, String key) throws JSONException {
        Object value = parent.get(key);
        check(value instanceof JSONObject, "object type: " + key);
        return (JSONObject) value;
    }
    private static JSONArray array(JSONObject parent, String key) throws JSONException {
        Object value = parent.get(key);
        check(value instanceof JSONArray, "array type: " + key);
        return (JSONArray) value;
    }
    private static String text(JSONObject parent, String key) throws JSONException {
        Object value = parent.get(key);
        check(value instanceof String, "string type: " + key);
        return (String) value;
    }
    private static int number(JSONObject parent, String key) throws JSONException {
        Object value = parent.get(key);
        check(value instanceof Integer, "integer type: " + key);
        return (Integer) value;
    }
    private static boolean bool(JSONObject parent, String key) throws JSONException {
        Object value = parent.get(key);
        check(value instanceof Boolean, "boolean type: " + key);
        return (Boolean) value;
    }
    private static void decimal(String value, int low, int high) throws JSONException {
        check(value.matches("-?[0-9]+"), "integer syntax");
        try {
            int number = Integer.parseInt(value);
            check(number >= low && number <= high, "integer domain");
        } catch (NumberFormatException e) {
            throw new JSONException("integer overflow");
        }
    }
    private static void check(boolean condition, String reason) throws JSONException {
        if (!condition) throw new JSONException(reason);
    }
    private static <K, V> Map<K, V> immutableMap(Map<K, V> value) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
    private static <T> Set<T> immutableSet(Set<T> value) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(value));
    }
}
