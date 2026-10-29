// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/** Serial worker publisher. Logical intents survive context holds and unknown storage outcomes. */
final class ThermalPolicyPublisher {
    private static final class Entry {
        final StockLcdPolicy policy;
        long lcdRevision, outdoorRevision;
        boolean registered;
        Entry(StockLcdPolicy policy) { this.policy = policy; }
    }
    private final StockLcdPanel mPanel;
    private final BiConsumer<String,StockLcdPolicy> mStore;
    private final Map<String,Entry> mEntries = new HashMap<>();
    private StockLcdCapAggregator.Dispatch mLcd;
    private Integer mOutdoor;
    private long mLcdRevision, mOutdoorRevision;
    private Integer mAplBatteryLevel;
    private Float mManualHistoryLux;
    private boolean mAplInvalidated;
    private StockLcdPolicy.AplControlOwner mAplControlOwner;

    void packedApl(long generation,int packed,BooleanSupplier lifetime) {
        if(mEntries.isEmpty())mAplInvalidated=true; // No cached event reinterpretation on later bind.
        for(Entry entry:mEntries.values())entry.policy.onPackedApl(generation,packed,lifetime);
    }
    void invalidateApl() {
        mAplInvalidated=true;
        for(Entry entry:mEntries.values())entry.policy.invalidateApl();
    }
    void aplBatteryLevel(int level) {
        mAplBatteryLevel=level;
        for(Entry entry:mEntries.values())entry.policy.offerAplBatteryLevel(level);
    }
    void manualHistoryLux(Float lux) {
        mManualHistoryLux=lux;
        for(Entry entry:mEntries.values())entry.policy.offerManualHistoryLux(lux);
    }
    void aplControlOwner(StockLcdPolicy.AplControlOwner owner) {
        mAplControlOwner=owner;
        for(Entry entry:mEntries.values())entry.policy.setAplControlOwner(owner);
    }

    ThermalPolicyPublisher(StockLcdPanel panel,BiConsumer<String,StockLcdPolicy> store) {
        mPanel = java.util.Objects.requireNonNull(panel);
        mStore = java.util.Objects.requireNonNull(store);
    }
    void offer(StockLcdCapAggregator.Dispatch lcd,StockLcdPolicy.StockContext context,
            Integer outdoor,Set<String> displays) {
        // Cache before any context or transport gate. Null is dedup, not a new command.
        if (lcd != null) { mLcd = lcd; ++mLcdRevision; }
        if (outdoor != null && (outdoor == 0 || outdoor == 1)) {
            mOutdoor = outdoor; ++mOutdoorRevision;
        }
        if (context == null) return;
        for (String id : displays) {
            Entry entry = mEntries.get(id);
            if(entry==null) {
                entry=new Entry(new StockLcdPolicy(id,mPanel));
                entry.policy.setAplControlOwner(mAplControlOwner);
                entry.policy.offerManualHistoryLux(mManualHistoryLux);
                if(mAplInvalidated)entry.policy.invalidateApl();
                // Sticky raw level is source state; a native ratio event is never replayed.
                if(mAplBatteryLevel!=null)entry.policy.offerAplBatteryLevel(mAplBatteryLevel);
                mEntries.put(id,entry);
            }
            boolean lcdNew = entry.lcdRevision < mLcdRevision;
            boolean outdoorNew = entry.outdoorRevision < mOutdoorRevision;
            // Qualified source API: five caps, nonnull context, Handler-backed nonthrowing post.
            entry.policy.offerPolicy(lcdNew ? mLcd : null,context,outdoorNew ? mOutdoor : null);
            entry.lcdRevision = mLcdRevision;
            entry.outdoorRevision = mOutdoorRevision;
            if (!entry.registered) {
                // updateValue has void API and can throw after storing, while notifying listeners.
                // Keep the SAME pending factory; only normal return acknowledges registration.
                mStore.accept(id,entry.policy);
                entry.registered = true;
            }
        }
        for (String id : new HashSet<>(mEntries.keySet())) {
            if (!displays.contains(id)) {
                mStore.accept(id,null);
                mEntries.remove(id); // Unknown clear completion stays pending for same retry.
            }
        }
    }
    int pendingRegistrations() {
        int count=0;
        for (Entry entry : mEntries.values()) if (!entry.registered) ++count;
        return count;
    }
}
