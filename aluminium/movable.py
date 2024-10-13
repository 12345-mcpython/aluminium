import math
from decimal import Decimal

from .event import Event


class Movable:
    def __init__(self, event: type[Event], name: str,
                 health: Decimal,
                 defence: Decimal,
                 attack: Decimal,
                 speed: Decimal, attributes: dict[str, Decimal]):
        self.name = name
        self.event = event(self)
        self.max_health = self.__health = health
        self.defence = defence
        self.attack = attack
        self.speed = speed
        self.tick = Decimal(0)
        self.length = Decimal(0)
        self.saw = True
        self.attributes = attributes

    @property
    def health(self):
        return math.floor(self.__health)

    @health.setter
    def health(self, value):
        self.__health = value
