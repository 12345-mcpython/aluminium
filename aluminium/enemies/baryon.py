from decimal import Decimal

from aluminium.enemy import EnemyEvent


class Baryon(EnemyEvent):
    def __init__(self, name: str, health: Decimal, defence: Decimal, attack: Decimal, stance: Decimal,
                 attributes: dict[str, Decimal],
                 speed: Decimal):
        super().__init__(name, health, defence, attack, stance, attributes, speed)

    @classmethod
    def generate_by_numbers(cls, name, values):
        return cls(name, values.health, values.defence)
