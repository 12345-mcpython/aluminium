# This file provides characters value and enemies value temporarily.
# TODO: Add ValueProvider
from decimal import Decimal

common_skill_rate = [0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.3]

pioneer_destruction = ("Pioneer", "Destruction", Decimal("163.68"), Decimal("62.7"), Decimal("84.48"),
                       {"crit_chance": Decimal(".05"), "crit_attack": Decimal(".05")}, Decimal("100"))

pioneer_protection = ("Pioneer", "Protection", Decimal("168.96"), Decimal("82.5"), Decimal("81.84"),
                      {"crit_chance": Decimal(".05"), "crit_attack": Decimal(".05")}, Decimal("95"))
