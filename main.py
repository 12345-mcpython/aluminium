from decimal import Decimal

from aluminium.character import CharacterEvent
from aluminium.damage import Damage
from aluminium.enemy import EnemyEvent, EBase, EBonus
from aluminium.relic import Relic


class TestEnemy1(EnemyEvent):
    def register_skill(self):
        self.skills[1] = self.skill1

    def skill1(self):
        return Damage(Decimal("2.5") * self.attack, Decimal(0), "quantum", "single", self)

    def attack_object(self, character):
        character.get_damage(self.skills[self.cursor])


class TestEnemy2(EnemyEvent):
    def register_skill(self):
        self.skills[1] = self.skill1
        self.skills[2] = self.skill2
        return self

    def skill1(self):
        self.cursor = 2
        return Damage(Decimal("2.5") * self.attack, Decimal(0), "imaginary", "single", self)

    def skill2(self):
        self.cursor = 1
        return Damage(Decimal("1.5") * self.attack, Decimal("1.5") * self.attack, "imaginary", "spread", self)


# The character and enemy class should use build() method to get the instance
# not use the Class().
class TestCharacter1(CharacterEvent):
    def register_skill(self):
        self.skills["common"] = self.common
        self.skills["skill"] = self.skill
        self.skills["ultra"] = self.ultra
        return self

    def skill(self):
        pass

    def common(self):
        pass

    def ultra(self):
        pass


relic_body = Relic.generate_from_json("body", 1, 5, {"main_attribute": {"crit_attack": 15},
                                                     "sub_attributes": {"crit_chance": [1, 2, 1, 2],
                                                                        "health": [2],
                                                                        "defensive": [1, 1, 1],
                                                                        "speed": [2]}})

relic_head = Relic.generate_from_json("head", 1, 5, {"main_attribute": {"health": 15},
                                                     "sub_attributes": {"crit_chance": [1, 2],
                                                                        "effect_resistance": [2],
                                                                        "defensive": [1],
                                                                        "speed": [2]}})

relic_boot = Relic.generate_from_json("boot", 1, 5, {"main_attribute": {"speed": 15},
                                                     "sub_attributes": {"crit_chance": [1, 2],
                                                                        "effect_resistance": [2],
                                                                        "defensive": [1],
                                                                        "effect_hit_rate": [1, 1]}})

relic_hand = Relic.generate_from_json("hand", 1, 5, {"main_attribute": {"attack": 15},
                                                     "sub_attributes": {"crit_chance": [1, 2],
                                                                        "effect_resistance": [2],
                                                                        "defensive": [1],
                                                                        "speed": [2]}})
relic_line = Relic.generate_from_json("ball", 1, 5, {"main_attribute": {"thunder_damage_boost": 15},
                                                     "sub_attributes": {"crit_chance": [1, 2],
                                                                        "crit_attack": [2, 2],
                                                                        "defensive": [1],
                                                                        "speed": [2]}})
relic_ball = Relic.generate_from_json("line", 1, 5, {"main_attribute": {"attack_percent": 15},
                                                     "sub_attributes": {"crit_chance": [1, 2],
                                                                        "crit_attack": [2],
                                                                        "defensive": [1],
                                                                        "speed": [2]}})

enemy1_base = EBase(base_health=Decimal("55.8"), base_speed=Decimal("83"), base_attack=Decimal("18"),
                    base_defensive=Decimal("210"))

enemy1_bonus = EBonus(bonus_defensive=Decimal("1"), bonus_speed=Decimal("1"), bonus_attack=Decimal("1"),
                      bonus_health=Decimal("1"))

enemy2_base = EBase(base_health=Decimal("55.8"), base_speed=Decimal("100"), base_attack=Decimal("18"),
                    base_defensive=Decimal("210"))

enemy2_bonus = EBonus(bonus_defensive=Decimal("1"), bonus_speed=Decimal("1"), bonus_attack=Decimal("1"),
                      bonus_health=Decimal("1"))

enemy1 = TestEnemy1.build("Test Enemy 1", 74, 1, enemy1_base, enemy1_bonus, ["ice", "wind"], 30, {})

enemy2 = TestEnemy2.build("Test Enemy 2", 74, 1, enemy2_base, enemy2_bonus, ["physics", "thunder"], 60, {})

enemies = []

characters = []

# queue = Queue()
