from decimal import Decimal

from .movable import Movable


class Damage:
    def __init__(self, damage_value: Decimal, damage_type: str, damage_giver: Movable, damage_getter: Movable):
        self.damage_value = damage_value
        self.damage_type = damage_type
        self.damage_giver = damage_giver
        self.damage_getter = damage_getter

    def __repr__(self):
        return f"<Damage damage_value={self.damage_value} damage_type={self.damage_type} damage_giver={self.damage_giver} damage_getter={self.damage_getter}>"
