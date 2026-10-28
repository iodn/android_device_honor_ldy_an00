# SPDX-License-Identifier: Apache-2.0
import importlib.util
import json
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("profile", HERE / "generate_power_profile.py")
profile = importlib.util.module_from_spec(spec)
spec.loader.exec_module(profile)


class PowerProfileTest(unittest.TestCase):
    def setUp(self):
        self.root = HERE.parent
        self.source = (self.root / "power/stock-power-profile.xml").read_bytes()
        self.config = json.loads((self.root / "power/profile.json").read_text())

    def test_exact_interpolated_and_outside_points(self):
        self.assertEqual(profile.estimate(300, [300, 500], [10, 30]), 10)
        self.assertEqual(profile.estimate(400, [300, 500], [10, 30]), 20)
        self.assertEqual(profile.estimate(600, [300, 500], [10, 30]), 30)
        self.assertEqual(profile.estimate(200, [300, 500], [10, 30]), 10)

    def test_kernel_step_order_and_endpoint_clamping(self):
        root = ET.fromstring(profile.generate(self.source, self.config))
        entries = {e.get("name"): e for e in root}
        self.assertEqual(entries["battery.capacity"].text, "5500")
        for cluster, (policy, data) in enumerate(self.config["policies"].items()):
            curve = entries[f"cpu.core_power.cluster{cluster}"]
            steps = entries[f"cpu.scaling_step_power.policy{policy}"]
            self.assertEqual(len(steps), len(data["frequencies_khz"]))
            self.assertEqual(float(steps[0].text), float(curve[-1].text))
            self.assertEqual(float(steps[-1].text), float(curve[0].text))

    def test_stock_entries_preserved(self):
        original = ET.fromstring(self.source)
        generated = ET.fromstring(profile.generate(self.source, self.config))
        for before, after in zip(original, generated):
            self.assertEqual((before.tag, before.attrib, (before.text or "").strip()),
                             (after.tag, after.attrib, (after.text or "").strip()))
            self.assertEqual([(v.tag, v.attrib, (v.text or "").strip()) for v in before],
                             [(v.tag, v.attrib, (v.text or "").strip()) for v in after])

    def test_unknown_source_rejected(self):
        with self.assertRaisesRegex(ValueError, "identity"):
            profile.generate(self.source + b" ", self.config)

    def test_wrong_cpu_membership_rejected(self):
        self.config["policies"]["4"]["cpus"] = [4, 5]
        with self.assertRaisesRegex(ValueError, "membership"):
            profile.generate(self.source, self.config)

    def test_duplicate_kernel_steps_rejected(self):
        self.config["policies"]["7"]["frequencies_khz"].append(300000)
        with self.assertRaisesRegex(ValueError, "frequency"):
            profile.generate(self.source, self.config)


if __name__ == "__main__":
    unittest.main()
