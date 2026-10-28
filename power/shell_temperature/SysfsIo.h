// Copyright (C) 2026 The LineageOS Project
// SPDX-License-Identifier: Apache-2.0
#pragma once

#include <cstdint>
#include <optional>
#include <string>

namespace honor::ldy_an00 {

std::optional<int32_t> ReadTemperature(const std::string& path);
bool WriteTemperature(const std::string& path, int32_t temperature);

}  // namespace honor::ldy_an00
