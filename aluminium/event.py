class Event:
    def __init__(self, movable):
        self.movable = movable

    def on_battle_start(self, battle):
        pass

    def on_battle_successful(self, battle):
        pass

    def on_battle_fail(self, battle):
        pass

    def on_health_reduce(self, battle, reduce_object):
        pass

    def on_enemy_killed(self, battle, enemy, killer) -> bool:
        pass

    def on_character_killed(self, battle, character, killer) -> bool:
        pass

    def on_character_died(self, battle, character) -> bool:
        pass

    def on_enemy_died(self, battle, enemy) -> bool:
        pass

    def before_move(self, battle):
        pass

    def after_move(self, battle):
        pass

    def on_break_stance(self, battle, breaker, broken_object):
        pass

    def on_heal_stance(self, battle, breaker, broken_object):
        pass
