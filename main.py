from decimal import Decimal

from aluminium.character import CharacterEvent
from aluminium.damage import Damage
from aluminium.enemy import EnemyEvent
from aluminium.queue import Queue
from aluminium.relic import Relic

relic = Relic.generate_from_json("head", 1, 5, {"main_attribute": {"crit_attack": 15},
                                                "sub_attributes": {"crit_chance": [3], "health": [3], "defensive": [1],
                                                                   "speed": [3, 3, 3, 3, 3, 3]}}, 15)


class TestEnemy(EnemyEvent):
    def register_skill(self):
        self.skills[1] = self.skill1

    def skill1(self):
        return Damage(Decimal("2.5") * self.attack, "quantum", "single", self)

    def attack_object(self, character):
        character.do_attack(self.skills[self.cursor])


# The character and enemy class should use build() method to get the instance
# not use the Class().
class TestCharacter1(CharacterEvent):
    def skill(self, skill_level: int, skill_type: str, position: int):
        pass


enemies = []

characters = []

queue = Queue()
