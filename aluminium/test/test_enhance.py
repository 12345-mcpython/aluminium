import unittest

from aluminium.enhances.base import Enhance


class EnhanceTest(unittest.TestCase):
    def test_enhance_json(self):
        jsons = {"main_attribute": {"crit_attack": 15},
                 "sub_attributes": {"crit_chance": [3, 3], "health": [0, 2], "defensive": [1, 1], "speed": [1, 3]}}
        enhance = Enhance.generate_from_json("hand", 1, 5, jsons)
        self.assertEqual(enhance)
