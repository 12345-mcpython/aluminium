from decimal import Decimal

from aluminium.enemy import EnemyEvent


class TestEnemy(EnemyEvent):
    def __init__(self, name: str, health: Decimal, defensive: Decimal, attack: Decimal, stance: Decimal,
                 stance_weak: list[str], attributes: dict[str, Decimal], speed: Decimal):
        super().__init__(name, health, defensive, attack, stance, stance_weak, attributes, speed)

    def attack(self):
        pass
