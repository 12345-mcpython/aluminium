from decimal import Decimal

from .buffs.base import Buff


class Movable:
    def __init__(self, name: str,
                 health: Decimal,
                 defensive: Decimal,
                 attack: Decimal,
                 speed: Decimal, attributes: dict[str, Decimal]):
        self.name = name
        self.health = health
        self.defensive = defensive
        self.attack = attack
        self.speed = speed
        self.tick = Decimal(0)
        self.length = Decimal(0)
        self.attributes = attributes
        self.buffs = []

    def add_buff(self, buff: Buff):
        self.buffs.append(buff)

    def check_buff(self):
        pass
