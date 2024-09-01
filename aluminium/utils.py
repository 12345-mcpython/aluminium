import random
from decimal import Decimal

ZERO = Decimal("0")


def random_chance(chance):
    if random.randint(1, 101) <= chance * 100:
        return True
    return False


def calc_character_rate(level: int, promotion=False):
    base_rate = 1 + (level - 1) * Decimal(".05")
    promote_count = level // 10 - (2 if not promotion else 1)
    if promotion and level == 80:
        promote_count -= 1
    if level <= 20:
        promote_count = 0
    return base_rate + promote_count * Decimal(".4")


def calc_attacker_rate(level, promotion=False):
    base = Decimal("1.00") + (level - 1) * Decimal(".15")
    first_promote = level > 20 or (level == 20 and promotion)
    promote_count = level // 10 - (3 if not promotion else 2)
    if promotion and level == 80:
        promote_count -= 1
    if level <= 20:
        promote_count = 0
    return base + Decimal("1.6") * promote_count + (Decimal("1.2") if first_promote else ZERO)


def list_str2decimal(string_list):
    return [Decimal(i) for i in string_list]
