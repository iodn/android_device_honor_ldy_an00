// SPDX-License-Identifier: Apache-2.0
package com.hihonor.android.telephony;

/** Exact stock slot-to-subscription fallback, scoped to the IMS application. */
public final class SubscriptionManagerEx {
    private SubscriptionManagerEx() {}
    public static int getSubIdUsingSlotId(int slotId) {
        int[] subIds = android.telephony.SubscriptionManager.getSubId(slotId);
        return subIds != null && subIds.length > 0 ? subIds[0] : -1;
    }
}
