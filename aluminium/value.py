from . import data_dir


class Value:
    def __init__(self, health, attack, defensive, speed, stance=0):
        self.health = health
        self.attack = attack
        self.defensive = defensive
        self.speed = speed
        self.stance = stance

    @classmethod
    def load(cls, load_type: str, load_name: str):
        file = (data_dir / (load_type + ".json")).open()
