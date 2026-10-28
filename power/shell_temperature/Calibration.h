// Copyright (C) 2026 The LineageOS Project
// SPDX-License-Identifier: Apache-2.0
#pragma once

#include <array>
#include <cstdint>

namespace honor::ldy_an00 {

// Recovered LDY stock table; decoded XML SHA-256:
// cc26013377454e0a3f449e92cc3d54f5eece1506ff85fcaac96f5a0c9e6e9ab2
inline constexpr int64_t kIntervalSeconds = 5;
inline constexpr int64_t kNtcMinimum = -20000;
inline constexpr int64_t kNtcMaximum = 80000;
inline constexpr int64_t kNtcStep = 2000;
inline constexpr int64_t kNormalStep = 300;
inline constexpr int64_t kMaximumStep = 10000;
inline constexpr std::array<const char*, 5> kInputNames = {"hot_pock", "pa_3", "mtkts_usbntc", "wireless", "nrpa_ntc"};
struct ShellCalibration {
    const char* name;
    int samples;
    int32_t bias;
    std::array<std::array<int32_t, 8>, 5> coefficients;
};
inline constexpr std::array<ShellCalibration, 3> kCalibration = {{
    {"shell_front", 6, 1533, {{
        {{3448, 7638, 4919, 641, -488, 3863, 0, 0}},
        {{-1188, -736, -288, -3188, 364, -3009, 0, 0}},
        {{144690, -19439, -34255, -19915, -21237, -5509, 0, 0}},
        {{-14338, 11067, 4042, 4993, 4599, 4064, 0, 0}},
        {{30259, 404, -635, 3310, -2039, -5547, 0, 0}},
    }}},
    {"shell_back", 8, 79, {{
        {{10609, 14707, 4653, -1431, -864, -1550, -1550, 2931}},
        {{5843, -5365, -3695, -4875, -3720, -2680, -3053, 5430}},
        {{103410, 23929, -5211, -13651, -14779, -16187, -21039, -11872}},
        {{-21666, 21493, 10266, 9820, 8453, 5788, 6746, -7444}},
        {{21284, -252, -1900, 1377, -3665, 950, -324, -10234}},
    }}},
    {"shell_frame", 8, 493, {{
        {{52283, -26207, 1543, 6082, 3293, 412, 204, 6792}},
        {{38000, 4633, -10488, -10680, -7026, -3573, -2807, -7043}},
        {{78648, 41067, 10729, -7015, -15400, -18813, -19516, -20414}},
        {{-57458, -20534, 4571, 13050, 12800, 9574, 6080, 5660}},
        {{32447, 9572, -4511, -6402, -3536, -128, 2066, 1415}},
    }}},
}};

}  // namespace honor::ldy_an00
