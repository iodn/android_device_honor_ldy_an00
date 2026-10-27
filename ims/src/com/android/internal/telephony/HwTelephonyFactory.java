// SPDX-License-Identifier: Apache-2.0
package com.android.internal.telephony;

/** Only the measured diagnostic factory API; no Honor telephony stack. */
public final class HwTelephonyFactory {
    private HwTelephonyFactory() {}

    public static HwChrServiceManager getHwChrServiceManager() {
        return DefaultHwChrServiceManager.getDefault();
    }
}
