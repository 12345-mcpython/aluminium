from decimal import Decimal

from aluminium.buffs.base import Buff
from aluminium.event import Event
from aluminium.movable import Movable


class Enemy(Movable):
    def __init__(self, name: str, health: Decimal, defensive: Decimal, attack: Decimal, stance: Decimal,
                 stance_weak: list[str], attributes: dict[str, Decimal], speed: Decimal):
        super().__init__(name, health, defensive, attack, speed, attributes)
        self.stance = stance
        self.stance_weak = stance_weak
        self.buffs = []
        self.extra = {}

    def __str__(self) -> str:
        return f"<{type(self).__name__} name={self.name} health={self.health} defensive={self.defensive} attack={self.attack} stance={self.stance} stance_weak={self.stance_weak} attributes={self.attributes} speed={self.speed} tick={self.tick} length={self.length} extra={self.extra}>"

    def __repr__(self):
        return str(self)

    def add_buff(self, buff: Buff):
        pass


class EnemyEvent(Enemy, Event):
    pass
