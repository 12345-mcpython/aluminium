import os
from decimal import Decimal

from aluminium.battle import Battle
from aluminium.character import CBase, Character
from aluminium.enemy import EBase, EBonus, Enemy
from aluminium.event import Event
from aluminium.queue import Queue
from aluminium.relic import Relic, Relics
from aluminium.weapon import Weapon

os.chdir(os.path.dirname(__file__))


class TestEnemy1(Enemy):
    pass


class TestEnemy2(Enemy):
    pass


class TestCharacter1(Character):
    def common(self, attack_object: Enemy):
        attack_object.health -= Decimal(self.skills_rate[self.skills_level] * self.attack)


# The character and enemy class should use build() method to get the instance
# not use the Class().


relic_body = Relic.generate_from_json(Event, "body", 1, 5, {
    "main_attribute": {
        "crit_chance": 15
    },
    "sub_attributes": {
        "health": {
            "promote_level": 2,
            "attribute_level": 2
        },
        "defence": {
            "promote_level": 0,
            "attribute_level": 1
        },
        "health_percent": {
            "promote_level": 2,
            "attribute_level": 3
        },
        "crit_attack": {
            "promote_level": 1,
            "attribute_level": 1
        }
    }
}, 2)

relic_head = Relic.generate_from_json(Event, "head", 1, 5, {
    "main_attribute": {
        "health": 15
    },
    "sub_attributes": {
        "attack": {
            "promote_level": 0,
            "attribute_level": 2
        },
        "attack_percent": {
            "promote_level": 0,
            "attribute_level": 2
        },
        "crit_chance": {
            "promote_level": 3,
            "attribute_level": 5
        },
        "breaking_effect": {
            "promote_level": 1,
            "attribute_level": 0
        }
    }
}, 2)

relic_boot = Relic.generate_from_json(Event, "boot", 1, 5, {
    "main_attribute": {
        "attack_percent": 15
    },
    "sub_attributes": {
        "attack": {
            "promote_level": 1,
            "attribute_level": 2
        },
        "health_percent": {
            "promote_level": 3,
            "attribute_level": 2
        },
        "crit_chance": {
            "promote_level": 0,
            "attribute_level": 1
        },
        "breaking_effect": {
            "promote_level": 0,
            "attribute_level": 2
        }
    }
}, 2)

relic_hand = Relic.generate_from_json(Event, "hand", 1, 5, {
    "main_attribute": {
        "attack": 15
    },
    "sub_attributes": {
        "health_percent": {
            "promote_level": 0,
            "attribute_level": 0
        },
        "attack_percent": {
            "promote_level": 1,
            "attribute_level": 4
        },
        "crit_chance": {
            "promote_level": 1,
            "attribute_level": 2
        },
        "crit_attack": {
            "promote_level": 2,
            "attribute_level": 3
        }
    }
}, 2)

relic_line = Relic.generate_from_json(Event, "ball", 1, 5, {
    "main_attribute": {
        "physical_damage_boost": 15
    },
    "sub_attributes": {
        "attack": {
            "promote_level": 1,
            "attribute_level": 4
        },
        "defence": {
            "promote_level": 0,
            "attribute_level": 1
        },
        "speed": {
            "promote_level": 1,
            "attribute_level": 3
        },
        "effect_hit_rate": {
            "promote_level": 2,
            "attribute_level": 2
        }
    }
}, 2)

relic_ball = Relic.generate_from_json(Event, "line", 1, 5, {
    "main_attribute": {
        "energy_regeneration_rate": 15
    },
    "sub_attributes": {
        "health": {
            "promote_level": 0,
            "attribute_level": 1
        },
        "defence_percent": {
            "promote_level": 1,
            "attribute_level": 3
        },
        "crit_chance": {
            "promote_level": 0,
            "attribute_level": 2
        },
        "effect_hit_rate": {
            "promote_level": 3,
            "attribute_level": 3
        }
    }
}, 2)

# enemy defence base is 200 + level * 10
enemy1_base = EBase(base_health=Decimal("55.8"), base_speed=Decimal("83"), base_attack=Decimal("18"),
                    base_defence=Decimal(str(210 + 10 * 74)))

enemy1_bonus = EBonus(bonus_defence=Decimal("1"), bonus_speed=Decimal("1"), bonus_attack=Decimal("1"),
                      bonus_health=Decimal("1"))

enemy2_base = EBase(base_health=Decimal("55.8"), base_speed=Decimal("100"), base_attack=Decimal("18"),
                    base_defence=Decimal(str(210 + 10 * 74)))

enemy2_bonus = EBonus(bonus_defence=Decimal("1"), bonus_speed=Decimal("1"), bonus_attack=Decimal("1"),
                      bonus_health=Decimal("1"))


class EnemyEvent1(Event):
    def __init__(self, movable):
        super().__init__(movable)
        self.died = False

    def on_enemy_died(self, battle, enemy) -> bool:
        if self.died:
            return super().on_enemy_died(battle, enemy)
        enemy.health += 1
        self.died = True
        return True


enemy1 = TestEnemy1.build(EnemyEvent1, "Test E 1", 74, 1, enemy1_base, enemy1_bonus, ["ice", "wind"], 30, {})

enemy2 = TestEnemy2.build(Event, "Test E 2", 74, 1, enemy2_base, enemy2_bonus, ["physical", "thunder"], 60, {})

relics = Relics()

relics.wear(relic_head)
relics.wear(relic_hand)
relics.wear(relic_body)
relics.wear(relic_ball)
relics.wear(relic_boot)
relics.wear(relic_line)


class Event1(Event):
    def on_battle_start(self, battle: Battle):
        print("on_battle_start called")
        self.movable.health *= .5
        for character in battle.queue.character_queue:
            if character != self.movable:
                character.health *= .5
        for enemy in battle.queue.enemy_queue:
            enemy.health *= .25


character1 = TestCharacter1.build(Event1, "Test C 1", "No", 80,
                                  CBase(Decimal("184.8"), Decimal("69.3"), Decimal("81.84"), Decimal("98"), 100, 120),
                                  Weapon(Event, "?1", "?", 80, Decimal("48"), Decimal("24"), Decimal("24")),
                                  relics)

character2 = TestCharacter1.build(Event, "Test C 2", "No", 80,
                                  CBase(Decimal("201"), Decimal("70"), Decimal("85"), Decimal("100"), 125, 140),
                                  Weapon(Event, "?2", "?", 80, Decimal("38.4"), Decimal("12"), Decimal("69.6")),
                                  relics)

# print(enemy1, "\n", enemy2, "\n", character1, "\n", character2)
#
battle_queue = Queue([character1, character2], [enemy1, enemy2])
battle_queue.reset()

battle = Battle(battle_queue)

battle.start_battle()
move = None

while battle.over():
    battle.print_info()
    print("行动: ", move if not move else move.name)
    while True:
        command = input()
        if command == "si":
            break
        elif command == "q":
            battle.queue.print()
        elif command == "pi":
            battle.print_more_info()
        elif command == "common" and move:
            print("释放普攻")
        elif command == "skill" and move:
            print("释放战绩")
        elif command == "ultra" and move:
            print("释放终结技")
        elif command == "cheat":
            for enemy in battle.queue.enemy_queue:
                enemy.health = 0
            print("成功作弊!")
        elif command == "kill_player":
            for character in battle.queue.character_queue:
                character.health = 0
        elif not command:
            pass
        else:
            print("未知命令!")
    if move:
        move.length = 0
        battle.queue.calc_tick()
    battle.step_in()
    move = battle.get_move()
    battle.check_death()
    if battle.check_win():
        print("战斗成功")
    if battle.check_fail():
        print("战斗失败")
