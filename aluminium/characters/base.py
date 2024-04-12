from decimal import Decimal

from ..buffs.base import Buff
from ..enhances.base import Enhances
from ..event import Event
from ..weapons.base import Weapon


class Character:
    def __init__(
            self,
            name: str,
            mt: str,
            health: Decimal,
            defensive: Decimal,
            attack: Decimal,
            attributes: dict[str, Decimal],
            speed: Decimal,
            weapon: Weapon = None,
            enhances: Enhances = None,
    ):
        self.name = name
        self.mt = mt
        self.health = health
        self.defensive = defensive
        self.attack = attack
        self.attributes = attributes
        self.weapon = weapon
        self.enhances = enhances
        self.speed = speed
        self.tick = Decimal(0)
        self.length = Decimal(0)
        self.buffs = []
        self.extra = {}

    def __str__(self) -> str:
        return f"<{type(self).__name__} name={self.name} mt={self.mt} health={self.health} defensive={self.defensive} speed={self.speed} attack={self.attack} attributes={self.attributes} weapon={self.weapon} enhances={self.enhances} tick={self.tick} length={self.length} extra={self.extra}>"

    def __repr__(self):
        return str(self)

    def common(self, position: int):
        pass

    def enhanced_common(self, skill_type: str, position: int, enhance_level: int = None):
        pass

    def skill(self, skill_type: str, position: int):
        pass

    def ultra(self, ultra_type: str, position: int):
        pass

    def add_buff(self, buff: Buff):
        pass


class CharacterEvent(Character, Event):
    pass
