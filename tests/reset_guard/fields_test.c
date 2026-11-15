/* SPDX-License-Identifier: GPL-2.0-only */
#include <assert.h>
#include <stdio.h>
#include "ldy_reset_guard_fields.h"
struct memory { unsigned int enable, mode; int call, fail, ignore; };
static int read_reg(void *p, unsigned int reg, unsigned int *v) {
 struct memory *m = p;
 if (++m->call == m->fail) return -EIO;
 *v = reg == LDY_RESET_ENABLE_REG ? m->enable : m->mode;
 return 0;
}
static int update_reg(void *p, unsigned int reg, unsigned int mask, unsigned int v) {
 struct memory *m = p;
 if (++m->call == m->fail) return -EIO;
 if (m->call != m->ignore) {
  unsigned int *out = reg == LDY_RESET_ENABLE_REG ? &m->enable : &m->mode;
  *out = (*out & ~mask) | (v & mask);
 }
 return 0;
}
int main(void) {
 unsigned int a, b;
 for (a = 0; a < 256; a++) for (b = 0; b < 256; b++) {
  struct memory m = { .enable = a, .mode = b };
  struct ldy_reset_io io = { &m, read_reg, update_reg };
  for (int round = 0; round < 5; round++) {
   assert(!ldy_reset_apply(&io, 1, a & 0x1c, b & 0x30));
   assert(m.enable == (a & ~0x1c));
   assert(m.mode == ((b & ~0x30) | 0x30));
   assert(!ldy_reset_apply(&io, 0, a & 0x1c, b & 0x30));
   assert(m.enable == a && m.mode == b);
  }
 }
 for (int protect = 0; protect < 2; protect++) for (int fail = 1; fail <= 4; fail++) {
  struct memory m = { .enable = protect ? 0x94 : 0x80, .mode = protect ? 0x62 : 0x72, .fail = fail };
  struct ldy_reset_io io = { &m, read_reg, update_reg };
  assert(ldy_reset_apply(&io, protect, 0x14, 0x20) == -EIO);
  assert(m.call == fail);
  m.fail = 0;
  assert(!ldy_reset_apply(&io, protect, 0x14, 0x20));
 }
 for (int ignore = 1; ignore <= 3; ignore += 2) {
  struct memory m = { .enable = 0x14, .mode = 0x20, .ignore = ignore };
  struct ldy_reset_io io = { &m, read_reg, update_reg };
  assert(ldy_reset_apply(&io, 1, 0x14, 0x20) == -EIO);
 }
 /* An ignored selection restore must never re-enable the reset sources. */
 {
  struct memory m = { .enable = 0x80, .mode = 0x72, .ignore = 1 };
  struct ldy_reset_io io = { &m, read_reg, update_reg };
  assert(ldy_reset_apply(&io, 0, 0x14, 0x20) == -EIO);
  assert(m.call == 2 && m.enable == 0x80);
 }
 puts("RESET_FIELDS_OK: 65536 register pairs, five round trips, access failures and ignored writes");
}
