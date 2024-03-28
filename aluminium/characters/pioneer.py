from decimal import Decimal

from .base import CharacterEvent


class Pioneer(CharacterEvent):
    def __init__(self, name: str, mt: str, health: Decimal, defensive: Decimal, attack: Decimal,
                 attributes: dict[str, Decimal], speed: Decimal):
        super().__init__("Pioneer", "I don't know", Decimal("163.68"), Decimal("62.7"), Decimal("84.48"),
                         {"crit_attack": Decimal(".05")}, speed)
