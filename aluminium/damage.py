from decimal import Decimal

from .movable import Movable
from .utils import ZERO, random_chance


class Damage:
    def __init__(self, damage_value: Decimal, damage_type: str, damage_giver: Movable, damage_getter: Movable):
        self.damage_value = damage_value
        self.damage_type = damage_type
        self.damage_giver = damage_giver
        self.damage_getter = damage_getter

    def __repr__(self):
        return f"<Damage damage_value={self.damage_value} damage_type={self.damage_type} damage_giver={self.damage_giver} damage_getter={self.damage_getter}>"


class DamageCalculator:
    # @classmethod
    # def generic_damage(cls, base_attack: Decimal, damage_boost: Decimal = ZERO, susceptible: Decimal = ZERO,
    #                    reduce_damage: Decimal = ZERO,
    #                    weak: Decimal = ZERO, crit_attack: tuple[Decimal] = (ZERO, ZERO), defensive: Decimal = ZERO,
    #                    resistance: Decimal = ZERO,
    #                    special_damage_boost: Decimal = ZERO, special_susceptible: Decimal = ZERO):
    #     pass

    @classmethod
    def calc_defensive_area(cls, attacker_level, attacked_defensive):
        return (200 + 10 * attacker_level) / (
                attacked_defensive + 200 + 10 * attacker_level)

    @classmethod
    def calc_crit_area(cls, crit_chance, crit_attack):
        return (1 + crit_attack if random_chance(crit_chance) else 1)
