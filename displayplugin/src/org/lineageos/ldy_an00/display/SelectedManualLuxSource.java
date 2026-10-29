package org.lineageos.ldy_an00.display;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.SystemClock;
import java.util.List;
import java.util.function.Consumer;

/** Selected hub/normal nonfold lux source. All methods run on the supplied existing worker. */
final class SelectedManualLuxSource {
    private final SensorManager sensors;
    private final Handler handler;
    private final Consumer<Float> output;
    private final StockManualLuxFusion fusion;
    private final CameraOsTorchSource torch;
    private boolean active;
    private boolean backEnabled;
    private long frontEnableNs, backEnableNs;
    private long enableUptime;
    private int frontWarm, backWarm, fusedWarm;
    private long frontFirstTime, backFirstTime, forecastTime;
    private boolean frontFirst, backFirst, forecast;
    private boolean frontUpdated, backUpdated;
    private int frontAverage;
    private float backFiltered;
    private final Samples front = new Samples();
    private final Samples back = new Samples();
    private final int[] timers = {-1, -1, -1};
    private int timer = -1;
    private long timerAt;
    private int backTimeout;
    private int backBypass;
    private final Runnable tick = () -> message(0);
    private final Runnable frontSend = () -> message(1);
    private final Runnable backSend = () -> message(2);
    private final Runnable fusedSend = () -> message(3);

    SelectedManualLuxSource(SensorManager manager, Handler worker,
            StockManualLuxFusion.Parameters parameters, Consumer<Float> onHistory) {
        sensors = manager;
        handler = worker;
        output = onHistory;
        fusion = new StockManualLuxFusion(parameters);
        // Torch callback is retained for process lifetime, matching the stock singleton.
        torch = new CameraOsTorchSource(worker, () -> {});
    }

    void setActive(boolean enabled) {
        if (enabled == active) return;
        if (!enabled) {
            active = false;
            sensors.unregisterListener(frontListener);
            sensors.unregisterListener(backListener);
            clear();
            output.accept(-1f);
            return;
        }
        clear();
        enableUptime = SystemClock.uptimeMillis();
        Sensor frontSensor = sensors.getDefaultSensor(Sensor.TYPE_LIGHT);
        List<Sensor> backSensors = sensors.getSensorList(0x10012);
        Sensor backSensor = backSensors.size() == 1 ? backSensors.get(0) : null;
        active = true;
        frontEnableNs = SystemClock.elapsedRealtimeNanos();
        if (frontSensor == null || !sensors.registerListener(frontListener, frontSensor,
                300000, handler)) {
            // Registration failure is explicitly unavailable, not a fabricated sensor sample.
            setActive(false);
            return;
        }
        backEnableNs = SystemClock.elapsedRealtimeNanos();
        backEnabled = backSensor != null && sensors.registerListener(backListener, backSensor,
                300000, handler);
        if (backBypass > 0) backBypass--;
        torch.ensureConnection();
        trigger(3);
    }

    private void clear() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(frontSend);
        handler.removeCallbacks(backSend);
        handler.removeCallbacks(fusedSend);
        front.clear();
        back.clear();
        frontWarm = backWarm = fusedWarm = 0;
        frontUpdated = backUpdated = frontFirst = backFirst = forecast = false;
        frontAverage = 0;
        backFiltered = 0;
        frontFirstTime = backFirstTime = forecastTime = -1;
        backEnabled = false;
        timer = timers[0] = timers[1] = timers[2] = -1;
    }

    private final SensorEventListener frontListener = new SensorEventListener() {
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        @Override public void onSensorChanged(SensorEvent event) {
            if (!active || event.values.length < 2) return;
            long now = SystemClock.uptimeMillis();
            int lux = StockManualLuxFusion.frontLux(event.values[0]);
            if (frontWarm < 2) {
                if (event.timestamp < frontEnableNs) {
                    forecast = true;
                    forecastTime = now;
                    front.add(lux);
                    trigger(1);
                    return;
                }
                if (frontWarm == 0) {
                    frontFirst = true;
                    forecast = false;
                    frontFirstTime = now;
                } else {
                    if (now - frontFirstTime < 50) return;
                    frontFirst = false;
                }
                front.add(lux);
                frontWarm++;
                trigger(1);
            } else {
                front.add(lux);
            }
        }
    };

    private final SensorEventListener backListener = new SensorEventListener() {
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        @Override public void onSensorChanged(SensorEvent event) {
            if (!active || !backEnabled || event.values.length < 2) return;
            long now = SystemClock.uptimeMillis();
            int lux = StockManualLuxFusion.backLux(event.values[0]);
            if (backWarm < 2) {
                if (event.timestamp < backEnableNs) return;
                if (backWarm == 0) {
                    backFirst = true;
                    backFirstTime = now;
                } else {
                    if (now - backFirstTime < 50) return;
                    backFirst = false;
                }
                back.add(lux);
                backWarm++;
                trigger(2);
            } else {
                back.add(lux);
            }
        }
    };

    private Runnable task(int what) {
        return what == 1 ? frontSend : what == 2 ? backSend : fusedSend;
    }

    private void trigger(int what) {
        Runnable task = task(what);
        handler.removeCallbacks(task);
        handler.post(task);
    }

    private void message(int what) {
        if (!active) return;
        if (what == 0) {
            int elapsed = timer;
            for (int i = 0; i < 3; i++) {
                if (timers[i] != -1 && Math.max(0, timers[i] - elapsed) == 0) send(i);
            }
            timer = -1;
            for (int value : timers) {
                if (value != -1 && (timer == -1 || value < timer)) timer = value;
            }
            if (timer != -1) queueTick(timer);
        } else {
            send(what - 1);
            int value = timers[what - 1];
            if (value != -1 && (timer == -1
                    || timer - (int) (SystemClock.uptimeMillis() - timerAt) > value)) {
                queueTick(value);
            }
        }
    }

    private void queueTick(int delay) {
        timer = delay;
        timerAt = SystemClock.uptimeMillis();
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, delay);
    }

    private void send(int channel) {
        if (channel == 0 || channel == 1) {
            Samples samples = channel == 0 ? front : back;
            Integer value = samples.take();
            if (value != null) {
                if (channel == 0) {
                    frontAverage = value;
                    frontUpdated = true;
                } else {
                    backFiltered = value;
                    backUpdated = true;
                }
                if (fusedWarm < 2) {
                    if (frontUpdated && backUpdated) fusedWarm++;
                    trigger(3);
                }
            }
        } else {
            sendFused();
        }
        int warm = channel == 0 ? frontWarm : channel == 1 ? backWarm : fusedWarm;
        boolean enabled = channel != 1 || backEnabled;
        timers[channel] = enabled && warm >= 2 ? 300 : -1;
    }

    private void sendFused() {
        if (!frontUpdated) return;
        if (backBypass <= 0 && !backUpdated && !frontFirst && !forecast) {
            if (++backTimeout >= 2) backBypass = 3; else return;
        }
        backTimeout = 0;
        StockManualLuxFusion.Result result = fusion.fuse(frontAverage, backFiltered, backEnabled,
                torch.connected(), torch.torchOn());
        backFiltered = result.backFiltered();
        if (forecast && backFirstTime > forecastTime
                && backFirstTime < enableUptime + 200) return;
        output.accept((float) result.lux());
    }

    /** Exact integer batch average; no-event send retains the last successful average. */
    static final class Samples {
        private int sum, count;
        private int last = -1;
        void add(int lux) {
            if (lux >= 0) { sum += lux; count++; }
        }
        Integer take() {
            if (count != 0) {
                int average = sum / count;
                if (average >= 0) last = average;
            }
            sum = count = 0;
            return last >= 0 ? last : null;
        }
        void clear() { sum = count = 0; last = -1; }
    }
}
