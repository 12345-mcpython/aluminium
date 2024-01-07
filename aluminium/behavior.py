from .models import CharacterBase


class BehaviorBuilder:
    def __init__(self, movables: list[CharacterBase]):
        self.movables = movables

    def build(self):
        on_battle_start = self.event_list("on_battle_start")

    def event_list(self, event_name):
        event_list = {}
        for i in self.movables:
            for j in i.behavior['talent']:
                if j.get("event") == event_name:
                    if j.get("condition"):
                        pass
                    event_list[i.name] = j
        return event_list

    def check_condition(self, condition: dict):
        condition_type = condition['type']
        if self.movables:
            pass
