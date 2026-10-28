// Copyright (C) 2026 The LineageOS Project
// SPDX-License-Identifier: Apache-2.0
#include "Estimator.h"

#include <algorithm>
#include <cstdlib>

#include "Calibration.h"

namespace honor::ldy_an00 {
namespace {

ShellInputs Normalize(const ShellInputs& inputs) {
    int64_t total = 0;
    int count = 0;
    for (int32_t value : inputs) {
        if (value >= kNtcMinimum && value <= kNtcMaximum) {
            total += value;
            ++count;
        }
    }
    const int32_t fallback = count ? static_cast<int32_t>(total / count) : 25000;
    ShellInputs result = inputs;
    for (auto& value : result) {
        if (value < kNtcMinimum || value > kNtcMaximum) value = fallback;
    }
    return result;
}

}  // namespace

ShellTemperatures ShellTemperatureEstimator::Sample(const ShellInputs& inputs, int64_t seconds) {
    const ShellInputs normalized = Normalize(inputs);
    ShellTemperatures result{};
    for (size_t index = 0; index < states_.size(); ++index) {
        auto& state = states_[index];
        const auto& calibration = kCalibration[index];
        const int samples = calibration.samples;
        const int previous = (state.head + samples - 1) % samples;
        if (!state.initialized) {
            for (int slot = 0; slot < samples; ++slot) {
                state.history[slot] = {seconds, normalized};
            }
            // Stock seeds this previous slot with raw readings before filtering
            // the first current slot. Preserve that ordering.
            state.history[previous] = {seconds, inputs};
            state.initialized = true;
        }
        if (std::abs(seconds - state.history[previous].seconds) > 2 * kIntervalSeconds) {
            for (int slot = 0; slot < samples; ++slot) {
                state.history[slot] = {seconds, normalized};
            }
        }
        ShellInputs current = normalized;
        for (size_t sensor = 0; sensor < current.size(); ++sensor) {
            const int32_t old = state.history[previous].inputs[sensor];
            if (std::abs(static_cast<int64_t>(current[sensor]) - old) / kIntervalSeconds >
                kNtcStep) {
                current[sensor] = old;
            }
        }
        state.history[state.head] = {seconds, current};
        int64_t weighted = 0;
        int32_t maximum = -40000;
        for (int lag = 0; lag < samples; ++lag) {
            const auto& row = state.history[(state.head + samples - lag) % samples].inputs;
            for (size_t sensor = 0; sensor < row.size(); ++sensor) {
                weighted += static_cast<int64_t>(row[sensor]) *
                            calibration.coefficients[sensor][lag];
                maximum = std::max(maximum, row[sensor]);
            }
        }
        const int64_t estimate = weighted / 100000 + calibration.bias;
        const int32_t candidate = estimate > maximum ? maximum :
                                  estimate < -40000 ? state.output :
                                  static_cast<int32_t>(estimate);
        if (candidate != -40000) {
            if (!state.output_valid) {
                state.output = candidate;
                state.output_valid = true;
            } else {
                const int64_t elapsed = seconds - last_sample_seconds_;
                const int64_t delta = static_cast<int64_t>(candidate) - state.output;
                if (std::abs(delta) < kMaximumStep * elapsed) {
                    const int64_t step = kNormalStep * elapsed;
                    state.output += static_cast<int32_t>(std::clamp(delta, -step, step));
                }
            }
        }
        result[index] = state.output;
        state.head = (state.head + 1) % samples;
    }
    last_sample_seconds_ = seconds;
    return result;
}

}  // namespace honor::ldy_an00
