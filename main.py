from aluminium.models import Monster, CombatQueue, Character, Attacker
from decimal import Decimal as De

attackers = Attacker.build_attacker(3, 1)
print("光锥: " + attackers.name)
a = Character.build_character(0, 1, attacker=attackers)
b = Monster("114514", 1, De(300), De(210), De(30), 10)

queue = CombatQueue([a], [b])
queue.attack()
