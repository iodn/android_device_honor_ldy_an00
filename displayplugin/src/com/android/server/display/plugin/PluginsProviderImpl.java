// Copyright (C) 2026 The LineageOS Project
// SPDX-License-Identifier: Apache-2.0
package com.android.server.display.plugin;

import android.content.Context;
import com.android.internal.annotations.Keep;
import java.util.List;
import java.util.Set;
import org.lineageos.ldy_an00.display.HdrThermalPlugin;
import org.lineageos.ldy_an00.display.ThermalPolicyPlugin;

/** Entry point loaded by the platform display PluginManager. */
@Keep
public final class PluginsProviderImpl implements PluginsProvider {
    @Override
    public List<Plugin> getPlugins(Context context, PluginStorage storage,
            PluginManager.PluginProviderDependencies dependencies,
            Set<PluginType<?>> enabledTypes) {
        if (enabledTypes.contains(PluginType.BRIGHTNESS_THERMAL_POLICY)) {
            // The coherent policy supersedes the legacy TTL-based restriction owner.
            return List.of(new ThermalPolicyPlugin(context,storage));
        }
        if (!enabledTypes.contains(PluginType.HDR_BOOST_OVERRIDE)) return List.of();
        return List.of(new HdrThermalPlugin(context, storage));
    }
}
