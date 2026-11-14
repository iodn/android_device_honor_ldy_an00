// SPDX-License-Identifier: Apache-2.0
#include "SensorDiscovery.h"

#include <cerrno>
#include <dirent.h>
#include <fstream>
#include <utility>

namespace honor::ldy_an00 {

std::optional<ThermalZonePaths> DiscoverThermalZones(const char* root) {
    DIR* directory = opendir(root);
    if (!directory) return std::nullopt;
    ThermalZonePaths result;
    bool complete = true;
    for (;;) {
        errno = 0;
        const auto* entry = readdir(directory);
        if (!entry) {
            complete &= errno == 0;
            break;
        }
        const std::string zone = entry->d_name;
        if (zone.compare(0, 12, "thermal_zone") != 0) continue;
        const std::string prefix = std::string(root) + "/" + zone;
        std::ifstream input(prefix + "/type");
        char name[32];
        if (!input.getline(name, sizeof(name)) || name[0] == '\0') {
            complete = false;
            continue;
        }
        result[name].push_back(prefix + "/temp");
    }
    complete &= closedir(directory) == 0;
    return complete ? std::optional<ThermalZonePaths>(std::move(result)) : std::nullopt;
}

}  // namespace honor::ldy_an00
