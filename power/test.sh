#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TEST_DIR=$(mktemp -d)
trap 'rm -rf "$TEST_DIR"' EXIT HUP INT TERM
javac --release 17 -d "$TEST_DIR" \
    "$ROOT/power/src/org/lineageos/ldy_an00/power/PowerPolicy.java" \
    "$ROOT/power/src/org/lineageos/ldy_an00/power/PowerController.java" \
    "$ROOT/power/tests/org/lineageos/ldy_an00/power/PowerPolicyTest.java" \
    "$ROOT/power/tests/org/lineageos/ldy_an00/power/PowerControllerTest.java"
java -cp "$TEST_DIR" org.lineageos.ldy_an00.power.PowerPolicyTest
java -cp "$TEST_DIR" org.lineageos.ldy_an00.power.PowerControllerTest
python3 -m unittest discover -s "$ROOT/power" -p "test_mtk_power_config.py"
