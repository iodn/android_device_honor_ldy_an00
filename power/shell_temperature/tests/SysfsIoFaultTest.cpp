// Copyright (C) 2026 The LineageOS Project
// SPDX-License-Identifier: Apache-2.0
#include "../SysfsIo.h"

#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <iostream>
#include <string>
#include <vector>

namespace {
struct Event { std::string text; int error = 0; };
std::vector<Event> reads;
std::vector<int> writes;
size_t read_calls, write_calls;
int open_calls, close_calls, close_error;
bool refuse_open;
void Verify(bool condition, int line) {
    if (!condition) { std::cerr << "check failed at line " << line << std::endl; std::abort(); }
}
#define Check(condition) Verify((condition), __LINE__)
void Reset() {
    reads.clear(); writes.clear();
    read_calls = write_calls = 0;
    open_calls = close_calls = close_error = 0;
    refuse_open = false;
}
}  // namespace

extern "C" int __wrap_open(const char*, int flags, ...) {
    ++open_calls;
    Check((flags & O_CLOEXEC) && (flags & O_NOFOLLOW));
    Check(!(flags & (O_CREAT | O_TRUNC)));
    if (refuse_open) { errno = EACCES; return -1; }
    return 42;
}
extern "C" ssize_t __wrap_read(int fd, void* buffer, size_t count) {
    Check(fd == 42 && read_calls < reads.size());
    const auto& event = reads[read_calls++];
    if (event.error) { errno = event.error; return -1; }
    Check(event.text.size() <= count);
    std::memcpy(buffer, event.text.data(), event.text.size());
    return event.text.size();
}
extern "C" ssize_t __wrap___read_chk(int fd, void* buffer, size_t count, size_t capacity) {
    Check(count <= capacity);
    return __wrap_read(fd, buffer, count);
}
extern "C" ssize_t __wrap_write(int fd, const void* buffer, size_t count) {
    Check(fd == 42 && write_calls < writes.size());
    Check(std::string(static_cast<const char*>(buffer), count) == "25000\n");
    const int result = writes[write_calls++];
    if (result < 0) { errno = -result; return -1; }
    return result;
}
extern "C" int __wrap_close(int fd) {
    Check(fd == 42); ++close_calls;
    if (close_error) { errno = close_error; return -1; }
    return 0;
}

int main() {
    using honor::ldy_an00::ReadTemperature;
    using honor::ldy_an00::WriteTemperature;
    Reset(); reads = {{"", EINTR}, {"25"}, {"000"}, {"\n"}, {""}};
    const auto first = ReadTemperature("fixture");
    Check(first == 25000 && read_calls == 5 && close_calls == 1);
    Reset(); reads = {{"25"}, {"", EIO}};
    Check(!ReadTemperature("fixture") && close_calls == 1);
    Reset(); reads = {{""}};
    Check(!ReadTemperature("fixture") && close_calls == 1);
    Reset(); reads = {{"25000\n"}, {""}}; close_error = EINTR;
    Check(!ReadTemperature("fixture") && close_calls == 1);
    Reset(); reads = {{std::string(32, '1')}};
    Check(!ReadTemperature("fixture") && read_calls == 1 && close_calls == 1);
    Reset(); refuse_open = true;
    Check(!ReadTemperature("fixture") && read_calls == 0 && close_calls == 0);
    Reset(); writes = {-EINTR, 6};
    Check(WriteTemperature("fixture", 25000) && write_calls == 2 && close_calls == 1);
    Reset(); writes = {3};
    Check(!WriteTemperature("fixture", 25000) && write_calls == 1 && close_calls == 1);
    Reset(); writes = {-EIO};
    Check(!WriteTemperature("fixture", 25000) && write_calls == 1 && close_calls == 1);
    Reset(); writes = {0};
    Check(!WriteTemperature("fixture", 25000) && write_calls == 1 && close_calls == 1);
    Reset(); writes = {6}; close_error = EINTR;
    Check(!WriteTemperature("fixture", 25000) && write_calls == 1 && close_calls == 1);
    Reset(); refuse_open = true;
    Check(!WriteTemperature("fixture", 25000) && write_calls == 0 && close_calls == 0);
    Reset();
    Check(!WriteTemperature("fixture", 145001) && open_calls == 0);
    std::cout << "SYSFS_IO_FAULT_TEST_PASS 13 scenarios\n";
}
