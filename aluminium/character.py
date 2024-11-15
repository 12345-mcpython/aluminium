from __future__ import annotations

from collections import namedtuple
from decimal import Decimal

from .event import Event
from .movable import Movable
from .relic import Relics
from .utils import calc_character_rate
from .weapon import Weapon

CBase = namedtuple("CBase", "base_health, base_defence, base_attack, base_speed, aggro, max_energy")


class Character(Movable):
    def __init__(self, event: type[Event],
                 name: str,
                 mt: str,
                 health: Decimal,
                 defence: Decimal,
                 attack: Decimal,
                 speed: Decimal,
                 aggro: int,
                 max_energy: Decimal,
                 attributes: dict[str, Decimal], weapon: Weapon,
                 relics: Relics, level: int = 1):
        super().__init__(event, name, health, defence, attack, speed, attributes)
        self.mt = mt
        self.aggro = aggro
        self.energy = Decimal(0)
        self.max_energy = max_energy
        self.level = level
        self.weapon = weapon
        self.relics = relics
        self.skills_rate = {}
        # record skill level value
        self.skills_level = {}

    def __str__(self) -> str:
        return f"<{type(self).__name__} name={self.name} mt={self.mt} health={self.health} defence={self.defence} speed={self.speed} aggro={self.aggro} energy={self.energy}/{self.max_energy} attack={self.attack} attributes={self.attributes} weapon={self.weapon} enhances={self.relics} tick={self.tick} length={self.length}>"

    def __repr__(self):
        return str(self)

    @classmethod
    def build(cls, event: type[Event], name: str, mt: str, level: int, base_value: CBase, weapon: Weapon,
              relics: Relics):
        base_health, base_defence, base_attack, base_speed, aggro, max_energy = base_value
        relic_total_value = relics.calc_total_value()
        health = (base_health * calc_character_rate(level, promotion=True) + weapon.health) * (
                relic_total_value.get("health_percent", 0) + 1) + relic_total_value.get("health",
                                                                            0)
        defence = (base_defence * calc_character_rate(level, promotion=True) + weapon.defence) * (
                relic_total_value.get("defence_percent", 0) + 1) + relic_total_value.get(
            "defence", 0)
        attack = (base_attack * calc_character_rate(level, promotion=True) + weapon.attack) * (
                relic_total_value.get("health_percent", 0) + 1) + relic_total_value.get("attack",
                                                                            0)
        speed = base_speed + relic_total_value.get("speed", 0)
        attribute = {"crit_attack": Decimal(.5) + relic_total_value.get("crit_attack", 0),
                     "crit_chance": Decimal(.05) + relic_total_value.get("crit_chance", 0),
                     "effect_resistance": relic_total_value.get("effect_resistance", 0),
                     "effect_hit_rate": relic_total_value.get("effect_hit_rate", 0),
                     "breaking_effect": relic_total_value.get("breaking_effect", 0),
                     "energy_regeneration_rate": relic_total_value.get("energy_regeneration_rate", 0),
                     "outgoing_healing_boost": relic_total_value.get("outgoing_healing_boost", 0)}

        health *= (relic_total_value.get("health_percent", 0) + 1)
        defence *= (relic_total_value.get("defence_percent", 0) + 1)
        attack *= (relic_total_value.get("attack_percent", 0) + 1)

        bd = cls(event, name, mt, health, defence, attack, speed, aggro, max_energy, attribute, weapon, relics, level)
        return bd
