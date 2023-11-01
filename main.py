from aluminium.models import Monster, CombatQueue, Character, Attacker
from decimal import Decimal as De

attackers = Attacker.build_attacker(3, 1)

attackers_1 = Attacker.build_attacker(0, 1)

attackers_2 = Attacker.build_attacker(2, 1)

attackers_3 = Attacker.build_attacker(6, 1)

a = Character.build_character(0, 1, attacker=attackers)
a1 = Character.build_character(1, 1, attacker=attackers_1)
a2 = Character.build_character(2, 1, attacker=attackers_3)
a3 = Character.build_character(3, 1, attacker=attackers_2)

b = Monster("114", 1, De(300), De(210), De(30), 100, 10)
b1 = Monster("114 1", 1, De(300), De(210), De(30), 100, 10)
b2 = Monster("114 2", 1, De(300), De(210), De(30), 100, 10)
b3 = Monster("114 3", 1, De(300), De(210), De(30), 100, 10)

queue = CombatQueue([a, a1, a2, a3], [b, b1, b2, b3])
queue.attack()
