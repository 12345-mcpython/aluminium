from decimal import Decimal


class Skill:
    def __init__(self, skill_rates: dict[str, dict[int, list[Decimal]]], skill_description: str):
        self.skill_rates = skill_rates
        self.skill_description = skill_description

    def get_skill_rate(self, skill_type: str, skill_level: int):
        return self.skill_rates[skill_type][skill_level]
