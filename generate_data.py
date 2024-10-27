import json
import pathlib
import sys

print("aluminium data generator 1.0.0-dev")
print("data version: 2.6.0")

data_path_file = pathlib.Path("data/data_path.txt")

if data_path_file.exists():
    file_path = pathlib.Path(data_path_file.read_text(encoding="utf-8").strip())
    print(f"use data path: {file_path}")
else:
    file_path = pathlib.Path(input("Input the StarRailData project dir: "))
    data_path_file.write_text(str(file_path.absolute()), encoding="utf-8")

with (file_path / "TextMap" / "TextMapCHS.json").open(encoding="utf-8") as f:
    translate_chn = json.load(f)

with (file_path / "TextMap" / "TextMapEN.json").open(encoding="utf-8") as f:
    translate_en = json.load(f)


def translate(hash_key: int):
    return {"chinese": translate_chn.get(str(hash_key), "").replace("{NICKNAME}", "开拓者"),
            "english": translate_en.get(str(hash_key), "").replace("{NICKNAME}", "Trailblazer")}


def parse_stance_list(stance_list: list):
    return {"single": stance_list[0]["Value"], "all": stance_list[1]["Value"], "spread": stance_list[2]["Value"]}


hard_level_group = {}

with (file_path / "ExcelOutput" / "HardLevelGroup.json").open(encoding="utf-8") as f:
    hard_level_group_json = json.load(f)

if not isinstance(hard_level_group_json, list):
    print("StarRailData data version incorrect.")
    print("Please download the latest StarRailData and replace the old path in data/data_path.txt.")
    sys.exit(1)

for i in hard_level_group_json:
    hard_level_group_id = i["HardLevelGroup"]
    hard_level_group_level = i["Level"]

    data_parsed = {
        "attack": i.get("AttackRatio", {}).get("Value", 1),
        "defence": i.get("DefenceRatio", {}).get("Value", 1),
        "health": i.get("HPRatio", {}).get("Value", 1),
        "speed": i.get("SpeedRatio", {}).get("Value", 1),
        "stance": i.get("StanceRatio", {}).get("Value", 1),
        "effect_hit_rate": i.get("StatusProbability", {}).get("Value", 0),
        "effect_resistance": i.get("StatusResistance", {}).get("Value", 0)
    }

    hard_level_group.setdefault(hard_level_group_id, {})[hard_level_group_level] = data_parsed

with open(f"data/hard_level_group.json", "w", encoding="utf-8") as f:
    json.dump(hard_level_group, f, ensure_ascii=False, indent=4)

with (file_path / "ExcelOutput" / "AvatarBreakDamage.json").open(encoding="utf-8") as f:
    breaking_rate = json.load(f)

d = {}
for i in breaking_rate:
    d[i['Level']] = i["BreakBaseDamage"]["Value"]

with open("data/breaking_rate.json", "w", encoding="utf-8") as f:
    json.dump(d, f, ensure_ascii=False, indent=4)


def parse_relic(data: dict[str, dict], sub=False):
    return {"base": data["BaseValue"]["Value"], "bonus": data["LevelAdd" if not sub else "StepValue"]["Value"]}


part_mapping = {1: "head", 2: "hand", 3: "body", 4: "boot", 5: "ball", 6: "line"}

with open("data/relic_mappings.json") as f:
    inner_outer_mapping = json.load(f)

with (file_path / "ExcelOutput" / "RelicMainAffixConfig.json").open(encoding="utf-8") as f:
    main_attribute_json_data = json.load(f)

with (file_path / "ExcelOutput" / "RelicSubAffixConfig.json").open(encoding="utf-8") as f:
    sub_attribute_json_data = json.load(f)

main_attribute_data_parsed = {}

for i in main_attribute_json_data:
    group_id = i["GroupID"]
    if group_id >= 100:
        continue

    part = part_mapping[group_id % 10]
    star = group_id // 10
    left = inner_outer_mapping[i["Property"]]
    parsed_data = parse_relic(i)

    if star not in main_attribute_data_parsed:
        main_attribute_data_parsed[star] = {}
    if part not in main_attribute_data_parsed[star]:
        main_attribute_data_parsed[star][part] = {}

    main_attribute_data_parsed[star][part][left] = parsed_data

with open("data/main_attribute.json", "w", encoding="utf-8") as f:
    json.dump(main_attribute_data_parsed, f, indent=4, ensure_ascii=False)

sub_attribute_data_parsed = {}

for i in sub_attribute_json_data:
    star = i["GroupID"]
    left = inner_outer_mapping[i["Property"]]
    parsed_data = parse_relic(i, True)
    sub_attribute_data_parsed.setdefault(star, {})[left] = parsed_data

with open("data/sub_attribute.json", "w", encoding="utf-8") as f:
    json.dump(sub_attribute_data_parsed, f, indent=4, ensure_ascii=False)

with (file_path / "ExcelOutput" / "AvatarConfig.json").open(encoding="utf-8") as f:
    character_data_json = json.load(f)

with (file_path / "ExcelOutput" / "AvatarPromotionConfig.json").open(encoding="utf-8") as f:
    character_promote_json = json.load(f)

characters_basic_data = {}

character_id_mapping = {}

for character in character_data_json:
    character_id_mapping[character["AvatarID"]] = translate(character["AvatarName"]["Hash"])
    character_data = {"id": character["AvatarID"], "attribute": character["DamageType"].lower(),
                      "short_name": character["AvatarVOTag"],
                      "max_energy": character["SPNeed"]["Value"], "name": translate(character["AvatarName"]["Hash"])}
    characters_basic_data[character["AvatarID"]] = character_data

characters_value_data = {}

for character_promote in character_promote_json:
    if character_promote.get("MaxLevel") != 20:
        continue
    character_value_data = {
        "health": character_promote["HPBase"]["Value"],
        "attack": character_promote["AttackBase"]["Value"],
        "defence": character_promote["DefenceBase"]["Value"],
        "speed": character_promote["SpeedBase"]["Value"],
        "aggro": character_promote["BaseAggro"]["Value"],
        "crit_chance": character_promote["CriticalChance"]["Value"],
        "crit_damage": character_promote["CriticalDamage"]["Value"]
    }
    characters_value_data[character_promote["AvatarID"]] = character_value_data

characters_data_no_skill = {}

for cid, character in characters_basic_data.items():
    character_value_data = characters_value_data[cid]
    characters_data_no_skill[cid] = dict(**character, **character_value_data)

with open("data/character_data.json", "w", encoding="utf-8") as f:
    json.dump(characters_data_no_skill, f, indent=4, ensure_ascii=False, sort_keys=True)

with open("data/character_id_mappings.json", "w", encoding="utf-8") as f:
    json.dump(character_id_mapping, f, indent=4, ensure_ascii=False, sort_keys=True)

with (file_path / "ExcelOutput" / "AvatarSkillConfig.json").open(encoding="utf-8") as f:
    character_skill_json = json.load(f)

character_skills = {}

for skill in character_skill_json:
    skill_owner = skill["SkillID"] // 100
    skill_number = skill["SkillID"] % 100
    # stance_list 0 单体 1 全部 2 扩散
    character_skills.setdefault(skill_owner, {}).setdefault(skill_number, []).append(
        {"name": translate(skill["SkillName"]["Hash"]),
         "attack_type": skill.get("AttackType"),
         "stance_list": parse_stance_list(
             skill["ShowStanceList"]),
         "skill_effect": skill.get("SkillEffect"),
         "skill_id": skill["SkillID"],
         "param_list": skill["ParamList"],
         # regex: #\d\[i\]\%
         "skill_introduction": translate(
             skill["SkillDesc"]["Hash"]),
         "level": skill["Level"], "max_level": skill["MaxLevel"]})

with open("data/skills.json", "w", encoding="utf-8") as f:
    json.dump(character_skills, f, indent=4, ensure_ascii=False, sort_keys=True)

weapons = {}

weapon_mappings = {"Mage": "all", "Priest": "healing", "Warrior": "destruction", "Knight": "protection",
                   "Rogue": "single", "Warlock": "debuff", "Shaman": "help"}

with (file_path / "ExcelOutput" / "EquipmentConfig.json").open(encoding="utf-8") as f:
    weapons_json = json.load(f)

with (file_path / "ExcelOutput" / "EquipmentPromotionConfig.json").open(encoding="utf-8") as f:
    weapons_promotion_json = json.load(f)

for weapon in weapons_json:
    weapons[weapon["EquipmentID"]] = {"name": translate(weapon["EquipmentName"]["Hash"]),
                                      "type": weapon_mappings[weapon["AvatarBaseType"]]}

weapons_promotion_data = {}

for weapon_promotion in weapons_promotion_json:
    if weapon_promotion.get("MaxLevel") != 20:
        continue
    weapon_promotion_data = {
        "health": weapon_promotion["BaseHP"]["Value"],
        "attack": weapon_promotion["BaseAttack"]["Value"],
        "defence": weapon_promotion["BaseDefence"]["Value"]
    }
    weapons_promotion_data[weapon_promotion["EquipmentID"]] = weapon_promotion_data

weapons_data = {}

for eid, character in weapons.items():
    weapon_promotion_data = weapons_promotion_data[eid]
    weapons_data[eid] = dict(**character, **weapon_promotion_data)

with open("data/weapons.json", "w", encoding="utf-8") as f:
    json.dump(weapons_data, f, indent=4, ensure_ascii=False, sort_keys=True)
