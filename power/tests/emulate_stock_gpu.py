# SPDX-License-Identifier: Apache-2.0
"""Exercise stock GPU arbitration in an ARM64 emulator; never access a device."""
import argparse
import hashlib
import json
import struct
from pathlib import Path

from elftools.elf.elffile import ELFFile
from unicorn import Uc, UC_ARCH_ARM64, UC_MODE_ARM, UC_HOOK_CODE
from unicorn.arm64_const import (
    UC_ARM64_REG_X0, UC_ARM64_REG_X1, UC_ARM64_REG_SP,
    UC_ARM64_REG_PC, UC_ARM64_REG_LR, UC_ARM64_REG_TPIDR_EL0,
)

SHA256 = "8bc192265e7b95842284e879f19a075af8baa4038c5544f98d844648e00120f0"
BASE = 0x100000
STACK, TLS, STOP = 0x4000000, 0x4100000, 0x4200000
HANDLERS = {"soft_min": 0x1800c0, "hard_min": 0x1802b0,
            "soft_max": 0x1804a0, "hard_max": 0x180680}


class StockGpu:
    def __init__(self, library):
        if hashlib.sha256(library.read_bytes()).hexdigest() != SHA256:
            raise ValueError("Stock library SHA-256 mismatch")
        self.uc = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
        with library.open("rb") as stream:
            elf = ELFFile(stream)
            segments = [s for s in elf.iter_segments() if s["p_type"] == "PT_LOAD"]
            end = max(s["p_vaddr"] + s["p_memsz"] for s in segments)
            self.uc.mem_map(BASE, (end + 4095) & ~4095)
            for segment in segments:
                self.uc.mem_write(BASE + segment["p_vaddr"], segment.data())
        # Resolve the sole GOT data reference used by these four handlers.
        self.uc.mem_write(0x19da28, struct.pack("<Q", 0x1a8908))
        for address in (STACK, TLS, STOP):
            self.uc.mem_map(address, 0x10000)
        self.uc.reg_write(UC_ARM64_REG_TPIDR_EL0, TLS)
        self.uc.hook_add(UC_HOOK_CODE, self.hook)
        self.writes = []
        self.stat_calls = 0

    def put(self, address, value):
        self.uc.mem_write(address, struct.pack("<I", value & 0xffffffff))

    def hook(self, uc, address, size, unused):
        if address == 0x19a590:
            raise AssertionError("Stock stack canary failed")
        if address in (0x19af50, 0x19af60):
            value = uc.reg_read(UC_ARM64_REG_X0) & 0xffffffff
            self.writes.append(("min" if address == 0x19af50 else "max", value))
        elif address == 0x19a520:
            pass  # Logging is intercepted, not executed.
        elif address == 0x19a720:
            self.stat_calls += 1
            uc.reg_write(UC_ARM64_REG_X0, 0xffffffff)
        else:
            return
        uc.reg_write(UC_ARM64_REG_PC, uc.reg_read(UC_ARM64_REG_LR))

    def reset(self, count=20, supported=True):
        self.writes = []
        self.stat_calls = 0
        self.put(0x1a8908, count)
        self.put(0x1f7e00, count - 1)  # Soft minimum index.
        self.put(0x1f7e04, count - 1)  # Hard minimum index.
        self.put(0x1f7e08, -1)         # Soft maximum index.
        self.put(0x1f7e0c, -1)         # Hard maximum index.
        self.uc.mem_write(0x1f7e10, bytes([1 if supported else 0]))
        self.put(0x1f7e14, 1 if supported else 0)

    def request(self, name, value):
        self.writes = []
        uc = self.uc
        uc.reg_write(UC_ARM64_REG_SP, STACK + 0xf000)
        uc.reg_write(UC_ARM64_REG_LR, STOP)
        uc.reg_write(UC_ARM64_REG_X0, value & 0xffffffff)
        uc.reg_write(UC_ARM64_REG_X1, 0)
        uc.emu_start(HANDLERS[name], STOP, timeout=1000000, count=10000)
        if uc.reg_read(UC_ARM64_REG_PC) != STOP:
            raise AssertionError("Stock function did not return")
        return list(self.writes)


def run(library):
    gpu = StockGpu(library)
    cases = []

    def check(name, requests, expected):
        gpu.reset()
        for handler, value in requests:
            actual = gpu.request(handler, value)
        if actual != [("min", expected[0]), ("max", expected[1])]:
            raise AssertionError((name, actual, expected))
        cases.append(name)

    check("unrestricted", [("soft_max", -1)], (19, 0))
    check("soft_ceiling", [("soft_max", 10)], (19, 10))
    check("hard_ceiling_survives_soft_release",
          [("hard_max", 12), ("soft_max", 10), ("soft_max", -1)], (19, 12))
    check("hard_ceiling_wins_over_soft_floor",
          [("hard_max", 12), ("soft_min", 5)], (12, 12))
    check("hard_floor_resolves_hard_conflict",
          [("hard_max", 12), ("hard_min", 8)], (8, 8))
    check("soft_floor_survives_soft_ceiling_release",
          [("soft_min", 5), ("soft_max", 10), ("soft_max", -1)], (5, 0))
    check("hard_ceiling_release_keeps_soft_ceiling",
          [("soft_max", 10), ("hard_max", 12), ("hard_max", -1)], (19, 10))
    check("both_ceiling_releases",
          [("soft_max", 10), ("hard_max", 12),
           ("soft_max", -1), ("hard_max", -1)], (19, 0))
    check("raw_out_of_range_max_clamps_to_floor", [("soft_max", 20)], (19, 19))
    check("out_of_range_min_restores_default", [("soft_min", 20)], (19, 0))

    for handler in HANDLERS:
        gpu.reset(supported=False)
        if gpu.request(handler, 10) or gpu.stat_calls != 1:
            raise AssertionError("Unsupported GPU path reached a driver write")
        cases.append("unsupported_" + handler)

    return {"status": "STOCK_GPU_EMULATION_OK", "library_sha256": SHA256,
            "case_count": len(cases), "cases": cases,
            "method": "Stock ARM64 machine code; logging/stat/driver calls intercepted",
            "limits": "Synthetic 20-level driver fixture; no hardware effect or frequency calibration proven",
            "device_access": False}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("library", type=Path)
    args = parser.parse_args()
    print(json.dumps(run(args.library), indent=2))
