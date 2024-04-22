from decimal import Decimal

from ..enhances.base import Enhances
from ..event import Event
from ..movable import Movable
from ..weapons.base import Weapon


class Character(Movable):
    def __init__(self, name: str, mt: str, health: Decimal, defensive: Decimal, attack: Decimal,
                 attributes: dict[str, Decimal], speed: Decimal, level: int = 1, weapon: Weapon = None,
                 enhances: Enhances = None):
        super().__init__(name, health, defensive, attack, speed, attributes)
        self.mt = mt
        self.level = level
        self.weapon = weapon
        self.enhances = enhances
        self.buffs = []
        self.extra = {}

    def __str__(self) -> str:
        return f"<{type(self).__name__} name={self.name} mt={self.mt} health={self.health} defensive={self.defensive} speed={self.speed} attack={self.attack} attributes={self.attributes} weapon={self.weapon} enhances={self.enhances} tick={self.tick} length={self.length} extra={self.extra}>"

    def __repr__(self):
        return str(self)

    # ("Pioneer", "Destruction", Decimal("163.68"), Decimal("62.7"), Decimal("84.48"),
    #                   {"crit_chance": Decimal(".05"), "crit_attack": Decimal(".05")}, Decimal("100"))
    @classmethod
    def build(cls, *args, **kwargs):
        base_health, base_defensive, base_attack, base_attribute, base_speed = args[2:]
        weapon: Weapon = kwargs.get("weapon")
        enhances: Enhances = kwargs.get("enhances")
        return cls(*args, **kwargs)

    def common(self, skill_level: int, process_object: Movable):
        pass

    def enhanced_common(self, skill_level: int, skill_type: str, position: int, enhance_level: int = None):
        pass

    def skill(self, skill_level: int, skill_type: str, position: int):
        pass

    def ultra(self, skill_level: int, ultra_type: str, position: int):
        pass

    def prepare_ultra(self, skill_level: int, ultra_type: str, position: int):
        pass


class CharacterEvent(Character, Event):
    pass
