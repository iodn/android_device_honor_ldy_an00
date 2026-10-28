// Copyright (C) 2026 The LineageOS Project
// SPDX-License-Identifier: Apache-2.0
#define LOG_TAG "LdyAn00ShellTemperature"

#include <android-base/logging.h>
#include <android-base/properties.h>
#include <cerrno>
#include <dirent.h>
#include <fstream>
#include <optional>
#include <string>
#include <time.h>

#include "Calibration.h"
#include "Estimator.h"
#include "SysfsIo.h"

namespace honor::ldy_an00 {
namespace {

using SensorPaths = std::array<std::string, 5>;
constexpr const char* kSnapshotProperty = "sys.lineage.ldy_an00.shell_temp";

std::optional<SensorPaths> FindSensors() {
    constexpr const char* root = "/sys/devices/virtual/thermal";
    DIR* directory = opendir(root);
    if (!directory) return std::nullopt;
    SensorPaths paths{};
    bool duplicate = false;
    bool scan_failed = false;
    for (;;) {
        errno = 0;
        const auto* entry = readdir(directory);
        if (!entry) {
            scan_failed = errno != 0;
            break;
        }
        const std::string zone = entry->d_name;
        if (zone.compare(0, 12, "thermal_zone") != 0) continue;
        const std::string prefix = std::string(root) + "/" + zone;
        std::ifstream input(prefix + "/type");
        char name[32];
        if (!input.getline(name, sizeof(name))) continue;
        for (size_t sensor = 0; sensor < paths.size(); ++sensor) {
            if (name != std::string(kInputNames[sensor])) continue;
            if (!paths[sensor].empty()) duplicate = true;
            paths[sensor] = prefix + "/temp";
        }
    }
    const bool closed = closedir(directory) == 0;
    if (!closed || scan_failed || duplicate) return std::nullopt;
    for (const auto& path : paths) if (path.empty()) return std::nullopt;
    return paths;
}

bool Sample(std::optional<SensorPaths>& paths, ShellTemperatureEstimator& estimator,
            std::string& error) {
    const std::string stock = android::base::GetProperty("init.svc.dubaid", "");
    if (!stock.empty() && stock != "stopped") {
        error = "Stock dubaid is active; shell publication suspended";
        return false;
    }
    if (!paths) paths = FindSensors();
    if (!paths) {
        error = "Required thermal sensors are missing or ambiguous";
        return false;
    }
    ShellInputs inputs{};
    bool any_in_range = false;
    for (size_t sensor = 0; sensor < inputs.size(); ++sensor) {
        const auto value = ReadTemperature((*paths)[sensor]);
        if (!value) {
            error = "Cannot read thermal sensor " + std::string(kInputNames[sensor]);
            paths.reset();
            return false;
        }
        inputs[sensor] = *value;
        any_in_range |= *value >= kNtcMinimum && *value <= kNtcMaximum;
    }
    if (!any_in_range) {
        error = "All thermal readings are outside the calibrated input range";
        return false;
    }
    timespec now{};
    if (clock_gettime(CLOCK_BOOTTIME, &now) != 0) {
        error = "Cannot read thermal sample clock";
        return false;
    }
    const auto outputs = estimator.Sample(inputs, now.tv_sec);
    for (size_t shell = 0; shell < outputs.size(); ++shell) {
        const std::string path = "/sys/devices/platform/" +
                                 std::string(kCalibration[shell].name) + "@0/temp";
        if (!WriteTemperature(path, outputs[shell])) {
            error = "Cannot publish shell temperature " + std::string(kCalibration[shell].name);
            return false;
        }
    }
    // One property value carries a coherent, timestamped sample. Readers must
    // validate its age; the legacy kernel caches do not carry freshness metadata.
    const int64_t milliseconds = now.tv_sec * 1000LL + now.tv_nsec / 1000000;
    std::string snapshot = "1:" + std::to_string(milliseconds);
    for (int32_t temperature : outputs) snapshot += ":" + std::to_string(temperature);
    if (!android::base::SetProperty(kSnapshotProperty, snapshot)) {
        error = "Cannot publish thermal snapshot";
        return false;
    }
    return true;
}

}  // namespace
}  // namespace honor::ldy_an00

int main(int argc, char** argv) {
    (void)argc;
    android::base::InitLogging(argv, android::base::LogdLogger(android::base::SYSTEM));
    if (!android::base::SetProperty(honor::ldy_an00::kSnapshotProperty, "")) {
        LOG(ERROR) << "Cannot invalidate previous thermal snapshot";
        return 1;
    }
    honor::ldy_an00::ShellTemperatureEstimator estimator;
    std::optional<honor::ldy_an00::SensorPaths> paths;
    std::string last_error;
    for (;;) {
        std::string error;
        if (!honor::ldy_an00::Sample(paths, estimator, error)) {
            if (!android::base::SetProperty(honor::ldy_an00::kSnapshotProperty, "")) {
                LOG(ERROR) << "Cannot invalidate failed thermal snapshot";
                return 1;
            }
            if (error != last_error) LOG(ERROR) << error;
        } else if (!last_error.empty()) {
            LOG(INFO) << "Shell temperature publication recovered";
        }
        last_error = error;
        timespec remaining{honor::ldy_an00::kIntervalSeconds, 0};
        int result;
        do {
            result = clock_nanosleep(CLOCK_MONOTONIC, 0, &remaining, &remaining);
        } while (result == EINTR);
        if (result != 0) {
            LOG(ERROR) << "Cannot wait for next thermal sample: " << result;
            return 1;
        }
    }
}
