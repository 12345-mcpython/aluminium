from __future__ import annotations

from decimal import Decimal
from typing import Optional, TYPE_CHECKING

from . import value
from .movable import Movable
from .utils import random_chance

if TYPE_CHECKING:
    from .character import Character


class Damage:
    def __init__(self, damage_value: Decimal, damage_type: str, damage_attribute: str, damage_giver: Movable):
        self.damage_value = damage_value
        self.damage_type = damage_type
        self.damage_attribute = damage_attribute
        self.damage_giver = damage_giver

    def __repr__(self):
        return f"<Damage damage_value={self.damage_value} damage_type={self.damage_type} damage_attribute={self.damage_attribute} damage_giver={self.damage_giver}"


class DamageCalculator:
    # Area part
    @classmethod
    def calc_defensive_area(cls, damage, attacker_level, attacked_defensive):
        return damage.damage_value * (200 + 10 * attacker_level) / (
                attacked_defensive + 200 + 10 * attacker_level)

    @classmethod
    def calc_crit_area(cls, damage, crit_chance: Decimal, crit_attack: Decimal):
        return damage.damage_value * (1 + crit_attack if random_chance(crit_chance) else 1)

    @classmethod
    def calc_damage_boost_area(cls, damage, boost_value: Decimal):
        return damage.damage_value * (1 + boost_value)

    # "Breaking" is not "broken"
    @classmethod
    def calc_not_break_damage_reduce_area(cls, damage: Damage, is_broke: bool):
        return damage.damage_value * 1 if is_broke else Decimal(".9")

    @classmethod
    def calc_damage_reduce_area(cls, damage, reduce_value: Decimal):
        return damage.damage_value * (1 - reduce_value)

    # Generate damage part
    @classmethod
    def calc_breaking_attack(cls, stance_length: int, breaking_attribute: str, character: Optional[Character]):
        level = character.level
        breaking_effect = character.attributes["breaking_effect"]
        attribute_rates = {"physical": Decimal(2),
                           "quantum": Decimal("0.5"),
                           "fire": Decimal(2),
                           "ice": Decimal(1),
                           "thunder": Decimal(1),
                           "imaginary": Decimal("0.5"),
                           "wind": Decimal("1.5")}
        attribute_rate = attribute_rates[breaking_attribute]
        breaking_rate = Decimal(str(value.BREAKING_RATE.read(str(level))))
        damage = breaking_rate * attribute_rate * (Decimal(1) + Decimal(str(breaking_effect))) * (
                stance_length + 2) / 4
        return Damage(damage, "breaking", breaking_attribute, character)

    @classmethod
    def calc_skill_damage(cls, giver: Character, attack, damage_rate, damage_attribute, damage_type):
        """
        :param giver: damage giver
        :param attack: after attack boost attack value
        :param damage_rate: in skill viewer
        :param damage_attribute: in skill viewer
        :param damage_type: damage type
        :return: Damage class
        """
        return Damage(attack * damage_rate, damage_type, damage_attribute, giver)

    # Utils part
    @classmethod
    def calc_attack_boost(cls, attack: Decimal, attack_percent: Decimal, attack_value: Decimal):
        return attack * (1 + attack_percent) + attack_value
