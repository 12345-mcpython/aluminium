import sys

from . import Character, Enemy, select_character
from .models import CharacterBase


class Battle:
    def __init__(self, players: list[Character], enemies: list[Enemy]):
        self.players: list[Character] = players
        self.enemies: list[Enemy] = enemies
        self.queue: list[CharacterBase] = list(players) + list(enemies)
        self.last_print: int = 0
        self.points: int = 3

    def _print_queue(self):
        print("队列")
        for i in sorted(self.queue, key=lambda a: a.length, reverse=True):
            print(i.name, "\t", i.pd, "\t", i.length)
        print()

    def _init_battle(self):
        for i in self.queue:
            i.length = 0
            i.pd = 0
            i.buffs = []
        fastest = max(self.queue, key=lambda a: a.speed)
        t = 10000 / fastest.speed
        for i in self.queue:
            i.length = i.speed * t
            i.pd = round((10000 - i.length) / i.speed)
        return fastest

    def _next(self):
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
        return fastest

    def _parse_command(self, command, process_player: Character):
        if not command:
            return
        command_type = command[0]
        if len(command) == 3:
            process_level = int(command[2])
        else:
            process_level = 0
        # 普攻
        if command_type == "a":
            process_object = int(command[1])
            process_player.common(self.enemies[process_object], process_level, 6)
            return 1
        elif command_type == "z":
            process_object = int(command[1])
            process_player.bp(self.enemies[process_object], self.players[process_object], 10)
            return 1
        elif command_type == "u":
            process_object = int(command[1])
            process_player.ultra(self.enemies[process_object], self.players[process_object], 10)
            return 1
        elif command_type == "v":
            self._print_queue()
        elif command_type == "q":
            sys.exit(0)
        else:
            print("未知命令!")

    def battle(self):
        # on battle start
        move = self._init_battle()
        # before move
        print("行动: ", move.name)
        if isinstance(move, Character):
            while self._parse_command(input(), move) != 1:
                pass
        # after move
        move.length = 0
        while True:
            # before move
            move = self._next()
            print("行动: ", move.name)
            if isinstance(move, Character):
                while self._parse_command(input(), move) != 1:
                    pass
            else:
                attacked_player = select_character(self.players)
                # on character attack
                print(attacked_player.name, "被攻击")
            move.length = 0
            # after move

    def _check_death(self):
        for i in self.queue:
            if i.health == 0:
                if isinstance(i, Character):
                    # on character death
                    print(i.name, "陷入无法战斗状态")
                else:
                    # on enemy death
                    print(i.name, "被击杀")
