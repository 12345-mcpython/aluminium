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
        self.length: Decimal = ZERO
        self.speed: int = speed if speed else 0
        self.pd: Decimal = ZERO

    def __repr__(self):
        return f"<{self.__class__.__name__} name={self.name} level={self.level} health={self.__health} " \
               f"defensive={self.defensive} attack={self.attack} length={self.length} speed={self.speed} pd={self.pd}>"

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

    def __repr__(self):
        return f"<{self.__class__.__name__} name={self.name} mt={self.mt} " \
               f"level={self.health} health={self.health} defensive={self.defensive} attack={self.attack}>"


class Enemy(CharacterBase):
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

    def __repr__(self):
        return super().__repr__()[:-1] + f" rd={self.rd}>"


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

    def __repr__(self):
        return super().__repr__()[:-1] + f" mt={self.mt} aggro={self.aggro} attacker={self.attacker} " \
                                         f"enhance={self.enhance} " \
                                         f"crit_attack={self.crit_attack} crit_chance={self.crit_chance}>"


class CombatQueue:
    def __init__(self, players: list[Character], enemies: list[Enemy]):
        self.players: list[Character] = players
        self.enemies: list[Enemy] = enemies
        self.queue: list[CharacterBase] = list(players) + list(enemies)
        self.last_print: int = 0
        self.points: int = 3
        self.total_aggro = sum([i.aggro for i in players])

    def print_queue(self):
        print("队列")
        for i in sorted(self.queue, key=lambda a: a.length, reverse=True):
            print(i.name, "\t", i.pd, "\t", i.length)
        print()

    def print_health(self):
        for i in range(1, len(self.enemies) + 1):
            print(i, end="\t")
        print()
        for i in self.enemies:
            print(i.name, end="\t")
        print()
        for i in self.enemies:
            print(i.health, end="\t")
        print()
        print()
        for i in range(1, len(self.players) + 1):
            print(i, end="\t")
        print()
        for i in self.players:
            print(i.name, end="\t")
        print()
        for i in self.players:
            print(i.health, end="\t")
        print()

    def clear_die(self):
        for i in self.players:
            if i.health == 0:
                self.players.remove(i)
        for i in self.enemies:
            if i.health == 0:
                self.enemies.remove(i)
        self.queue = list(self.players) + list(self.enemies)

    def make_queue(self):

        fastest = max(self.queue, key=lambda a: a.speed)

        t = 10000 / fastest.speed

        for i in self.queue:
            i.length = i.speed * t
            i.pd = round((10000 - i.length) / i.speed)

        # self.print_queue()
        print()
        # fastest.length = 0
        while True:
            fastest = max(self.queue, key=lambda a: a.length)
            for i in sorted(self.queue, key=lambda a: a.length):
                if round(i.length) >= 10000:
                    i.length = 10000
                    continue
                if round(i.pd) < 0:
                    i.pd = 0
                    continue
                i.length += (10000 - fastest.length) / fastest.speed * i.speed
                i.pd = round((10000 - i.length) / i.speed)
            self.print_health()
            print("行动: ", fastest.name)
            # self.print_queue()
            if isinstance(fastest, Character):
                while True:
                    message = input()
                    if not message:
                        continue
                    command, *args = message.split(" ")
                    if len(args) == 1:
                        args = args[0]
                    match command:
                        case "a":
                            if not str(args).isdigit():
                                print("数值错误! ")
                                continue
                            fastest.do_attack(self.enemies[int(args) - 1])
                            break
                        case "cheat":
                            for i in self.enemies:
                                i.health = 0
                            break
                        case "view_queue":
                            self.print_queue()
                        case _:
                            print("未知命令!")
            else:
                choose_character = None
                flag = True
                while flag:
                    for i in self.players:
                        if random_chance(i.aggro / self.total_aggro):
                            choose_character = i
                            flag = False
                            break
                print(fastest.name, "对", choose_character.name, "进行攻击.")
                fastest.do_attack(choose_character)
            self.clear_die()
            for i in self.players:
                if i.health != 0:
                    break
            else:
                print("战斗失败")
                return Signal.COMBAT_FAIL
            for i in self.enemies:
                if i.health != 0:
                    break
            else:
                print("战斗结束")
                return Signal.COMBAT_WIN

            fastest.length = 0
