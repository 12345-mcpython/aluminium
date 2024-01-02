from .models import CharacterBase


class BehaviorBuilder:
    def __init__(self, movables: list[CharacterBase]):
        self.movables = movables

    def build(self):
        on_start = []
        for i in self.movables:
            pass
