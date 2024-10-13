from decimal import Decimal

from .character import Character
from .enemy import Enemy


class Queue:
    def __init__(
            self, character_queue: list[Character], enemy_queue: list[Enemy]
    ) -> None:
        self.character_queue = character_queue
        self.enemy_queue = enemy_queue
        self.queue: list[Character | Enemy] = list(character_queue) + list(enemy_queue)

    def get_enemy_list(self):
        return [i for i in self.enemy_queue if i.saw]

    def get_character_list(self):
        return [i for i in self.character_queue if i.saw]

    def get_movable(self):
        return [i for i in self.queue if i.saw]

    def print(self):
        print("队列")
        for i in sorted(self.get_movable(), key=lambda a: a.tick):
            print(i.name, "\t", round(i.tick), "\t", i.length)
        print()

    def reset(self):
        for i in self.get_movable():
            i.length = 0
            i.tick = 0

    def init_queue(self):
        self.calc_tick()

    def calc_tick(self):
        for i in self.get_movable():
            i.tick = (Decimal(10000) - i.length) / i.speed

    def move(self):
        fastest = min(self.get_movable(), key=lambda a: a.tick)
        move_tick = (Decimal(10000) - fastest.length) / fastest.speed
        for i in self.get_movable():
            i.length += i.speed * move_tick
            if i.length > Decimal(10000):
                i.length = Decimal(10000)
        self.calc_tick()

    def get_move(self):
        return max(self.get_movable(), key=lambda a: a.length)

    def move_front(self, refactor_object: Character | Enemy, length):
        refactor_object.length += Decimal(length)
        if refactor_object.length > Decimal(10000):
            refactor_object.length = Decimal(10000)
        self.calc_tick()

    def set_speed(self, refactor_object: Character | Enemy, speed):
        refactor_object.speed = Decimal(speed)
        self.calc_tick()

    def move_back(self, refactor_object: Character | Enemy, length):
        refactor_object.length -= Decimal(length)
        self.calc_tick()
