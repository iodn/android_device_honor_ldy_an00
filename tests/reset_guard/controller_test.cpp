/* SPDX-License-Identifier: Apache-2.0 */
#include <cassert>
#include <iostream>
#include <map>
#include "locked_reset_guard.h"
using Guard = android::init::LockedResetGuard;
struct Io {
 std::map<std::string, std::string> files;
 std::string fail_write, fail_read;
 int control_writes = 0, diagnostic_writes = 0;
 Guard::ReadResult Read(const std::string& p, std::string* v) {
  if (p == fail_read) return Guard::ReadResult::ERROR;
  auto i = files.find(p);
  if (i == files.end()) return Guard::ReadResult::MISSING;
  *v = i->second; return Guard::ReadResult::OK;
 }
 bool Write(const std::string& p, const std::string& v) {
  if (p == fail_write) return false;
  if (p == Guard::kControl) control_writes++; else diagnostic_writes++;
  files[p] = v; return true;
 }
};
int main() {
 for (const std::string original : {"0", "1", "2", "-1", "2147483647"}) {
  Guard g; Io io; io.files[Guard::kDiagnostic] = original;
  for (int round = 0; round < 5; round++) {
   assert(g.Apply(true, io)); assert(g.Apply(true, io)); // lock and duplicate request
   assert(io.files[Guard::kDiagnostic] == "0");
   assert(g.Apply(false, io)); assert(io.files[Guard::kDiagnostic] == original);
   assert(io.files[Guard::kControl] == "0");
  }
 }
 { Guard g; Io io; io.files[Guard::kDiagnostic] = "1"; io.fail_write = Guard::kControl;
   assert(!g.Apply(true, io)); assert(io.diagnostic_writes == 0); }
 { Guard g; Io io; io.files[Guard::kDiagnostic] = "1"; io.fail_write = Guard::kDiagnostic;
   assert(!g.Apply(true, io)); io.fail_write.clear(); assert(g.Apply(true, io));
   io.fail_write = Guard::kDiagnostic; assert(!g.Apply(false, io));
   io.fail_write.clear(); assert(g.Apply(false, io)); assert(io.files[Guard::kDiagnostic] == "1"); }
 { Guard g; Io io; io.files[Guard::kDiagnostic] = "1"; io.fail_read = Guard::kDiagnostic;
   assert(!g.Apply(true, io)); assert(io.diagnostic_writes == 0);
   io.fail_read.clear(); assert(g.Apply(true, io)); assert(g.Apply(false, io));
   assert(io.files[Guard::kDiagnostic] == "1"); }
 { Guard g; Io io; assert(g.Apply(true, io)); assert(g.Apply(false, io)); }
 for (const std::string invalid : {"", "unknown", "1x", "2147483648"}) {
  Guard g; Io io; io.files[Guard::kDiagnostic] = invalid;
  assert(!g.Apply(true, io)); assert(io.diagnostic_writes == 0);
 }
 std::cout << "RESET_CONTROLLER_OK: diagnostic restoration, duplicates and error recovery\n";
}
