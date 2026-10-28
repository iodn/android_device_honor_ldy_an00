// Copyright (C) 2026 The LineageOS Project
// SPDX-License-Identifier: Apache-2.0
#include "SysfsIo.h"

#include <cerrno>
#include <charconv>
#include <cctype>
#include <fcntl.h>
#include <unistd.h>

namespace honor::ldy_an00 {

std::optional<int32_t> ReadTemperature(const std::string& path) {
    const int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return std::nullopt;
    char buffer[32];
    size_t used = 0;
    bool complete = false;
    while (used < sizeof(buffer)) {
        const ssize_t count = read(fd, buffer + used, sizeof(buffer) - used);
        if (count < 0 && errno == EINTR) continue;
        if (count < 0) break;
        if (count == 0) {
            complete = true;
            break;
        }
        used += count;
    }
    const bool closed = close(fd) == 0;
    if (!complete || !closed || used == 0) return std::nullopt;
    const char* begin = buffer;
    const char* end = buffer + used;
    while (begin < end && std::isspace(static_cast<unsigned char>(*begin))) ++begin;
    while (begin < end && std::isspace(static_cast<unsigned char>(end[-1]))) --end;
    if (begin == end) return std::nullopt;
    if (*begin == '+') {
        ++begin;
        if (begin == end || *begin < '0' || *begin > '9') return std::nullopt;
    }
    int32_t value;
    const auto parsed = std::from_chars(begin, end, value);
    if (parsed.ec != std::errc{} || parsed.ptr != end) return std::nullopt;
    return value;
}

bool WriteTemperature(const std::string& path, int32_t temperature) {
    // Bounds accepted by the retained shell-temperature kernel attribute.
    if (temperature < -40000 || temperature > 145000) return false;
    const std::string value = std::to_string(temperature) + "\n";
    const int fd = open(path.c_str(), O_WRONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return false;
    ssize_t count;
    do {
        count = write(fd, value.data(), value.size());
    } while (count < 0 && errno == EINTR);
    const bool closed = close(fd) == 0;
    // A short sysfs write has an uncertain outcome. Do not issue a second store.
    return count == static_cast<ssize_t>(value.size()) && closed;
}

}  // namespace honor::ldy_an00
