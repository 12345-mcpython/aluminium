from decimal import Decimal

from aluminium.utils import calc_attacker_rate


class Weapon:
    def __init__(
            self, name: str, mt: str, level: int, health: Decimal, defence: Decimal, attack: Decimal
    ):
        self.name = name
        self.mt = mt
        self.level = level
        self.health = health
        self.defence = defence
        self.attack = attack

    def __str__(self) -> str:
        return f"<{type(self).__name__} name={self.name} mt={self.mt} level={self.level} health={self.health} defence={self.defence} attack={self.attack}>"

    def __repr__(self):
        return str(self)

    @classmethod
    def build(cls, name: str, mt: str, level: int, base_health: Decimal, base_defense: Decimal, base_attack: Decimal,
              promotion: bool = True):
        health = base_health * calc_attacker_rate(level, promotion)
        defense = base_defense * calc_attacker_rate(level, promotion)
        attack = base_attack * calc_attacker_rate(level, promotion)

        return cls(name, mt, level, health, defense, attack)
