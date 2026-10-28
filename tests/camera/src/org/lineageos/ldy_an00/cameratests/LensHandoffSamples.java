// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.cameratests;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Bounded matching of actual output-buffer timestamps to completed capture results. */
final class LensHandoffSamples {
    private static final int PENDING_LIMIT = 64;
    static final class Sample {
        final long timestamp;
        final long frame;
        final Float zoom;
        final String physicalId;
        Sample(long timestamp, long frame, Float zoom, String physicalId) {
            this.timestamp = timestamp;
            this.frame = frame;
            this.zoom = zoom;
            this.physicalId = physicalId;
        }
    }
    private final int wanted;
    private Long firstTimestamp;
    private final Set<Long> images = new LinkedHashSet<>();
    private final LinkedHashMap<Long, Sample> results = new LinkedHashMap<>();
    private final LinkedHashMap<Long, Sample> complete = new LinkedHashMap<>();

    LensHandoffSamples(int wanted) {
        if (wanted < 1 || wanted > PENDING_LIMIT) throw new IllegalArgumentException("sample count");
        this.wanted = wanted;
    }

    static List<Float> zoomPlan(float min, float max) {
        if (!Float.isFinite(min) || !Float.isFinite(max) || min <= 0 || max < min) {
            throw new IllegalArgumentException("Invalid advertised zoom range");
        }
        TreeSet<Float> values = new TreeSet<>();
        values.add(min);
        values.add(max);
        for (float value : new float[] {1, 1.5f, 2, 3, 4, 5, 8}) {
            if (value >= min && value <= max) values.add(value);
        }
        return new ArrayList<>(values);
    }

    synchronized boolean afterSettling(long timestamp, long settleNs) {
        if (timestamp <= 0) return false;
        if (firstTimestamp == null) firstTimestamp = timestamp;
        return timestamp - firstTimestamp >= settleNs;
    }

    synchronized boolean image(long timestamp) {
        if (timestamp <= 0 || complete.size() >= wanted) return isComplete();
        images.add(timestamp);
        trim(images);
        match(timestamp);
        return isComplete();
    }

    synchronized boolean result(Sample sample) {
        if (sample.timestamp <= 0 || complete.size() >= wanted
                || complete.containsKey(sample.timestamp)) return isComplete();
        results.put(sample.timestamp, sample);
        while (results.size() > PENDING_LIMIT) results.remove(results.keySet().iterator().next());
        match(sample.timestamp);
        return isComplete();
    }

    private void match(long timestamp) {
        Sample result = results.get(timestamp);
        if (result != null && images.remove(timestamp)) {
            complete.put(timestamp, result);
            results.remove(timestamp);
        }
    }

    private static void trim(Set<Long> values) {
        while (values.size() > PENDING_LIMIT) values.remove(values.iterator().next());
    }

    synchronized boolean isComplete() { return complete.size() >= wanted; }
    synchronized List<Sample> samples() { return new ArrayList<>(complete.values()); }
}
