import math
from decimal import Decimal

from .buff import Buff


class Movable:
    def __init__(self, name: str,
                 health: Decimal,
                 defence: Decimal,
                 attack: Decimal,
                 speed: Decimal, attributes: dict[str, Decimal]):
        self.name = name
        self.__health = health
        self.defence = defence
        self.attack = attack
        self.speed = speed
        self.tick = Decimal(0)
        self.length = Decimal(0)
        self.attributes = attributes

    @property
    def health(self):
        return math.floor(self.__health)
