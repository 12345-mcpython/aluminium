from decimal import Decimal


class Enemy:
    def __init__(
            self,
            name: str,
            health: Decimal,
            defensive: Decimal,
            attack: Decimal,
            attributes: dict[str, Decimal],
            speed: Decimal,
    ):
        self.name = name
        self.health = health
        self.defensive = defensive
        self.attack = attack
        self.attributes = attributes
        self.speed = speed
        self.tick = 0
        self.length = 0
        self.buffs = []
        self.extra = {}

    def __str__(self) -> str:
        return f"<{type(self).__name__} name={self.name} health={self.health} defensive={self.defensive} attack={self.attack} attributes={self.attributes} speed={self.speed} tick={self.tick} length={self.length} extra={self.extra}>"
