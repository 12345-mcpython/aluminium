from aluminium.models import Monster, CombatQueue, Character, Attacker
from decimal import Decimal as De

attacker = Attacker.build_attacker(0, 1)

a = Character.build_character(0, 1, attacker=attacker)
b = Monster("114514", 1, De(139.5), De(210), De(30), 10)

queue = CombatQueue([a], [b])
queue.attack()
