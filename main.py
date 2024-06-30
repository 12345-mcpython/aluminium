from aluminium.relic import Relic, Relics
#
# # 2201
# head = {"main_attribute": {"health": 15},
#         "sub_attributes": {"speed": [3], "crit_chance": [], "crit_attack": [1], "breaking_effect": []}}
#
# # 1300
# hand = {"main_attribute": {"attack": 15},
#         "sub_attributes": {"crit_attack": [2, 2, 2, 2, 2, 2], "speed": [2], "crit_chance": [1], "effect_hit_rate": [2]}}
#
# # 1031
# body = {"main_attribute": {"crit_attack": 15},
#         "sub_attributes": {"defensive": [2, 2, 2, 2, 2, 2], "defensive_percent": [1], "speed": [2], "breaking_effect": [2]}}
#
# # 0202
# boot = {"main_attribute": {"speed": 15},
#         "sub_attributes": {"attack": [2, 2, 2, 2, 2, 2], "defensive": [2], "crit_attack": [1], "effect_resistance": [2]}}
#
# # 1202
# line = {"main_attribute": {"defensive_percent": 15},
#         "sub_attributes": {"speed": [2, 2, 2, 2, 2, 2], "crit_attack": [2], "effect_hit_rate": [1], "effect_resistance": [2]}}
#
# # 2011
# ball = {"main_attribute": {"energy_regeneration_rate": 15},
#         "sub_attributes": {"health": [0], "defensive_percent": [2], "crit_attack": [1], "effect_resistance": [2]}}
#
# enhance2 = Relic.generate_from_json("hand", 1, 5, hand)
#
# enhance1 = Relic.generate_from_json("head", 1, 5, head)
#
# enhance3 = Relic.generate_from_json("body", 1, 5, body)
#
# enhance4 = Relic.generate_from_json("boot", 1, 5, boot)
#
# enhance5 = Relic.generate_from_json("line", 1, 5, line)
#
# enhance6 = Relic.generate_from_json("ball", 1, 5, ball)
#
# enhances = Relics()
#
# for i in [i for i in dir() if i.startswith("enhance") and i != "enhances"]:
#     enhances.wear(eval(i))
#
# for i, j in enhances.calc_total_value().items():
#     print(i, j)

hand = {"main_attribute": {"attack": 15},
        "sub_attributes": {"crit_attack": [2, 2, 2, 2, 2, 2], "speed": [], "crit_chance": [1], "effect_hit_rate": [2]}}

print(Relic.generate_from_json("hand", 1, 5, hand).print())