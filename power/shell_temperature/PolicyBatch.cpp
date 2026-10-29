// SPDX-License-Identifier: Apache-2.0
#include "PolicyBatch.h"
namespace honor::ldy_an00 {
std::optional<std::string> EncodePolicyBatch(const PolicyBatch& batch) {
    if (batch.startedMilliseconds < 0
            || batch.completedMilliseconds < batch.startedMilliseconds) return std::nullopt;
    const auto field = [](std::optional<int32_t> value) {
        return value ? std::to_string(*value) : std::string("x");
    };
    const char* invalid = !batch.anyReporterInvalid ? "x" : *batch.anyReporterInvalid ? "1" : "0";
    return "4:" + std::to_string(batch.sequence) + ":"
            + std::to_string(batch.startedMilliseconds) + ":"
            + std::to_string(batch.completedMilliseconds) + ":" + invalid + ":" + field(batch.back) + ":"
            + field(batch.usb) + ":" + field(batch.battery);
}
}  // namespace honor::ldy_an00
