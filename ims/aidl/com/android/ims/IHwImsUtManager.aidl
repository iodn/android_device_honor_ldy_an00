// SPDX-License-Identifier: Apache-2.0
package com.android.ims;

// Wire-contract draft only. No service publication or permission claim.
interface IHwImsUtManager {
    @EnforcePermission("MODIFY_PHONE_STATE")
    int updateCallForwardUncondTimer(int phoneId, int startHour, int startMinute,
            int endHour, int endMinute, int action, int condition, @nullable String number);
    @EnforcePermission("MODIFY_PHONE_STATE")
    int queryCallForwardForServiceClass(int phoneId, int reason,
            @nullable String number, int serviceClass);
    @EnforcePermission("MODIFY_PHONE_STATE")
    void processECT(int phoneId);
}
