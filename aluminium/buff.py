from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from .movable import Movable


class Buff:
    def __init__(self, name: str, tick: int, given: Movable):
        self.name: str = name
        self.tick: int = tick
        self.given: Movable = given
