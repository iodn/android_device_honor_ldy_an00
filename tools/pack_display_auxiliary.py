#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Extract private selected manual-lux and APS metadata; no firmware contents embedded."""
from pathlib import Path
import hashlib,json,re,xml.etree.ElementTree as E

def manual(source):
 raw=Path(source).read_bytes()
 digest=hashlib.sha256(raw).hexdigest()
 if digest!="7e2796607e9bd7fd16181e99cb2d34b28a2b5cdd99ce1cf0ea15b7710240d719":raise ValueError("manual source")
 text=re.sub(r"<\?xml[^>]*\?>","",raw.decode("utf-8"))
 root=E.fromstring("<root>"+text+"</root>")
 def one(tag):
  rows=list(root.iter(tag))
  if len(rows)!=1:raise ValueError("manual tag "+tag)
  return rows[0].text.strip()
 def boolean(tag):
  value=one(tag)
  if value not in ("true","false"):raise ValueError("manual boolean")
  return value=="true"
 # Selected XML omits these private field overrides; audited constructors default false.
 for tag in ("CamServiceEnable","IsOutwardLightSensor"):
  if list(root.iter(tag)):raise ValueError("manual unsupported override")
 names=("HubFloorThresh","HubDarkRoomThresh","HubDarkRoomThresh1","HubDarkRoomThresh2",
 "HubDarkRoomThresh3","HubDarkRoomThresh4","HubDarkRoomDelta","HubDarkRoomDelta1",
 "HubDarkRoomDelta2","HubDarkRoomDelta3","HubFusedRatio")
 data={"schema":1,"sourceSha256":digest,"parameters":{tag:float(one(tag)) for tag in names},
 "profile":{"frontRateMs":int(one("FrontSensorRateMillis")),
 "backRateMs":int(one("BackSensorRateMillis")),"fusedRateMs":int(one("FusedSensorRateMillis")),
 "sensorHub":boolean("SensorHubEnable"),"isFilterOn":boolean("IsFilterOn"),
 "sensorVersion":int(one("SensorVersion")),"moduleOption":int(one("HwLightSensorController")),
 "camServiceEnable":False,"outward":False,"sensorSelectType":0}}
 return (json.dumps(data,sort_keys=True,separators=(",",":"))+"\n").encode("ascii")

def fullscreen(source,properties):
 raw=Path(source).read_bytes();prop=Path(properties).read_bytes()
 if hashlib.sha256(raw).hexdigest()!="4a2611b90bd2044ed948aecab4e71ef02d0d926330219e2cbc8552d31c416c8d":raise ValueError("APS source")
 if hashlib.sha256(prop).hexdigest()!="2dd8f4fcf872ffaff69e8d259201731ce3ac63dcfe393ea92ec480637142bf8a":raise ValueError("APS initial property")
 root=E.fromstring(raw)
 packages=[x.attrib["name"].split("_")[0] for x in root.iter("aps")
  if x.attrib.get("type")=="10012" and x.attrib.get("switch_level")=="1"]
 if len(packages)!=16 or len(set(packages))!=16:raise ValueError("APS package profile")
 lines=[line for line in prop.decode("utf-8").splitlines() if line.startswith("ro.config.default_screensize=")]
 if len(lines)!=1:raise ValueError("APS initial dimensions")
 width,height=map(int,lines[0].split("=")[1].split(","))
 data={"schema":1,"sourceXmlSha256":hashlib.sha256(raw).hexdigest(),
 "defaultNonFoldPhone":True,"debounceMillis":700,"packages":packages,
 "initialWidth":width,"initialHeight":height,
 "sourceInitialPropertySha256":hashlib.sha256(prop).hexdigest()}
 return (json.dumps(data,separators=(",",":"))+"\n").encode("ascii")
