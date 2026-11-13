#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Exercise production timer and USB accounting with simulated OS operations."""
import argparse
import subprocess
import tempfile
from pathlib import Path
import xml.etree.ElementTree as ET


def method(text, signature):
    start = text.index(signature)
    opening = text.index('{', start)
    depth, end = 1, opening + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[start:end]


def native_timer(core, temporary):
    text = (core / 'init/property_service.cpp').read_text()
    start = text.index('// Adapted from GrapheneOS\'s init auto-reboot timer')
    end = text.index('// This returns one of the enum of PROP_SUCCESS or PROP_ERROR*, or std::nullopt', start)
    production = text[start:end].replace('[[clang::no_destroy]] ', '')
    admission = method(text, '    if (name == "sys.ldy_security.reboot_ctl" || name == "sys.ldy_security.reboot_inhibit") {')
    dispatcher = ('std::optional<unsigned int> dispatch_security_property('
                  'const std::string& name, const std::string& value, unsigned int uid) {'
                  'struct { unsigned int uid; } cr{uid};' + admission + 'return std::nullopt;}')
    prefix = r"""
#include <signal.h>
#include <time.h>
#include <cerrno>
#include <cstring>
#include <iostream>
#include <mutex>
#include <string>
#include <charconv>
#include <cassert>
#include <optional>
#define AID_SYSTEM 1000
#define PROP_ERROR_PERMISSION_DENIED 3
static bool inhibited = true, fail_create = false, fail_set = false, fail_get = false;
static int shutdowns, creates, sets;
static itimerspec pending = {};
static int fake_create(clockid_t clock, sigevent*, timer_t* timer) {
    assert(clock == CLOCK_BOOTTIME_ALARM);
    ++creates; *timer = (timer_t)1; return fail_create ? -1 : 0;
}
static int fake_set(timer_t, int, const itimerspec* duration, itimerspec*) {
    ++sets; if (fail_set) return -1; pending = *duration; return 0;
}
static int fake_get(timer_t, itimerspec* remaining) {
    *remaining = pending; return fail_get ? -1 : 0;
}
#define timer_create fake_create
#define timer_settime fake_set
#define timer_gettime fake_get
#define LOG(level) std::cerr
#define PROP_SUCCESS 0
#define PROP_ERROR_SET_FAILED 1
#define PROP_ERROR_INVALID_VALUE 2
namespace android::base {
bool GetBoolProperty(const char*, bool) { return inhibited; }
bool ParseUint(const std::string& value, unsigned int* result) {
    auto parsed = std::from_chars(value.data(), value.data() + value.size(), *result);
    return parsed.ec == std::errc() && parsed.ptr == value.data() + value.size();
}
}
static void trigger_shutdown(const char* command) {
    assert(std::string(command) == "reboot,security"); ++shutdowns;
}
"""
    cases = r"""
static void expire() { pending = {}; auto_reboot_timer_callback({}); }
int main() {
    assert(auto_reboot_handle_property_set("600") == 0);
    assert(!auto_reboot_armed && creates == 0);
    for (const char* invalid : {"", "-1", "599", "259201", "600junk", "999999999999999999999"})
        assert(auto_reboot_handle_property_set(invalid) == PROP_ERROR_INVALID_VALUE);
    assert(auto_reboot_handle_property_set("on_device_unlocked") == 0);
    fail_create = true;
    assert(auto_reboot_handle_property_set("600") == PROP_ERROR_SET_FAILED);
    assert(!auto_reboot_armed);
    fail_create = false; fail_set = true;
    assert(auto_reboot_handle_property_set("600") == PROP_ERROR_SET_FAILED);
    assert(!auto_reboot_armed);
    fail_set = false;
    assert(auto_reboot_handle_property_set("600") == 0);
    int previous_sets = sets;
    assert(auto_reboot_handle_property_set("3600") == 0);
    assert(sets == previous_sets);
    expire(); assert(shutdowns == 0 && pending.it_value.tv_sec == 60);
    inhibited = false;
    expire(); assert(shutdowns == 1 && !auto_reboot_armed);
    assert(auto_reboot_handle_property_set("600") == 0);
    fail_set = true;
    assert(auto_reboot_handle_property_set("on_device_unlocked") == PROP_ERROR_SET_FAILED);
    assert(!auto_reboot_armed);
    fail_set = false;
    expire(); assert(shutdowns == 1);
    assert(auto_reboot_handle_property_set("600") == 0);
    auto_reboot_timer_callback({}); assert(shutdowns == 1);
    assert(auto_reboot_handle_property_set("off") == 0);
    expire(); assert(shutdowns == 1);
    assert(auto_reboot_handle_property_set("259200") == 0);
    fail_get = true; expire(); assert(shutdowns == 1);
    fail_get = false; expire(); assert(shutdowns == 2);
    assert(auto_reboot_handle_property_set("600") == 0);
    inhibited = true; fail_set = true; expire();
    assert(shutdowns == 2 && !auto_reboot_armed);
    for (unsigned int uid : {0u, 2000u, 10000u, 101000u}) {
        assert(dispatch_security_property("sys.ldy_security.reboot_ctl", "off", uid) == 3);
        assert(dispatch_security_property("sys.ldy_security.reboot_inhibit", "0", uid) == 3);
    }
    fail_set = false;
    assert(dispatch_security_property("sys.ldy_security.reboot_ctl", "off", 1000) == 0);
    assert(!dispatch_security_property("sys.ldy_security.reboot_inhibit", "0", 1000).has_value());
    std::cout << "INIT_TIMER_TESTS_OK\n";
}
"""
    cpp = temporary / 'timer.cpp'
    cpp.write_text(prefix + production + dispatcher + cases)
    subprocess.run(['g++', '-std=c++17', '-Wall', '-Wextra', '-Werror', '-pthread',
                    str(cpp), '-o', str(temporary / 'timer')], check=True)
    subprocess.run([str(temporary / 'timer')], check=True)


def usb_accounting(framework, temporary):
    root = framework / 'services/usb/java'
    text = (root / 'com/android/server/usb/UsbService.java').read_text()
    production = method(text, 'private boolean shouldUpdateUsbSignaling(')
    helper_path = temporary / 'com/android/server/usb/UsbDataSignalDisableRequesters.java'
    helper_path.parent.mkdir(parents=True)
    helper_path.write_text((root / 'com/UsbDataSignalDisableRequesters.java').read_text())
    arrayset = temporary / 'android/util/ArraySet.java'
    arrayset.parent.mkdir(parents=True)
    arrayset.write_text('package android.util; public class ArraySet<E> extends java.util.HashSet<E> {}')
    flags = temporary / 'android/hardware/usb/flags/Flags.java'
    flags.parent.mkdir(parents=True)
    flags.write_text('package android.hardware.usb.flags; public class Flags {'
                    'public static boolean enableUsbDataSignalStakingInternal() {return false;} }')
    source = """package com.android.server.usb;
import java.util.HashMap;
public class UsbRequestsTest {
 private Object mUsbLockdown = new Object();
 private final HashMap<String, UsbDataSignalDisableRequesters> mUsbDisableRequesters = new HashMap<>();
""" + production + """
 private boolean request(boolean enable, int requester, boolean internal) {
    return shouldUpdateUsbSignaling("port0", enable, requester, internal);
 }
 private static void check(boolean value) { if (!value) throw new AssertionError(); }
 public static void main(String[] args) {
    UsbRequestsTest p = new UsbRequestsTest();
    int lock = 0x4c4459;
    check(p.request(false, 12001, false));
    for (int i=0; i<5; ++i) {
      check(p.request(false, lock, true));
      check(!p.request(true, lock, true));
    }
    check(p.request(true, 12001, false));
    check(p.request(false, lock, true));
    check(!p.request(true, 1000, false));
    check(p.request(false, 2, true));
    check(!p.request(true, lock, true));
    check(p.request(true, 2, true));
    p.mUsbLockdown = null;
    check(!p.request(false, lock, true));
    System.out.println("USB_REQUEST_ACCOUNTING_TESTS_OK");
 }
}
"""
    java = helper_path.with_name('UsbRequestsTest.java')
    java.write_text(source)
    subprocess.run(['javac', '--release', '17', '-d', str(temporary), str(helper_path),
                    str(arrayset), str(flags), str(java)], check=True)
    subprocess.run(['java', '-cp', str(temporary), 'com.android.server.usb.UsbRequestsTest'], check=True)


def authorization(args):
    ns = '{http://schemas.android.com/apk/res/android}'
    manifest = ET.parse(args.phone / 'AndroidManifest.xml').getroot()
    permission = next(e for e in manifest.findall('permission')
                      if e.get(ns + 'name') == 'com.android.phone.permission.HONOR_LDY_AN00_LPA_LEASE')
    assert permission.get(ns + 'protectionLevel') == 'signature'
    services = {e.get(ns + 'name'): e for e in manifest.find('application').findall('service')}
    assert services['com.android.internal.telephony.honor.HonorLpaLeaseService'].get(ns+'permission') == permission.get(ns+'name')
    assert services['com.android.phone.HonorLdyAn00EsimSetupService'].get(ns+'permission') == 'android.permission.MODIFY_PHONE_STATE'
    lease = (args.phone / 'src/com/android/internal/telephony/honor/HonorLpaLeaseService.java').read_text()
    for guard in ['enforceCallingPermission(PERMISSION', 'UserHandle.getUserId(uid)!=0',
                  'validateSelectedCaller(uid)', 'pm.checkSignatures("android",selected.packageName)']:
        assert guard in lease, guard
    setup = (args.phone / 'src/com/android/phone/HonorLdyAn00EsimSetupService.java').read_text()
    assert 'if (!admitted(msg.sendingUid)) return;' in setup
    face = (args.frameworks_base / 'core/java/android/hardware/face/HonorLdyAn00FaceVendorClient.java').read_text()
    assert 'Binder.getCallingUid() != android.os.Process.SYSTEM_UID' in face
    for relative in ['app/src/main/AndroidManifest.xml', 'app-common/src/main/AndroidManifest.xml']:
        root = ET.parse(args.openeuicc / relative).getroot()
        for service in root.find('application').findall('service'):
            if service.get(ns+'exported') == 'true':
                assert service.get(ns+'permission') == 'android.permission.BIND_EUICC_SERVICE'
    for file in ['device_protection_settings.xml', 'more_security_privacy_settings.xml', 'security_advanced_settings.xml']:
        ET.parse(args.settings / 'res/xml' / file)
    controller = (args.settings / 'src/com/android/settings/security/DeviceProtectionSettings.java').read_text()
    assert 'Failure to launch authentication must not silently authorize a change.' in controller
    policy = (Path(__file__).resolve().parents[2] / 'sepolicy/system_ext/private/ldy_an00_security.te').read_text()
    assert 'allow init self:global_capability2_class_set wake_alarm;' in policy
    print('CUSTOM_INTEGRATION_AUTHORIZATION_CHECKS_OK')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['frameworks-base', 'system-core', 'settings', 'phone', 'openeuicc']:
        parser.add_argument('--' + name, required=True, type=Path)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory() as temp:
        temporary = Path(temp)
        native_timer(args.system_core, temporary)
        usb_accounting(args.frameworks_base, temporary)
        authorization(args)
    print('SECURITY_HOST_CHECKS_OK')


if __name__ == '__main__':
    main()
