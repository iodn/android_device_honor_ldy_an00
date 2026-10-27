// SPDX-License-Identifier: Apache-2.0
package com.android.internal.telephony;

/** Stock's default absent diagnostic implementation, with no added transport. */
public final class DefaultHwChrServiceManager implements HwChrServiceManager {
    private static final HwChrServiceManager INSTANCE = new DefaultHwChrServiceManager();

    private DefaultHwChrServiceManager() {}

    public static HwChrServiceManager getDefault() {
        return INSTANCE;
    }
}
