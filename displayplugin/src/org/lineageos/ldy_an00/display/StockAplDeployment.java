// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime admission for the HOST-VERIFIED fixed vendor cohort; not six-ELF attestation.
 * First-install full-super verification and release/OTA vendor-retention checks are prerequisites.
 * All six ELF digests are HOST preflight inputs. Runtime vendor ELF reads violate platform policy.
 * Generic MTK build properties alone neither identify Honor113 nor prove unchanged vendor files.
 * Runs once on the existing worker. No native callback owner query/atomic lease exists.
 */
final class StockAplDeployment {
    static final Map<String,String> COHORT=Map.ofEntries(
            Map.entry("ro.vendor.build.fingerprint","alps/vext_k6993v1_64/k6993v1_64:16/BP2A.250605.031.A3/mp1tc36spV1:user/release-keys"),
            Map.entry("ro.vendor.build.version.incremental","mp1tc36spV1"),
            Map.entry("ro.vendor.build.security_patch","2025-12-01"),
            Map.entry("ro.vendor.build.date.utc","1767002547"),
            Map.entry("ro.vendor.build.id","BP2A.250605.031.A3"),
            Map.entry("ro.vendor.build.version.release","16"),
            Map.entry("ro.product.vendor.device","k6993v1_64"));
    static final Set<String> ABSENT=Set.of("/system/bin/displayengineserver",
            "/system/lib64/libdisplayengineservice.so","/system/lib/libdisplayengineservice.so",
            "/system_ext/bin/displayengineserver","/system_ext/lib64/libdisplayengineservice.so",
            "/system_ext/lib/libdisplayengineservice.so");
    private static final AtomicBoolean CLAIMED=new AtomicBoolean();
    static boolean qualifyAndClaim(boolean optedIn,boolean legacyAuthorityAbsent,
            Map<String,String> cohort) {
        return qualifyAndClaim(Path.of("/"),optedIn,legacyAuthorityAbsent,cohort);
    }
    // Host tests execute the same file/hash/claim admission under a fixture filesystem root.
    static boolean qualifyAndClaim(Path root,boolean optedIn,boolean legacyAuthorityAbsent,
            Map<String,String> cohort) {
        if(!optedIn || !legacyAuthorityAbsent || !COHORT.equals(cohort))return false;
        try {
            for(String name:ABSENT) {
                try { Files.readAttributes(root.resolve(name.substring(1)),BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);return false; }
                catch(NoSuchFileException absent) { /* Missing entry, never permission-denied. */ }
            }
        } catch(IOException | SecurityException unavailable) { return false; }
        // Keep the process claim even after an uncertain registration transport outcome.
        return CLAIMED.compareAndSet(false,true);
    }
}
