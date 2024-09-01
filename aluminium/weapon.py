from decimal import Decimal


class Weapon:
    def __init__(
            self, name: str, mt: str, health: Decimal, defence: Decimal, attack: Decimal
    ):
        self.name = name
        self.mt = mt
        self.health = health
        self.defence = defence
        self.attack = attack

    def __str__(self) -> str:
        return f"<{type(self).__name__} name={self.name} mt={self.mt} health={self.health} defence={self.defence} attack={self.attack}>"

    def __repr__(self):
        return str(self)
