// SPDX-License-Identifier: Apache-2.0
package com.hihonor.android.cust;

/** No Honor carrier-policy provider is installed; preserve its measured null fallback. */
public final class HwCfgFilePolicy {
    private HwCfgFilePolicy() {}
    public static <T> T getValue(String key, int slotId, Class<T> valueClass) {
        return null;
    }
}
