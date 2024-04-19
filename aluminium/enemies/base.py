from decimal import Decimal

from ..buffs.base import Buff
from ..event import Event


class Enemy:
    def __init__(
            self,
            name: str,
            health: Decimal,
            defensive: Decimal,
            attack: Decimal,
            stance: Decimal,
            attributes: dict[str, Decimal],
            speed: Decimal,
    ):
        self.name = name
        self.health = health
        self.defensive = defensive
        self.attack = attack
        self.stance = stance
        self.attributes = attributes
        self.speed = speed
        self.tick = Decimal(0)
        self.length = Decimal(0)
        self.buffs = []
        self.extra = {}

    def __str__(self) -> str:
        return f"<{type(self).__name__} name={self.name} health={self.health} defensive={self.defensive} attack={self.attack} stance={self.stance} attributes={self.attributes} speed={self.speed} tick={self.tick} length={self.length} extra={self.extra}>"

    def __repr__(self):
        return str(self)

    def add_buff(self, buff: Buff):
        pass


class EnemyEvent(Enemy, Event):
    pass
