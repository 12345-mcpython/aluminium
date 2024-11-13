import unittest
from decimal import Decimal

from aluminium.event import Event
from aluminium.relic import Relic, Relics
from aluminium.utils import calc_character_rate, calc_attacker_rate


class RelicTest(unittest.TestCase):
    def test_relic_json(self):
        relic_body = Relic.generate_from_json(Event, "body", 1, 5, {
            "main_attribute": {
                "crit_attack": 15
            },
            "sub_attributes": {
                "defence": {
                    "promote_level": 1,
                    "attribute_level": 1
                },
                "defence_percent": {
                    "promote_level": 0,
                    "attribute_level": 1
                },
                "speed": {
                    "promote_level": 3,
                    "attribute_level": 4
                },
                "breaking_effect": {
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
                "speed": {
                    "promote_level": 2,
                    "attribute_level": 0
                },
                "crit_chance": {
                    "promote_level": 2,
                    "attribute_level": 5
                },
                "crit_attack": {
                    "promote_level": 0,
                    "attribute_level": 1
                },
                "breaking_effect": {
                    "promote_level": 1,
                    "attribute_level": 0
                }
            }
        }, 2)

        relic_boot = Relic.generate_from_json(Event, "boot", 1, 5, {
            "main_attribute": {
                "speed": 15
            },
            "sub_attributes": {
                "health_percent": {
                    "promote_level": 2,
                    "attribute_level": 4
                },
                "attack_percent": {
                    "promote_level": 1,
                    "attribute_level": 3
                },
                "defence_percent": {
                    "promote_level": 1,
                    "attribute_level": 1
                },
                "crit_attack": {
                    "promote_level": 0,
                    "attribute_level": 0
                }
            }
        }, 2)

        relic_hand = Relic.generate_from_json(Event, "hand", 1, 5, {
            "main_attribute": {
                "attack": 15
            },
            "sub_attributes": {
                "defence": {
                    "promote_level": 1,
                    "attribute_level": 2
                },
                "speed": {
                    "promote_level": 3,
                    "attribute_level": 3
                },
                "crit_attack": {
                    "promote_level": 0,
                    "attribute_level": 0
                },
                "effect_hit_rate": {
                    "promote_level": 0,
                    "attribute_level": 1
                }
            }
        }, 2)
        relic_line = Relic.generate_from_json(Event, "ball", 1, 5, {
            "main_attribute": {
                "defence_percent": 15
            },
            "sub_attributes": {
                "speed": {
                    "promote_level": 1,
                    "attribute_level": 2
                },
                "crit_attack": {
                    "promote_level": 2,
                    "attribute_level": 3
                },
                "effect_hit_rate": {
                    "promote_level": 0,
                    "attribute_level": 2
                },
                "effect_resistance": {
                    "promote_level": 2,
                    "attribute_level": 4
                }
            }
        }, 2)
        relic_ball = Relic.generate_from_json(Event, "line", 1, 5, {
            "main_attribute": {
                "energy_regeneration_rate": 15
            },
            "sub_attributes": {
                "health": {
                    "promote_level": 2,
                    "attribute_level": 2
                },
                "defence_percent": {
                    "promote_level": 0,
                    "attribute_level": 2
                },
                "crit_attack": {
                    "promote_level": 1,
                    "attribute_level": 2
                },
                "effect_resistance": {
                    "promote_level": 1,
                    "attribute_level": 2
                }
            }
        }, 2)
        relics = Relics()

        relics.wear(relic_head)
        relics.wear(relic_hand)
        relics.wear(relic_body)
        relics.wear(relic_ball)
        relics.wear(relic_boot)
        relics.wear(relic_line)

        total_value = relics.calc_total_value()
        print(total_value)
        in_game_health = 4711
        in_game_defence = 1492
        in_game_attack = 1718
        in_game_crit_chance = 14.3
        in_game_crit_attack = 184.8
        in_game_effect_resistance = 42.8

        # 行迹
        base_health_percent = 6 + 6 + 4 + 4 + 8
        base_effect_resistance = 10

        weapon_health = Decimal("52.8") * calc_attacker_rate(80, promotion=True)
        weapon_defence = Decimal(21) * calc_attacker_rate(80, promotion=True)
        weapon_attack = Decimal(24) * calc_attacker_rate(80, promotion=True)
        white_health = calc_character_rate(80, promotion=True) * Decimal("190.08")
        white_defence = calc_character_rate(80, promotion=True) * Decimal("66")
        white_attack = calc_character_rate(80, promotion=True) * Decimal("71.28")

        no_relic_health = white_health + weapon_health
        no_relic_defence = white_defence + weapon_defence
        no_relic_attack = white_attack + weapon_attack
        print(no_relic_health, no_relic_defence, no_relic_attack)

        calc_health = no_relic_health * (1 + total_value.get("health_percent", Decimal(0))) + total_value["health"]
        calc_defence = no_relic_defence * (1 + total_value.get("defence_percent", Decimal(0))) + total_value["defence"]
        calc_attack = no_relic_attack * (1 + total_value.get("attack_percent", Decimal(0))) + total_value["attack"]

        print(calc_health, calc_defence, calc_attack)

        calc_health *= Decimal(1 + base_health_percent / 100)

        self.assertAlmostEqual(calc_health, in_game_health, delta=Decimal("10"))
        self.assertAlmostEqual(calc_defence, in_game_defence, delta=Decimal("10"))
        self.assertAlmostEqual(calc_attack, in_game_attack, delta=Decimal("10"))
        self.assertAlmostEqual(Decimal("0.5") + total_value["crit_attack"] + Decimal(10.7 + 5.3 + 8.0) / 100,
                               in_game_crit_attack, delta=Decimal("0.1"))
        self.assertAlmostEqual(Decimal("0.05") + total_value["crit_chance"], in_game_crit_chance, delta=Decimal("0.01"))
        self.assertAlmostEqual(base_effect_resistance * Decimal("0.1") + total_value["effect_resistance"],
                               in_game_effect_resistance, delta=Decimal("0.1"))
