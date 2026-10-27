// SPDX-License-Identifier: Apache-2.0
package com.android.internal.telephony;

/** App-local absent Honor diagnostic transport; not call or capability policy. */
public interface HwChrServiceManager {
    // Exact stock default implementation: return-void (DEX 2d71a0).
    default void reportCallException(String appName, int subId, int callType,
            String params) {}
}
