import json
from decimal import Decimal

from aluminium.utils import calc_character_rate


class Attacker:
    def __init__(self, name: str, mt: str, health: Decimal, defensive: Decimal, attack: Decimal):
        self.name = name
        self.mt = mt
        self.health = health
        self.defensive = defensive
        self.attack = attack


class Event:
    def on_battle_start(self):
        pass

    def on_battle_successful(self):
        pass

    def on_battle_fail(self):
        pass

    def on_health_reduce(self):
        pass

    def on_enemy_killed(self):
        pass

    def on_character_died(self):
        pass


class Character:
    def __init__(self, name: str, mt: str, health: Decimal, defensive: Decimal, attack: Decimal,
                 attributes: dict[str, Decimal], attacker: Attacker = None,
                 enhances=None):
        self.name = name
        self.mt = mt
        self.health = health
        self.defensive = defensive
        self.attack = attack
        self.attributes = attributes
        self.attacker = attacker
        self.enhances = enhances

    @classmethod
    def build(cls, character_id: int, level: int, attacker: Attacker = None, enhances=None):
        with open(f'data/characters.json', encoding='utf-8') as f:
            character_json = json.load(f)[character_id]
        return cls(character_json['name'], character_json['mt'],
                   character_json['health'] * calc_character_rate(level),
                   character_json['defensive'] * calc_character_rate(level),
                   character_json['attack'] * calc_character_rate(level),
                   {'crit_attack': Decimal('.5')}, attacker, enhances)

