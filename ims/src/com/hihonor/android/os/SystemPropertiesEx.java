// SPDX-License-Identifier: Apache-2.0
package com.hihonor.android.os;

/** Exact stock getter semantics, scoped to the IMS application. */
public final class SystemPropertiesEx {
    private SystemPropertiesEx() {}
    public static boolean getBoolean(String key, boolean defaultValue) {
        return android.os.SystemProperties.getBoolean(key, defaultValue);
    }
}
