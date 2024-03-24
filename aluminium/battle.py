from decimal import Decimal
from .characters.base import Character

from .enemies.base import Enemy


class Battle:
    def __init__(
            self, character_queue: list[Character], enemy_queue: list[Enemy]
    ) -> None:
        self.character_queue = character_queue
        self.enemy_queue = enemy_queue
        self.queue: list[Character | Enemy] = list(character_queue) + list(enemy_queue)

    def print_queue(self):
        print("队列")
        for i in sorted(self.queue, key=lambda a: a.length, reverse=True):
            print(i.name, "\t", i.tick, "\t", i.length)

        print()

    def calc_tick(self):
        for i in self.queue:
            i.tick = (Decimal(10000) - i.length) / i.speed

    def move(self):
        fastest = max(self.queue, key=lambda a: a.length)
        move_tick = (Decimal(10000) - fastest.length) / fastest.speed
        for i in self.queue:
            i.length += i.speed * move_tick
            if i.length > Decimal(10000):
                i.length = Decimal(10000)
        self.calc_tick()

    def get_move(self):
        return max(self.queue, key=lambda a: a.length)
