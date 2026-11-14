# SPDX-License-Identifier: Apache-2.0
"""Exercise pinned stock thermal arithmetic and threshold callbacks without hardware."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

from elftools.elf.elffile import ELFFile
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM, UC_HOOK_CODE
from unicorn.arm64_const import (
    UC_ARM64_REG_X0, UC_ARM64_REG_X1, UC_ARM64_REG_X2,
    UC_ARM64_REG_PC, UC_ARM64_REG_SP, UC_ARM64_REG_LR, UC_ARM64_REG_TPIDR_EL0,
)

SHA256 = "11b5e3e9a0c9c0261c5b18a9e6b0b1cbcc3b55eadcf809d688e32c444d650486"
BASE = 0x100000
STACK, TLS, STOP = 0x4000000, 0x4100000, 0x4200000
PARAMS, ZONE, COOLER, CALLBACK = 0x4300000, 0x4400000, 0x4500000, 0x4600000


class StockThermal:
    def __init__(self, library):
        if hashlib.sha256(library.read_bytes()).hexdigest() != SHA256:
            raise ValueError("Stock thermal_core SHA-256 mismatch")
        self.uc = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
        with library.open("rb") as stream:
            elf = ELFFile(stream)
            segments = [s for s in elf.iter_segments() if s["p_type"] == "PT_LOAD"]
            end = max(s["p_vaddr"] + s["p_memsz"] for s in segments)
            self.uc.mem_map(BASE, (end + 4095) & ~4095)
            for segment in segments:
                self.uc.mem_write(BASE + segment["p_vaddr"], segment.data())
        for address in (STACK, TLS, STOP, PARAMS, ZONE, COOLER, CALLBACK):
            self.uc.mem_map(address, 0x10000)
        self.uc.reg_write(UC_ARM64_REG_TPIDR_EL0, TLS)
        self.uc.hook_add(UC_HOOK_CODE, self.hook)
        self.callbacks = []
        self.callback_result = 1
        self.suppressed_writes = 0
        self.put(0x160310, 0)  # Disable stock logging branches.

    def put(self, address, value):
        self.uc.mem_write(address, struct.pack("<I", value & 0xffffffff))

    def pointer(self, address, value):
        self.uc.mem_write(address, struct.pack("<Q", value))

    def integer(self, address):
        return struct.unpack("<i", self.uc.mem_read(address, 4))[0]

    def hook(self, uc, address, size, unused):
        if address == 0x1459f0:
            raise AssertionError("Stock stack canary failed")
        if address == CALLBACK:
            self.callbacks.append(uc.reg_read(UC_ARM64_REG_X2))
            uc.reg_write(UC_ARM64_REG_X0, self.callback_result)
        elif address == 0x110a70:
            self.suppressed_writes += 1
            uc.reg_write(UC_ARM64_REG_X0, 0)
        elif address in (0x1459d0, 0x110d50):
            uc.reg_write(UC_ARM64_REG_X0, 0)  # Intercept logging/formatting.
        else:
            return
        uc.reg_write(UC_ARM64_REG_PC, uc.reg_read(UC_ARM64_REG_LR))

    def call(self, address, first, second=0):
        self.uc.reg_write(UC_ARM64_REG_SP, STACK + 0xf000)
        self.uc.reg_write(UC_ARM64_REG_LR, STOP)
        self.uc.reg_write(UC_ARM64_REG_X0, first & 0xffffffffffffffff)
        self.uc.reg_write(UC_ARM64_REG_X1, second)
        self.uc.emu_start(address, STOP, timeout=1000000, count=10000)
        if self.uc.reg_read(UC_ARM64_REG_PC) != STOP:
            raise AssertionError("Stock function did not return")
        value = self.uc.reg_read(UC_ARM64_REG_X0) & 0xffffffff
        return value - 0x100000000 if value & 0x80000000 else value


def run(library):
    stock = StockThermal(library)
    cases = []
    # Exact scalar values from the pinned thermal.conf, not a replacement controller.
    for index, value in {0:105000, 4:105000, 5:55000, 6:85000, 7:85000,
                         9:500, 10:500, 0x24:1, 0x25:0}.items():
        stock.put(PARAMS + index * 4, value)
    stock.call(0x1456c0, PARAMS)
    if tuple(stock.integer(PARAMS + i * 4) for i in (8, 12, 13)) != (0, 102400, 102400):
        raise AssertionError("Unexpected CATM coefficients")
    for temperature in (-274000, -20000, 0, 44999, 45000, 47000, 47001,
                        84999, 85000, 85001, 105000, 125000, 85000, 25000):
        if stock.call(0x145720, temperature, PARAMS) != 105000:
            raise AssertionError(("Unexpected pinned CATM target", temperature))
        cases.append("catm_" + str(temperature) + "_" + str(len(cases)))

    # Synthetic nonfixed limits exercise arithmetic hidden by stock max == min.
    # This fixture is never written into the stock policy or a device.
    dynamic = StockThermal(library)
    for index, value in {0:105000, 4:65000, 5:55000, 6:45000, 7:47000,
                         9:500, 10:500, 0x24:2, 0x25:0}.items():
        dynamic.put(PARAMS + index * 4, value)
    dynamic.call(0x1456c0, PARAMS)
    coefficients = tuple(dynamic.integer(PARAMS + i * 4) for i in (8, 12, 13))
    if coefficients != (25600, 20480, 102400):
        raise AssertionError(("Unexpected synthetic CATM coefficients", coefficients))
    for name, temperature, expected in [
            ("dynamic_below_trip_resets_integral", 44999, 105000),
            ("dynamic_equal_trip_maximum_clamp", 45000, 105000),
            ("dynamic_integral_accumulates", 46000, 90488),
            ("dynamic_zero_error_retains_integral", 47000, 65488),
            ("dynamic_above_target_minimum_clamp", 48000, 65000),
            ("dynamic_falling_gain_maximum_clamp", 46000, 105000)]:
        observed = dynamic.call(0x145720, temperature, PARAMS)
        if observed != expected:
            raise AssertionError((name, observed, expected))
        cases.append(name)

    negative = StockThermal(library)
    for index, value in {0:105000, 4:45000, 5:55000, 6:45000, 7:47000,
                         9:500, 10:500, 0x24:2, 0x25:0}.items():
        negative.put(PARAMS + index * 4, value)
    negative.call(0x1456c0, PARAMS)
    for name, temperature, expected in [
            ("negative_integral_minimum_clamp", 60000, 45000),
            ("negative_integral_lower_bound", 60000, 45000),
            ("negative_zero_error_retains_integral", 47000, 45000),
            ("negative_falling_gain", 46000, 95488)]:
        observed = negative.call(0x145720, temperature, PARAMS)
        if observed != expected:
            raise AssertionError((name, observed, expected))
        cases.append(name)

    stock.put(0x160680, 1)
    stock.pointer(0x160688, ZONE)
    stock.pointer(0x160690, 0)
    stock.uc.mem_write(PARAMS, bytes(128))
    stock.put(PARAMS, 7)
    stock.pointer(PARAMS + 8, COOLER)
    stock.pointer(COOLER + 0x30, CALLBACK)
    stock.put(PARAMS + 20, 47000)
    stock.put(PARAMS + 24, 2000)

    def threshold(name, temperature, expected_calls, expected_state, callback_result=1):
        stock.callbacks = []
        stock.callback_result = callback_result
        stock.put(ZONE + 0x28, temperature)
        stock.call(0x117b90, PARAMS)
        if stock.callbacks != expected_calls or stock.integer(PARAMS + 16) != expected_state:
            raise AssertionError((name, stock.callbacks, stock.integer(PARAMS + 16)))
        cases.append(name)

    threshold("threshold_equal_trip_stays_off", 47000, [], 0)
    threshold("threshold_above_trip_enters", 47001, [1], 1)
    threshold("threshold_equal_clear_stays_on", 45000, [], 1)
    threshold("threshold_below_clear_releases", 44999, [0], 0)
    threshold("threshold_failed_enable_not_latched", 47001, [1], 0, 0)
    threshold("threshold_enable_recovers", 47001, [1], 1)
    threshold("threshold_active_equal_trip_stays_on", 47000, [], 1)
    threshold("threshold_release_clears_state_even_on_failure", 44999, [0], 0, 0)
    stock.put(PARAMS + 4, 1)
    threshold("threshold_missing_sensor_does_not_act", 60000, [], 0)
    return {"status":"STOCK_THERMAL_EMULATION_OK", "binary_sha256":SHA256,
            "case_count":len(cases), "cases":cases,
            "suppressed_node_writes":stock.suppressed_writes + dynamic.suppressed_writes + negative.suppressed_writes,
            "limits":"Pinned CATM scalars, synthetic dynamic CATM scalars and one-zone callback fixture; no hardware, thermal-hint loop or physical effectiveness test.",
            "device_access":False}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("library", type=Path)
    args = parser.parse_args()
    print(json.dumps(run(args.library), indent=2))
