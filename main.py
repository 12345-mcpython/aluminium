# import sys
# from decimal import Decimal as D
#
# from aluminium.character import Character
# from aluminium.enemy import Enemy
# from aluminium.queue import Queue
#
#
# def find_object(queue: Queue, name: str):
#     for i in queue.queue:
#         if i.name == name:
#             return i
#
#
# c1 = Character("a1", "?", D(114), D(514), D(1919), {}, D(104))
# c2 = Character("b1", "?", D(114), D(514), D(1919), {}, D(101))
# c3 = Character("c1", "?", D(114), D(514), D(1919), {}, D(100))
# # self, name: str, health: Decimal, defensive: Decimal, attack: Decimal, stance: Decimal,
# #                  stance_weak: list[str], attributes: dict[str, Decimal], speed: Decimal
#
# e1 = Enemy("a2", D(114), D(514), D(1919), D(10), ["1", "1"], {}, D(100))
# e2 = Enemy("b2", D(114), D(514), D(1919), D(10), ["1", "1"], {}, D(100))
# e3 = Enemy("c2", D(114), D(514), D(1919), D(10), ["1", "1"], {}, D(100))
#
# # noinspection PyTypeChecker
# b = Queue([c1, c2, c3], [e1, e2, e3])
#
# b.calc_tick()
#
# while True:
#     while True:
#         command_input = input()
#         match command_input:
#             case "move_front":
#                 refactor_object_name = input("object name: ")
#                 object_ = find_object(b, refactor_object_name)
#                 b.move_front(object_, input("move front length: "))
#                 b.print()
#             case "move_back":
#                 refactor_object_name = input("object name: ")
#                 object_ = find_object(b, refactor_object_name)
#                 b.move_back(object_, input("move back length: "))
#                 b.print()
#             case "set_speed":
#                 refactor_object_name = input("object name: ")
#                 object_ = find_object(b, refactor_object_name)
#                 b.set_speed(object_, input("speed: "))
#                 b.print()
#             case "exit":
#                 sys.exit(0)
#             case "print":
#                 b.print()
#             case _:
#                 break
#     b.move()
#     fastest = b.get_move()
#     print("行动: ", fastest.name)
#     fastest.length = D(0)

from aluminium.enhance import Enhance, Enhances

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

enhance2 = Enhance.generate_from_json("hand", 1, 5, hand)

enhance1 = Enhance.generate_from_json("head", 1, 5, head)

enhance3 = Enhance.generate_from_json("body", 1, 5, body)

enhance4 = Enhance.generate_from_json("boot", 1, 5, boot)

enhance5 = Enhance.generate_from_json("line", 1, 5, line)

enhance6 = Enhance.generate_from_json("ball", 1, 5, ball)

enhances = Enhances()

#

for i in [i for i in dir() if i.startswith("enhance") and i != "enhances"]:
    enhances.wear(eval(i))

for i, j in enhances.calc_total_value().items():
    print(i, j)
