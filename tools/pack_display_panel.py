#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Generate the hash-qualified private panel payload from the selected firmware XML."""
from pathlib import Path
import hashlib,json,xml.etree.ElementTree as ET
E=ET
SHA=SOURCE_SHA="15385bc3829a53ee78c5f47feae7a45f10b55ffac2421aa80ae2bb9d640dbbdd"
CURVES=['LocalHdrBrightnessPoints','HlgHdrBrightnessPoints','HdrPreviewBrightnessPoints','LowHdrBrightnessPoints','BrightnessMappingPointsForHdrGalleryMode','SdrVideoPlusBrightnessPoints','ThirdPartSdrBrightnessPoints']
FLAGS=['LocalHdrModeEnable','WhiteHdrModeEnable','HdrGalleryModeEnable','HdrPreviewModeEnable','HlgHdrModeEnable','SdrVideoPlusModeEnable','EhdrAplEnable','ThirdPartSdrModeEnable']
def extract(source):
    raw=Path(source).read_bytes()
    if hashlib.sha256(raw).hexdigest()!=SOURCE_SHA:raise ValueError('Wrong source')
    root=E.fromstring(raw)
    def one(tag):
        values=list(root.iter(tag))
        if len(values)!=1:raise ValueError('Missing or duplicate tag '+tag)
        return values[0]
    curves={}
    for tag in CURVES:
        curves[tag]=[[float(v.strip()) for v in child.text.split(',')] for child in one(tag)]
        if any(len(point)!=2 for point in curves[tag]):raise ValueError('Invalid point')
    flags={}
    for tag in FLAGS:
        v=one(tag).text.strip()
        if v not in ('true','false'):raise ValueError('Not boolean')
        flags[tag]=v=='true'
    return {'schemaVersion':1,'device':'LDY-AN00','sourceXmlSha256':SOURCE_SHA,'curves':curves,'flags':flags,'EhdrAplLuxThreshold':float(one('EhdrAplLuxThreshold').text)}

def generate(source):
    SOURCE=Path(source)
    raw = SOURCE.read_bytes()
    assert hashlib.sha256(raw).hexdigest() == SHA
    root = ET.fromstring(raw)
    def unique(tag):
        nodes = list(root.iter(tag))
        assert len(nodes) == 1, (tag, len(nodes))
        return nodes[0]
    def text(tag): return unique(tag).text.strip()
    def boolean(tag):
        value = text(tag)
        assert value in ("true", "false"), tag
        return value == "true"
    values = {
        "manualMaxRaw": int(text("ManualBrightnessMaxLimit")),
        "seekbarMaxRaw": int(text("AutoModeSeekBarMaxBrightnessValue")),
        "switchPackageMillis": int(text("ThermalSwitchPkgUpdateTime")),
        "powerUpdateMillis": int(text("ThermalPowerUpdateTime")),
        "outdoorDiscountRaw": int(text("OutdoorBrightnessDiscount")),
        "outdoorDiscountDelayMillis": int(text("OutdoorBrightnessDiscountTimeMs")),
        "outdoorLuxThreshold": float(text("OutdoorUpThresold")),
        "aplLuxThreshold": float(text("AplLuxThreshold")),
        "thermalDragStrategyEnable": boolean("ThermalDragStrategyEnable"),
        "thermalModeBrightnessMappingEnable": boolean("ThermalModeBrightnessMappingEnable"),
        "fullSceneApl": boolean("FullSceneApl"),
        "fullSceneAplThermalEnable": boolean("FullSceneAplThermalEnable"),
        "hdrNoLimit": boolean("HdrBrightnessNolimit"),
        "outdoorDiscountEnable": boolean("OpenOutdoorBrightnessDiscount"),
        # XML tag absent. Exact Data ctor default at DEX250b54..250b58 is4f.
        "minValidThermalRaw": 4,
    }
    assert not list(root.iter("MinValidThermalBrightness"))
    points = []
    for node in unique("HdrThermalRatioPoints"):
        assert node.tag == "Point"
        pair = node.text.strip().split(",")
        assert len(pair) == 2
        points.append([float(x) for x in pair])
    payload = dict(schemaVersion=1, device="LDY-AN00", sourceXmlSha256=SHA,
            semanticDefault=dict(field="minValidThermalBrightness", floatBits="40800000",
                    owner="HwBrightnessXmlLoader.Data", dexStore="250b58"),
            panel=values, hdrThermalRatioPoints=points)
    extra=extract(SOURCE)
    payload["hdrModes"]={"curves":extra["curves"],"flags":extra["flags"],
     "ehdrAplLuxThreshold":extra["EhdrAplLuxThreshold"],"maxHdrSdrRatio":5,
     "ratioDefault":{"field":"maxHdrSdrRatio","floatBits":"40a00000","owner":"HwBrightnessXmlLoader.Data","dexStore":"24ffde"}}
    if list(root.iter("CareerModeEnable")):raise ValueError("Unexpected career override")
    flag=text("IsAutoModeSeekBarMaxBrightnessBasedLux")
    if flag not in ("true","false"):raise ValueError("seekbar flag")
    payload["hdrModes"].update(seekbarBasedLuxEnabled=flag=="true",
     seekbarLuxThreshold=float(text("AutoModeSeekBarMaxBrightnessLuxTh")),careerModeEnabled=False,
     careerDefault={"field":"careerModeEnable","value":False,"owner":"HwBrightnessXmlLoader.Data","dexStore":"24fe5c"},
     fingerprintOffUnlockEnable=boolean("FingerprintOffUnlockEnable"))
    root=E.fromstring(source.read_bytes())
    def one(tag):
     entries=list(root.iter(tag))
     if len(entries)!=1:raise ValueError("missing/duplicate "+tag)
     return entries[0]
    def boolean(tag):
     text=one(tag).text.strip()
     if text not in ("true","false"):raise ValueError("boolean "+tag)
     return text=="true"
    def number(tag):return float(one(tag).text.strip())
    def points(tag):return [[float(v) for v in p.text.split(",")] for p in one(tag)]
    for tag in ("GalleryAplModeEnable","SpecialAplEnable","EhdrAplMaxRatio"):
     if list(root.iter(tag)):raise ValueError("source default needs absent exactXMLtag "+tag)
    apl={"enabled":boolean("AplModeEnable"),"sunEnabled":boolean("SunAplEnable"),
     "esdrEnabled":boolean("EsdrAplEnable"),"galleryEnabled":False,"specialEnabled":False,
     "ehdrMaxRatio":-1.0,"batteryThreshold":int(one("AplBatteryLevelThreshold").text),
     "brightenAmount":number("AplBrightenAmount"),"darkenAmount":number("AplDarkenAmount"),
     "fullBrightenAmount":number("FullAplBrightenAmount"),"fullDarkenAmount":number("FullAplDarkenAmount"),
     "fullStartLevel":int(one("FullAplStartLevel").text),
     "hdrDividedBySdrRatio":number("HdrDividedBySdrRatio"),
     "hdrDividedBySdrAplRatio":number("HdrDividedBySdrAplRatio"),
     "animationBrightenSeconds":number("AplModeAnimationBrightenTime"),
     "animationDarkenSeconds":number("AplModeAnimationDarkenTime"),
     "sunRatioPoints":points("RatioLineForSunAplMode"),
     "sdrRatioPoints":points("RatioLineForSdrPlusApl"),
     "indexThresholds":[float(v) for v in one("AplIndexList").attrib["aplList"].split(",")],
     "luxRows":[],"defaults":[
     {"field":"GalleryAplModeEnable","value":False,"dexStore":"24fee8"},
     {"field":"specialAplEnable","value":False,"dexStore":"24ff66"},
     {"field":"ehdrAplMaxRatio","value":-1.0,"floatBits":"bf800000","dexStore":"24ff6a","constant":"24fdb8"}]}
    seen=set()
    for row in root.iter("LuxAplMap"):
     lux=int(row.attrib["lux"])
     if lux in seen:raise ValueError("duplicate lux")
     seen.add(lux)
     apl["luxRows"].append({"lux":lux,"brightnessLevels":[float(v) for v in row.attrib["brightnessLevel"].split(",")]})
    apl["luxRows"].sort(key=lambda r:r["lux"])

    payload["schemaVersion"]=6;payload["apl"]=apl
    return (json.dumps(payload,sort_keys=True,separators=(",",":"))+"\n").encode("ascii")
