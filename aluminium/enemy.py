import typing
from decimal import Decimal

from .buff import Buff
from .event import Event
from .movable import Movable

if typing.TYPE_CHECKING:
    from .character import Character
    from .damage import Damage


class Enemy(Movable):
    def __init__(self, name: str, health: Decimal, defensive: Decimal, attack: Decimal, stance: Decimal,
                 stance_weak: list[str], attributes: dict[str, Decimal], speed: Decimal):
        super().__init__(name, health, defensive, attack, speed, attributes)
        self.stance = stance
        self.stance_weak = stance_weak
        self.buffs = []
        self.extra = {}
        self.skills = {}
        self.cursor = 1

    def __str__(self) -> str:
        return f"<{type(self).__name__} name={self.name} health={self.health} defensive={self.defensive} attack={self.attack} stance={self.stance} stance_weak={self.stance_weak} attributes={self.attributes} speed={self.speed} tick={self.tick} length={self.length} extra={self.extra} cursor={self.cursor}>"

    def __repr__(self):
        return str(self)

    def add_buff(self, buff: Buff):
        pass

    def attack_object(self, character: Character):
        pass

    def get_damage(self, damage: Damage):
        pass
    def register_skill(self):
        pass


class EnemyEvent(Enemy, Event):
    pass
