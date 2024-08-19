import json
from decimal import Decimal

path = input("Enter tool's json: ")

percent = ["effect_hit_rate", "effect_resistance", "attack_percent", "health_percent", "defensive_percent",
           "crit_chance", "crit_attack", "breaking_effect"]


def get_promote_level(attribute, value, star):
    value = Decimal(str(value))
    if attribute in percent:
        value /= 100
    with open(f"data/relic_{star}_sub_attribute.json") as f:
        sub_data = json.load(f)
    base = Decimal(str(sub_data[attribute]['base']))
    bonus = Decimal(str(sub_data[attribute]['bonus']))
    cursor = 0
    promote_level = int(round(value, 5) // round(base, 5))
    p = base * promote_level
    for i in range(promote_level * 3):
        if value - p < 10 ** -5:
            return {"promote_level": promote_level - 1, "attribute_level": cursor}
        p += bonus
        cursor += 1
    print(attribute, value, p, promote_level, "error!")


with open(path, encoding="utf-8") as f:
    data = json.load(f)


def parse_slot(slot):
    match slot:
        case "Link Rope":
            return "line"
        case "Body":
            return "body"
        case "Hands":
            return "hand"
        case "Head":
            return "head"
        case "Feet":
            return "boot"
        case "Planar Sphere":
            return "ball"


def parse_main_attribute(attribute, part):
    match attribute:
        case "ATK" if part == "Hands":
            return "attack"
        case "ATK" if part != "Hands":
            return "attack_percent"
        case "DEF":
            return "defensive_percent"
        case "HP" if part == "Head":
            return "health"
        case "HP" if part != "Head":
            return "health_percent"
        case "SPD":
            return "speed"
        case "CRIT Rate":
            return "crit_chance"
        case "CRIT DMG":
            return "crit_attack"
        case "Energy Regeneration Rate":
            return "energy_regeneration_rate"
        case "Break Effect":
            return "breaking_effect"
        case "Effect Hit Rate":
            return "effect_hit_rate"
        case "Physical DMG Boost":
            return "physical_damage_boost"
        case "Wind DMG Boost":
            return "wind_damage_boost"
        case "Fire DMG Boost":
            return "fire_damage_boost"
        case "Ice DMG Boost":
            return "ice_damage_boost"
        case "Lightning DMG Boost":
            return "lightning_damage_boost"
        case "Quantum DMG Boost":
            return "quantum_damage_boost"
        case "Imaginary DMG Boost":
            return "fire_damage_boost"
        case "Outgoing Healing Boost":
            return "outgoing_healing_boost"


def parse_sub_attribute(attribute):
    match attribute:
        case "ATK":
            return "attack"
        case "ATK_":
            return "attack_percent"
        case "DEF":
            return "defensive"
        case "DEF_":
            return "defensive_percent"
        case "HP":
            return "health"
        case "HP_":
            return "health_percent"
        case "SPD":
            return "speed"
        case "CRIT Rate_":
            return "crit_chance"
        case "CRIT DMG_":
            return "crit_attack"
        case "Break Effect_":
            return "breaking_effect"
        case "Effect Hit Rate_":
            return "effect_hit_rate"
        case "Effect RES_":
            return "effect_resistance"


relics = data["relics"]

characters_tool_data = {}

for i in relics:
    if i["location"]:
        if characters_tool_data.get(i["location"]):
            characters_tool_data[i["location"]].append(i)
        else:
            characters_tool_data[i["location"]] = [i]

characters_dump_data = {}

for i, j in characters_tool_data.items():
    character_data = []
    for k in j:
        relic = {"set": k['set'], "type": parse_slot(k["slot"]),
                 "json": {"main_attribute": {parse_main_attribute(k['mainstat'], k['slot']): k['level']},
                          "sub_attributes": {parse_sub_attribute(abc['key']): get_promote_level(
                              parse_sub_attribute(abc['key']), abc['value'], k['rarity']) for abc in
                              k["substats"]}}}
        character_data.append(relic)
    characters_dump_data[i] = character_data

with open("data/dump_data.json", "w", encoding="utf-8") as f:
    json.dump(characters_dump_data, f, indent=4, ensure_ascii=False)
