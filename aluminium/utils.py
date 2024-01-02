import json
import random

from decimal import Decimal

ZERO = Decimal(0)


def random_chance(chance):
    if random.randint(1, 101) <= chance * 100:
        return True
    return False


def select_character(characters):
    while True:
        for character in characters:
            if random_chance(character.aggro / sum([i.aggro for i in characters])):
                return character


def calc_character_rate(level, promotion=False):
    base_rate = 1 + (level - 1) * Decimal(".05")
    promotion_level = calc_promotion_level(level, promotion) * Decimal(".4")
    return base_rate + promotion_level


def calc_attacker_rate(level, promotion=False):
    base_rate = 1 + (level - 1) * Decimal(".15")
    promotion_level = calc_promotion_level(level, promotion) * Decimal("1.2")
    return base_rate + promotion_level


def calc_promotion_level(level, promotion=False):
    promotion_level = level // 10 - 1
    if level < 10:
        promotion_level += 1
    if level == 80:
        promotion_level -= 1
    if level % 10 == 0 and not promotion and level != 80:
        promotion_level -= 1
    return promotion_level


def translate(translatable_string, lang="zh_cn"):
    with open(f"data/lang/{lang.lower()}.json", encoding="utf-8") as f:
        return json.load(f).get(translatable_string, translatable_string)


def get_enemy_table(level: int, key: str):
    with open("data/number1.json") as f:
        return json.load(f)[level - 1].get(key)
