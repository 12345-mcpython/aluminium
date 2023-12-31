from aluminium import Enemy, Battle, Character, Attacker, read_data
from decimal import Decimal as De

attackers = Attacker.build_attacker(3, 1)

attackers_1 = Attacker.build_attacker(0, 1)

attackers_2 = Attacker.build_attacker(2, 1)

attackers_3 = Attacker.build_attacker(6, 1)

a = Character.build_character(0, 1, attacker=attackers)
a1 = Character.build_character(1, 1, attacker=attackers_1)
a2 = Character.build_character(2, 1, attacker=attackers_3)
a3 = Character.build_character(3, 1, attacker=attackers_2)

# b = Enemy("114", 1, De(300), De(210), De(30), 100, 10)
# b1 = Enemy("114 1", 1, De(300), De(210), De(30), 100, 10)
# b2 = Enemy("114 2", 1, De(300), De(210), De(30), 100, 10)
# b3 = Enemy("114 3", 1, De(300), De(210), De(30), 100, 10)

e1 = Enemy.build_enemy("baryon", 1)

queue = Battle([a, a1, a2, a3], [e1])
queue.battle()
