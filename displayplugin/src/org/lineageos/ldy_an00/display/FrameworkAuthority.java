// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;

/** Absence is qualified only through an actual framework/services parent, without initialization. */
final class FrameworkAuthority {
    static boolean allAbsent(ClassLoader frameworkParent,String... names) {
        if (frameworkParent == null) return false; // Bootstrap-only cannot prove services absence.
        for (String name : names) {
            try {
                Class.forName(name,false,frameworkParent);
                return false;
            } catch (ClassNotFoundException absent) {
                // This exact authority is absent; every requested authority must be absent.
            } catch (LinkageError unavailable) { return false; }
        }
        return true;
    }
}
