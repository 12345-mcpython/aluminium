class Event:
    def on_battle_start(self, battle):
        pass

    def on_battle_successful(self, battle):
        pass

    def on_battle_fail(self, battle):
        pass

    def on_health_reduce(self, battle, reduce_object):
        pass

    def on_enemy_killed(self, battle, enemy) -> bool:
        pass

    def on_character_died(self, battle, character) -> bool:
        pass

    def before_move(self, battle, move_object):
        pass

    def after_move(self, battle, move_object):
        pass
