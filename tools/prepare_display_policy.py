#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Reproduce all four private display payloads from the qualified ignored source inputs."""
import argparse,hashlib,os
from pathlib import Path
import pack_display_panel,pack_display_thermal,pack_display_auxiliary
from verify_display_policy_inputs import verify

def prepare(root):
 data=verify(root,sources_only=True)
 src={row["id"]:root/row["proprietary_path"] for row in data["source_inputs"]}
 outputs={
 "stock-lcd-panel.json":pack_display_panel.generate(src["panel"]),
 "stock-manual-lux.json":pack_display_auxiliary.manual(src["manual"]),
 "thermal-display-policy.json":pack_display_thermal.build(src["thermal_custom"],src["thermal_asset"]),
 "stock-aps-fullscreen.json":pack_display_auxiliary.fullscreen(src["aps"],src["properties"])}
 pending=[]
 for row in data["generated"]:
  dest=root/row["proprietary_path"]
  raw=outputs[dest.name]
  if len(raw)!=row["size"] or hashlib.sha256(raw).hexdigest()!=row["sha256"]:
   raise ValueError("Generated display payload differs: "+dest.name)
  pending.append((dest,raw))
 # Validate every output before replacing any generated file.
 for dest,raw in pending:
  dest.parent.mkdir(parents=True,exist_ok=True)
  temp=dest.with_suffix(dest.suffix+".tmp")
  temp.write_bytes(raw);os.replace(temp,dest)
 verify(root)
 return len(pending)

if __name__=="__main__":
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument("--device-root",type=Path,default=Path(__file__).resolve().parents[1])
 args=parser.parse_args();print("DISPLAY_POLICY_PREPARED",prepare(args.device_root))
