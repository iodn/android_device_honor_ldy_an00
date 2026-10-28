// Copyright (C) 2026 The LineageOS Project
// SPDX-License-Identifier: Apache-2.0
#pragma once

#include <array>
#include <cstdint>

namespace honor::ldy_an00 {

using ShellInputs = std::array<int32_t, 5>;
using ShellTemperatures = std::array<int32_t, 3>;

// LDY calibration only. The caller supplies CLOCK_BOOTTIME whole seconds.
// This class performs no I/O and does not change thermal or charging controls.
class ShellTemperatureEstimator {
  public:
    ShellTemperatures Sample(const ShellInputs& inputs, int64_t seconds);

  private:
    struct History {
        int64_t seconds = 0;
        ShellInputs inputs{};
    };
    struct State {
        std::array<History, 8> history{};
        int head = 0;
        bool initialized = false;
        bool output_valid = false;
        int32_t output = 0;
    };
    std::array<State, 3> states_{};
    int64_t last_sample_seconds_ = 0;
};

}  // namespace honor::ldy_an00
