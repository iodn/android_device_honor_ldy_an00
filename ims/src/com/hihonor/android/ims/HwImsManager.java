// SPDX-License-Identifier: Apache-2.0
package com.hihonor.android.ims;

import android.content.Context;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.android.ims.ImsManager;

/** IMS-app-local compatibility API; never install on the shared boot class path. */
public final class HwImsManager {
    private HwImsManager() {}

    private static boolean valid(Context context, int phoneId) {
        return context != null && SubscriptionManager.isValidPhoneId(phoneId);
    }

    public static boolean isEnhanced4gLteModeSettingEnabledByUser(
            Context context, int phoneId) {
        return valid(context, phoneId)
                && ImsManager.getInstance(context, phoneId)
                        .isEnhanced4gLteModeSettingEnabledByUser();
    }

    public static boolean isVolteEnabledByPlatform(Context context, int phoneId) {
        return valid(context, phoneId)
                && ImsManager.getInstance(context, phoneId).isVolteEnabledByPlatform();
    }

    public static boolean isWfcEnabledByUser(Context context, int phoneId) {
        return valid(context, phoneId)
                && ImsManager.getInstance(context, phoneId).isWfcEnabledByUser();
    }

    public static void updateImsServiceConfig(Context context, int phoneId, boolean force) {
        if (!valid(context, phoneId)) return;
        if (!force) {
            TelephonyManager telephony = context.getSystemService(TelephonyManager.class);
            if (telephony == null
                    || telephony.getSimState(phoneId) != TelephonyManager.SIM_STATE_READY) return;
        }
        // Android 17 always reevaluates current carrier/user configuration.
        // Preserve the measured non-forced SIM-readiness gate; no Honor cache is recreated.
        ImsManager.getInstance(context, phoneId).updateImsServiceConfig();
    }
}
