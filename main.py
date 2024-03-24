from decimal import Decimal as D

from aluminium.battle import Battle

from aluminium.characters.base import Character

from aluminium.enemies.base import Enemy

c1 = Character("a1", "?", D(114), D(514), D(1919), {}, D(104))
c2 = Character("b1", "?", D(114), D(514), D(1919), {}, D(101))
c3 = Character("c1", "?", D(114), D(514), D(1919), {}, D(100))

e1 = Enemy("a2", D(114), D(514), D(1919), {}, D(100))
e2 = Enemy("b2", D(114), D(514), D(1919), {}, D(100))
e3 = Enemy("c2", D(114), D(514), D(1919), {}, D(97))

b = Battle([c1, c2, c3], [e1, e2, e3])

b.calc_tick()
while True:
    b.print_queue()
    b.move()
    b.print_queue()
    fastest = b.get_move()
    print("行动: ", fastest.name)
    fastest.length = D(0)
    input()
