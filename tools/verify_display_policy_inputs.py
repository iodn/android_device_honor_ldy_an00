#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Verify ignored display-policy sources and generated private payloads."""
import argparse,hashlib,json
from pathlib import Path,PurePosixPath

def inventory(root):
 data=json.loads((root/"display-policy-inputs.json").read_text())
 if data["firmware"]!="10.0.0.113(C00E100R101P1)":raise ValueError("Unsupported display firmware")
 rows=data["source_inputs"]+data["generated"]
 if len(rows)!=10 or len({r["proprietary_path"] for r in rows})!=10:raise ValueError("Display inventory")
 for row in rows:
  rel=PurePosixPath(row["proprietary_path"])
  if rel.is_absolute() or ".." in rel.parts or not str(rel).startswith("displayplugin/stock-policy-assets/proprietary/"):
   raise ValueError("Private display input path")
 return data

def verify(root,sources_only=False):
 data=inventory(root)
 rows=data["source_inputs"] if sources_only else data["source_inputs"]+data["generated"]
 for row in rows:
  path=root/row["proprietary_path"]
  if path.is_symlink() or not path.is_file():raise ValueError("Missing private display input: "+row["proprietary_path"])
  raw=path.read_bytes()
  if len(raw)!=row["size"] or hashlib.sha256(raw).hexdigest()!=row["sha256"]:
   raise ValueError("Private display input mismatch: "+row["proprietary_path"])
 return data

if __name__=="__main__":
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument("--device-root",type=Path,default=Path(__file__).resolve().parents[1])
 args=parser.parse_args();data=verify(args.device_root)
 print("DISPLAY_POLICY_INPUTS_OK",len(data["source_inputs"])+len(data["generated"]))
