from decimal import Decimal


def calc_character_rate(level: int, promotion=False):
    base_rate = 1 + (level - 1) * Decimal('.05')
    promotion_level = calc_promotion_level(level, promotion) * Decimal('.4')
    return base_rate + promotion_level


def calc_attacker_rate(level, promotion=False):
    base_rate = 1 + (level - 1) * Decimal('.15')
    promotion_level = calc_promotion_level(level, promotion) * Decimal('1.2')
    return base_rate + promotion_level


def calc_promotion_level(level, promotion=False):
    promotion_level = Decimal(level) // 10 - 1
    if level < 10:
        promotion_level += 1
    if level == 80:
        promotion_level -= 1
    if level % 10 == 0 and not promotion and level != 80:
        promotion_level -= 1
    return promotion_level


def list_str2decimal(string_list):
    return [Decimal(i) for i in string_list]
