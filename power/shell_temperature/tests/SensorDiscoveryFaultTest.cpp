// SPDX-License-Identifier: Apache-2.0
#include "../SensorDiscovery.h"

#include <cassert>
#include <cerrno>
#include <cstdlib>
#include <dirent.h>
#include <filesystem>
#include <fstream>
#include <iostream>

namespace {
bool failRead;
bool failClose;
}
extern "C" dirent* __real_readdir(DIR*);
extern "C" int __real_closedir(DIR*);
extern "C" dirent* __wrap_readdir(DIR* directory) {
    if (failRead) {
        errno = EIO;
        return nullptr;
    }
    return __real_readdir(directory);
}
extern "C" int __wrap_closedir(DIR* directory) {
    const int result = __real_closedir(directory);
    if (failClose) {
        errno = EIO;
        return -1;
    }
    return result;
}

int main() {
    char pattern[] = "/tmp/ldy-thermal-discovery-fault-XXXXXX";
    const char* directory = mkdtemp(pattern);
    assert(directory);
    const std::filesystem::path root(directory);
    std::filesystem::create_directory(root / "thermal_zone0");
    std::ofstream(root / "thermal_zone0" / "type") << "battery\n";
    assert(honor::ldy_an00::DiscoverThermalZones(directory));

    failRead = true;
    assert(!honor::ldy_an00::DiscoverThermalZones(directory));
    failRead = false;
    failClose = true;
    assert(!honor::ldy_an00::DiscoverThermalZones(directory));
    failClose = false;
    assert(honor::ldy_an00::DiscoverThermalZones(directory));
    std::filesystem::remove_all(root);
    std::cout << "THERMAL_SENSOR_DISCOVERY_FAULT_TESTS_OK\n";
}
