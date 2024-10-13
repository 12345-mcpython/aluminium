import math
from typing import Optional

from .character import Character
from .enemy import Enemy
from .queue import Queue


# battle start -> step in -> before move -> check death ->
# "player -> waiting process / enemy -> do process" -> check_death ->
# after move -> check death -> check all death -> if True -> battle end
# else -> loop to "step in"


class Battle:
    def __init__(self, queue: Queue):
        self.queue = queue
        self.move_object: Optional[Character | Enemy] = None
        self.queue.init_queue()

    def __repr__(self):
        return f"<Battle queue={self.queue}"

    def start_battle(self):
        for i in self.queue.queue:
            i.event.on_battle_start(self)
        return self

    def step_in(self):
        self.queue.move()

    # before move
    def move_start(self):
        move_object = self.queue.get_move()
        # calc dot
        move_object.before_move(self)
        self.move_object = move_object

    # after move
    def move_over(self):
        self.move_object.event.after_move(self)
        self.move_object = None

    def check_death(self):
        for i in self.queue.get_movable():
            if i.health == 0:
                if isinstance(i, Character):
                    # on_character_died 返回 False 则不默认处理 died 事件用于复活
                    if not i.event.on_character_died(self, i):
                        print(i.name, "卒")
                        i.length = -2 ** 32
                        i.saw = False
                if isinstance(i, Enemy):
                    if not i.event.on_enemy_died(self, i):
                        print(i.name, "卒")
                        i.length = -2 ** 32
                        i.saw = False

    # all thing died
    def check_fail(self):
        if all([i.health == 0 for i in self.queue.queue if isinstance(i, Character)]):
            return True
        return False

    def check_win(self):
        if all([i.health == 0 for i in self.queue.queue if isinstance(i, Enemy)]):
            return True
        return False

    def over(self):
        return not (self.check_win() or self.check_fail())

    def skill_config(self, character, skill_name):
        return character.skills[skill_name]["type"]

    def print_info(self):
        for index, enemy in enumerate(self.queue.get_enemy_list()):
            print(
                f"{index} \"{enemy.name}\" {enemy.health / math.floor(enemy.max_health):.3%} {enemy.stance}/{enemy.max_stance}")
        print()
        for index, character in enumerate(self.queue.get_character_list()):
            print(f"{index} \"{character.name}\" {character.health}/{math.floor(character.max_health)} "
                  f"{character.energy / character.max_energy:.0%} 预留buff")

    def print_more_info(self):
        for index, enemy in enumerate(self.queue.get_enemy_list()):
            print(
                f"{index} \"{enemy.name}\" {enemy.health / math.floor(enemy.max_health):.3%} {enemy.stance}/{enemy.max_stance}")
            print(f"行动值: {enemy.tick} {enemy.length} {enemy.attributes}")
            print(
                f"health: {enemy.health}/{math.floor(enemy.max_health)} attack: {enemy.attack} defensive: {enemy.defence} speed: {enemy.speed}")
        print()
        for index, character in enumerate(self.queue.get_character_list()):
            print(f"{index} \"{character.name}\" {character.health}/{math.floor(character.max_health)} "
                  f"{character.energy / character.max_energy:.0%} 预留buff")
            print(f"行动值: {character.tick} {character.length} {character.attributes}")
            print(
                f"health: {character.health}/{math.floor(character.max_health)} attack: {character.attack} defensive: {character.defence} speed: {character.speed}")

    def get_move(self):
        return self.queue.get_move()
