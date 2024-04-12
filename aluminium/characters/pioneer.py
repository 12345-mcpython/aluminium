from decimal import Decimal

from .base import CharacterEvent


class DestructionPioneer(CharacterEvent):
    def __init__(self):
        super().__init__("Pioneer", "Destruction", Decimal("163.68"), Decimal("62.7"), Decimal("84.48"),
                         {"crit_chance": Decimal(".05"), "crit_attack": Decimal(".05")}, Decimal("100"))

    def common(self, position: int):
        pass

    def skill(self, skill_type: str, position: int):
        pass

    def ultra(self, ultra_type: str, position: int):
        pass


class ProtectionPioneer(CharacterEvent):
    def __init__(self):
        super().__init__("Pioneer", "Protection", Decimal("168.96"), Decimal("82.5"), Decimal("81.84"),
                         {"crit_chance": Decimal(".05"), "crit_attack": Decimal(".05")}, Decimal("95"))

    def common(self, position: int):
        pass

    def enhanced_common(self, skill_type: str, position: int, enhance_level: int = None):
        pass

    def skill(self, skill_type: str, position: int):
        pass

    def ultra(self, ultra_type: str, position: int):
        pass
