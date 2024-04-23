# from decimal import Decimal as D
#
# from aluminium.battle import Battle
#
# from aluminium.characters.base import Character
#
# from aluminium.enemies.base import Enemy
#
# c1 = Character("a1", "?", D(114), D(514), D(1919), {}, D(104))
# c2 = Character("b1", "?", D(114), D(514), D(1919), {}, D(101))
# c3 = Character("c1", "?", D(114), D(514), D(1919), {}, D(100))
#
# e1 = Enemy("a2", D(114), D(514), D(1919), {}, D(100))
# e2 = Enemy("b2", D(114), D(514), D(1919), {}, D(100))
# e3 = Enemy("c2", D(114), D(514), D(1919), {}, D(97))
#
# b = Battle([c1, c2, c3], [e1, e2, e3])
#
# b.calc_tick()
# while True:
#     b.print_queue()
#     b.move()
#     b.print_queue()
#     fastest = b.get_move()
#     print("行动: ", fastest.name)
#     fastest.length = D(0)
#     input()
from aluminium.enhances import Enhance, Enhances

jsons = {"main_attribute": {"attack": 15},
         "sub_attributes": {"crit_chance": [2, 2, 2, 2, 2, 2], "health": [2], "defensive": [1], "speed": [2]}}

json1 = {"main_attribute": {"health": 15},
         "sub_attributes": {"crit_chance": [2, 2, 2, 2, 2, 2], "crit_attack": [2], "defensive": [1], "speed": [2]}}

json2 = {"main_attribute": {"crit_attack": 15},
         "sub_attributes": {"crit_chance": [2, 2, 2, 2, 2, 2], "health": [2], "defensive": [1], "speed": [2]}}

json3 = {"main_attribute": {"speed": 15},
         "sub_attributes": {"crit_chance": [2, 2, 2, 2, 2, 2], "health": [2], "defensive": [1], "crit_attack": [2]}}

json4 = {"main_attribute": {"attack_percent": 15},
         "sub_attributes": {"crit_chance": [2, 2, 2, 2, 2, 2], "health": [2], "defensive": [1], "speed": [2]}}

json5 = {"main_attribute": {"defensive_percent": 0},
         "sub_attributes": {"health": [0], "health_percent": [2], "attack_percent": [1], "effect_hit_rate": [2]}}

enhance1 = Enhance.generate_from_json("head", 1, 5, jsons)

enhance2 = Enhance.generate_from_json("hand", 1, 5, json1)

enhance3 = Enhance.generate_from_json("body", 1, 5, json2)

enhance4 = Enhance.generate_from_json("boot", 1, 5, json3)

enhance5 = Enhance.generate_from_json("line", 1, 5, json4)

enhance6 = Enhance.generate_from_json("ball", 1, 5, json5)

enhances = Enhances()

for i in [i for i in dir() if i.startswith("enhance") and i != "enhances"]:
    enhances.wear(eval(i))

print(enhances.calc_total_value())
