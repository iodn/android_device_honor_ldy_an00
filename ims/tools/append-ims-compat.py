#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# SPDX-License-Identifier: Apache-2.0
"""Apply reviewed CHR classes.dex and append app-local classes2.dex; no signing/install/device operation."""
import copy,hashlib,json,os,struct,sys,zipfile,zlib
from pathlib import Path
STOCK="9b91d69f0062c9b50c8f30f6cee0cc8b6a49976cceaf060b1f223f15140340d5"
PATCHED_CHR="84487a11317cf7d645f8eb0b45f0ed916283923c744b655c771a686750c905f8"
ORIGINAL_DEX="38a5d3cf47ba6ed4adaf899f6f9b22a19cdf5c95adc4c8476a727ba69b1b0c4c"
MANIFEST="f2ebd722df41b772fa7bd4e826b67d5463ae1595cbfbbf186f1bdc6f3b0cad99"
OWN={
"Lcom/hihonor/android/ims/HwImsManager;","Lcom/hihonor/android/os/SystemPropertiesEx;",
"Lcom/hihonor/android/telephony/SubscriptionManagerEx;","Lcom/hihonor/android/cust/HwCfgFilePolicy;",
"Lhihonor/cust/HwCfgFilePolicy;","Lcom/hihonor/chr/HnChrReportConnImpl;",
"Lcom/android/ims/IHwImsUtManager;","Lcom/android/ims/IHwImsUtManager$Default;",
"Lcom/android/ims/IHwImsUtManager$Stub;","Lcom/android/ims/IHwImsUtManager$Stub$Proxy;",
"Lcom/android/internal/telephony/metrics/TelephonyMetrics;",
"Lcom/android/internal/telephony/HwChrServiceManager;",
"Lcom/android/internal/telephony/DefaultHwChrServiceManager;",
"Lcom/android/internal/telephony/HwTelephonyFactory;"}
def sha(b):return hashlib.sha256(b).hexdigest()
def classes(b):
 if b[:4]!=b"dex\n" or len(b)<112:raise ValueError("not standard DEX")
 u=lambda p:struct.unpack_from("<I",b,p)[0]
 if u(32)!=len(b) or u(8)!=zlib.adler32(b[12:]) or b[12:32]!=hashlib.sha1(b[32:]).digest():raise ValueError("DEX integrity")
 strings=[]
 for i in range(u(56)):
  p=u(u(60)+4*i)
  while b[p]&128:p+=1
  p+=1;strings.append(b[p:b.index(0,p)])
 types=[strings[u(u(68)+4*i)].decode("utf8") for i in range(u(64))]
 out=[types[u(u(100)+32*i)] for i in range(u(96))]
 if len(out)!=len(set(out)):raise ValueError("duplicate DEX type")
 return set(out)
if len(sys.argv)!=6:raise SystemExit("usage: append-ims-compat.py STOCK_APK MANIFEST_ONLY_APK COMPAT_DEX_JAR REVIEWED_CHR_DEX NEW_UNSIGNED_APK")
stock,draft,jar,chr_dex,out=map(Path,sys.argv[1:]);partial=out.with_name(out.name+".partial")
if out.exists() or partial.exists():raise SystemExit("REFUSE existing output/partial")
if sha(stock.read_bytes())!=STOCK:raise SystemExit("REFUSE stock hash")
chr_bytes=chr_dex.read_bytes()
if sha(chr_bytes)!=PATCHED_CHR:raise SystemExit("REFUSE reviewed CHR DEX hash")
with zipfile.ZipFile(stock) as s,zipfile.ZipFile(draft) as a,zipfile.ZipFile(jar) as j:
 names=a.namelist()
 if len(names)!=len(set(names)) or names!=s.namelist() or [x for x in names if x.endswith(".dex")]!=["classes.dex"]:raise SystemExit("REFUSE changed entries/multidex")
 for n in names:
  expected=MANIFEST if n=="AndroidManifest.xml" else sha(s.read(n))
  if sha(a.read(n))!=expected:raise SystemExit("REFUSE changed original entry "+n)
 if [n for n in j.namelist() if n.endswith(".dex")]!=["classes.dex"]:raise SystemExit("REFUSE non-single compatibility DEX")
 compat=j.read("classes.dex");defined=classes(compat);original=classes(s.read("classes.dex"))
 if sha(s.read("classes.dex"))!=ORIGINAL_DEX or len(original)!=4926 or classes(chr_bytes)!=original:raise SystemExit("REFUSE CHR original class set")
 if defined!=OWN or defined&original:raise SystemExit("REFUSE changed/duplicate compatibility classes")
 for token in [b"setResponseFunctionsHn",b"vendor/honor/hardware/hnradio",b"vendor.honor.hardware.hnradio",b"HwRILReferenceImpl",b"HnRadioServiceProxy"]:
  if token in compat:raise SystemExit("REFUSE Honor radio callback owner in compatibility DEX")
 expected={n:sha(a.read(n)) for n in names};expected["classes2.dex"]=sha(compat);expected["classes.dex"]=PATCHED_CHR
 with zipfile.ZipFile(partial,"x") as w:
  for i in a.infolist():w.writestr(copy.copy(i),chr_bytes if i.filename=="classes.dex" else a.read(i.filename))
  info=zipfile.ZipInfo("classes2.dex",(2009,1,1,0,0,0));info.compress_type=zipfile.ZIP_STORED;w.writestr(info,compat)
with zipfile.ZipFile(partial) as z:
 if {n:sha(z.read(n)) for n in z.namelist()}!=expected:raise SystemExit("REFUSE unexpected final entries; preserve partial")
if sha(stock.read_bytes())!=STOCK:raise SystemExit("REFUSE original changed")
os.link(partial,out);partial.unlink()
print(json.dumps({"status":"UNSIGNED_APP_LOCAL_COMPAT_PENDING_NORMAL_SIGNING_AND_ABI_REVIEW","output_sha256":sha(out.read_bytes()),"compat_dex_sha256":sha(compat),"exact_new_classes":sorted(defined),"stock_dex_unchanged":False,"stock_dex_change":"reviewed app-local CHR3class4method compatibility patch","original_dex_sha256":ORIGINAL_DEX,"reviewed_chr_dex_sha256":PATCHED_CHR,"other_original_entries_unchanged_except_reviewed_manifest":True}))
