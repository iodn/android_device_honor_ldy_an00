#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# SPDX-License-Identifier: Apache-2.0
"""Host-only packaging guard test; use an archived qualified APK, never OUT."""
import argparse,hashlib,json,subprocess,sys,tempfile,zipfile
from pathlib import Path
ap=argparse.ArgumentParser(description=__doc__);ap.add_argument("--qualified-compat-apk",type=Path,required=True);a=ap.parse_args()
R=Path(__file__).resolve().parents[1]
sha=lambda b:hashlib.sha256(b).hexdigest()
if sha(a.qualified_compat_apk.read_bytes())!="79f2cd0f0534224fe078112c79dfb6945b6579371c98ce3818ac18cd4981959c":raise SystemExit("REFUSE fixture APK hash")
stock=R/"proprietary/ImsService.apk";patched=R/"proprietary/ims-chr-compat.dex";checks=[]
original_sha=sha(stock.read_bytes());script_pins={p.name:sha(p.read_bytes()) for p in (R/"tools").glob("*.py")}
def run(tool,args,ok,needle=None):
 p=subprocess.run([sys.executable,str(R/"tools"/tool),*map(str,args)],capture_output=True,text=True,timeout=30)
 assert (p.returncode==0)==ok,(tool,p.returncode,p.stderr,p.stdout)
 if needle:assert needle in p.stdout+p.stderr
 return p
with tempfile.TemporaryDirectory(prefix="ims-chr-guards-") as tmp:
 T=Path(tmp);draft=T/"manifest.apk";jar=T/"compat.jar";out=T/"result.apk"
 run("prepare-ims-manifest.py",[stock,draft],True)
 with zipfile.ZipFile(a.qualified_compat_apk) as z,zipfile.ZipFile(jar,"x") as j:j.writestr("classes.dex",z.read("classes2.dex"))
 p=run("append-ims-compat.py",[stock,draft,jar,patched,out],True)
 report=json.loads(p.stdout);assert report["stock_dex_unchanged"] is False
 with zipfile.ZipFile(stock) as s,zipfile.ZipFile(draft) as d,zipfile.ZipFile(jar) as j,zipfile.ZipFile(out) as o:
  assert o.namelist()==d.namelist()+["classes2.dex"]
  for n in s.namelist():
   assert o.read(n)==(patched.read_bytes() if n=="classes.dex" else d.read(n))
   if n not in ("classes.dex","AndroidManifest.xml"):assert o.read(n)==s.read(n)
  assert o.read("classes2.dex")==j.read("classes.dex")
 checks.append("positive_exact_replacement_other_entries_and_14class_guards")
 before=out.read_bytes();run("append-ims-compat.py",[stock,draft,jar,patched,out],False,"existing")
 assert out.read_bytes()==before;checks.append("existing_output_refused_unchanged")
 wrong=T/"wrong.dex"
 with zipfile.ZipFile(stock) as z:wrong.write_bytes(z.read("classes.dex"))
 rejected=T/"wrong-output.apk";run("append-ims-compat.py",[stock,draft,jar,wrong,rejected],False,"CHR DEX hash")
 assert not rejected.exists() and not rejected.with_name(rejected.name+".partial").exists();checks.append("wrong_chr_hash_no_output")
 wrong_apk=T/"wrong.apk";wrong_apk.write_bytes(b"wrong original")
 run("append-ims-compat.py",[wrong_apk,draft,jar,patched,T/"wrong-original.apk"],False,"stock hash");checks.append("wrong_original_hash_refused")
 partial=T/"partial.apk";partial.with_name(partial.name+".partial").write_bytes(b"retain")
 run("append-ims-compat.py",[stock,draft,jar,patched,partial],False,"existing")
 assert partial.with_name(partial.name+".partial").read_bytes()==b"retain";checks.append("existing_partial_refused_preserved")
 # Reproducer fails before invoking any Java on these negative paths.
 base=["--stock-apk",wrong_apk,"--java",wrong,"--baksmali",wrong,"--smali",wrong,"--output",T/"repro.dex"]
 run("reproduce-ims-chr.py",base,False,"stock APK hash");checks.append("reproducer_wrong_original_before_tools")
 clobber=T/"repro.dex";clobber.write_bytes(b"preserve")
 base[1]=stock;run("reproduce-ims-chr.py",base,False,"existing output")
 assert clobber.read_bytes()==b"preserve";checks.append("reproducer_existing_output_refused")
 assert sha(stock.read_bytes())==original_sha
 assert {p.name:sha(p.read_bytes()) for p in (R/"tools").glob("*.py")}==script_pins
 checks.append("original_and_tools_unchanged")
print(json.dumps({"passed":True,"checks":checks,"check_count":len(checks),"tool_pins":script_pins,"scope":"Host packaging guards, not Android/ART/runtime qualification."},indent=2))
