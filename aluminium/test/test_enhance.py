import unittest

from aluminium.enhance import Enhance


class EnhanceTest(unittest.TestCase):
    def test_enhance_json(self):
        jsons = {"main_attribute": {"crit_attack": 15},
                 "sub_attributes": {"crit_chance": [2, 2, 2, 2, 2, 2], "health": [1], "defensive": [1], "speed": [1]}}
        enhance = Enhance.generate_from_json("hand", 1, 5, jsons)
        # self.assertEqual(enhance)
