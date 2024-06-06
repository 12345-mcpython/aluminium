from decimal import Decimal

from .movable import Movable
from .utils import random_chance


class Damage:
    def __init__(self, damage_value: Decimal, damage_type: str, damage_attribute: str, damage_giver: Movable,
                 damage_getter: Movable):
        self.damage_value = damage_value
        self.damage_type = damage_type
        self.damage_attribute = damage_attribute
        self.damage_giver = damage_giver
        self.damage_getter = damage_getter

    def __repr__(self):
        return f"<Damage damage_value={self.damage_value} damage_type={self.damage_type} damage_attribute={self.damage_attribute} damage_giver={self.damage_giver} damage_getter={self.damage_getter}>"


class DamageCalculator:
    @classmethod
    def calc_defensive_area(cls, damage, attacker_level, attacked_defensive):
        return damage.damage_value * (200 + 10 * attacker_level) / (
                attacked_defensive + 200 + 10 * attacker_level)

    @classmethod
    def calc_crit_area(cls, damage, crit_chance, crit_attack):
        return damage.damage_value * (1 + crit_attack if random_chance(crit_chance) else 1)

    @classmethod
    def calc_damage_boost(cls, damage, boost_value):
        return damage.damage_value * (1 + boost_value)

    @classmethod
    def calc_skill_damage(cls, attack, damage_rate, damage_attribute):
        """

        :param attack: after attack boost attack value
        :param damage_rate: in skill viewer
        :param damage_attribute: in skill viewer
        :return: Damage class
        """
        return Damage()
