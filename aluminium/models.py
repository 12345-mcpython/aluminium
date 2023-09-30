from decimal import Decimal

from aluminium import characters
from aluminium import attackers
from aluminium.utils import random_chance, calc_character_rate, ZERO, calc_attacker_rate


class Signal:
    OK = 0
    PLAYER_DIED = 1
    MONSTER_DIED = 2
    CANNOT_ATTACK = 3
    COMBAT_WIN = 4
    COMBAT_FAIL = 5


def check_died(signal):
    if signal == Signal.MONSTER_DIED:
        print("Monster Died")
        return Signal.COMBAT_WIN
    elif signal == Signal.PLAYER_DIED:
        print("Player Died")
        return Signal.COMBAT_FAIL
    else:
        return Signal.OK


class CharacterBase:
    def __init__(self, name: str = None,
                 level: int = None,
                 health: float = None,
                 defensive: float = None,
                 attack: float = None,
                 speed: int = None
                 ):
        self.name = name
        self.level: int = level if level else 1
        self.__health: Decimal = health if health else ZERO
        self.defensive: Decimal = defensive if defensive else ZERO
        self.attack: Decimal = attack if attack else ZERO
        self.speed: int = speed if speed else 0

    def do_attack(self, attack_object):
        pass

    @property
    def health(self):
        return round(self.__health)

    @health.setter
    def health(self, set_value):
        self.__health = set_value


class Attacker:
    def __init__(self, name: str = None,
                 mt: str = None,
                 level: int = None,
                 health: Decimal = None,
                 defensive: Decimal = None,
                 attack: Decimal = None):
        self.name = name
        self.mt = mt
        self.level: int = level if level else 1
        self.health: Decimal = health if health else 0
        self.defensive: Decimal = defensive if defensive else 0
        self.attack: Decimal = attack if attack else 0

    @classmethod
    def build_attacker(cls, aid, level):
        return Attacker(attackers[aid]["name"], attackers[aid]["mt"], level,
                        Decimal(attackers[aid]["health"]) * calc_attacker_rate(level),
                        Decimal(attackers[aid]["defensive"]) * calc_attacker_rate(level),
                        Decimal(attackers[aid]["attack"]) * calc_attacker_rate(level))


class Monster(CharacterBase):
    def __init__(self, name: str = None,
                 level: int = None,
                 health: Decimal = None,
                 defensive: Decimal = None,
                 attack: Decimal = None,
                 speed: int = None,
                 rd: int = None):
        super().__init__(name, level, health, defensive, attack, speed)
        self.rd: int = rd if rd else 1

    def do_attack(self, player: CharacterBase):
        if self.health == 0:
            print("Can't attack.")
            return Signal.CANNOT_ATTACK
        player.health -= self.attack * ((200 + 10 * self.level) / (player.defensive + 200 + 10 * player.level))
        if player.health <= 0:
            player.health = 0
            print("Player health is 0")
            return Signal.PLAYER_DIED
        return Signal.OK


class Character(CharacterBase):
    def __init__(self, name: str = None,
                 mt: str = None,
                 level: int = None,
                 health: Decimal = None,
                 defensive: Decimal = None,
                 attack: Decimal = None,
                 speed: int = None,
                 aggro: int = None,
                 crit_chance: Decimal = 0.05,
                 crit_attack: Decimal = 0.50,
                 attacker: Attacker = None,
                 enhance=None):
        super().__init__(name, level, health, defensive, attack, speed)
        self.mt = mt
        self.aggro = aggro if aggro else 0
        self.attacker = attacker
        self.enhance = enhance
        self.crit_attack = crit_attack
        self.crit_chance = crit_chance

    def do_attack(self, monster: CharacterBase):
        if self.health == 0:
            return Signal.CANNOT_ATTACK
        crit = random_chance(self.crit_chance)
        if crit:
            print(self.name + " 暴击")
        monster.health -= self.attack * Decimal(
            ((200 + 10 * self.level) / (monster.defensive + 200 + 10 * monster.level))) * Decimal(
            ((1 + self.crit_attack * self.crit_chance) if crit else 1))
        if monster.health <= 0:
            monster.health = 0
            return Signal.MONSTER_DIED
        return Signal.OK

    @classmethod
    def build_character(cls, cid, level, attacker=Attacker(), enhance=None):
        crit_chance = Decimal(".05")
        crit_attack = Decimal(".5")
        if attacker.mt != characters[cid]['mt']:
            print("警告: 武器与角色的命途属性不同! ")
        return cls(characters[cid]['name'], characters[cid]['mt'], 1,
                   Decimal(characters[cid]['health']) * calc_character_rate(level, promotion=True) + attacker.health,
                   Decimal(characters[cid]['defensive']) * calc_character_rate(level,
                                                                               promotion=True) + attacker.defensive,
                   Decimal(characters[cid]['attack']) * calc_character_rate(level, promotion=True) + attacker.attack,
                   characters[cid]['speed'],
                   characters[cid]['aggro'], crit_chance, crit_attack, attacker, enhance)


class CombatQueue:
    def __init__(self, players: list[Character], monsters: list[Monster]):
        self.players = players
        self.monsters = monsters

    def attack(self):
        print("战斗简介")
        print(self.players[0].name, self.monsters[0].name)
        while True:
            signal_monster = self.players[0].do_attack(self.monsters[0])
            if check_died(signal_monster):
                return
            signal_player = self.monsters[0].do_attack(self.players[0])
            if check_died(signal_player):
                return
            print(self.players[0].health, self.monsters[0].health)
