import json
import os
from decimal import Decimal

common_skill_rate = [0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.3]

pioneer_destruction = ("Pioneer", "Destruction", Decimal("163.68"), Decimal("62.7"), Decimal("84.48"),
                       {"crit_chance": Decimal(".05"), "crit_attack": Decimal(".05")}, Decimal("100"))

pioneer_protection = ("Pioneer", "Protection", Decimal("168.96"), Decimal("82.5"), Decimal("81.84"),
                      {"crit_chance": Decimal(".05"), "crit_attack": Decimal(".05")}, Decimal("95"))


class ValueProvider:
    def __init__(self, from_json):
        self.from_json = from_json
        self.content = {}
        self.active = False

    def init(self):
        if not os.path.exists(f"data/{self.from_json}"):
            print(f"Unable to find json data {self.from_json}.")
            return
        with open(f"data/{self.from_json}", encoding="utf-8") as f:
            self.content = json.load(f)
        self.active = True
        return self

    def read(self, *args):
        if not self.active:
            print("This ValueProvider \"{self.from_json}\" is not init.")
            return
        m = self.content
        for i in args:
            m = m[i]
        return m
