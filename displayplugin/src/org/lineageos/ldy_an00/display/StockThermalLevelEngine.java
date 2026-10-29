// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Recovered K0/I0 math for explicitly selected stock tables without rise-rate predicates.
 * No sensor acquisition, active-config selection or freshness policy is implemented here.
 */
final class StockThermalLevelEngine {
    public record Row(int level, int enter, int clear) {}
    public record SensorTable(int sensorId, List<Row> rows) {
        public SensorTable { rows = List.copyOf(rows); }
    }
    public record Cluster(String name, String scene, List<SensorTable> sensors) {
        public Cluster { sensors = List.copyOf(sensors); }
    }
    public record Sample(int index, int level, int temperature, int enter, int sensorId) {}
    public record Result(String name, String scene, List<Sample> sensors,
                         Sample representative) {
        public Result { sensors = List.copyOf(sensors); }
    }

    private List<Result> history = List.of();

    /** Null/empty input retains history and returns null, matching K0.g. */
    public List<Result> process(List<Cluster> configs, Map<Integer, Integer> readings) {
        if (readings == null || readings.isEmpty()) return null;
        List<Accumulated> next = new ArrayList<>();
        for (Cluster cluster : configs) {
            Result prior = find(history, cluster.name(), cluster.scene());
            for (SensorTable table : cluster.sensors()) {
                Integer temperature = readings.get(table.sensorId());
                if (temperature == null) continue;
                Sample previous = null;
                if (prior != null) {
                    for (Sample sample : prior.sensors()) {
                        if (sample.sensorId() == table.sensorId()) previous = sample;
                    }
                }
                int index = selectIndex(table.rows(), previous, temperature);
                if (index < 0) continue;
                Row row = table.rows().get(index);
                Accumulated target = null;
                for (Accumulated item : next) {
                    if (Objects.equals(item.name, cluster.name())
                            && Objects.equals(item.scene, cluster.scene())) {
                        target = item;
                        break;
                    }
                }
                if (target == null) {
                    target = new Accumulated(cluster.name(), cluster.scene());
                    next.add(target);
                }
                target.add(new Sample(index, row.level(), temperature,
                        row.enter(), table.sensorId()));
            }
        }
        List<Result> results = new ArrayList<>();
        for (Accumulated item : next) results.add(item.result());
        history = List.copyOf(results);
        return history;
    }

    private static Result find(List<Result> list, String name, String scene) {
        for (Result item : list) {
            if (Objects.equals(item.name(), name) && Objects.equals(item.scene(), scene)) {
                return item;
            }
        }
        return null;
    }

    private static int selectIndex(List<Row> rows, Sample prior, int temperature) {
        int start = prior == null ? 0 : prior.index();
        if (start < 0 || start >= rows.size()) return -1;
        boolean allWarm = true;
        boolean allCold = true;
        for (Row row : rows) {
            allWarm &= row.clear() <= row.enter();
            allCold &= row.clear() > row.enter();
        }
        if (!allWarm && !allCold) return -1;
        int entered = -1;
        for (int i = start; i < rows.size(); ++i) {
            Row row = rows.get(i);
            if (allWarm ? temperature < row.enter() : temperature > row.enter()) break;
            entered = i;
        }
        if (entered >= start || prior == null) return entered;
        int retained = start;
        for (int i = start; i >= 0; --i) {
            Row row = rows.get(i);
            if (allWarm ? temperature > row.clear() : temperature < row.clear()) break;
            retained = i - 1;
        }
        return retained;
    }

    private static final class Accumulated {
        final String name;
        final String scene;
        final Map<Integer, Sample> samples = new LinkedHashMap<>();
        Integer selected;

        Accumulated(String name, String scene) {
            this.name = name;
            this.scene = scene;
        }

        void add(Sample sample) {
            Sample current = selected == null ? null : samples.get(selected);
            if (current == null || sample.level() > current.level()
                    || (sample.level() == current.level()
                    && sample.temperature() > current.temperature())) {
                selected = sample.sensorId();
            }
            samples.put(sample.sensorId(), sample);
        }

        Result result() {
            return new Result(name, scene, new ArrayList<>(samples.values()),
                    samples.get(selected));
        }
    }
}
