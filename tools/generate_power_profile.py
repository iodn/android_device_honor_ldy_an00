#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Generate the device power model from stock estimates and retained kernel tables."""
import argparse
from bisect import bisect_left
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "overlay/FrameworkPowerProfile/res/xml/power_profile.xml"


def estimate(frequency, frequencies, currents):
    """Interpolate stock estimates; never extrapolate beyond their supplied range."""
    index = bisect_left(frequencies, frequency)
    if index == 0:
        return currents[0]
    if index == len(frequencies):
        return currents[-1]
    before, after = frequencies[index - 1], frequencies[index]
    weight = (frequency - before) / (after - before)
    return currents[index - 1] + weight * (currents[index] - currents[index - 1])


def generate(source, config):
    if hashlib.sha256(source).hexdigest() != config["stock_profile_sha256"]:
        raise ValueError("Stock power profile identity mismatch")
    root = ET.fromstring(source)
    entries = {element.get("name"): element for element in root}
    if len(entries) != len(root) or root.tag != "device":
        raise ValueError("Invalid or duplicate stock power entries")
    counts = [int(v.text) for v in entries["cpu.clusters.cores"]]
    policies = config["policies"]
    if list(policies) != ["0", "4", "7"] or counts != [4, 3, 1]:
        raise ValueError("Unexpected CPU topology")
    if config["nominal_capacity_mah"] != 5500:
        raise ValueError("Unverified nominal capacity")
    ET.SubElement(root, "item", name="battery.capacity").text = "5500"
    for cluster, (policy, record) in enumerate(policies.items()):
        expected_cpus = list(range(int(policy), int(policy) + counts[cluster]))
        if record["cpus"] != expected_cpus:
            raise ValueError("CPU policy membership mismatch")
        frequencies = [int(v.text) for v in entries[f"cpu.core_speeds.cluster{cluster}"]]
        currents = [float(v.text) for v in entries[f"cpu.core_power.cluster{cluster}"]]
        if (len(frequencies) != len(currents) or len(frequencies) < 2
                or frequencies != sorted(set(frequencies))
                or any(value <= 0 for value in currents)):
            raise ValueError("Invalid stock CPU curve")
        actual = record["frequencies_khz"]
        if len(actual) != len(set(actual)) or any(value <= 0 for value in actual):
            raise ValueError("Invalid kernel frequency table")
        # Android 17 preserves sysfs order. Do not sort these per-policy arrays.
        array = ET.SubElement(root, "array", name=f"cpu.scaling_step_power.policy{policy}")
        for frequency in actual:
            ET.SubElement(array, "value").text = format(
                estimate(frequency, frequencies, currents), ".9g")
    ET.indent(root, space="  ")
    # Preserve the original Apache license, including its copyright notice.
    header = source.decode().split("<device", 1)[0]
    note = ("<!-- Stock estimates, not new measurements. CPU step arrays follow the retained\n"
            "     kernel's sysfs order. Out-of-range frequencies use the nearest stock\n"
            "     endpoint; higher boost-bin consumption may therefore be underestimated. -->\n")
    return (header + note + ET.tostring(root, encoding="unicode") + "\n").encode()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    data = generate((ROOT / "power/stock-power-profile.xml").read_bytes(),
                    json.loads((ROOT / "power/profile.json").read_text()))
    if args.check:
        if OUTPUT.read_bytes() != data:
            raise SystemExit("Generated power profile is stale")
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_bytes(data)
    print("POWER_PROFILE_VERIFIED")


if __name__ == "__main__":
    main()
