// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure primary-scene selection. The producer owns acquisition of every input. */
final class ThermalSceneResolver {
    enum Outcome { READY, NOT_READY }
    record PackageRule(String key, String scene) {}
    record Foreground(String packageName, Integer cmsAppType, int matchedPid,
            String previousRawScene) {}
    // Boxed flags deliberately distinguish unavailable context from false.
    record Flags(Boolean vr, Boolean highSpeed, Boolean screenOn, Boolean pcCast,
            boolean demoVersionKnown, String demoVersion, Boolean flagF,
            Boolean flagR, Boolean flagS, Integer modeM) {}
    record Snapshot(Foreground foreground, Flags flags,
            Map<String, Integer> cachedPackageTypes) {
        Snapshot {
            if (cachedPackageTypes != null) {
                cachedPackageTypes = immutableMap(cachedPackageTypes);
            }
        }
    }
    record Policy(Map<Integer, String> appTypeScenes, List<PackageRule> packageScenes,
            boolean scene70003Exists, String packageH, String packageI, String packageJ,
            Set<String> warmConfigs, Set<String> coldConfigs, Set<String> safeConfigs) {
        Policy {
            if (appTypeScenes != null) appTypeScenes = immutableMap(appTypeScenes);
            if (packageScenes != null) {
                packageScenes = Collections.unmodifiableList(new ArrayList<>(packageScenes));
            }
            if (warmConfigs != null) warmConfigs = immutableSet(warmConfigs);
            if (coldConfigs != null) coldConfigs = immutableSet(coldConfigs);
            if (safeConfigs != null) safeConfigs = immutableSet(safeConfigs);
        }
    }
    /** Missing default configs are reported explicitly, never fabricated as usable items. */
    record Decision(Outcome outcome, Foreground foreground, String rawScene,
            String effectiveScene, String warmConfig, boolean warmPresent,
            String coldConfig, boolean coldPresent, String safeConfig, boolean safePresent,
            boolean safe99998Present) {}

    private final Policy mPolicy;
    ThermalSceneResolver(Policy policy) { mPolicy = policy; }

    Decision resolve(Snapshot snapshot) {
        if (!ready(snapshot)) {
            return new Decision(Outcome.NOT_READY, null, null, null, null, false,
                    null, false, null, false, false);
        }
        Foreground foreground = snapshot.foreground();
        Flags flags = snapshot.flags();
        String raw = rawScene(snapshot);
        // A known null package preserves the previous raw scene in Z0.B0.
        if (raw == null) {
            return new Decision(Outcome.NOT_READY, foreground, null, null, null, false,
                    null, false, null, false, false);
        }
        String effective = effectiveScene(raw, flags);
        if (effective == null) {
            return new Decision(Outcome.NOT_READY, foreground, raw, null, null, false,
                    null, false, null, false, false);
        }
        String warm = mPolicy.warmConfigs().contains(effective) ? effective : "10000";
        String cold = mPolicy.coldConfigs().contains(effective) ? effective : "10000";
        return new Decision(Outcome.READY, foreground, raw, effective, warm,
                mPolicy.warmConfigs().contains(warm), cold,
                mPolicy.coldConfigs().contains(cold), "99999",
                mPolicy.safeConfigs().contains("99999"),
                mPolicy.safeConfigs().contains("99998"));
    }

    private boolean ready(Snapshot s) {
        if (s == null || s.foreground() == null || s.flags() == null
                || s.cachedPackageTypes() == null || mPolicy == null
                || mPolicy.appTypeScenes() == null || mPolicy.packageScenes() == null
                || mPolicy.warmConfigs() == null || mPolicy.coldConfigs() == null
                || mPolicy.safeConfigs() == null) return false;
        return s.foreground().packageName() == null
                || s.foreground().cmsAppType() != null;
    }

    // Require only context that can change the recovered branch result.
    // Unknown is never converted to false or to a stock default.
    private static String effectiveScene(String raw, Flags f) {
        if (f.vr() == null) return null;
        String effective;
        if (f.vr()) effective = "200020";
        else if ("100100".equals(raw)) effective = "10000";
        else if (Boolean.TRUE.equals(f.pcCast()) || Boolean.TRUE.equals(f.screenOn())) {
            effective = raw;
        } else {
            if (f.pcCast() == null || f.screenOn() == null || !f.demoVersionKnown()) {
                return null;
            }
            effective = "true".equals(f.demoVersion()) ? "100050" : "20004";
        }
        if ("10000".equals(effective) || "20004".equals(effective)) {
            if (f.highSpeed() == null) return null;
            if (f.highSpeed()) effective = "100061";
        }
        return effective;
    }

    private String rawScene(Snapshot snapshot) {
        Foreground foreground = snapshot.foreground();
        String name = foreground.packageName();
        if (name == null) return foreground.previousRawScene();
        Flags f = snapshot.flags();
        Integer cachedType = snapshot.cachedPackageTypes().get(name);
        if (cachedType != null) {
            String mapped = mPolicy.appTypeScenes().get(cachedType);
            if (mapped != null) {
                if (mPolicy.scene70003Exists() && cachedType == 5001) {
                    if (f.flagF() == null) return null;
                    if (f.flagF()) return "70003";
                }
                return mapped;
            }
        }
        if (name.equals(mPolicy.packageH())) {
            if (f.flagR() == null) return null;
            if (f.flagR()) return "70002";
        }
        if (mPolicy.packageI() != null && name.equals(mPolicy.packageJ())) {
            if (f.flagS() == null) return null;
            if (f.flagS()) return "200010";
        }
        if (foreground.cmsAppType() == 0) {
            if (f.modeM() == null) return null;
            if (f.modeM() == 1) return "70001";
        }
        // r.S uses supplied map iteration order, not exact-before-wildcard sorting.
        for (PackageRule rule : mPolicy.packageScenes()) {
            if (rule.key() == null || rule.scene() == null) continue;
            int star = rule.key().indexOf('*');
            if (star >= 0 ? name.startsWith(rule.key().substring(0, star))
                    : name.equals(rule.key())) return rule.scene();
        }
        if ("com.hihonor.android.clone".equals(name)) return "100100";
        String mapped = mPolicy.appTypeScenes().get(foreground.cmsAppType());
        return mapped == null ? "10000" : mapped;
    }

    static boolean initialScreenOn(int displayState) {
        return displayState != 1 && displayState != 3 && displayState != 4;
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
    private static Set<String> immutableSet(Set<String> source) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }
}
