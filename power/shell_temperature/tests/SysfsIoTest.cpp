// Copyright (C) 2026 The LineageOS Project
// SPDX-License-Identifier: Apache-2.0
#include "../SysfsIo.h"

#include <cstdlib>
#include <fstream>
#include <iostream>
#include <iterator>
#include <optional>
#include <string>
#include <unistd.h>

namespace {
struct Reading {
    std::string text;
    std::optional<int32_t> expected;
};
void Check(bool ok, const char* message) {
    if (!ok) {
        std::cerr << message << '\n';
        std::exit(1);
    }
}
void Put(const std::string& path, const std::string& value) {
    std::ofstream output(path, std::ios::binary | std::ios::trunc);
    output.write(value.data(), value.size());
    Check(output.good(), "fixture write failed");
}
std::string Get(const std::string& path) {
    std::ifstream input(path, std::ios::binary);
    return {std::istreambuf_iterator<char>(input), std::istreambuf_iterator<char>()};
}
}  // namespace

int main() {
    char directory[] = "/tmp/ldy-an00-thermal-XXXXXX";
    Check(mkdtemp(directory) != nullptr, "fixture directory failed");
    const std::string path = std::string(directory) + "/temp";
    const std::string link = std::string(directory) + "/link";
    const std::string absent = std::string(directory) + "/absent";
    const Reading readings[] = {
        {"25000\n", 25000}, {"-40000\n", -40000}, {"0", 0},
        {"  +25000\r\n", 25000}, {"\t-1\n", -1},
        {"2147483647\n", INT32_MAX}, {"-2147483648\n", INT32_MIN},
        {"", std::nullopt}, {" \t\n", std::nullopt}, {"garbage", std::nullopt},
        {"25000junk", std::nullopt}, {"25 000", std::nullopt},
        {"+", std::nullopt}, {"-", std::nullopt}, {"+-1", std::nullopt},
        {"++1", std::nullopt}, {"2147483648", std::nullopt},
        {"-2147483649", std::nullopt}, {"0x10", std::nullopt},
        {std::string("25000\0junk", 10), std::nullopt},
        {std::string(32, '1'), std::nullopt},
        {std::string(64, ' '), std::nullopt},
        {"25000" + std::string(32, ' '), std::nullopt},
    };
    int checks = 0;
    for (const auto& reading : readings) {
        Put(path, reading.text);
        Check(honor::ldy_an00::ReadTemperature(path) == reading.expected, "read parser mismatch");
        ++checks;
    }
    Check(!honor::ldy_an00::ReadTemperature(absent), "missing input accepted");
    Check(!honor::ldy_an00::ReadTemperature(directory), "directory input accepted");
    Check(symlink(path.c_str(), link.c_str()) == 0, "fixture symlink failed");
    Check(!honor::ldy_an00::ReadTemperature(link), "final-component input symlink followed");
    Put(path, "");
    Check(honor::ldy_an00::WriteTemperature(path, 25000), "valid output rejected");
    Check(Get(path) == "25000\n", "output formatting mismatch");
    for (int32_t value : {-40000, 145000}) {
        Put(path, "");
        Check(honor::ldy_an00::WriteTemperature(path, value), "output bound rejected");
        Check(Get(path) == std::to_string(value) + "\n", "output bound formatting mismatch");
    }
    Put(path, "original");
    Check(!honor::ldy_an00::WriteTemperature(path, -40001), "low output bound accepted");
    Check(!honor::ldy_an00::WriteTemperature(path, 145001), "high output bound accepted");
    Check(!honor::ldy_an00::WriteTemperature(link, 25000), "output symlink followed");
    Check(Get(path) == "original", "refused output changed target");
    Check(!honor::ldy_an00::WriteTemperature(absent, 25000), "missing output created");
    Check(access(absent.c_str(), F_OK) != 0, "missing output exists");
    Check(!honor::ldy_an00::WriteTemperature(directory, 25000), "directory output accepted");
    Check(unlink(link.c_str()) == 0 && unlink(path.c_str()) == 0 &&
          rmdir(directory) == 0, "fixture cleanup failed");
    std::cout << "SYSFS_IO_TEST_PASS " << checks << " parser cases plus path/output checks\n";
}
