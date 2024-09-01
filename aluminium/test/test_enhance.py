import unittest

from aluminium.relic import Relic


class EnhanceTest(unittest.TestCase):
    def test_enhance_json(self):
        jsons = {"main_attribute": {"crit_attack": 15},
                 "sub_attributes": {"crit_chance": [2, 2, 2, 2, 2, 2], "health": [1], "defence": [1], "speed": [1]}}
        enhance = Relic.generate_from_json("hand", 1, 5, jsons)
        # self.assertEqual(enhance)
