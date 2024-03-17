from decimal import Decimal as D

from aluminium.battle import Battle

from aluminium.characters.base import Character

from aluminium.enemies.base import Enemy

c1 = Character("a1", "?", D(114), D(514), D(1919), {}, D(100))
c2 = Character("b1", "?", D(114), D(514), D(1919), {}, D(100))
c3 = Character("c1", "?", D(114), D(514), D(1919), {}, D(100))

e1 = Enemy("a2", D(114), D(514), D(1919), {}, D(100))
e2 = Enemy("b2", D(114), D(514), D(1919), {}, D(100))
e3 = Enemy("c2", D(114), D(514), D(1919), {}, D(100))

b = Battle([c1, c2, c3], [e1, e2, e3])

move = b.init_battle()

while True:
    print("行动: ", move.name)
    b.print_queue()
    move.length = 0
    move = b.next()
    input()