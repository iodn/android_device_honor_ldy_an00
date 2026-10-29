// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Seven-control primitive for recovered packaged LCD policy, with caller-selected passes.
 * Game-secondary LCD evaluation is omitted because stock restores its saved device map.
 * Interpolation is absent because both parameters are zero in this exact stock APK.
 */
final class StockLcdCapAggregator {
    public static final List<String> CHANNELS = List.of(
            "lcd", "lcd_manual", "lcd_outdoor", "lcd_outdoor_manual", "lcd_outdoor_apl");
    public static final List<String> CONTROL_CHANNELS = List.of("hdr_ctrl", "outbrightness_ctrl");
    public static final List<String> DISPLAY_CHANNELS = List.of(
            "lcd", "lcd_manual", "lcd_outdoor", "lcd_outdoor_manual", "lcd_outdoor_apl",
            "hdr_ctrl", "outbrightness_ctrl");
    public record Leaf(String channel, String value, String batteryRange) {}
    public record Item(String cluster, int level, List<Leaf> leaves) {
        public Item { leaves = List.copyOf(leaves); }
    }
    public record Policy(String scene, List<Item> items) {
        public Policy { items = List.copyOf(items); }
    }
    public record Owner(String scene, String cluster, int itemLevel,
                        StockThermalLevelEngine.Sample sample) {}
    public record Aggregate(Map<String, String> raw, Owner owner, Map<String, Owner> controlOwners) {
        public Aggregate { raw = Map.copyOf(raw); controlOwners = Map.copyOf(controlOwners); }
        public Aggregate(Map<String, String> raw, Owner owner) { this(raw, owner, Map.of()); }
    }
    public record Dispatch(List<Integer> caps, int temperature) {
        public Dispatch { caps = List.copyOf(caps); }
    }
    private record Selected(Item item, StockThermalLevelEngine.Sample sample) {}

    public static Aggregate evaluate(List<Policy> primary, List<Policy> secondary,
            List<StockThermalLevelEngine.Result> levels, int batteryPercent) {
        Aggregate first = pass(primary, levels, batteryPercent);
        Aggregate second = pass(secondary, levels, batteryPercent);
        Map<String, String> merged = new LinkedHashMap<>(second.raw());
        Owner owner = second.owner();
        Map<String, Owner> controlOwners = new LinkedHashMap<>(second.controlOwners());
        // Stock N merges saved primary INTO secondary, so primary owns equal caps.
        for (var entry : first.raw().entrySet()) {
            if (merge(merged, entry.getKey(), entry.getValue())) {
                if (entry.getKey().equals("lcd")) owner = first.owner();
                if (CONTROL_CHANNELS.contains(entry.getKey())) {
                    controlOwners.put(entry.getKey(), first.controlOwners().get(entry.getKey()));
                }
            }
        }
        return new Aggregate(merged, owner, controlOwners);
    }

    private static Aggregate pass(List<Policy> policies,
            List<StockThermalLevelEngine.Result> levels, int batteryPercent) {
        Map<String, String> raw = new LinkedHashMap<>();
        Owner owner = null;
        Map<String, Owner> controlOwners = new LinkedHashMap<>();
        for (Policy policy : policies) {
            Map<String, Selected> selected = new LinkedHashMap<>();
            for (Item item : policy.items()) {
                StockThermalLevelEngine.Sample sample = representative(
                        levels, item.cluster(), policy.scene());
                if (sample != null && item.level() <= sample.level()) {
                    // Whole-item replacement, including an empty LCD projection.
                    selected.put(item.cluster(), new Selected(item, sample));
                }
            }
            var ordered = new ArrayList<>(selected.entrySet());
            // Stable sort preserves insertion order inside hash-collision runs.
            ordered.sort(Comparator.comparingInt(entry -> entry.getKey().hashCode()));
            for (var entry : ordered) {
                Selected candidate = entry.getValue();
                for (Leaf leaf : candidate.item().leaves()) {
                    if (!matchesBattery(leaf.batteryRange(), batteryPercent)) continue;
                    if (merge(raw, leaf.channel(), leaf.value())) {
                        Owner incoming = new Owner(policy.scene(), candidate.item().cluster(),
                                candidate.item().level(), candidate.sample());
                        if (leaf.channel().equals("lcd")) owner = incoming;
                        if (CONTROL_CHANNELS.contains(leaf.channel())) {
                            controlOwners.put(leaf.channel(), incoming);
                        }
                    }
                }
            }
        }
        return new Aggregate(raw, owner, controlOwners);
    }

    private static StockThermalLevelEngine.Sample representative(
            List<StockThermalLevelEngine.Result> levels, String cluster, String scene) {
        for (var result : levels) {
            if (!cluster.equals(result.name())) continue;
            if (result.scene() != null) {
                for (String excluded : result.scene().split(",")) {
                    if (scene.equals(excluded)) return null;
                }
            }
            return result.representative();
        }
        return null;
    }

    private static boolean matchesBattery(String range, int percent) {
        if (range == null) return true;
        String[] parts = range.split(",");
        if (parts.length != 2) return false;
        Integer low = integer(parts[0]);
        Integer high = integer(parts[1]);
        return low != null && high != null && low >= -999999 && low <= 999999
                && high >= -999999 && high <= 999999 && percent >= low && percent < high;
    }

    private static Integer integer(String value) {
        try { return Integer.valueOf(value); }
        catch (NumberFormatException e) { return null; }
    }

    private static boolean merge(Map<String, String> map, String key, String incoming) {
        String old = map.get(key);
        if (old == null) {
            map.put(key, incoming);
            return true;
        }
        Integer left = integer(old);
        Integer right = integer(incoming);
        String result = left == null || right == null
                ? old : Integer.toString(CONTROL_CHANNELS.contains(key)
                        ? Math.max(left, right) : Math.min(left, right));
        map.put(key, result);
        return result.equals(incoming);
    }

    public record ControlDispatch(String channel, int value, int temperature) {}

    /** x0.c0 residual release-then-change intent; no receiver or Binder execution. */
    public static final class ControlSender {
        private final Map<String, String> last = new LinkedHashMap<>();

        public List<ControlDispatch> offer(Aggregate aggregate) {
            var current = new LinkedHashMap<String, String>();
            for (String key : CONTROL_CHANNELS) {
                String value = aggregate.raw().get(key);
                if (value != null) current.put(key, value);
            }
            var output = new ArrayList<ControlDispatch>();
            var ordered = new ArrayList<>(CONTROL_CHANNELS);
            ordered.sort(Comparator.comparingInt(String::hashCode));
            for (String key : ordered) {
                if (last.containsKey(key) && !current.containsKey(key)) {
                    output.add(new ControlDispatch(key, 0, -1));
                }
            }
            for (String key : ordered) {
                String value = current.get(key);
                if (value == null || value.equals(last.get(key))) continue;
                Integer parsed = integer(value);
                // j0/T reject invalid syntax, out-of-domain values and negatives.
                if (parsed == null || parsed < 0 || parsed > 999999999) continue;
                Owner owner = aggregate.controlOwners().get(key);
                output.add(new ControlDispatch(key, parsed, owner == null
                        ? -1 : owner.sample().temperature() / 1000));
            }
            last.clear();
            last.putAll(current);
            return List.copyOf(output);
        }
    }

    /** Stock grouped Y assignment and five-cap-only dedup; this does not send Binder calls. */
    public static final class Sender {
        private final int[] pending = new int[5];
        private int[] last = {-1, -1, -1, -1, -1};

        public Dispatch offer(Aggregate aggregate, Set<String> supportedOptional) {
            for (int i = 0; i < CHANNELS.size(); ++i) {
                String key = CHANNELS.get(i);
                Integer value = integer(aggregate.raw().getOrDefault(key, "0"));
                if (value == null || value < -999999999 || value > 999999999) continue;
                int cap = value == 0 ? 255 : value;
                pending[i] = i == 0 || supportedOptional.contains(key) ? cap : -1;
            }
            if (Arrays.equals(last, pending)) return null;
            last = pending.clone();
            int temperature = aggregate.owner() == null
                    ? -1 : aggregate.owner().sample().temperature() / 1000;
            return new Dispatch(List.of(pending[0], pending[1], pending[2],
                    pending[3], pending[4]), temperature);
        }
    }
}
