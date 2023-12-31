import json
from decimal import Decimal
from enum import Enum

from . import characters, attackers
from . import (
    random_chance,
    calc_character_rate,
    ZERO,
    calc_attacker_rate,
)
from .utils import translate, get_enemy_table


class EnhanceAttributes(Enum):
    EFFECT_RESISTANCE = 0
    EFFECT_HIT_RATE = 1
    CRIT_RATE = 2
    CRIT_DMG = 3
    BREAK_EFFECT = 4
    OUTGOING_HEALING_BOOST = 5
    ENERGY_REGENERATION_RATE = 6


class CharacterBase:
    def __init__(
            self,
            name: str,
            level: int,
            health: Decimal,
            defensive: Decimal,
            attack: Decimal,
            speed: int,
            attribute: dict[str, Decimal] = None,
            behavior: dict[str, list[dict] | dict[str, str]] = None
    ):
        self.name = name
        self.level: int = level
        self.__health: Decimal = Decimal(health)
        self.defensive: Decimal = Decimal(defensive)
        self.attack: Decimal = Decimal(attack)
        self.length: Decimal = ZERO
        self.speed: int = speed
        self.attribute = attribute
        self.behavior = behavior
        self.pd: Decimal = ZERO
        self.buffs = []

    def __repr__(self):
        return (
            f"<{self.__class__.__name__} name={self.name} level={self.level} health={self.__health} "
            f"defensive={self.defensive} attack={self.attack} length={self.length} speed={self.speed} pd={self.pd}>"
        )

    @property
    def health(self):
        return round(self.__health)

    @health.setter
    def health(self, set_value):
        self.__health = set_value


class Attacker:
    def __init__(
            self,
            name: str = None,
            mt: str = None,
            level: int = None,
            health: Decimal = None,
            defensive: Decimal = None,
            attack: Decimal = None,
    ):
        self.name = name
        self.mt = mt
        self.level: int = level if level else 1
        self.health: Decimal = health if health else ZERO
        self.defensive: Decimal = defensive if defensive else ZERO
        self.attack: Decimal = attack if attack else ZERO

    @classmethod
    def build_attacker(cls, aid, level):
        return Attacker(
            attackers[aid]["name"],
            attackers[aid]["mt"],
            level,
            Decimal(attackers[aid]["health"]) * calc_attacker_rate(level),
            Decimal(attackers[aid]["defensive"]) * calc_attacker_rate(level),
            Decimal(attackers[aid]["attack"]) * calc_attacker_rate(level),
        )

    def __repr__(self):
        return (
            f"<{self.__class__.__name__} name={self.name} mt={self.mt} "
            f"level={self.health} health={self.health} defensive={self.defensive} attack={self.attack}>"
        )


class Enemy(CharacterBase):
    def __init__(
            self,
            name: str,
            level: int,
            health: Decimal,
            defensive: Decimal,
            attack: Decimal,
            speed: int,
            rd: int,
            attribute: dict[str, Decimal] = None,
            behavior: list = None
    ):
        super().__init__(name, level, health, defensive, attack, speed, attribute, behavior)
        self.rd: int = rd if rd else 1
        self.buffs = []

    def __repr__(self):
        return super().__repr__()[:-1] + f" rd={self.rd}>"

    @classmethod
    def build_enemy(cls, name, level):
        with open(f"data/enemies/{name}.json") as f:
            data = json.load(f)
        name = translate(data["name"])
        attack = data["attack"] * get_enemy_table(level, "attack")
        defensive = data["attack"] * get_enemy_table(level, "defensive")
        health = data["attack"] * get_enemy_table(level, "health")
        speed = data["speed"] * get_enemy_table(level, "speed")
        rd = data["rd"]
        attribute = data["attribute"]
        behavior = data["behavior"]
        return cls(name, level, health, defensive, attack, speed, rd, attribute, behavior)


class Character(CharacterBase):
    def __init__(
            self,
            name: str,
            mt: str,
            level: int,
            health: Decimal,
            defensive: Decimal,
            attack: Decimal,
            speed: int,
            aggro: int,
            attribute: dict[str, Decimal] = None,
            attacker: Attacker = None,
            behavior: list = None
    ):
        super().__init__(name, level, health, defensive, attack, speed, attribute, behavior)
        self.mt = mt
        self.aggro = aggro if aggro else 0
        self.attacker = attacker
        self.buffs = []

    def common(self, monster: Enemy, level: int) -> None:
        crit = random_chance(self.attribute["crit_chance"])
        monster.health -= (
                self.attack
                * Decimal(
            (
                    (200 + 10 * self.level)
                    / (monster.defensive + 200 + 10 * monster.level)
            )
        )
                * Decimal(((1 + self.attribute["crit_attack"] * self.attribute["crit_chance"]) if crit else 1))
        )

    def bp(self, monster_center: Enemy, player_center: "Character", level: int):
        # print(self.skill["skill"])
        print("bp")

    def ultra(self, monster_center: Enemy, player_center: "Character", level: int):
        # print(self.skill["end"])
        print("ultra")

    @classmethod
    def build_character(
            cls,
            cid: int,
            level: int,
            attacker: Attacker = Attacker()
    ) -> "Character":
        crit_chance = Decimal(".05")
        crit_attack = Decimal(".5")
        if attacker.mt != characters[cid]["mt"]:
            print("警告: 武器与角色的命途属性不同! ")
        return cls(
            characters[cid]["name"],
            characters[cid]["mt"],
            level,
            Decimal(characters[cid]["health"])
            * calc_character_rate(level, promotion=True)
            + attacker.health,
            Decimal(characters[cid]["defensive"])
            * calc_character_rate(level, promotion=True)
            + attacker.defensive,
            Decimal(characters[cid]["attack"])
            * calc_character_rate(level, promotion=True)
            + attacker.attack,
            characters[cid]["speed"],
            characters[cid]["aggro"],
            {"crit_chance": crit_chance, "crit_attack": crit_attack},
            attacker
        )

    def __repr__(self):
        return (
                super().__repr__()[:-1]
                + f" mt={self.mt} aggro={self.aggro} attacker={self.attacker} "
                  f"attributes={self.attribute} >"
        )
