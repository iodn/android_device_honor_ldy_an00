// SPDX-License-Identifier: Apache-2.0
#include "../SensorDiscovery.h"

#include <cassert>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <iostream>

namespace fs = std::filesystem;

int main() {
    char pattern[] = "/tmp/ldy-thermal-discovery-XXXXXX";
    const char* directory = mkdtemp(pattern);
    assert(directory);
    const fs::path root(directory);
    const auto add = [&](const std::string& zone, const std::string& type) {
        fs::create_directory(root / zone);
        std::ofstream(root / zone / "type") << type;
        std::ofstream(root / zone / "temp") << "25000\n";
    };
    add("thermal_zone0", "battery\n");
    add("thermal_zone1", "Battery\n");
    fs::create_directory(root / "cooling_device0");  // Not a temperature zone.
    auto scan = honor::ldy_an00::DiscoverThermalZones(directory);
    assert(scan && scan->size() == 2);
    assert(scan->at("battery").size() == 1 && scan->at("Battery").size() == 1);
    assert(scan->at("battery").front() == (root / "thermal_zone0" / "temp").string());

    add("thermal_zone2", "battery\n");
    scan = honor::ldy_an00::DiscoverThermalZones(directory);
    assert(scan && scan->at("battery").size() == 2);  // Caller must reject ambiguity.

    fs::create_directory(root / "thermal_zone3");
    // An unreadable type could hide another matching sensor. Never return the partial map.
    assert(!honor::ldy_an00::DiscoverThermalZones(directory));
    std::ofstream(root / "thermal_zone3" / "type") << std::string(40, 'x') << "\n";
    assert(!honor::ldy_an00::DiscoverThermalZones(directory));
    std::ofstream(root / "thermal_zone3" / "type") << "\n";
    assert(!honor::ldy_an00::DiscoverThermalZones(directory));
    std::ofstream(root / "thermal_zone3" / "type") << "pa_2\n";
    scan = honor::ldy_an00::DiscoverThermalZones(directory);
    assert(scan && scan->count("pa_2") == 1);
    fs::remove_all(root);
    assert(!honor::ldy_an00::DiscoverThermalZones(directory));
    std::cout << "THERMAL_SENSOR_DISCOVERY_TESTS_OK\n";
}
