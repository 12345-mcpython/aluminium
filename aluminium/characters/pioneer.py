from decimal import Decimal

from .base import CharacterEvent
from ..damage import Damage
from ..movable import Movable


class DestructionPioneer(CharacterEvent):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)

    def common(self, skill_level: int, process_object: Movable):
        # self.value, common_skill_rate[skill_level - 1]
        return Damage(Decimal(1),
                      damage_type="common", damage_giver=self, damage_getter=process_object)

    def skill(self, skill_level: int, skill_type: str, position: int):
        pass

    def prepare_ultra(self, skill_level: int, ultra_type: str, position: int):
        pass

    def ultra(self, skill_level: int, ultra_type: str, position: int):
        pass


class ProtectionPioneer(CharacterEvent):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)

    def common(self, skill_level: int, process_object: Movable):
        pass

    def enhanced_common(self, skill_level: int, skill_type: str, position: int, enhance_level: int = None):
        pass

    def skill(self, skill_level: int, skill_type: str, position: int):
        pass

    def prepare_ultra(self, skill_level: int, ultra_type: str, position: int):
        pass

    def ultra(self, skill_level: int, ultra_type: str, position: int):
        pass
