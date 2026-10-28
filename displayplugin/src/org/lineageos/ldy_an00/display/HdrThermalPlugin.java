// Copyright (C) 2026 The LineageOS Project
// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.view.Display;

import com.android.server.display.plugin.Plugin;
import com.android.server.display.plugin.PluginStorage;
import com.android.server.display.plugin.PluginType;
import com.android.server.display.plugin.types.HdrBoostOverride;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

/** Supplies only an HDR restriction; the framework remains the brightness owner. */
public final class HdrThermalPlugin implements Plugin {
    private final Context mContext;
    private final PluginStorage mStorage;
    private final HdrThermalPolicy mPolicy = new HdrThermalPolicy();
    private final Set<String> mDisplayIds = new HashSet<>();
    private final Runnable mRefresh = this::refresh;
    private Handler mHandler;
    private DisplayManager mDisplays;
    private Boolean mLastRestricted;
    private volatile String mDumpState = "not started";

    public HdrThermalPlugin(Context context, PluginStorage storage) {
        mContext = context;
        mStorage = storage;
    }

    @Override
    public void onBootCompleted() {
        if (mHandler != null) return;
        HandlerThread thread = new HandlerThread("LdyHdrThermal");
        thread.start();
        mHandler = new Handler(thread.getLooper());
        mDisplays = mContext.getSystemService(DisplayManager.class);
        mDisplays.registerDisplayListener(new DisplayManager.DisplayListener() {
            @Override public void onDisplayAdded(int displayId) { refresh(); }
            @Override public void onDisplayRemoved(int displayId) { refresh(); }
            @Override public void onDisplayChanged(int displayId) { refresh(); }
        }, mHandler);
        mHandler.post(mRefresh);
    }

    private void refresh() {
        mHandler.removeCallbacks(mRefresh);
        Set<String> current = new HashSet<>();
        boolean awake = false;
        for (Display display : mDisplays.getDisplays()) {
            if (display.getType() != Display.TYPE_INTERNAL) continue;
            current.add(display.getUniqueId());
            awake |= display.getState() == Display.STATE_ON;
        }
        ShellTemperatureSnapshot sample = ShellTemperatureSnapshot.parse(
                SystemProperties.get(ShellTemperatureSnapshot.PROPERTY, ""),
                SystemClock.elapsedRealtime());
        boolean restricted = !awake || mPolicy.restricted(sample);
        for (String id : current) {
            if (!mDisplayIds.contains(id) || mLastRestricted == null
                    || mLastRestricted != restricted) {
                mStorage.updateValue(PluginType.HDR_BOOST_OVERRIDE, id,
                        restricted ? HdrBoostOverride.forHdrOff() : null);
            }
        }
        for (String id : mDisplayIds) {
            if (!current.contains(id)) {
                mStorage.updateValue(PluginType.HDR_BOOST_OVERRIDE, id, null);
            }
        }
        mDisplayIds.clear();
        mDisplayIds.addAll(current);
        mLastRestricted = restricted;
        mDumpState = "restricted=" + restricted + " fresh=" + (sample != null)
                + " awake=" + awake + " displays=" + current;
        // Handler delays do not wake a suspended phone. Display-state callbacks
        // recheck elapsedRealtime freshness when the internal display turns on.
        if (awake) mHandler.postDelayed(mRefresh, 1000);
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println("LDY-AN00 HDR thermal plugin: " + mDumpState);
    }
}
