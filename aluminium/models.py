from decimal import Decimal

from . import characters, attackers
from . import (
    random_chance,
    calc_character_rate,
    ZERO,
    calc_attacker_rate,
    select_character,
)


class CharacterBase:
    def __init__(
            self,
            name: str = None,
            level: int = None,
            health: float = None,
            defensive: float = None,
            attack: float = None,
            speed: int = None,
    ):
        self.name = name
        self.level: int = level if level else 1
        self.__health: Decimal = health if health else ZERO
        self.defensive: Decimal = defensive if defensive else ZERO
        self.attack: Decimal = attack if attack else ZERO
        self.length: Decimal = ZERO
        self.speed: int = speed if speed else 0
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
            name: str = None,
            level: int = None,
            health: Decimal = None,
            defensive: Decimal = None,
            attack: Decimal = None,
            speed: int = None,
            rd: int = None,
    ):
        super().__init__(name, level, health, defensive, attack, speed)
        self.rd: int = rd if rd else 1
        self.buffs = []

    # def do_attack(self, player: CharacterBase):
    #     if self.health == 0:
    #         print("Can't attack.")
    #         return Signal.CANNOT_ATTACK
    #     player.health -= self.attack * ((200 + 10 * self.level) / (player.defensive + 200 + 10 * player.level))
    #     if player.health <= 0:
    #         player.health = 0
    #         print("Player health is 0")
    #         return Signal.PLAYER_DIED
    #     return Signal.OK

    def __repr__(self):
        return super().__repr__()[:-1] + f" rd={self.rd}>"


class EnhanceAttribute:
    def __init__(self, attribute_name, value):
        self.attribute_name: str = attribute_name
        self.value: Decimal = value


class Enhance:

    def __init__(self, name, enhance_type, enhance_position, attributes):
        self.name = name
        self.enhance_type = enhance_type
        self.enhance_position = enhance_position
        self.attributes: list[EnhanceAttribute] = attributes
        self.main_attribute: EnhanceAttribute = self.attributes[0]


class Character(CharacterBase):
    def __init__(
            self,
            name: str = None,
            mt: str = None,
            level: int = None,
            health: Decimal = None,
            defensive: Decimal = None,
            attack: Decimal = None,
            speed: int = None,
            aggro: int = None,
            attribute: str = "",
            attributes: dict[str, Decimal] = "",
            attacker: Attacker = None,
            enhance=None,
    ):
        super().__init__(name, level, health, defensive, attack, speed)
        self.mt = mt
        self.aggro = aggro if aggro else 0
        self.attacker = attacker
        self.enhance = enhance
        self.buffs = []
        self.attributes = attributes

    def common(self, monster: Enemy, process_level: int, level: int) -> None:
        crit = random_chance(self.attributes['crit_chance'])
        print(process_level)
        monster.health -= (
                (self.attack * skills[0]["table"][level - 1])
                * Decimal(
            (
                    (200 + 10 * self.level)
                    / (monster.defensive + 200 + 10 * monster.level)
            )
        )
                * Decimal(((1 + self.attributes['crit_attack'] * self.attributes['crit_chance']) if crit else 1))
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
            attacker: Attacker = Attacker(),
            enhance: list[Enhance] = None,
    ) -> "Character":
        crit_chance = Decimal(".05")
        crit_attack = Decimal(".5")
        if attacker.mt != characters[cid]["mt"]:
            print("警告: 武器与角色的命途属性不同! ")
        """
        name: str = None,
            mt: str = None,
            level: int = None,
            health: Decimal = None,
            defensive: Decimal = None,
            attack: Decimal = None,
            speed: int = None,
            aggro: int = None,
            attribute: str = "",
            attributes: dict[str, Decimal] = "",
            attacker: Attacker = None,
            enhance=None,"""
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
            "None",
            {},
            attacker,
            enhance,
        )

    def __repr__(self):
        return (
                super().__repr__()[:-1]
                + f" mt={self.mt} aggro={self.aggro} attacker={self.attacker} "
                  f"enhance={self.enhance} "
                  f"crit_attack={self.crit_attack} crit_chance={self.crit_chance}>"
        )
