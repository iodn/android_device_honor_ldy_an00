// SPDX-License-Identifier: Apache-2.0
#pragma once

#include <map>
#include <optional>
#include <string>
#include <vector>

namespace honor::ldy_an00 {

using ThermalZonePaths = std::map<std::string, std::vector<std::string>>;

// Absence and uniqueness are meaningful only after a complete, readable scan.
std::optional<ThermalZonePaths> DiscoverThermalZones(
        const char* root = "/sys/devices/virtual/thermal");

}  // namespace honor::ldy_an00
