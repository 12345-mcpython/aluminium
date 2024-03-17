from .characters.base import Character

from .enemies.base import Enemy


class Battle:
    def __init__(self, character_queue: list[Character], enemy_queue: list[Enemy]) -> None:
        self.character_queue = character_queue
        self.enemy_queue = enemy_queue
        self.queue: list[Character | Enemy] = list(character_queue) + list(enemy_queue)

    def print_queue(self):
        print("队列")
        for i in sorted(self.queue, key=lambda a: a.length, reverse=True):
            print(i.name, "\t", i.tick, "\t", i.length)
        print()

    def init_battle(self):
        for i in self.queue:
            i.length = 0
            i.tick = 0
            i.buffs = []
        fastest = max(self.queue, key=lambda a: a.speed)
        t = 10000 / fastest.speed
        for i in self.queue:
            i.length = i.speed * t
            i.tick = round((10000 - i.length) / i.speed)
        return fastest

    def next(self):
        fastest = max(self.queue, key=lambda a: a.length)
        for i in sorted(self.queue, key=lambda a: a.length):
            if round(i.length) >= 10000:
                i.length = 10000
                continue
            if round(i.tick) < 0:
                i.tick = 0
                continue
            i.length += (10000 - fastest.length) / fastest.speed * i.speed
            i.tick = round((10000 - i.length) / i.speed)
        return fastest
