from __future__ import annotations

import math
import random
from decimal import Decimal

from .value import MAIN_ATTRIBUTE, SUB_ATTRIBUTE

position_attributes = {i: list(j.keys()) for i, j in MAIN_ATTRIBUTE.read("2").items()}

main_attribute_star_mapping = {"2": MAIN_ATTRIBUTE.read("2"), "3": MAIN_ATTRIBUTE.read("3"),
                               "4": MAIN_ATTRIBUTE.read("4"),
                               "5": MAIN_ATTRIBUTE.read("5")}

sub_attribute_star_mapping = {"2": SUB_ATTRIBUTE.read("2"), "3": SUB_ATTRIBUTE.read("3"),
                              "4": SUB_ATTRIBUTE.read("4"),
                              "5": SUB_ATTRIBUTE.read("5")}

sub_attributes_key_table = list(MAIN_ATTRIBUTE.read("2").keys())


# TODO: add xp
# xp = RELIC_EXP.read()


class Attribute:
    def __init__(self, left: str, right: Decimal, extra: int = None):
        self.left = left
        self.right = right
        self.extra = extra

    def __str__(self):
        return f"<{type(self).__name__} left={self.left} right={self.right} extra={self.extra}>"


class Relic:
    def __init__(
            self,
            level: int,
            enhance_id: int,
            position: str,
            star: int,
            main_attribute: Attribute,
            sub_attributes: list[Attribute],
    ):
        self.level = level
        self.enhance_id = enhance_id
        self.position = position
        self.star = star
        self.main_attribute = main_attribute
        self.sub_attributes = sub_attributes
        # self.total_xp = sum(xp[str(star)][:level])

    @classmethod
    def generate_random_enhance(
            cls, position: str, enhance_id: int, star: int
    ) -> Relic:
        star_main_attributes = main_attribute_star_mapping[str(star)][position]
        star_sub_attributes = sub_attribute_star_mapping[str(star)]
        chosen_main_attribute = random.choice(position_attributes[position])
        chosen_sub_attributes = random.sample(
            sub_attributes_key_table, random.randint(star - 2, star - 1)
        )
        chosen_main_attribute_base = Decimal(str(star_main_attributes[chosen_main_attribute][
                                                     "base"
                                                 ]))
        chosen_sub_attributes_base = [
            Decimal(str(star_sub_attributes[i]["base"])) for i in chosen_sub_attributes
        ]
        main_attribute = Attribute(chosen_main_attribute, chosen_main_attribute_base)
        sub_attributes = [
            Attribute(i, j, extra=0)
            for i, j in zip(chosen_sub_attributes, chosen_sub_attributes_base)
        ]
        return cls(0, enhance_id, position, star, main_attribute, sub_attributes)

    @classmethod
    def generate_from_json(cls, position: str, enhance_id: int, star: int,
                           enhance_json: dict, format_version: int = 1,
                           level: int = 15, verify: bool = True):
        """
        Generate Enhance from json
        :param format_version:
        :param verify: check the enhances validity
        :param level: enhance level
        :param position: enhance position "hand" "head" "body" "boot" "ball" "line"
        :param enhance_id: enhance id
        :param star: enhance star 2,3,4,5
        :param enhance_json: {"main_attribute": {"crit_attack": 15},
                 "sub_attributes": {"crit_chance": [3], "health": [3], "defence": [1], "speed": [3,3,3,3,3,3]}}
        3, 3, 3, 3, 3, 3 is the promote level.
        :return: Enhance class
        """

        # Verify part begin
        main_attribute = enhance_json["main_attribute"]
        sub_attributes = enhance_json["sub_attributes"]

        if verify and format_version == 1:
            assert level <= star * 3, f"The enhances level can't above {star * 3}."
            assert all([enhance_json.get("main_attribute"), enhance_json.get("sub_attributes")]), \
                "JSON don't have 'main_attribute' or 'sub_attributes' keys."

            assert list(main_attribute.values())[0] <= 15, "The main attribute's promote level can't above 15."

            total = 0
            for i, j in sub_attributes.items():
                assert i != list(main_attribute.keys())[
                    0], f"The main attribute '{list(main_attribute.keys())[0]}' can't appear in the sub attribute."
                assert len(j) <= 6, f"The sub attribute '{i}' 's promote level can't above 5."
                assert len(j) >= 1, f"The sub attribute '{i}' must have a base value."
                for m in j:
                    assert m <= 2, f"The sub attribute '{i}' 's promote bonus level can't above 2."
                total += len(j)
            assert total <= 9, "The total sub attribute's promote level can't above 5."
            assert list(main_attribute.keys())[0] in position_attributes[
                position], f"Illegal main attribute '{list(main_attribute.keys())[0]}' in position '{position}'."
        if verify and format_version == 2:
            assert level <= star * 3, f"The enhances level can't above {star * 3}."
            assert all([enhance_json.get("main_attribute"), enhance_json.get("sub_attributes")]), \
                "JSON don't have 'main_attribute' or 'sub_attributes' keys."

        # Verify part end
        main_attribute_left = list(main_attribute.keys())[0]
        main_attribute_right = list(main_attribute.values())[0]
        star_main_attributes = main_attribute_star_mapping[str(star)]
        star_sub_attributes = sub_attribute_star_mapping[str(star)]
        main_attribute_value = Decimal(str(star_main_attributes[position][main_attribute_left]["base"])) + \
                               Decimal(str(star_main_attributes[position][main_attribute_left]["bonus"])) * int(
            main_attribute_right)

        main_attribute_class = Attribute(main_attribute_left, main_attribute_value)
        sub_attributes_class = []
        if format_version == 1:
            sub_attributes_left = list(sub_attributes.keys())
            sub_attributes_right = list(sub_attributes.values())

            for left, right in zip(sub_attributes_left, sub_attributes_right):
                sub_attribute_value = star_sub_attributes[left]
                sub_attribute_base = Decimal(str(sub_attribute_value["base"]))
                sub_attribute_bonus = Decimal(str(sub_attribute_value["bonus"]))
                sub_attribute_right = Decimal(0)
                for value in right:
                    sub_attribute_right += (sub_attribute_base + value * sub_attribute_bonus)
                sub_attribute = Attribute(left, sub_attribute_right, extra=len(right) - 1)
                sub_attributes_class.append(sub_attribute)
        elif format_version == 2:
            for key, value in sub_attributes.items():
                sub_attribute_value = star_sub_attributes[key]
                sub_attribute_base = Decimal(str(sub_attribute_value["base"]))
                sub_attribute_bonus = Decimal(str(sub_attribute_value["bonus"]))
                sub_attribute = Attribute(key, sub_attribute_base * (value["promote_level"] + 1) + sub_attribute_bonus *
                                          value["attribute_level"], extra=value["promote_level"])
                sub_attributes_class.append(sub_attribute)
        else:
            raise Exception("Unknown format version:", format_version)

        return cls(level, enhance_id, position, star, main_attribute_class, sub_attributes_class)

    def print(self):
        print("Level: ", self.level)
        print(self.main_attribute.left,
              f"{self.main_attribute.right:.2%}" if self.main_attribute.right < 1 else math.floor(
                  self.main_attribute.right))

        print("sub attributes:")

        for i in self.sub_attributes:
            print(i.left, f"{i.right:.2%}" if i.right < 1 else math.floor(i.right), i.extra)

    def _xp_reached_level(self, given_xp):
        level = 0
        total = self.total_xp
        for i in xp[self.star][self.level:]:
            total += i
            if given_xp < total:
                break
            level += 1
        return level

    def promote_level(self, given_xp):
        if given_xp > len(xp[self.star]):
            print("xp outflow")
        star_main_attributes = main_attribute_star_mapping[str(self.star)]
        star_sub_attributes = sub_attribute_star_mapping[str(self.star)]
        promote_level = self._xp_reached_level(given_xp)
        level_range = range(self.level, promote_level + 1)
        self.total_xp += given_xp
        self.level += promote_level
        added = False
        main_attribute_number = star_main_attributes[self.main_attribute.left]
        self.main_attribute.right += main_attribute_number["bonus"] * promote_level
        if 3 in level_range and len(self.sub_attributes) == 3:
            print("Add sub attribute")
            a = list([r.left for r in self.sub_attributes])
            b = list(sub_attributes_key_table)
            for he in a:
                b.remove(he)
            added_sub_attribute_key = random.choice(b)
            added_attribute = Attribute(
                added_sub_attribute_key,
                star_sub_attributes[added_sub_attribute_key]["base"],
            )
            self.sub_attributes.append(added_attribute)
            added = True
        if len([q for q in level_range if q % 3 == 0 and q != 0]):
            for i in [p for p in level_range if p % 3 == 0 and p != 0]:
                if added and i == 3:
                    continue
                print("Random promote sub attribute")
                promoted_sub_attribute: Attribute = random.choice(self.sub_attributes)
                print(promoted_sub_attribute)
                promoted_sub_attribute_bonus = star_sub_attributes[promoted_sub_attribute.left]["bonus"]
                promoted_sub_attribute.right += promoted_sub_attribute_bonus
                promoted_sub_attribute.extra += 1

    def __str__(self) -> str:
        return f"<{type(self).__name__} enhance_id={self.enhance_id} positon={self.position} star={self.star} main_attribute={self.main_attribute} sub_attribute={','.join(str(i) for i in self.sub_attributes)}>"

    def __repr__(self):
        return str(self)


class Relics:
    def __init__(
            self, hand=None, head=None, body=None, boot=None, ball=None, line=None
    ) -> None:
        self.hand: Relic = hand
        self.head: Relic = head
        self.body: Relic = body
        self.boot: Relic = boot
        self.ball: Relic = ball
        self.line: Relic = line
        self.total: list[Relic] = [hand, head, body, boot, ball, line]

    def __str__(self) -> str:
        return f"<{type(self).__name__} hand={str(self.hand)} head={str(self.head)} body={str(self.body)} boot={str(self.boot)} ball={str(self.ball)} line={str(self.line)}>"

    def __repr__(self):
        return str(self)

    def calc_total_value(self):
        total = {}
        for i in self.total:
            if not i:
                continue
            if total.get(i.main_attribute.left):
                total[i.main_attribute.left] += i.main_attribute.right
            else:
                total[i.main_attribute.left] = i.main_attribute.right
            for j in i.sub_attributes:
                if total.get(j.left):
                    total[j.left] += j.right
                else:
                    total[j.left] = j.right
        return total

    def wear(self, enhance: Relic):
        match enhance.position:
            case "hand":
                self.hand = enhance
            case "head":
                self.head = enhance
            case "body":
                self.body = enhance
            case "boot":
                self.boot = enhance
            case "ball":
                self.ball = enhance
            case "line":
                self.line = enhance
            case _:
                return ValueError(f"Can't wear Position {enhance.position}.")

        self.total: list[Relic] = [self.hand, self.head, self.body, self.boot, self.ball, self.line]
