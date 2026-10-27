#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# SPDX-License-Identifier: Apache-2.0
"""Execute a bounded host model of the actual round-tripped diagnostic Smali.

Only the new helper's instructions execute in this model; Android/vendor code does
not execute. API calls are explicit mocks. This is not ART/runtime qualification.
"""
import argparse,json,re
from pathlib import Path

ap=argparse.ArgumentParser(description=__doc__)
ap.add_argument("--baseline-smali",type=Path,required=True)
ap.add_argument("--candidate-smali",type=Path,required=True)
a=ap.parse_args()
handler="com/mediatek/ims/ImsCallSessionProxy$MyHandler.smali"
old=(a.baseline_smali/handler).read_text();new=(a.candidate_smali/handler).read_text()
method=".method private logLdySipDiagnostic(Lcom/mediatek/ims/SipMessage;[Ljava/lang/String;)V"
helper=new[new.index(method):new.index(".end method",new.index(method))+len(".end method")]
injection="invoke-direct {p0, v4, v0}, Lcom/mediatek/ims/ImsCallSessionProxy$MyHandler;->logLdySipDiagnostic(Lcom/mediatek/ims/SipMessage;[Ljava/lang/String;)V"
checks=[]
def check(name,condition):
    if not condition:raise AssertionError(name)
    checks.append(name)

def methods(s):
    return {m.group(1):m.group(0) for m in re.finditer(r"(^\.method[^\n]+)\n.*?^\.end method",s,re.M|re.S)}

def normalized(s):
    statements=[];labels={}
    for raw in s.splitlines():
        x=raw.strip()
        if not x or x.startswith(("#",".line ",".local ",".end local ",".restart local ",".param ",".prologue")) or x==injection or x=="nop":continue
        if x.startswith(":"):labels[x]=str(len(statements))
        else:statements.append(x)
    return [re.sub(r":[a-zA-Z0-9_]+",lambda m:"@"+labels[m[0]] if m[0] in labels else m[0],x) for x in statements]

om,nm=methods(old),methods(new)
check("exact_one_private_method_added",set(nm)-set(om)=={method})
changed_method=".method private updateImsReasonInfo(Landroid/os/AsyncResult;)V"
check("only_alignment_nop_removed",len(re.findall(r"^    nop$",om[changed_method],re.M))==1 and not re.search(r"^    nop$",nm[changed_method],re.M) and bool(re.search(r"return-void\n\s+nop\n\s+:\w+\n\s+\.sparse-switch",om[changed_method])))
check("all_prior_handler_methods_and_branch_targets_unchanged_after_single_invoke_removed",all(normalized(x)==normalized(nm[k]) for k,x in om.items()))
check("one_injection_after_existing_constructor",new.count(injection)==1 and "invoke-direct {v4, v0}, Lcom/mediatek/ims/SipMessage;-><init>([Ljava/lang/String;)V\n\n    "+injection in new)
check("handler_existing_exception_regions_unchanged",all([x for x in v.splitlines() if x.strip().startswith(".catch")] == [x for x in nm[k].splitlines() if x.strip().startswith(".catch")] for k,v in om.items()))
files=sorted(a.baseline_smali.rglob("*.smali"))
check("all4926_classes_preserved",len(files)==4926 and len(list(a.candidate_smali.rglob("*.smali")))==4926)
check("all4925_other_classes_byteidentical",all(p.read_bytes()==(a.candidate_smali/p.relative_to(a.baseline_smali)).read_bytes() for p in files if str(p.relative_to(a.baseline_smali))!=handler))
check("helper_does_not_read_reason_destination_or_arrays_beyond_index0",all(x not in helper for x in ["getReasonHeader","mCallNumber","mCallProfile","toString([", "aget-object v0, p2, v1"]))
check("only_new_private_field",new.count(".field private mLdySipDiagnosticRecords:I")==1 and new.replace(".field private mLdySipDiagnosticRecords:I\n\n","").split("# direct methods")[0]==old.split("# direct methods")[0])

# Actual original call-ID equality branch must dominate the new call. Explore
# both outcomes of every condition; the unmatched route must never reach it.
body=nm[changed_method];code=[];labels={}
for line in body.splitlines():
    x=line.strip()
    if x.startswith(":"):labels[x]=len(code)
    elif x and not x.startswith((".","#")):code.append(x)
hook=code.index(injection)
equals=code.index("invoke-virtual {v4, v5}, Ljava/lang/Object;->equals(Ljava/lang/Object;)Z")
guard=equals+2
check("call_id_guard_exact_shape",code[equals+1]=="move-result v4" and code[guard].startswith("if-nez v4, "))
match=labels[code[guard].split(",")[-1].strip()]
def reachable(start,omit_match=False):
    pending=[start];seen=set()
    while pending:
        i=pending.pop()
        if i==hook:return True
        if i in seen or i>=len(code):continue
        seen.add(i);x=code[i]
        if x.startswith(("return-","throw ")):continue
        if x.startswith("goto"):pending.append(labels[x.split()[-1]])
        elif x.startswith("if-"):
            target=labels[x.split(",")[-1].strip()]
            pending.append(i+1)
            if not (omit_match and i==guard):pending.append(target)
        else:pending.append(i+1)
    return False
check("matched_call_id_route_reaches_hook",reachable(match))
check("mismatched_call_id_route_never_reaches_hook",not reachable(guard+1))
check("matching_branch_dominates_hook",not reachable(0,omit_match=True))

class RuntimeFault(Exception):pass

def execute(debug=True,counter=0,callid="7",slot=1,values=(1,1,1,500),log_result=40,log_throws=False,getter_throws=False):
    code=[];labels={};catches=[]
    for raw in helper.splitlines():
        x=raw.strip()
        if not x or x.startswith("#"):continue
        if x.startswith(":"):labels[x]=len(code)
        elif x.startswith(".catch"):
            m=re.search(r"\{(:\w+) \.\. (:\w+)\} (:\w+)",x);catches.append(m.groups())
        elif not x.startswith("."):code.append(x)
    state={"counter":counter,"slot":slot};regs={"p0":state,"p1":dict(zip(["getDir","getType","getMethod","getCode"],values)),"p2":[callid,"DO_NOT_LOG_DIRECTION_STRING","DO_NOT_LOG_TYPE_STRING","DO_NOT_LOG_METHOD_STRING","DO_NOT_LOG_CODE_STRING","SECRET_REASON_AND_SDP"]}
    logs=[];attempts=[];pc=0;result=None;steps=0
    def invoke(name,args):
        if "Integer;->parseInt" in name:
            if not re.fullmatch(r"[+-]?[0-9]+",str(args[0])):raise RuntimeFault("number")
            n=int(args[0])
            if not -(2**31)<=n<2**31:raise RuntimeFault("overflow")
            return n
        if "-$$Nest$fgetmPhoneId" in name:return args[0]["slot"]
        if "SipMessage;->get" in name:
            if getter_throws:raise RuntimeFault("getter")
            return args[0][name.split("->")[1].split("(")[0]]
        if "StringBuilder;-><init>" in name:return None
        if "StringBuilder;->append" in name:args[0].append(str(args[1]));return args[0]
        if "StringBuilder;->toString" in name:return "".join(args[0])
        if "Log;->i" in name:
            attempts.append(tuple(args))
            if log_throws:raise RuntimeFault("log")
            if log_result>0:logs.append(tuple(args))
            return log_result
        raise AssertionError("unmodeled invoke: "+name)
    while pc<len(code):
        steps+=1
        if steps>300:raise AssertionError("helper did not terminate")
        x=code[pc];at=pc;pc+=1
        try:
            op=x.split()[0];args=x[len(op):].strip()
            if op=="return-void":break
            if op=="sget-boolean":regs[args.split(",")[0]]=debug
            elif op in ["iget","iget-object"]:
                dst,obj,field=[q.strip() for q in args.split(",",2)];regs[dst]=regs[obj] if "this$0" in field else regs[obj]["counter"]
            elif op=="iput":
                val,obj,_=[q.strip() for q in args.split(",",2)];regs[obj]["counter"]=regs[val]
            elif op.startswith("const-string"):
                dst,val=args.split(",",1);regs[dst.strip()]=json.loads(val.strip())
            elif op.startswith("const/"):
                dst,val=args.split(",");regs[dst.strip()]=int(val.strip(),0)
            elif op.startswith("if-"):
                aa=[q.strip() for q in args.split(",")];v=regs[aa[0]];take={"if-eqz":lambda:v==0,"if-lez":lambda:v<=0,"if-ge":lambda:v>=regs[aa[1]]}[op]()
                if take:pc=labels[aa[-1]]
            elif op=="aget-object":
                dst,obj,index=[q.strip() for q in args.split(",")];regs[dst]=regs[obj][regs[index]]
            elif op=="new-instance":regs[args.split(",")[0]]=[]
            elif op.startswith("invoke-"):
                m=re.fullmatch(r"\{([^}]*)\}, (.+)",args);rr=[q.strip() for q in m[1].split(",") if q.strip()];result=invoke(m[2],[regs[q] for q in rr])
            elif op.startswith("move-result"):regs[args]=result
            elif op=="move-exception":regs[args]=RuntimeFault()
            elif op=="add-int/lit8":
                dst,reg,val=[q.strip() for q in args.split(",")];regs[dst]=regs[reg]+int(val,0)
            else:raise AssertionError("unmodeled opcode: "+x)
        except RuntimeFault:
            targets=[target for begin,end,target in catches if labels[begin]<=at<labels[end]]
            if not targets:raise
            pc=labels[targets[0]]
    return state["counter"],logs,attempts

n,logs,_=execute();check("positive_exact_six_integer_fields",n==1 and logs==[("LdyImsSipDiag","slot=1 callId=7 direction=1 type=1 method=1 code=500")])
check("release_gate_before_parse_and_log",execute(debug=False,callid="SECRET_NUMBER")== (0,[],[]))
check("malformed_identifier_is_not_logged",execute(callid="SECRET_NUMBER")== (0,[],[]))
check("overflow_identifier_is_not_logged",execute(callid="2147483648")== (0,[],[]))
check("getter_runtime_failure_does_not_escape_or_increment",execute(getter_throws=True)== (0,[],[]))
n,logs,attempts=execute(log_throws=True);check("log_failure_does_not_escape_or_increment",n==0 and not logs and len(attempts)==1)
for rv in [0,-1]:
    n,logs,attempts=execute(log_result=rv);check("nonemitted_log_no_increment_"+str(rv),n==0 and not logs and len(attempts)==1)
n=0;emitted=[]
for i in range(80):n,logs,_=execute(counter=n);emitted+=logs
check("64_successes_then_no_more_log_attempts",n==64 and len(emitted)==64 and execute(counter=n)==(64,[],[]))
check("counter63_emits_last_record",execute(counter=63)[0]==64 and len(execute(counter=63)[1])==1)
check("separate_handler_has_separate_budget",execute(counter=0,slot=0)[0]==1)
check("adversarial_protocol_values_remain_integers",execute(callid="-1",values=(-1,99,-2,2147483647))[1][0][1]=="slot=1 callId=-1 direction=-1 type=99 method=-2 code=2147483647")
print(json.dumps({"passed":True,"checks":checks,"count":len(checks),"scope":"Actual roundtrip Smali static comparison and bounded helper-instruction model with mocked Android APIs; not ART, Binder, modem or call acceptance."},indent=2))
