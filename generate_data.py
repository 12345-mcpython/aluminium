import pathlib
import json

file_path = pathlib.Path(input("Input the StarRailData project dir: "))

with (file_path / "ExcelOutput" / "HardLevelGroup.json").open() as f:
    hard_level_group: dict = json.load(f)

for i, j in hard_level_group.items():
    d = {}
    for key, value in j.items():
        d[key] = {
            "attack": value.get("AttackRatio", {"Value": 1})["Value"],
            "defensive": value.get("DefenceRatio", {"Value": 1})["Value"],
            "health": value.get("HPRatio", {"Value": 1})["Value"],
            "speed": value.get("SpeedRatio", {"Value": 1})["Value"],
            "stance": value.get("StanceRatio", {"Value": 1})["Value"],
            "effect_hit_rate": value.get("StatusProbability", {"Value": 0})["Value"],
            "effect_resistance": value.get("StatusResistance", {"Value": 0})["Value"]}
    with open(f"data/hard_level_group_{i}.json", "w") as f:
        json.dump(d, f, ensure_ascii=False, indent=4)
