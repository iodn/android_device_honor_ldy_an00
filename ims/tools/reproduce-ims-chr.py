#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# SPDX-License-Identifier: Apache-2.0
"""Reproduce pinned CHR repair and debug-only scalar SIP diagnostic DEX."""
from pathlib import Path
import atexit,argparse,hashlib,json,os,shutil,subprocess,tempfile,zipfile
STOCK="9b91d69f0062c9b50c8f30f6cee0cc8b6a49976cceaf060b1f223f15140340d5"
DEX="38a5d3cf47ba6ed4adaf899f6f9b22a19cdf5c95adc4c8476a727ba69b1b0c4c"
EXPECTED="84487a11317cf7d645f8eb0b45f0ed916283923c744b655c771a686750c905f8"
TOOL_PINS={"java":"cfa21aeadd00925925c9e1287f5c08f3e04ee7f26a9e23d9dbfb16149153d842",
"baksmali":"9b821ea330526f97231888402664b50617b81561e8b2d6bc21c55c358bc8440f",
"smali":"b8f790266a640a0de35391dc91fc12c7519ae4c5501f12496b138003fe4631ef"}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
ap=argparse.ArgumentParser(description=__doc__)
for name in ("stock-apk","java","baksmali","smali","output"):ap.add_argument("--"+name,type=Path,required=True)
opts=ap.parse_args()
for field in ("stock_apk","java","baksmali","smali"):setattr(opts,field,getattr(opts,field).resolve())
opts.output=opts.output.absolute()
if opts.output.exists() or opts.output.is_symlink():raise SystemExit("REFUSE existing output")
if sha(opts.stock_apk)!=STOCK:raise SystemExit("REFUSE stock APK hash")
for key,pin in TOOL_PINS.items():
 if sha(getattr(opts,key))!=pin:raise SystemExit("REFUSE "+key+" hash")
helper=Path(__file__).with_name("sip-diagnostic-method.smali")
if sha(helper)!="5de791238e64d93877bdc3f627e2fc7f44be8fa0c972fc4f4129f97787be6ca2":raise ValueError("REFUSE scalar diagnostic helper hash")
with zipfile.ZipFile(opts.stock_apk) as z:raw=z.read("classes.dex")
if hashlib.sha256(raw).hexdigest()!=DEX:raise SystemExit("REFUSE stock DEX hash")
def run(*args,cwd=None):subprocess.run([str(x) for x in args],cwd=cwd,check=True,timeout=180)
temp=tempfile.TemporaryDirectory(prefix="ims-chr-",dir=opts.output.parent)
atexit.register(temp.cleanup)
T=Path(temp.name);original=T/"original.dex";original.write_bytes(raw);O=T/"original";N=T/"patched-smali"
run(opts.java,"-Xmx512m","-jar",opts.baksmali,"disassemble","--api","36","-j","1",original,"-o",O)
expected={"com/mediatek/ims/chr/HwMtkChrManager$1.smali":"9b2e5ee8fac818f95642537f6da65da8b3ae7333f6c2d18b189b31a8b558940d",
"com/mediatek/ims/chr/HwMtkChrManager$SubListener.smali":"0258ba4b016c5e875acba010d01e4223258b1359c63606154e75a855c8a4e5e8",
"com/mediatek/ims/chr/HwMtkChrManager.smali":"aebb56b0f579228f854952f278b7b10a34b44ec53a5281df1ee94624c9eda3b5"}
for n,pin in expected.items():
 if sha(O/n)!=pin:raise ValueError("Original class shape")
shutil.copytree(O,N)
def replace(s,old,new):
 if s.count(old)!=1:raise ValueError("Expected exactly one old instruction block: "+old)
 return s.replace(old,new)
p=N/"com/mediatek/ims/chr/HwMtkChrManager$1.smali";s=p.read_text()
s=replace(s,"# instance fields\n","# instance fields\n.field private final mLdySubId:I\n\n")
s=replace(s,"    .registers 3\n","    .registers 4\n")
s=replace(s,"    invoke-direct {p0, p2}, Landroid/telephony/PhoneStateListener;-><init>(Ljava/lang/Integer;)V",
"""    invoke-direct {p0}, Landroid/telephony/PhoneStateListener;-><init>()V

    invoke-virtual {p2}, Ljava/lang/Integer;->intValue()I

    move-result v0

    iput v0, p0, Lcom/mediatek/ims/chr/HwMtkChrManager$1;->mLdySubId:I""")
s=replace(s,"""    iget-object v0, p0, Lcom/mediatek/ims/chr/HwMtkChrManager$1;->mSubId:Ljava/lang/Integer;

    invoke-virtual {v0}, Ljava/lang/Integer;->intValue()I

    move-result v0""","    iget v0, p0, Lcom/mediatek/ims/chr/HwMtkChrManager$1;->mLdySubId:I")
s=replace(s,"""    .line 198
    .local v0, "slotId":I
    if-eqz p1, :cond_21""","""    .line 198
    .local v0, "slotId":I
    if-ltz v0, :ldy_removed_subscription

    if-eqz p1, :cond_21""")
s=replace(s,"""    .line 206
    return-void""","""    .line 206
    :ldy_removed_subscription
    return-void""");p.write_text(s)
p=N/"com/mediatek/ims/chr/HwMtkChrManager$SubListener.smali";s=p.read_text()
for reg in ["v4","v7"]:
 old=f"""    invoke-static {{{reg}}}, Lcom/mediatek/ims/chr/HwMtkChrManager;->-$$Nest$fgetmTelephonyManager(Lcom/mediatek/ims/chr/HwMtkChrManager;)Landroid/telephony/TelephonyManager;

    move-result-object {reg}"""
 new=old+f"""

    invoke-virtual {{{reg}, v5}}, Landroid/telephony/TelephonyManager;->createForSubscriptionId(I)Landroid/telephony/TelephonyManager;

    move-result-object {reg}"""
 s=replace(s,old,new)
p.write_text(s)
p=N/"com/mediatek/ims/chr/HwMtkChrManager.smali";s=p.read_text()
a=s.index(".method private containsSlotId");b=s.index(".end method",a)
body=s[a:b]
body=replace(body,"SubscriptionInfo;->getSimSlotIndex()I","SubscriptionInfo;->getSubscriptionId()I")
p.write_text(s[:a]+body+s[b:])
# Preserve the three CHR fixes above exactly; add one private diagnostic class delta.
handler="com/mediatek/ims/ImsCallSessionProxy$MyHandler.smali"
if sha(O/handler)!="f2634ff567ab83c45d71959654dfe425e9253dbc03742187e5611a28cb6830ab":
    raise ValueError("REFUSE original SIP handler shape")

s=(N/handler).read_text()
s=replace(s,"# instance fields\n","# instance fields\n.field private mLdySipDiagnosticRecords:I\n\n")
constructor="    invoke-direct {v4, v0}, Lcom/mediatek/ims/SipMessage;-><init>([Ljava/lang/String;)V"
s=replace(s,constructor,constructor+"\n\n    invoke-direct {p0, v4, v0}, Lcom/mediatek/ims/ImsCallSessionProxy$MyHandler;->logLdySipDiagnostic(Lcom/mediatek/ims/SipMessage;[Ljava/lang/String;)V")
s+="\n"+helper.read_text().split("\n",1)[1]
(N/handler).write_text(s)
files=list(O.rglob("*.smali"))
changed={str(p.relative_to(O)) for p in files if p.read_bytes()!=(N/p.relative_to(O)).read_bytes()}
if len(files)!=4926 or changed!=set(expected)|{handler}:raise ValueError("REFUSE changed class scope")
output=T/"patched.dex"
run(opts.java,"-Xmx512m","-jar",opts.smali,"assemble","--api","36","-j","1",N.name,"-o",output.name,cwd=T)
if sha(output)!=EXPECTED:raise ValueError("REFUSE reproduced DEX hash")
if sha(opts.stock_apk)!=STOCK:raise ValueError("REFUSE original changed")
# Atomic no-clobber publication; temporary output remains in same filesystem.
os.link(output,opts.output)
print(json.dumps({"reproduced_sha256":EXPECTED,"original_apk_unchanged":True,"changed_classes":sorted(changed),"scope":"64 scalar records per handler on debuggable images; no phone execution"}))
