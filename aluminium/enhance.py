from decimal import Decimal
import random

from aluminium.utils import list_str2decimal as ls2d


# code part


class Attribute:
    def __init__(self, left: str, right: Decimal):
        self.left = left
        self.right = right

    def __str__(self):
        return f"<Attribute left={self.left} right={self.right}>"


class Enhance:
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
        self.xp = sum(xp[star][:level])

    @classmethod
    def generate_random_enhance(
        cls, position: str, enhance_id: int, star: int
    ) -> "Enhance":
        chosen_main_attribute = random.choice(position_attributes[position])
        chosen_sub_attributes = random.sample(
            sub_attributes_key_table, random.randint(star - 2, star - 1)
        )
        chosen_main_attribute_base = main_attribute_table[chosen_main_attribute][
            "base"
        ][star - 2]
        chosen_sub_attributes_base = [
            sub_attribute_table[i]["base"][star - 2] for i in chosen_sub_attributes
        ]
        main_attribute = Attribute(chosen_main_attribute, chosen_main_attribute_base)
        sub_attributes = [
            Attribute(i, j)
            for i, j in zip(chosen_sub_attributes, chosen_sub_attributes_base)
        ]
        return cls(0, enhance_id, position, star, main_attribute, sub_attributes)

    def _xp_reached_level(self, given_xp):
        level = 0
        total = self.xp
        for i in xp[self.star][self.level :]:
            total += i
            if given_xp < total:
                break
            level += 1
        return level

    def promote_level(self, given_xp):
        if given_xp > len(xp[self.star]):
            print("xp outflow")
        promote_level = self._xp_reached_level(given_xp)
        level_range = range(self.level, promote_level + 1)
        self.xp += given_xp
        self.level += promote_level
        added = False
        if 3 in level_range and len(self.sub_attributes) == 3:
            print("Add sub attribute")
            a = list([r.left for r in self.sub_attributes])
            b = list(sub_attributes_key_table)
            for he in a:
                b.remove(he)
            added_sub_attribute_key = random.choice(b)
            added_attribute = Attribute(
                added_sub_attribute_key,
                sub_attribute_table[added_sub_attribute_key]["base"],
            )
            self.sub_attributes.append(added_attribute)
            added = True
        if len([q for q in level_range if q % 3 == 0 and q != 0]):
            for i in [p for p in level_range if p % 3 == 0 and p != 0]:
                if added and i == 3:
                    continue
                print("Random promote sub attribute")
                promoted_sub_attribute = random.choice(self.sub_attributes)
                print(promoted_sub_attribute)

    def __str__(self) -> str:
        return f"<Enhance enhance_id={self.enhance_id} positon={self.position} star={self.star} main_attribute={self.main_attribute} sub_attribute={','.join(str(i) for i in self.sub_attributes)}>"


# data part

speed_base = ls2d(["1.6128", "2.4192", "3.2256", "4.032"])
speed_bonus = ls2d(["1", "1", "1.1", "1.4"])
health_base = ls2d(["45.1584", "67.7376", "90.3168", "112.896"])
health_bonus = ls2d(["15.80544", "23.70816", "31.61088", "39.5136"])
attack_base = ls2d(["22.5792", "33.8688", "45.1584", "56.448"])
attack_bonus = ls2d(["7.90272", "11.85408", "15.80544", "19.7568"])
health_percent_base = ls2d(["0.027648", "0.041472", "0.055296", "0.069120"])
health_percent_bonus = ls2d(["0.009677", "0.014515", "0.019354", "0.024192"])
attack_percent_base = ls2d(["0.027648", "0.041472", "0.055296", "0.069120"])
attack_percent_bonus = ls2d(["0.009677", "0.014515", "0.019354", "0.024192"])
breaking_effect_base = ls2d(["0.041472", "0.062208", "0.082944", "0.103680"])
breaking_effect_bonus = ls2d(["0.014515", "0.021773", "0.029030", "0.036288"])
effect_hit_rate_base = ls2d(["0.027648", "0.041472", "0.055296", "0.069120"])
effect_hit_rate_bonus = ls2d(["0.009677", "0.014515", "0.019354", "0.024192"])
energy_regeneration_rate_base = ls2d(["0.012442", "0.018662", "0.024883", "0.031104"])
energy_regeneration_rate_bonus = ls2d(["0.004355", "0.006532", "0.008709", "0.010886"])
outgoing_healing_boost_base = ls2d(["0.022118", "0.033178", "0.044237", "0.055296"])
outgoing_healing_boost_bonus = ls2d(["0.007741", "0.011612", "0.015483", "0.019354"])
crit_chance_base = ls2d(["0.020736", "0.031104", "0.041472", "0.051840"])
crit_chance_bonus = ls2d(["0.007258", "0.010886", "0.014515", "0.018144"])
crit_attack_base = ls2d(["0.041472", "0.062208", "0.082944", "0.103680"])
crit_attack_bonus = ls2d(["0.014515", "0.021773", "0.029030", "0.036288"])
defensive_percent_base = ls2d(["0.034560", "0.051840", "0.069120", "0.086400"])
defensive_percent_bonus = ls2d(["0.012096", "0.018144", "0.024192", "0.030240"])
damage_boost_base = ls2d(["0.024883", "0.037325", "0.049766", "0.062208"])
damage_boost_bonus = ls2d(["0.008709", "0.013064", "0.017418", "0.021773"])

main_attribute_table = {
    "health": {"base": health_base, "bonus": health_bonus},
    "health_percent": {"base": health_percent_base, "bonus": health_percent_bonus},
    "speed": {"base": speed_base, "bonus": speed_bonus},
    "attack": {"base": attack_base, "bonus": attack_bonus},
    "attack_percent": {"base": attack_percent_base, "bonus": attack_percent_bonus},
    "breaking_effect": {"base": breaking_effect_base, "bonus": breaking_effect_bonus},
    "effect_hit_rate": {"base": effect_hit_rate_base, "bonus": effect_hit_rate_bonus},
    "energy_regeneration_rate": {
        "base": energy_regeneration_rate_base,
        "bonus": energy_regeneration_rate_bonus,
    },
    "outgoing_healing_boost": {
        "base": outgoing_healing_boost_base,
        "bonus": outgoing_healing_boost_bonus,
    },
    "defensive_percent": {
        "base": defensive_percent_base,
        "bonus": defensive_percent_bonus,
    },
    "crit_chance": {"base": crit_chance_base, "bonus": crit_chance_bonus},
    "crit_attack": {"base": crit_attack_base, "bonus": crit_attack_bonus},
    "physical_damage_boost": {"base": damage_boost_base, "bonus": damage_boost_bonus},
    "quantum_damage_boost": {"base": damage_boost_base, "bonus": damage_boost_bonus},
    "fire_damage_boost": {"base": damage_boost_base, "bonus": damage_boost_bonus},
    "ice_damage_boost": {"base": damage_boost_base, "bonus": damage_boost_bonus},
    "thunder_damage_boost": {"base": damage_boost_base, "bonus": damage_boost_bonus},
    "imaginary_damage_boost": {"base": damage_boost_base, "bonus": damage_boost_bonus},
    "wind_damage_boost": {"base": damage_boost_base, "bonus": damage_boost_bonus},
}

speed_extra_base = ls2d(["1", "1.2", "1.6", "2"])
speed_extra_bonus = ls2d(["0.1", "0.1", "0.2", "0.3"])
attack_extra_base = ls2d(["6.774", "10.161", "13.548", "16.935"])
attack_extra_bonus = ls2d(["0.84675", "1.270126", "1.6935", "2.116877"])
defensive_extra_base = ls2d(["6.774", "10.161", "13.548", "16.935"])
defensive_extra_bonus = ls2d(["0.84675", "1.270126", "1.6935", "2.116877"])
health_extra_base = ls2d(["13.548", "20.322", "27.096", "33.870"])
health_extra_bonus = ls2d(["1.6935", "2.540253", "3.3870", "4.233755"])
health_percent_extra_base = ls2d(["0.013824", "0.020736", "0.027648", "0.034560"])
health_percent_extra_bonus = ls2d(["0.001728", "0.002592", "0.003456", "0.004320"])
attack_percent_extra_base = ls2d(["0.013824", "0.020736", "0.027648", "0.034560"])
attack_percent_extra_bonus = ls2d(["0.001728", "0.002592", "0.003456", "0.004320"])
effect_hit_rate_extra_base = ls2d(["0.013824", "0.020736", "0.027648", "0.034560"])
effect_hit_rate_extra_bonus = ls2d(["0.001728", "0.002592", "0.003456", "0.004320"])
effect_resistance_extra_base = ls2d(["0.013824", "0.020736", "0.027648", "0.034560"])
effect_resistance_extra_bonus = ls2d(["0.001728", "0.002592", "0.003456", "0.004320"])
defensive_percent_extra_base = ls2d(["0.017280", "0.025920", "0.034560", "0.043200"])
defensive_percent_extra_bonus = ls2d(["0.002160", "0.003240", "0.004320", "0.005400"])
crit_chance_extra_base = ls2d(["0.010368", "0.015552", "0.020736", "0.025920"])
crit_chance_extra_bonus = ls2d(["0.001296", "0.001944", "0.002592", "0.003240"])
crit_attack_extra_base = ls2d(["0.020736", "0.031104", "0.041472", "0.051840"])
crit_attack_extra_bonus = ls2d(["0.002592", "0.003888", "0.005184", "0.006480"])
breaking_effect_extra_base = ls2d(["0.020736", "0.031104", "0.041472", "0.051840"])
breaking_effect_extra_bonus = ls2d(["0.002592", "0.003888", "0.005184", "0.006480"])

sub_attribute_table = {
    "speed": {"base": speed_extra_base, "bonus": speed_extra_bonus},
    "health": {"base": health_extra_base, "bonus": health_extra_bonus},
    "health_percent": {
        "base": health_percent_extra_base,
        "bonus": health_percent_extra_bonus,
    },
    "attack": {"base": attack_extra_base, "bonus": attack_extra_bonus},
    "attack_percent": {
        "base": attack_percent_extra_base,
        "bonus": attack_percent_extra_bonus,
    },
    "defensive": {"base": defensive_extra_base, "bonus": defensive_extra_bonus},
    "defensive_percent": {
        "base": defensive_percent_extra_base,
        "bonus": defensive_percent_extra_bonus,
    },
    "breaking_effect": {
        "base": breaking_effect_extra_base,
        "bonus": breaking_effect_extra_bonus,
    },
    "effect_hit_rate": {
        "base": effect_hit_rate_extra_base,
        "bonus": effect_hit_rate_extra_bonus,
    },
    "effect_resistance": {
        "base": effect_resistance_extra_base,
        "bonus": effect_resistance_extra_bonus,
    },
    "crit_chance": {"base": crit_chance_extra_base, "bonus": crit_chance_extra_bonus},
    "crit_attack": {"base": crit_attack_extra_base, "bonus": crit_attack_extra_bonus},
}

hand_attributes = ["health"]
head_attributes = ["attack"]
body_attributes = [
    "health_percent",
    "attack_percent",
    "defensive_percent",
    "crit_chance",
    "crit_attack",
    "outgoing_healing_boost",
    "effect_hit_rate",
]
boot_attributes = ["speed", "health_percent", "attack_percent", "defensive_percent"]
# physical resistance boost
ball_attributes = [
    "health_percent",
    "attack_percent",
    "defensive_percent",
    "physical_damage_boost",
    "quantum_damage_boost",
    "fire_damage_boost",
    "ice_damage_boost",
    "thunder_damage_boost",
    "imaginary_damage_boost",
    "wind_damage_boost",
]
line_attributes = [
    "health_percent",
    "attack_percent",
    "defensive_percent",
    "breaking_effect",
    "energy_regeneration_rate",
]
position_attributes = {
    "hand": hand_attributes,
    "head": head_attributes,
    "body": body_attributes,
    "boot": boot_attributes,
    "ball": ball_attributes,
    "line": line_attributes,
}
sub_attributes_key_table = list(sub_attribute_table.keys())

xp = {
    2: [170, 240, 310, 410, 500, 620],
    3: [280, 400, 520, 680, 840, 1080, 1320, 1620, 2060],
    4: [420, 600, 780, 1020, 1200, 1480, 1980, 2430, 3090, 3840, 4760, 5900],
    5: [
        560,
        800,
        1040,
        1360,
        1680,
        2060,
        2640,
        3240,
        4120,
        5120,
        6350,
        8030,
        10320,
        12960,
        15720,
    ],
}
