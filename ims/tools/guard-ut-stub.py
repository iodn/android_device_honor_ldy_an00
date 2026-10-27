#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# SPDX-License-Identifier: Apache-2.0
# Guarded post-generation transform; do not hand-edit or check in generated Java.
import hashlib
import sys
from pathlib import Path

if len(sys.argv) != 3:
    raise SystemExit("usage: guard-ut-stub.py GENERATED_JAVA NEW_OUTPUT_JAVA")
source, output = map(Path, sys.argv[1:])
if output.exists():
    raise SystemExit("REFUSE existing output")
raw = source.read_text()
offset = raw.index("package com.android.ims;")
body = raw[offset:]
if hashlib.sha256(body.encode()).hexdigest() != "108c9a11caf631bbcca2499456a721075604eca7bf1af2a363af903bcac8ac84":
    raise SystemExit("REFUSE changed generated contract/compiler output")
for name in ("updateCallForwardUncondTimer", "queryCallForwardForServiceClass", "processECT"):
    case = "case TRANSACTION_" + name + ":\n        {\n"
    if body.count(case) != 1:
        raise SystemExit("REFUSE changed transaction shape")
    body = body.replace(case, case + "          " + name + "_enforcePermission();\n", 1)
# Legacy timer IPC is unsupported; negative request ID triggers stock failure completion.
dispatch = "int _result = this.updateCallForwardUncondTimer(_arg0, _arg1, _arg2, _arg3, _arg4, _arg5, _arg6, _arg7);"
if body.count(dispatch) != 1:
    raise SystemExit("REFUSE changed timer dispatch")
body = body.replace(dispatch, "int _result = -1; // Timed CF unsupported; no vendor dispatch.", 1)
with output.open("x") as stream:
    stream.write(raw[:offset] + body)
