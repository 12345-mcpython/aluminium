from .characters.base import CharacterEvent
from .enemies.base import EnemyEvent
from .queue import Queue


class Battle:
    def __init__(self, queue: Queue, battle_type: str = "common"):
        self.queue = queue
        self.attribute = {}
        self.battle_type = battle_type

    def __str__(self):
        return f"<Battle queue={self.queue} attribute={self.attribute}>"

    def __repr__(self):
        return str(self)

    def start_battle(self):
        for i in self.queue.queue:
            i.on_battle_start(self)

    def move(self):
        self.queue.move()

    def check_death(self):
        for i in self.queue.queue:
            if i.health == 0:
                if isinstance(i, CharacterEvent):
                    if not i.on_character_died(self, i):
                        self.queue.queue.remove(i)
                if isinstance(i, EnemyEvent):
                    if not i.on_enemy_died(self, i):
                        self.queue.queue.remove(i)
