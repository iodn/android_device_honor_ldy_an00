#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check normal-Android console policy against the retained init implementation."""
import argparse
from pathlib import Path
import unittest


DECLARATION = "PRODUCT_SYSTEM_PROPERTIES += ro.boot.serialconsole=0"


def ordered(text, *tokens):
    positions = [text.index(token) for token in tokens]
    if positions != sorted(positions) or len(set(positions)) != len(tokens):
        raise ValueError("Init property/trigger order changed")


def verify(native, properties, init, service):
    if native.splitlines().count(DECLARATION) != 1:
        raise ValueError("Normal native product must disable the unused serial console")
    prop_init = properties[properties.index("void PropertyInit() {"):]
    ordered(prop_init, "ProcessBootconfig();", "ProcessKernelCmdline();",
            "PropertyLoadBootDefaults();")
    defaults = properties[properties.index("void PropertyLoadBootDefaults() {"):]
    ordered(defaults, 'load_properties_from_file("/system/build.prop"',
            "for (const auto& [name, value] : properties)", "PropertySetNoSocket(")
    second = init[init.index("int SecondStageMain("):]
    ordered(second, "PropertyInit();", "LoadBootScripts(am, sm);",
            'am.QueueEventTrigger("init");')
    console = service[service.index("Result<void> Service::CheckConsole() {"):
                      service.index("void Service::ConfigureMemcg()")]
    ordered(console, 'GetProperty("ro.boot.serialconsole", "") == "0"',
            "flags_ |= SVC_DISABLED;", 'if (proc_attr_.console.empty())')
    # Only the normal product changes. Recovery keeps its own console/ADB init path.
    if "ctl.stop" in native or "ro.debuggable=0" in native:
        raise ValueError("Console fix must not disable debugging globally")


class PolicyTest(unittest.TestCase):
    def test_current_init_contract(self):
        verify(*self.inputs)

    def test_missing_product_default_rejected(self):
        with self.assertRaisesRegex(ValueError, "native product"):
            verify(self.inputs[0].replace(DECLARATION, ""), *self.inputs[1:])

    def test_late_property_defaults_rejected(self):
        p = self.inputs[1].replace("PropertyLoadBootDefaults();", "PropertyLoadBootDefaultsLate();")
        with self.assertRaises(ValueError):
            verify(self.inputs[0], p, *self.inputs[2:])

    def test_missing_upstream_console_gate_rejected(self):
        service = self.inputs[3].replace(
            'GetProperty("ro.boot.serialconsole", "") == "0"',
            'GetProperty("unrelated", "") == "0"')
        with self.assertRaises(ValueError):
            verify(*self.inputs[:3], service)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--init-source", type=Path, required=True)
    args = parser.parse_args()
    device = Path(__file__).resolve().parents[2]
    core = args.init_source
    PolicyTest.inputs = (
        (device / "lineage_ldy_an00_native.mk").read_text(),
        (core / "init/property_service.cpp").read_text(),
        (core / "init/init.cpp").read_text(),
        (core / "init/service.cpp").read_text(),
    )
    unittest.main(argv=[__file__])
