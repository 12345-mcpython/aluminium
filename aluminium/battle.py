import sys

from . import Character, Enemy, select_character


class Battle:
    def __init__(self, players: list[Character], enemies: list[Enemy]):
        self.players: list[Character] = players
        self.enemies: list[Enemy] = enemies
        self.queue: list[Character | Enemy] = [*list(players), *list(enemies)]
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

    def _parse_command(self, command, process_player: Character, enemy=False):
        if not command:
            return
        command_type = command[0]
        # 1 2 3 4 5
        # 1 2 3 4
        #
        # 普攻
        # a1
        try:
            if command_type == "a" and len(command) == 2 and not enemy:
                process_object = int(command[1]) - 1
                process_player.common(self.enemies[process_object], 6)
                return 1
            # z2
            elif command_type == "z" and len(command) == 2 and not enemy:
                process_object = int(command[1]) - 1
                process_player.bp(self.enemies[process_object], self.players[process_object], 10)
                return 1
            # u1 回合内释放终结技
            elif command_type == "u" and len(command) == 2 and not enemy:
                process_object = int(command[1]) - 1
                process_player.ultra(self.enemies[process_object], self.players[process_object], 10)
                return 1
            # u23 使用 2 位置的角色对 3 对象进行作用 抢终结技
            elif command_type == "u" and len(command) == 3:
                process_player = self.players[int(command[1]) - 1]
                process_object = int(command[2]) - 1
                process_player.ultra(self.enemies[process_object], self.players[process_object], 10)
                return 1
            elif command_type == "v":
                self._print_queue()
            elif command_type == "q":
                sys.exit(0)
            else:
                print("未知命令!")
        except IndexError:
            print("选择目标超出范围!")

    def _action(self, player):
        while self._parse_command(input(), player) != 1:
            pass

    def _check_death(self):
        for i in self.queue:
            if i.health <= 0:
                if isinstance(i, Character):
                    print(i.name, "陷入无法战斗状态")
                    # event: on character death: call reborn
                    if i.health <= 0:
                        self._remove_player(i)
                        return 2
                    else:
                        # event: on character reborn: ?
                        pass
                else:
                    print(i.name, "被击杀")
                    # event: on enemy death: character call reproduced, reborn
                    if i.health <= 0:
                        self._remove_enemy(i)
                        return 2
                    else:
                        # event: on enemy reborn: ?
                        pass
        return 0

    def _check_battle_status(self):
        for i in self.players:
            if i.health > 0:
                break
        else:
            print("战斗失败")
            return "failure"
        for i in self.enemies:
            if i.health > 0:
                break
        else:
            print("战斗成功")
            return "successful"
        return "battling"

    def _remove_player(self, i: Character):
        self.players.remove(i)
        self.queue.remove(i)

    def _remove_enemy(self, i: Enemy):
        self.enemies.remove(i)
        self.queue.remove(i)

    def _print_information(self):
        print("\t".join([i.name for i in self.players]))
        print("\t".join([str(i.health) for i in self.players]))
        print()
        print("\t".join([i.name for i in self.enemies]))
        print("\t".join([str(i.health) for i in self.enemies]))

    def battle(self):
        # on battle start: add default buff
        move = self._init_battle()
        while True:
            # before move: apply dot attack, healing
            if self._check_death() == 2:
                move = self._next()
                continue
            if self._check_battle_status() == "failure" or self._check_battle_status() == "successful":
                break
            self._print_information()
            print("行动: ", move.name)
            if isinstance(move, Character):
                self._action(move)
            else:
                # do enemy move

                # attacked_player = select_character(self.players)
                # on character attack
                # print(attacked_player.name, "被攻击")
                if self._check_death() == 2:
                    move = self._next()
                    continue
                if self._check_battle_status() == "failure" or self._check_battle_status() == "successful":
                    break
            move.length = 0
            move = self._next()
            # after move
