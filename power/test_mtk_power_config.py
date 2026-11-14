# SPDX-License-Identifier: Apache-2.0
import hashlib
import json
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

from generate_mtk_power_config import generate

SOURCE = Path(__file__).resolve().parent / "proprietary/powerscntbl-stock113.xml"


def signature(element):
    return (element.tag, element.attrib, (element.text or "").strip(),
            [signature(e) for e in element])


@unittest.skipUnless(SOURCE.exists(), "Requires the excluded, verified stock scenario table")
class MediaTekPowerConfigTest(unittest.TestCase):
    def setUp(self):
        self.source = SOURCE.read_bytes()
        self.original = ET.fromstring(self.source)
        self.generated = ET.fromstring(generate(self.source))

    def test_original_scenarios_preserved(self):
        before = {e.get("powerhint"): signature(e) for e in self.original}
        after = {e.get("powerhint"): signature(e) for e in self.generated}
        self.assertEqual(len(after), len(before) + 2)
        for name, value in before.items():
            self.assertEqual(after[name], value)

    def test_interaction_uses_exact_stock_touch_resources(self):
        source = self.original.find("./scenario[@powerhint='MTKPOWER_HINT_UX_TOUCH_MOVE']")
        result = self.generated.find("./scenario[@powerhint='INTERACTION']")
        self.assertEqual([signature(e) for e in result], [signature(e) for e in source])

    def test_sustained_uses_only_stock_balanced_ceilings_and_screenoff_lifecycle(self):
        result = self.generated.find("./scenario[@powerhint='SUSTAINED_PERFORMANCE']")
        self.assertEqual({e.get("cmd"): e.get("param1") for e in result}, {
            "PERF_RES_CPUFREQ_MAX_CLUSTER_0": "2300000",
            "PERF_RES_CPUFREQ_MAX_CLUSTER_1": "2800000",
            "PERF_RES_CPUFREQ_MAX_CLUSTER_2": "2450000",
            "PERF_RES_POWERHAL_SCREEN_OFF_STATE": "1",
        })
        self.assertFalse(any("MIN" in e.get("cmd") or "THERMAL" in e.get("cmd")
                             for e in result))

    def test_cpu_limits_match_retained_kernel_steps(self):
        config = json.loads((SOURCE.parent.parent / "profile.json").read_text())
        sustained = self.generated.find("./scenario[@powerhint='SUSTAINED_PERFORMANCE']")
        for data, policy in zip(list(sustained)[:3], ("0", "4", "7")):
            self.assertIn(int(data.get("param1")),
                          config["policies"][policy]["frequencies_khz"])

    def test_resource_audit_matches_pinned_scenarios(self):
        audit = json.loads((SOURCE.parent.parent / "stock-mtk-resources.json").read_text())
        self.assertEqual(hashlib.sha256(self.source).hexdigest(),
                         audit["source_sha256"]["powerscntbl.xml"])
        self.assertEqual(audit["scenarios"], [
            {"name":s.get("powerhint"), "resources":[
                {"name":d.get("cmd"), "parameter":d.get("param1")} for d in s]}
            for s in self.original])
        used = {d.get("cmd") for s in self.original for d in s}
        self.assertEqual(used, {r["name"] for r in audit["resources"]})

    def test_generated_hints_use_resolved_native_resources(self):
        audit = json.loads((SOURCE.parent.parent / "stock-mtk-resources.json").read_text())
        by_name = {r["name"]:r for r in audit["resources"]}
        for name in ("INTERACTION", "SUSTAINED_PERFORMANCE"):
            for data in self.generated.find("./scenario[@powerhint='" + name + "']"):
                resource = by_name[data.get("cmd")]
                self.assertNotEqual(resource["dispatch"], "unknown_to_pinned_parser")
                self.assertGreater(int(resource["opcode"], 16), 0)

    def test_unknown_source_rejected(self):
        with self.assertRaisesRegex(ValueError, "identity"):
            generate(self.source + b" ")


if __name__ == "__main__":
    unittest.main()
