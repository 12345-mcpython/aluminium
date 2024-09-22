from collections import namedtuple
from decimal import Decimal

from .event import Event
from .movable import Movable
from .value import HARD_LEVEL_GROUP

EBase = namedtuple("EBase", "base_health, base_defence, base_attack, base_speed")

EBonus = namedtuple("EBonus", "bonus_health, bonus_defence, bonus_attack, bonus_speed")


def _stance_weak_not_in(stance_list: list[str]):
    stances = ["physical", "fire", "ice", "thunder", "wind", "quantum", "imaginary"]
    ni = []
    for i in stances:
        if i not in stance_list:
            ni.append(i)
    return ni


class Enemy(Movable):
    def __init__(self, name: str, health: Decimal, defence: Decimal, attack: Decimal, speed: Decimal, stance: Decimal,
                 stance_weak: list[str], attributes: dict[str, Decimal]):
        super().__init__(name, health, defence, attack, speed, attributes)
        self.stance = stance
        self.stance_weak = stance_weak
        self.skills = {}
        self.cursor = 1

    def __str__(self) -> str:
        return f"<{type(self).__name__} name={self.name} health={self.health} defence={self.defence} attack={self.attack} stance={self.stance} stance_weak={self.stance_weak} attributes={self.attributes} speed={self.speed} tick={self.tick} length={self.length} extra={self.extra} cursor={self.cursor}>"

    def __repr__(self):
        return str(self)

    @classmethod
    def build(cls, name: str, level: int, hard_level_group_id: int,
              base_value: tuple[Decimal, Decimal, Decimal, Decimal],
              bonus_value: tuple[Decimal, Decimal, Decimal, Decimal], stance: list[str],
              stance_length: int, extra_attribute: dict[str, Decimal]):
        base_health, base_defence, base_attack, base_speed = base_value
        bonus_health, bonus_defence, bonus_attack, bonus_speed = bonus_value
        hard_level_group_mapping = HARD_LEVEL_GROUP.read()[str(hard_level_group_id)][str(level)]
        health, defence, attack, speed = Decimal(str(base_health)) * Decimal(str(hard_level_group_mapping[
                                                                                     "health"])) * Decimal(
            str(bonus_health)), Decimal(str(base_defence)) * Decimal(
            str(hard_level_group_mapping[
                    "defence"])) * Decimal(str(bonus_defence)), base_attack * \
                                         Decimal(str(hard_level_group_mapping["attack"])) * Decimal(
            str(bonus_attack)), base_speed * \
                                         Decimal(str(hard_level_group_mapping["speed"])) * Decimal(str(bonus_speed))
        attribute = extra_attribute.copy()
        for i in _stance_weak_not_in(stance):
            if not attribute.get(f"{i}_resistance"):
                attribute[f"{i}_resistance"] = Decimal(".2")
        bd = cls(name, health, defence, attack, speed, Decimal(stance_length), stance, attribute)
        return bd.register_skill()

    def register_skill(self):
        pass


class EnemyEvent(Enemy, Event):
    pass
