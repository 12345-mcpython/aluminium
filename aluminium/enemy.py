from collections import namedtuple
from decimal import Decimal

from .buff import Buff
from .event import Event
from .movable import Movable
from .value import HARD_LEVEL_GROUP_WORLD, HARD_LEVEL_GROUP_VIRTUAL, HARD_LEVEL_GROUP_MAIN_LINE, \
    HARD_LEVEL_GROUP_SPECIAL

EBase = namedtuple("EBase", "base_health, base_defensive, base_attack, base_speed")

EBonus = namedtuple("EBonus", "bonus_health, bonus_defensive, bonus_attack, bonus_speed")


def _stance_weak_not_in(stance_list: list[str]):
    stances = ["physical", "fire", "ice", "thunder", "wind", "quantum", "imaginary"]
    ni = []
    for i in stances:
        if i not in stance_list:
            ni.append(i)
    return ni


class Enemy(Movable):
    def __init__(self, name: str, health: Decimal, defensive: Decimal, attack: Decimal, speed: Decimal, stance: Decimal,
                 stance_weak: list[str], attributes: dict[str, Decimal]):
        super().__init__(name, health, defensive, attack, speed, attributes)
        self.stance = stance
        self.stance_weak = stance_weak
        self.buffs = []
        self.extra = {}
        self.skills = {}
        self.cursor = 1

    def __str__(self) -> str:
        return f"<{type(self).__name__} name={self.name} health={self.health} defensive={self.defensive} attack={self.attack} stance={self.stance} stance_weak={self.stance_weak} attributes={self.attributes} speed={self.speed} tick={self.tick} length={self.length} extra={self.extra} cursor={self.cursor}>"

    def __repr__(self):
        return str(self)

    def add_buff(self, buff: Buff):
        pass

    def attack_object(self, character):
        pass

    def get_damage(self, damage):
        pass

    def register_skill(self):
        return self

    @classmethod
    def build(cls, name: str, level: int, hard_level_group_id: int,
              base_value: tuple[Decimal, Decimal, Decimal, Decimal],
              bonus_value: tuple[Decimal, Decimal, Decimal, Decimal], stance: list[str],
              stance_length: int, extra_attribute: dict[str, Decimal]):
        base_health, base_defensive, base_attack, base_speed = base_value
        bonus_health, bonus_defensive, bonus_attack, bonus_speed = bonus_value
        hard_level_groups = {1: HARD_LEVEL_GROUP_WORLD, 2: HARD_LEVEL_GROUP_MAIN_LINE, 3: HARD_LEVEL_GROUP_VIRTUAL,
                             1401: HARD_LEVEL_GROUP_SPECIAL}
        hard_level_group_mapping = hard_level_groups[hard_level_group_id].read(str(level))
        health, defensive, attack, speed = Decimal(str(base_health)) * Decimal(str(hard_level_group_mapping[
                                                                                       "health"])) * Decimal(
            str(bonus_health)), Decimal(str(base_defensive)) * Decimal(
            str(hard_level_group_mapping[
                    "defensive"])) * Decimal(str(bonus_defensive)), base_attack * \
                                           Decimal(str(hard_level_group_mapping["attack"])) * Decimal(
            str(bonus_attack)), base_speed * \
                                           Decimal(str(hard_level_group_mapping["speed"])) * Decimal(str(bonus_speed))
        attribute = extra_attribute.copy()
        for i in _stance_weak_not_in(stance):
            if not attribute.get(f"{i}_resistance"):
                attribute[f"{i}_resistance"] = Decimal(".2")
        bd = cls(name, health, defensive, attack, speed, Decimal(stance_length), stance, attribute)
        return bd.register_skill()


class EnemyEvent(Enemy, Event):
    pass
