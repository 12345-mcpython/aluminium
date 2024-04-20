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
from aluminium.enhances.base import Enhance

jsons = {"main_attribute": {"crit_attack": 15},
         "sub_attributes": {"crit_chance": [2, 2, 2, 2, 2, 2], "health": [2], "defensive": [1], "speed": [2]}}

test_json = {"main_attribute": {"defensive_percent": 0},
             "sub_attributes": {"health": [0], "health_percent": [2], "attack_percent": [1], "effect_hit_rate": [2]}}

enhance1 = Enhance.generate_from_json("hand", 1, 5, jsons)

enhance1.print()

enhance2 = Enhance.generate_from_json("hand", 1, 5, test_json, level=1)

enhance2.print()
