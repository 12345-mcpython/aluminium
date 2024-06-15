from aluminium.relic import Relic, Relics

head = {"main_attribute": {"health": 15},
        "sub_attributes": {"crit_chance": [2, 2, 2, 2, 2, 2], "crit_attack": [2], "defensive": [1], "speed": [2]}}

hand = {"main_attribute": {"attack": 15},
        "sub_attributes": {"crit_chance": [2, 2, 2, 2, 2, 2], "health": [2], "defensive": [1], "speed": [2]}}

body = {"main_attribute": {"crit_attack": 15},
        "sub_attributes": {"crit_chance": [2, 2, 2, 2, 2, 2], "health": [2], "defensive": [1], "speed": [2]}}

boot = {"main_attribute": {"speed": 15},
        "sub_attributes": {"crit_chance": [2, 2, 2, 2, 2, 2], "health": [2], "defensive": [1], "crit_attack": [2]}}

line = {"main_attribute": {"attack_percent": 15},
        "sub_attributes": {"crit_chance": [2, 2, 2, 2, 2, 2], "health": [2], "defensive": [1], "speed": [2]}}

ball = {"main_attribute": {"defensive_percent": 0},
        "sub_attributes": {"health": [0], "health_percent": [2], "attack_percent": [1], "effect_hit_rate": [2]}}

enhance2 = Relic.generate_from_json("hand", 1, 5, hand)

enhance1 = Relic.generate_from_json("head", 1, 5, head)

enhance3 = Relic.generate_from_json("body", 1, 5, body)

enhance4 = Relic.generate_from_json("boot", 1, 5, boot)

enhance5 = Relic.generate_from_json("line", 1, 5, line)

enhance6 = Relic.generate_from_json("ball", 1, 5, ball)

enhances = Relics()

for i in [i for i in dir() if i.startswith("enhance") and i != "enhances"]:
    enhances.wear(eval(i))

for i, j in enhances.calc_total_value().items():
    print(i, j)
