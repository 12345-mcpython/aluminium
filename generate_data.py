import json
import pathlib

file_path = pathlib.Path(input("Input the StarRailData project dir: "))

translator = {}

with (file_path / "TextMap" / "TextMapCHS.json").open(encoding="utf-8") as f:
    translator["CHS"]: dict = json.load(f)

with (file_path / "TextMap" / "TextMapEN.json").open(encoding="utf-8") as f:
    translator["EN"]: dict = json.load(f)


def get_translator(hash_code: int):
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
    with open(f"data/hard_level_group_{i}.json", "w", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False, indent=4)

with (file_path / "ExcelOutput" / "AvatarBreakDamage.json").open(encoding="utf-8") as f:
    breaking_rate: dict[str, dict] = json.load(f)

d = {}
for i, j in breaking_rate.items():
    d[i] = j["BreakBaseDamage"]["Value"]

with open("data/breaking_rate.json", "w", encoding="utf-8") as f:
    json.dump(d, f, ensure_ascii=False, indent=4)


def parse_relic(data: dict[str, dict], sub=False):
    parsed_datas = {}
    for i, j in data.items():
        parsed_data = {"base": j["BaseValue"]["Value"], "bonus": j["LevelAdd" if not sub else "StepValue"]["Value"]}
        parsed_datas[inner_outer_mapping[j["Property"]]] = parsed_data
    return parsed_datas


# 2 品质 1 头 2 手 3 身 4 鞋
with (file_path / "ExcelOutput" / "RelicMainAffixConfig.json").open(encoding="utf-8") as f:
    main_attributes: dict[str, dict] = json.load(f)

two_star = {}
three_star = {}
four_star = {}
five_star = {}

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

main_attribute_table = {2: two_star, 3: three_star, 4: four_star, 5: five_star}

for i, j in main_attributes.items():
    if len(i) != 2:
        continue
    match i[0]:
        case "2":
            two_star[part_mapping[int(i[1])]] = parse_relic(j)
        case "3":
            three_star[part_mapping[int(i[1])]] = parse_relic(j)
        case "4":
            four_star[part_mapping[int(i[1])]] = parse_relic(j)
        case "5":
            five_star[part_mapping[int(i[1])]] = parse_relic(j)
        case _:
            raise Exception("This line never run.")

for i, j in main_attribute_table.items():
    with open(f"data/relic_{i}_main_attribute.json", "w", encoding="utf-8") as f:
        json.dump(j, f, ensure_ascii=False, indent=4)

with (file_path / "ExcelOutput" / "RelicSubAffixConfig.json").open(encoding="utf-8") as f:
    sub_attributes: dict[str, dict] = json.load(f)

for i, j in sub_attributes.items():
    with open(f"data/relic_{i}_sub_attribute.json", "w", encoding="utf-8") as f:
        json.dump(parse_relic(j, sub=True), f, ensure_ascii=False, indent=4)

exp = {}

with (file_path / "ExcelOutput" / "RelicExpType.json").open(encoding="utf-8") as f:
    exp_json: dict[str, dict] = json.load(f)

for i, j in exp_json.items():
    ls = []
    for k in j.values():
        if k.get("Exp"):
            ls.append(k.get("Exp"))
    exp[int(i) + 1] = ls

with open("data/relic_exp.json", "w", encoding="utf-8") as f:
    json.dump(exp, f, ensure_ascii=False, indent=4)

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

# ExcelOutput\AvatarConfig.json
# ExcelOutput\AvatarPromotionConfig.json
# ExcelOutput\AvatarSkillConfig.json

# BPNeed 战技点使用

with (file_path / "ExcelOutput" / "AvatarSkillConfig.json").open(encoding="utf-8") as f:
    character_skills: dict[str, dict] = json.load(f)

with (file_path / "ExcelOutput" / "AvatarPromotionConfig.json").open(encoding="utf-8") as f:
    character_promotions: dict[str, dict] = json.load(f)

with (file_path / "ExcelOutput" / "AvatarConfig.json").open(encoding="utf-8") as f:
    characters: dict[str, dict] = json.load(f)

with open("data/character_skills.json", "w", encoding="utf-8") as f:
    p = {}
    for i, j in character_skills.items():
        one_skill = j["1"]
        one_character_skill = {"skill_name": get_translator(one_skill["SkillName"]["Hash"]),
                               "skill_stance_attribute": one_skill.get("StanceDamageType"),
                               "use_point": one_skill.get("BPNeed", {"Value": -404})["Value"]}
        p[i] = one_character_skill
    json.dump(p, f, ensure_ascii=False, indent=4)
