import json
import pathlib

file_path = pathlib.Path(input("Input the StarRailData project dir: "))

translator = {}

with (file_path / "TextMap" / "TextMapCHS.json").open(encoding="utf-8") as f:
    translator["CHS"]: dict = json.load(f)

with (file_path / "TextMap" / "TextMapEN.json").open(encoding="utf-8") as f:
    translator["EN"]: dict = json.load(f)


def get_translator(hash_code):
    return {"CHS": translator["CHS"].get(str(hash_code)), "EN": translator["EN"].get(str(hash_code))}


with (file_path / "ExcelOutput" / "HardLevelGroup.json").open(encoding="utf-8") as f:
    hard_level_group_json: dict = json.load(f)

for i, j in hard_level_group_json.items():
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

# with (file_path / "ExcelOutput" / "MonsterConfig.json").open(encoding="utf-8") as f:
#     monster_configs_json: dict = json.load(f)
#
# with (file_path / "ExcelOutput" / "MonsterTemplateConfig.json").open(encoding="utf-8") as f:
#     monster_templates_json: dict = json.load(f)
#
# for i in monster_configs_json.values():
#     monster = {}
#     monster["name"] = get_translator(i["MonsterName"]["Hash"])
#     monster["introduction"] = get_translator(i["MonsterIntroduction"]["Hash"])
#     monster["battle_introduction"] = get_translator(i["MonsterBattleIntroduction"]["Hash"])
#     monster["stance_weak"] = i["StanceWeakList"]
#     monster["damage_resistance"] = [{"damage_type": j["DamageType"], "value": j["Value"]["Value"]} for j in
#                                     i["DamageTypeResistance"]]
#
#     print(monster)
