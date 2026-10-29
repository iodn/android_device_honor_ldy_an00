// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Serial worker coordinator. Sensor epochs advance independently of scene readiness. */
final class ThermalPolicyCoordinator {
    enum Status { NOT_READY_SENSOR, NOT_READY_CONTEXT, READY }
    record Result(Status status, long epoch, StockLcdCapAggregator.Dispatch dispatch,
            ThermalSceneResolver.Decision scene,
            List<StockLcdCapAggregator.ControlDispatch> controls) {}
    private static final Set<String> OPTIONAL = Set.of(
            "lcd_manual","lcd_outdoor","lcd_outdoor_manual","lcd_outdoor_apl");
    private final ThermalMetadataLoader.Metadata mMetadata;
    private final ThermalSceneResolver mScenes;
    private final ThermalReporterGeneration mReports = new ThermalReporterGeneration();
    private final StockLcdCapAggregator.Sender mSender = new StockLcdCapAggregator.Sender();
    private final StockLcdCapAggregator.ControlSender mControls =
            new StockLcdCapAggregator.ControlSender();
    private StockThermalLevelEngine mEngine;
    private List<StockThermalLevelEngine.Result> mLevels;
    private long mEpoch = -1;

    ThermalPolicyCoordinator(ThermalMetadataLoader.Metadata metadata) {
        mMetadata = metadata;
        // Stock constructor H/I/J are explicitly known-null for this port-owned generation.
        mScenes = new ThermalSceneResolver(metadata.scenePolicy(
                new ThermalMetadataLoader.ContextPackages(null,null,null)));
    }

    Result observe(ThermalDisplayBatch batch, ThermalSceneResolver.Snapshot snapshot,
            Integer batteryPercent, boolean ordinarySecondaryQualifiedEmpty) {
        var report = mReports.observe(batch);
        if (report.status() == ThermalReporterGeneration.Status.NOT_READY) {
            mEngine = null; mLevels = null; mEpoch = report.epoch();
            return new Result(Status.NOT_READY_SENSOR,mEpoch,null,null,List.of());
        }
        if (report.status() == ThermalReporterGeneration.Status.ADVANCED) {
            if (mEngine == null || mEpoch != report.epoch()) {
                mEngine = new StockThermalLevelEngine(); mEpoch = report.epoch();
            }
            var readings = new java.util.LinkedHashMap<Integer,Integer>();
            for (var sensor : report.reports()) readings.put(sensor.id(),sensor.milliCelsius());
            // One complete history update, not separate warm/cold engine invocations.
            mLevels = mEngine.process(mMetadata.levelTables(),readings);
        }
        if (mLevels == null) return new Result(Status.NOT_READY_SENSOR,mEpoch,null,null,List.of());
        var scene = mScenes.resolve(snapshot);
        if (scene.outcome() != ThermalSceneResolver.Outcome.READY
                || !scene.warmPresent() || !scene.coldPresent() || !scene.safePresent()
                || !ordinarySecondaryQualifiedEmpty || batteryPercent == null
                || batteryPercent < 0 || batteryPercent > 100) {
            return new Result(Status.NOT_READY_CONTEXT,mEpoch,null,scene,List.of());
        }
        var policies = new ArrayList<StockLcdCapAggregator.Policy>();
        add(policies,"warm_policy",scene.warmConfig(),scene.effectiveScene());
        add(policies,"cold_policy",scene.coldConfig(),scene.effectiveScene());
        add(policies,"safe_policy",scene.safeConfig(),scene.safeConfig());
        // Pinned nonfold/duo predicates prove ordinary secondary empty. Game-secondary LCD
        // is restored by stock and must not introduce another LCD pass.
        var aggregate = StockLcdCapAggregator.evaluate(policies,List.of(),mLevels,batteryPercent);
        return new Result(Status.READY,mEpoch,mSender.offer(aggregate,OPTIONAL),scene,mControls.offer(aggregate));
    }

    private void add(List<StockLcdCapAggregator.Policy> target, String feature,
            String config, String requestedScene) {
        var policy = mMetadata.policyConfigs().get(feature).get(config);
        if (policy == null) throw new IllegalStateException("Missing qualified LCD policy");
        // Requested scene owns level-exclusion and winner metadata even when items fall back.
        target.add(new StockLcdCapAggregator.Policy(requestedScene,policy.items()));
    }
}
