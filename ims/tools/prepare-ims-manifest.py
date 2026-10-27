#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# SPDX-License-Identifier: Apache-2.0
"""Prepare an UNSIGNED manifest-only IMS draft. No DEX merge, signing or deployment."""
import copy,hashlib,json,os,struct,sys,zipfile
from pathlib import Path
APK_SHA="9b91d69f0062c9b50c8f30f6cee0cc8b6a49976cceaf060b1f223f15140340d5"
MANIFEST_SHA="6e6e05855b66077af0e03eed6e247222a4a5638a5731b61b32ca9dd1ed217de4"
NAMES={"com.mediatek.ims.oemplugin","com.mediatek.ims.plugin"}
def sha(b):return hashlib.sha256(b).hexdigest()
def rewrite(b):
 if struct.unpack_from("<HHI",b,0)!=(3,8,len(b)):raise ValueError("XML header")
 chunks=[];p=8;strings=None
 while p<len(b):
  typ,head,size=struct.unpack_from("<HHI",b,p)
  if size<head or head<8 or p+size>len(b):raise ValueError("chunk bounds")
  c=b[p:p+size];chunks.append((typ,c));p+=size
  if typ!=1:continue
  if strings is not None:raise ValueError("multiple string pools")
  count,_,flags,start,_=struct.unpack_from("<IIIII",c,8);strings=[]
  def length8(q):
   n=c[q];q+=1
   if n&128:n=((n&127)<<8)|c[q];q+=1
   return n,q
  def length16(q):
   n=struct.unpack_from("<H",c,q)[0];q+=2
   if n&32768:n=((n&32767)<<16)|struct.unpack_from("<H",c,q)[0];q+=2
   return n,q
  for i in range(count):
   q=start+struct.unpack_from("<I",c,head+4*i)[0]
   if flags&256:
    _,q=length8(q);n,q=length8(q);s=c[q:q+n].decode("utf8")
   else:n,q=length16(q);s=c[q:q+2*n].decode("utf-16le")
   strings.append(s)
 if p!=len(b) or strings is None:raise ValueError("XML chunks/pool")
 stack=[];remove=set();seen=set()
 for i,(typ,c) in enumerate(chunks):
  if typ==0x102:
   ns,name=struct.unpack_from("<II",c,16);tag=strings[name];mark=False
   if tag=="uses-library":
    attrstart,attrsize,count=struct.unpack_from("<HHH",c,24)
    if attrsize!=20:raise ValueError("attribute size")
    attrs={}
    for j in range(count):
     a=16+attrstart+j*attrsize;nsi,nm,raw=struct.unpack_from("<III",c,a)
     size,zero,t,data=struct.unpack_from("<HBBI",c,a+12)
     if size!=8 or zero or nsi==0xffffffff or strings[nsi]!="http://schemas.android.com/apk/res/android":raise ValueError("uses-library namespace")
     key=strings[nm]
     if key in attrs:raise ValueError("duplicate attribute")
     attrs[key]=(t,data,raw)
    if "name" not in attrs or attrs["name"][0]!=3:raise ValueError("library name")
    lib=strings[attrs["name"][1]]
    if lib in NAMES:
     if lib in seen or count!=2 or set(attrs)!={"name","required"} or attrs["required"][:2]!=(18,0):raise ValueError("expected optional library")
     if not stack or stack[-1][0]!="application":raise ValueError("not application child")
     seen.add(lib);mark=True;remove.add(i)
   stack.append((tag,i,mark,ns,name))
  elif typ==0x103:
   ns,name=struct.unpack_from("<II",c,16)
   if not stack:raise ValueError("unbalanced end")
   tag,start,mark,expected_ns,expected_name=stack.pop()
   if (ns,name)!=(expected_ns,expected_name):raise ValueError("mismatched end")
   if mark:
    if i!=start+1:raise ValueError("library node has content")
    remove.add(i)
 if stack or seen!=NAMES:raise ValueError("missing exact libraries")
 out=b[:8]+b"".join(c for i,(_,c) in enumerate(chunks) if i not in remove)
 out=out[:4]+struct.pack("<I",len(out))+out[8:]
 return out,sorted(seen)
if len(sys.argv)!=3:raise SystemExit("usage: prepare-ims-manifest.py STOCK_APK NEW_UNSIGNED_MANIFEST_ONLY_APK")
src,out=map(Path,sys.argv[1:]);partial=out.with_name(out.name+".partial")
if out.exists() or partial.exists():raise SystemExit("REFUSE existing output/partial")
raw=src.read_bytes()
if sha(raw)!=APK_SHA:raise SystemExit("REFUSE stock APK hash")
with zipfile.ZipFile(src) as z:
 infos=z.infolist();names=[i.filename for i in infos]
 if len(names)!=len(set(names)) or [n for n in names if n.endswith(".dex")]!=["classes.dex"]:raise SystemExit("REFUSE duplicate/multidex input")
 manifest=z.read("AndroidManifest.xml")
 if sha(manifest)!=MANIFEST_SHA:raise SystemExit("REFUSE manifest hash")
 changed,removed=rewrite(manifest);original={i.filename:sha(z.read(i.filename)) for i in infos}
 with zipfile.ZipFile(partial,"x") as w:
  for i in infos:w.writestr(copy.copy(i),changed if i.filename=="AndroidManifest.xml" else z.read(i.filename))
with zipfile.ZipFile(partial) as z:
 actual={n:sha(z.read(n)) for n in z.namelist()}
 expected=dict(original);expected["AndroidManifest.xml"]=sha(changed)
 if actual!=expected:raise SystemExit("REFUSE unintended APK entry changes; preserve partial")
if sha(src.read_bytes())!=APK_SHA:raise SystemExit("REFUSE input changed")
os.link(partial,out)  # Atomic no-clobber publication; preserve partial on collision.
partial.unlink()
print(json.dumps({"status":"UNSIGNED_MANIFEST_ONLY_NOT_INSTALLABLE_CANDIDATE","input_sha256":APK_SHA,"output_sha256":sha(out.read_bytes()),"manifest_before":MANIFEST_SHA,"manifest_after":sha(changed),"only_changed_entry":"AndroidManifest.xml","removed_optional_libraries":removed,"all_other_uncompressed_entry_hashes_unchanged":True,"compat_dex_not_yet_added":True}))
