from typing import Optional

from .character import CharacterEvent
from .enemy import EnemyEvent
from .movable import Movable
from .queue import Queue


# battle start -> step in -> before move -> check death ->
# "player -> waiting process / enemy -> do process" -> check_death ->
# after move -> check death -> check all death -> if True -> battle end
# else -> loop to "step in"


class Battle:
    def __init__(self, queue: Queue, battle_type: str = "common"):
        self.queue = queue
        self.attribute = {}
        self.battle_type = battle_type
        self.now_object: Optional[CharacterEvent | EnemyEvent] = None

    def __str__(self):
        return f"<Battle queue={self.queue} attribute={self.attribute}>"

    def __repr__(self):
        return str(self)

    def start_battle(self):
        for i in self.queue.queue:
            i.on_battle_start(self)
        return self

    def step_in(self):
        self.queue.move()

    # before move
    def move_start(self):
        move_object = self.queue.get_move()
        # calc dot
        move_object.before_move(self)
        self.now_object = move_object

    def enemy_action(self, movable: Movable):
        self.now_object.attack_object(movable)

    # after move
    def move_over(self):
        self.now_object.after_move(self)
        self.now_object = None

    def check_death(self):
        for i in self.queue.queue:
            if i.health == 0:
                if isinstance(i, CharacterEvent):
                    if not i.on_character_died(self, i):
                        self.queue.queue.remove(i)
                if isinstance(i, EnemyEvent):
                    if not i.on_enemy_died(self, i):
                        self.queue.queue.remove(i)

    def check_fail(self):
        if all([i.health == 0 for i in self.queue.queue if isinstance(i, CharacterEvent)]):
            return True
        return False

    def check_win(self):
        if all([i.health == 0 for i in self.queue.queue if isinstance(i, EnemyEvent)]):
            return True
        return False
