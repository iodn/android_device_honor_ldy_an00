// SPDX-License-Identifier: Apache-2.0
#include "ReporterValidity.h"
namespace honor::ldy_an00 {
std::optional<bool> AnyReporterSensorInvalid(
        const ReporterValidityInputs& in, const AuxiliaryReadings& aux) {
    struct Entry { std::optional<int32_t> raw; int minimum; int maximum; };
    // Exact enabled ID order. ID24 is proven always valid under pinned debug0
    // Ambient lifecycle; ID35 missing-fd native cache starts0 and remains0.
    const std::array<Entry, 12> entries{{
        {aux.battery, -20, 75},         // 9
        {in.ntc[3], -20, 80},           // 14
        {in.shell[2], 0, 80},           // 22
        {in.shell[0], 0, 80},           // 28
        {in.shell[1], 0, 80},           // 29
        {aux.values[0], -30, 80},       // 30
        {aux.values[1], 0, 80},         // 53
        {in.ntc[2], -30, 80},           // 55
        {aux.values[2], -30, 80},       // 58
        {aux.values[3], 0, 80},       // 59
        {in.ntc[4], 0, 80},           // 60
        {in.ntc[0], -30, 80},           // 64
    }};
    bool unknown = !aux.pa2DefinitelyAbsent;
    for (const auto& entry : entries) {
        if (!entry.raw) {
            unknown = true;
            continue;
        }
        const int whole = static_cast<int>(static_cast<double>(*entry.raw) / 1000.0);
        if (whole < entry.minimum || whole > entry.maximum) return true;
    }
    return unknown ? std::nullopt : std::optional<bool>(false);
}
}  // namespace honor::ldy_an00
