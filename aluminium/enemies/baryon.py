from decimal import Decimal

from .base import EnemyEvent


class Baryon(EnemyEvent):
    def __init__(self, name: str, health: Decimal, defensive: Decimal, attack: Decimal, rd: Decimal,
                 attributes: dict[str, Decimal],
                 speed: Decimal):
        super().__init__(name, health, defensive, attack, rd, attributes, speed)
