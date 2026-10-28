#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Fill the retained MediaTek HAL's empty standard interaction/sustained hints."""
import argparse
import copy
import hashlib
from pathlib import Path
import xml.etree.ElementTree as ET

STOCK_SHA256 = "b8923a5046d1a5fd4df584148f0273ad0a8a5cb8efb715d0dbb7d96d15c1ee44"


def generate(source):
    if hashlib.sha256(source).hexdigest() != STOCK_SHA256:
        raise ValueError("Stock MediaTek scenario table identity mismatch")
    parser = ET.XMLParser(target=ET.TreeBuilder(insert_comments=True))
    root = ET.fromstring(source, parser=parser)
    scenarios = {e.get("powerhint"): e for e in root if e.tag == "scenario"}
    if "INTERACTION" in scenarios or "SUSTAINED_PERFORMANCE" in scenarios:
        raise ValueError("Standard hints already configured")

    # Same resource as the device's stock UX touch hint; the HAL owns duration.
    interaction = copy.deepcopy(scenarios["MTKPOWER_HINT_UX_TOUCH_MOVE"])
    interaction.set("powerhint", "INTERACTION")
    root.append(interaction)

    # Stock normal_cpu_policy.xml/default_front CPU ceilings, in kHz.
    # This is a candidate sustained profile, not a measured thermal calibration.
    sustained = ET.SubElement(root, "scenario", powerhint="SUSTAINED_PERFORMANCE")
    for cluster, frequency in enumerate((2300000, 2800000, 2450000)):
        ET.SubElement(sustained, "data",
                      cmd=f"PERF_RES_CPUFREQ_MAX_CLUSTER_{cluster}", param1=str(frequency))
    # Like Honor's ceiling requests: survive boost cleanup without raising minima.
    ET.SubElement(sustained, "data", cmd="PERF_RES_POWERHAL_SCREEN_OFF_STATE", param1="1")

    ET.indent(root, space="    ")
    header = source.decode().split("<SCNTABLE>", 1)[0]
    return (header + ET.tostring(root, encoding="unicode") + "\n").encode()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.write_bytes(generate(args.source.read_bytes()))


if __name__ == "__main__":
    main()
