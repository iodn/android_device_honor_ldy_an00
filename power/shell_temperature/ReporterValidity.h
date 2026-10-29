// SPDX-License-Identifier: Apache-2.0
#pragma once
#include <array>
#include <cstdint>
#include <optional>

namespace honor::ldy_an00 {
struct ReporterValidityInputs {
    std::array<std::optional<int32_t>, 5> ntc;
    std::array<std::optional<int32_t>, 3> shell;
};
struct AuxiliaryReadings {
    std::optional<int32_t> battery;
    // Exact source order: usb_port, mtkts_chargerntc, ap_ntc, ltepa_ntc.
    std::array<std::optional<int32_t>, 4> values;
    bool pa2DefinitelyAbsent = false;
};
// Pinned LDY normal-debug0 reporter range OR: true dominates unknown.
std::optional<bool> AnyReporterSensorInvalid(
        const ReporterValidityInputs& inputs, const AuxiliaryReadings& auxiliary);
}  // namespace honor::ldy_an00
