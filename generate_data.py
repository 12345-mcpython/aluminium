import json
import pathlib

print("aluminium data generator 1.0.0")
print("data version: 2.4.0")
if pathlib.Path("data/data_path.txt").exists():
    file_path = pathlib.Path(pathlib.Path("data/data_path.txt").read_text(encoding="utf-8"))
    print(f"use data path: {file_path}")
else:
    file_path = pathlib.Path(input("Input the StarRailData project dir: "))
    pathlib.Path("data/data_path.txt").write_text(str(file_path.absolute()), encoding="utf-8")

hard_level_group = {}

with (file_path / "ExcelOutput" / "HardLevelGroup.json").open(encoding="utf-8") as f:
    hard_level_group_json = json.load(f)

"""d[key] = {
            "attack": value.get("AttackRatio", {"Value": 1})["Value"],
            "defensive": value.get("DefenceRatio", {"Value": 1})["Value"],
            "health": value.get("HPRatio", {"Value": 1})["Value"],
            "speed": value.get("SpeedRatio", {"Value": 1})["Value"],
            "stance": value.get("StanceRatio", {"Value": 1})["Value"],
            "effect_hit_rate": value.get("StatusProbability", {"Value": 0})["Value"],
            "effect_resistance": value.get("StatusResistance", {"Value": 0})["Value"]}"""

for i in hard_level_group_json:
    hard_level_group_id = i["HardLevelGroup"]
    hard_level_group_level = i["Level"]
    data_parsed = {
        "attack": i.get("AttackRatio", {"Value": 1})["Value"],
        "defensive": i.get("DefenceRatio", {"Value": 1})["Value"],
        "health": i.get("HPRatio", {"Value": 1})["Value"],
        "speed": i.get("SpeedRatio", {"Value": 1})["Value"],
        "stance": i.get("StanceRatio", {"Value": 1})["Value"],
        "effect_hit_rate": i.get("StatusProbability", {"Value": 0})["Value"],
        "effect_resistance": i.get("StatusResistance", {"Value": 0})["Value"]}
    if hard_level_group.get(hard_level_group_id):
        hard_level_group[hard_level_group_id][hard_level_group_level] = data_parsed
    else:
        hard_level_group[hard_level_group_id] = {hard_level_group_level: data_parsed}
for i, j in hard_level_group.items():
    with open(f"data/hard_level_group_{i}.json", "w", encoding="utf-8") as f:
        json.dump(j, f, ensure_ascii=False, indent=4)

with (file_path / "ExcelOutput" / "AvatarBreakDamage.json").open(encoding="utf-8") as f:
    breaking_rate = json.load(f)

d = {}
for i in breaking_rate:
    d[i['Level']] = i["BreakBaseDamage"]["Value"]

with open("data/breaking_rate.json", "w", encoding="utf-8") as f:
    json.dump(d, f, ensure_ascii=False, indent=4)

# 2 品质 1 头 2 手 3 身 4 鞋

part_mapping = {1: "head", 2: "hand", 3: "body", 4: "boot", 5: "ball", 6: "line"}

inner_outer_mapping = {"HPDelta": "health", "AttackDelta": "attack", "HPAddedRatio": "health_percent",
                       "AttackAddedRatio": "attack_percent", "DefenceAddedRatio": "defensive_percent",
                       "CriticalChanceBase": "crit_chance", "CriticalDamageBase": "crit_attack",
                       "HealRatioBase": "outgoing_healing_boost", "StatusProbabilityBase": "effect_hit_rate",
                       "SpeedDelta": "speed", "PhysicalAddedRatio": "physical_damage_boost",
                       "FireAddedRatio": "fire_damage_boost", "IceAddedRatio": "ice_damage_boost",
                       "ThunderAddedRatio": "thunder_damage_boost",
                       "WindAddedRatio": "wind_damage_boost", "QuantumAddedRatio": "quantum_damage_boost",
                       "ImaginaryAddedRatio": "imaginary_damage_boost", "BreakDamageAddedRatioBase": "breaking_effect",
                       "SPRatioBase": "energy_regeneration_rate", "DefenceDelta": "defensive",
                       "StatusResistanceBase": "effect_resistance"}

# BPNeed 战技点使用
