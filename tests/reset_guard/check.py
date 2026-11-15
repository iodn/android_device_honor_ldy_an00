#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Exercise production reset field logic, init controller, policy and credential gate."""
import argparse
from pathlib import Path
import subprocess
import tempfile

p = argparse.ArgumentParser()
for name in ("kernel", "framework", "init"):
    p.add_argument("--" + name, required=True, type=Path)
p.add_argument("--javac", default="javac")
p.add_argument("--java", default="java")
a = p.parse_args()
here = Path(__file__).resolve().parent

def method(text, marker):
    start = text.index(marker)
    opening = text.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (text[end] == "{") - (text[end] == "}")
        end += 1
    return text[start:end]

with tempfile.TemporaryDirectory() as temporary:
    out = Path(temporary)
    for source, compiler, include, standard in (
        ("fields_test.c", "cc", a.kernel / "drivers/platform", "c11"),
        ("controller_test.cpp", "c++", a.init / "init", "c++17"),
    ):
        exe = out / source.split(".")[0]
        subprocess.run([compiler, "-std=" + standard, "-Wall", "-Wextra", "-Werror",
                        "-I", str(include), str(here / source), "-o", str(exe)], check=True)
        subprocess.run([exe], check=True)
    source = (a.framework / "services/core/java/com/android/server/policy/keyguard/LockedResetGuard.java").read_text()
    predicate = method(source, "static boolean shouldProtect(")
    tests = """
    static void check(boolean expected, boolean enabled, boolean showing,
                      boolean userUnlocked, boolean deviceLocked) {
        if (shouldProtect(enabled, showing, userUnlocked, deviceLocked) != expected)
            throw new AssertionError();
    }
    public static void main(String[] args) {
        check(false, false, true, false, true); // disabled before first unlock
        check(true, true, true, false, true); // opted-in before first unlock
        check(false, true, false, true, false); // successful unlock
        check(true, true, true, true, true); // relocked
        check(true, true, false, true, true); // camera occludes secure keyguard
        check(true, true, false, false, false); // new locked user, stale callback
        check(false, false, true, true, true); // setting disabled while locked
        System.out.println("RESET_LOCK_POLICY_OK");
    }
    """
    (out / "ResetPolicyTest.java").write_text("public class ResetPolicyTest {" + predicate + tests + "}")
    subprocess.run([a.javac, str(out / "ResetPolicyTest.java")], check=True)
    subprocess.run([a.java, "-cp", str(out), "ResetPolicyTest"], check=True)
    native = (a.init / "init/property_service.cpp").read_text()
    gate = method(native, 'if (name == "sys.ldy_security.reset_protected") {')
    code = r"""
#include <cassert>
#include <cstdint>
#include <string>
using std::string;
constexpr int AID_SYSTEM = 1000, PROP_ERROR_PERMISSION_DENIED = 1,
    PROP_ERROR_INVALID_VALUE = 2, PROP_SUCCESS = 0;
int calls;
int auto_reboot_handle_property_set(const string&) { return 0; }
int locked_reset_guard_handle_property_set(bool) { ++calls; return 0; }
int apply(const string& name, const string& value, unsigned uid) {
    struct { unsigned uid; } cr{uid};
    """ + gate + r"""
    return 0;
}
int main() {
    const string key = "sys.ldy_security.reset_protected";
    for (auto uid : {0u, 2000u, 10001u}) {
        assert(apply(key, "1", uid) == PROP_ERROR_PERMISSION_DENIED);
        assert(calls == 0);
    }
    for (const auto& value : {"", "true", "2", "off", "1\n"})
        assert(apply(key, value, 1000) == PROP_ERROR_INVALID_VALUE);
    assert(calls == 0);
    assert(apply(key, "1", 1000) == 0 && calls == 1);
    assert(apply(key, "0", 1000) == 0 && calls == 2);
}
"""
    (out / "gate.cpp").write_text(code)
    subprocess.run(["c++", "-std=c++17", "-Wall", "-Wextra", "-Werror", str(out / "gate.cpp"),
                    "-o", str(out / "gate")], check=True)
    subprocess.run([out / "gate"], check=True)
    print("RESET_CREDENTIAL_GATE_OK")
print("RESET_GUARD_HOST_TESTS_OK")
