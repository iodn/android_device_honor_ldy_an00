#!/usr/bin/env python3
"""Package the qualified private seven-control display metadata. Host only."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import xml.etree.ElementTree as ET

CUSTOM_SHA = "a5c896d295125f1ceac621acdbe3c3ced712884be60f7f8a693a245c3ac025c4"
ASSET_SHA = "3dd3d73cad59d6dd2f7520e5b157755d15db4017fa9f29c2e5cd131d3b864e09"
CHANNELS = ("lcd", "lcd_manual", "lcd_outdoor", "lcd_outdoor_manual", "lcd_outdoor_apl", "hdr_ctrl", "outbrightness_ctrl")
SENSOR_IDS = {"shell_back": 29, "mtkts_usbntc": 55, "battery": 9, "shell_front": 28}
FEATURES = ("warm_policy", "safe_policy", "cold_policy")

def require(condition, reason):
    if not condition:
        raise ValueError(reason)

def canonical(value):
    return (json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
            + "\n").encode("ascii")

def attrs(node, allowed, required=()):
    require(set(node.attrib) <= set(allowed), "unsupported attributes on " + node.tag)
    require(set(required) <= set(node.attrib), "missing attributes on " + node.tag)

def integer(value):
    require(isinstance(value, str) and re.fullmatch(r"-?[0-9]+", value) is not None,
            "invalid integer")
    result = int(value)
    require(-(1 << 31) <= result < (1 << 31), "integer overflow")
    return result

def unique_configs(root, feature):
    f = root.find("feature[@name='" + feature + "']")
    require(f is not None and f.attrib == {"name": feature}, "missing/invalid feature")
    output = {}
    for c in f:
        require(c.tag == "config", "unsupported feature child")
        attrs(c, ("name", "scene", "outdoor", "folded") if feature == "level"
              else ("name",), ("name",))
        require(c.get("name") not in output, "duplicate config")
        output[c.get("name")] = c
    return output

def java_hash(text):
    value = 0
    encoded = text.encode("utf-16-be")
    for pos in range(0, len(encoded), 2):
        value = (31 * value + int.from_bytes(encoded[pos:pos + 2], "big")) & 0xffffffff
    return value if value < (1 << 31) else value - (1 << 32)

def item_projection(item):
    require(item.tag == "item", "unsupported policy child")
    attrs(item, ("name", "level"), ("name", "level"))
    leaves = []
    for leaf in item:
        if leaf.tag.startswith("lcd"):
            require(leaf.tag in CHANNELS, "unsupported LCD channel")
        if leaf.tag not in CHANNELS:
            continue
        attrs(leaf, ("battery_level",))
        require(len(leaf) == 0 and leaf.text is not None, "invalid LCD leaf")
        value = leaf.text.strip()
        require(-999999999 <= integer(value) <= 999999999, "LCD value outside stock domain")
        battery = leaf.get("battery_level")
        if battery is not None:
            parts = battery.split(",")
            require(len(parts) == 2, "invalid battery range")
            for part in parts:
                require(-999999 <= integer(part) <= 999999, "battery range outside stock domain")
        leaves.append({"channel": leaf.tag, "value": value, "batteryRange": battery})
    return {"cluster": item.get("name"), "level": integer(item.get("level")),
            "leaves": leaves}

def opaque_item(item):
    attrs(item, ("name", "level"), ("name", "level"))
    leaves = []
    for leaf in item:
        require(len(leaf) == 0, "nested non-LCD sideeffect unsupported")
        leaves.append({"tag": leaf.tag, "attributes": dict(leaf.attrib),
                       "value": "" if leaf.text is None else leaf.text.strip()})
    return {"cluster": item.get("name"), "level": integer(item.get("level")),
            "leaves": leaves}

def scene_metadata(custom, asset):
    # Exact r.s0: asset thermal_scene configs replace matching custom configs.
    configs = unique_configs(custom, "thermal_scene")
    configs.update(unique_configs(asset, "thermal_scene"))
    scene_map = configs.get("thermal_scene_map")
    require(scene_map is not None, "missing thermal_scene_map")
    types, packages, markers = {}, {}, []
    for item in scene_map:
        require(item.tag == "item" and len(item) == 0, "invalid scene item")
        attrs(item, ("thermal_scene", "type_list", "app_list"), ("thermal_scene",))
        scene = item.get("thermal_scene")
        require(scene != "", "empty scene")
        type_list, app_list = item.get("type_list"), item.get("app_list")
        require(not (type_list is not None and app_list is not None),
                "ambiguous scene mapping")
        if type_list is None and app_list is None:
            markers.append(scene)
        if type_list is not None:
            require(type_list != "", "empty type list")
            for token in type_list.split(","):
                types[integer(token)] = scene
        if app_list is not None:
            require(app_list != "", "empty package list")
            for package in app_list.split("|"):
                require(package != "", "empty package")
                packages[package] = scene
    # r.b is ArrayMap: signed hash ascending; ties retain original insertion order.
    package_rules = [{"key": key, "scene": packages[key]}
                     for key in sorted(packages, key=java_hash)]
    return {"appTypeScenes": [{"appType": key, "scene": types[key]}
                              for key in sorted(types)],
            "packageScenes": package_rules,
            "unmappedSceneMarkers": markers,
            "scene70003Exists": "70003" in markers}

def package_metadata(custom, asset):
    require(custom.tag == "thermal"
            and custom.attrib == {"version": "5.10.24.100", "product": "LDY-AN00"},
            "wrong custom identity")
    require(asset.tag == "thermal"
            and asset.attrib == {"version": "5.10.22.100", "product": "SYSTEM"},
            "wrong asset identity")
    policies, presence, dependencies = [], [], set()
    custom_items = 0
    for feature in FEATURES:
        configs = unique_configs(custom, feature)
        presence.append({"feature": feature, "scenes": list(configs)})
        for scene, config in configs.items():
            projected = [item_projection(i) for i in config]
            relevant = {i["cluster"] for i in projected if i["leaves"]}
            dependencies.update(relevant)
            if not relevant and not (feature == "safe_policy" and scene == "99999"):
                continue
            kept = [i for i in projected
                    if i["cluster"] in relevant or (feature == "safe_policy"
                           and i["cluster"] == "warm_shell_global")]
            custom_items += len(kept)
            policies.append({"feature": feature, "scene": scene, "items": kept})
    require(len(policies) == 30 and custom_items == 294, "custom projection scope changed")
    safe_append = unique_configs(asset, "safe_policy")
    require(set(safe_append) == {"99999"}, "unsupported asset safe configs")
    append_items = [item_projection(i) for i in safe_append["99999"]]
    require(len(append_items) == 22 and all(not i["leaves"] for i in append_items),
            "unsupported asset LCD sideeffects")
    safe = next(p for p in policies if p["feature"] == "safe_policy"
                and p["scene"] == "99999")
    require(len(safe["items"]) == 12, "neutral custom identity/items changed")
    safe["items"].extend(append_items)
    level_nodes = unique_configs(custom, "level")
    asset_levels = asset.find("feature[@name='level']")
    require(asset_levels is not None, "missing asset levels")
    require(not dependencies.intersection(c.get("name") for c in asset_levels),
            "asset replaces a required level table")
    levels = []
    for name, node in level_nodes.items():
        if name not in dependencies:
            continue
        # Required subset has no scene/outdoor/rise-rate predicates.
        attrs(node, ("name",), ("name",))
        sensors = []
        for sensor in node:
            require(sensor.tag == "item", "unsupported level child")
            attrs(sensor, ("sensor",), ("sensor",))
            require(sensor.get("sensor") in SENSOR_IDS, "unsupported required sensor")
            rows = []
            for row in sensor:
                require(row.tag == "temp" and len(row) == 0, "unsupported level row")
                attrs(row, ("level", "thresholds", "thresholds_clr"),
                      ("level", "thresholds", "thresholds_clr"))
                enter = integer(row.get("thresholds")) * 1000
                clear = integer(row.get("thresholds_clr")) * 1000
                require(-(1 << 31) <= enter < (1 << 31)
                        and -(1 << 31) <= clear < (1 << 31), "temperature overflow")
                rows.append({"level": integer(row.get("level")),
                             "enter": enter, "clear": clear})
            sensors.append({"sensorId": SENSOR_IDS[sensor.get("sensor")], "rows": rows})
        levels.append({"name": name, "scene": None, "sensors": sensors})
    require({v["name"] for v in levels} == dependencies and len(levels) == 7,
            "missing/unsupported LCD level dependency")
    safe99998 = unique_configs(custom, "safe_policy").get("99998")
    require(safe99998 is not None, "missing explicit safe99998 obligation")
    result = {"schemaVersion": 2, "device": "LDY-AN00", "firmware": "10.0.0.113",
              "sources": {"customSha256": CUSTOM_SHA, "assetSha256": ASSET_SHA,
                          "customVersion": "5.10.24.100", "assetVersion": "5.10.22.100"},
              "levelTables": levels, "policyConfigs": policies, "configPresence": presence,
              "pluginOrder": ["warm_policy", "cold_policy", "safe_policy"],
              "scenePolicy": scene_metadata(custom, asset),
              "safe99998Obligation": {"present": True, "executionImplemented": False,
                    "items": [opaque_item(i) for i in safe99998]},
              "safe99999SideEffects": {"executionImplemented": False,
                    "customItems": [opaque_item(i) for i in unique_configs(custom, "safe_policy")["99999"]],
                    "assetAppendItems": [opaque_item(i) for i in safe_append["99999"]]},
              "counts": {"customPolicies": 30, "customItems": custom_items,
                         "assetSafeAppendItems": len(append_items),
                         "mergedItems": sum(len(p["items"]) for p in policies),
                         "displayLeaves": sum(len(i["leaves"]) for p in policies for i in p["items"]),
                         "lcdLeaves": sum(l["channel"].startswith("lcd") for p in policies for i in p["items"] for l in i["leaves"]),
                         "controlLeaves": sum(l["channel"] in ("hdr_ctrl", "outbrightness_ctrl") for p in policies for i in p["items"] for l in i["leaves"])}}
    require(result["counts"]["mergedItems"] == 316
            and result["counts"]["displayLeaves"] == 1142 and result["counts"]["lcdLeaves"] == 1134 and result["counts"]["controlLeaves"] == 8, "merged projection scope changed")
    require(sum(len(p["scenes"]) for p in presence) == 31, "presence scope changed")
    return result

def build(custom, asset):
    data = []
    for path, digest in [(Path(custom), CUSTOM_SHA), (Path(asset), ASSET_SHA)]:
        raw = path.read_bytes()
        require(hashlib.sha256(raw).hexdigest() == digest, "source hash mismatch")
        data.append(ET.fromstring(raw))
    return canonical(package_metadata(*data))
