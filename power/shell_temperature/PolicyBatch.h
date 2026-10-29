// SPDX-License-Identifier: Apache-2.0
#pragma once
#include <cstdint>
#include <optional>
#include <string>

namespace honor::ldy_an00 {
// One cycle's successful raw values. Missing fields are explicit, never zero/default25.
// Times describe userspace retrieval span, not ADC conversion times.
struct PolicyBatch {
    uint32_t sequence = 0;
    int64_t startedMilliseconds = -1;
    int64_t completedMilliseconds = -1;
    std::optional<bool> anyReporterInvalid;
    std::optional<int32_t> back;
    std::optional<int32_t> usb;
    std::optional<int32_t> battery;
};
std::optional<std::string> EncodePolicyBatch(const PolicyBatch& batch);
}  // namespace honor::ldy_an00
