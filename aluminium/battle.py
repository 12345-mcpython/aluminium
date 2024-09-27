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
        for i in self.queue.queue:
            if i.health == 0:
                if isinstance(i, Character):
                    if not i.event.on_character_died(self, i):
                        self.queue.queue.remove(i)
                if isinstance(i, Enemy):
                    if not i.event.on_enemy_died(self, i):
                        self.queue.queue.remove(i)

    def check_fail(self):
        if all([i.health == 0 for i in self.queue.queue if isinstance(i, Character)]):
            return True
        return False

    def check_win(self):
        if all([i.health == 0 for i in self.queue.queue if isinstance(i, Enemy)]):
            return True
        return False

    def skill_config(self, character, skill_name):
        return character.skills[skill_name]["type"]
