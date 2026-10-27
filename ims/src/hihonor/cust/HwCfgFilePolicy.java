// SPDX-License-Identifier: Apache-2.0
package hihonor.cust;

/** No Honor cloud-config provider is installed; preserve the supplied default. */
public final class HwCfgFilePolicy {
    private HwCfgFilePolicy() {}
    public static boolean getCloudCfgBoolValue(String directory, String key,
            boolean defaultValue) {
        return defaultValue;
    }
}
