from collections import namedtuple
from decimal import Decimal

from .event import Event
from .movable import Movable
from .relic import Relics
from .utils import calc_character_rate
from .weapon import Weapon

CBase = namedtuple("CBase", "base_health, base_defence, base_attack, base_speed")

CBonus = namedtuple("CBonus", "bonus_health, bonus_defence, bonus_attack, bonus_speed")


class Character(Movable):
    def __init__(self, name: str, mt: str, health: Decimal, defence: Decimal, attack: Decimal,
                 attributes: dict[str, Decimal], speed: Decimal, level: int = 1, weapon: Weapon = None,
                 enhances: Relics = None):
        super().__init__(name, health, defence, attack, speed, attributes)
        self.mt = mt
        self.level = level
        self.weapon = weapon
        self.enhances = enhances
        self.skills = {}
        self.skill_level = {}
        self.buffs = []
        self.extra = {}

    def __str__(self) -> str:
        return f"<{type(self).__name__} name={self.name} mt={self.mt} health={self.health} defence={self.defence} speed={self.speed} attack={self.attack} attributes={self.attributes} weapon={self.weapon} enhances={self.enhances} tick={self.tick} length={self.length} extra={self.extra}>"

    def __repr__(self):
        return str(self)

    @classmethod
    def build(cls, name: str, mt: str, level: int, base_value: CBase, weapon: Weapon, relics: Relics):
        base_health, base_defence, base_attack, base_speed = base_value
        total_value = relics.calc_total_value()
        health = (base_health * calc_character_rate(level, promotion=True) + weapon.health) * (
                total_value.get("health_percent", 0) + 1) + total_value.get("health",
                                                                            0)
        defence = (base_defence * calc_character_rate(level, promotion=True) + weapon.defence) * (
                total_value.get("defence_percent", 0) + 1) + total_value.get(
            "defence", 0)
        attack = (base_attack * calc_character_rate(level, promotion=True) + weapon.attack) * (
                total_value.get("health_percent", 0) + 1) + total_value.get("attack",
                                                                            0)
        speed = base_speed + total_value.get("speed", 0)
        attribute = {"crit_attack": Decimal(.5) + total_value.get("crit_attack", 0),
                     "crit_chance": Decimal(.05) + total_value.get("crit_chance", 0),
                     "effect_resistance": total_value.get("effect_resistance", 0),
                     "effect_hit_rate": total_value.get("effect_hit_rate", 0),
                     "breaking_effect": total_value.get("breaking_effect", 0),
                     "energy_regeneration_rate": total_value.get("energy_regeneration_rate", 0)}

        health *= (total_value.get("health_percent", 0) + 1)
        defence *= (total_value.get("defence_percent", 0) + 1)
        attack *= (total_value.get("attack_percent", 0) + 1)

        bd = cls(name, mt, health, defence, attack, attribute, speed, level, weapon, relics)
        return bd.register_skill()

    def register_skill(self):
        return self

    def attack_object(self, enemy):
        pass

    def get_damage(self, damage):
        pass


class CharacterEvent(Character, Event):
    pass
