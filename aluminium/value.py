from .values import ValueProvider

HARD_LEVEL_GROUP_WORLD = ValueProvider("hard_level_group_1.json").init()
HARD_LEVEL_GROUP_MAIN_LINE = ValueProvider("hard_level_group_2.json").init()
HARD_LEVEL_GROUP_VIRTUAL = ValueProvider("hard_level_group_3.json").init()
HARD_LEVEL_GROUP_SPECIAL = ValueProvider("hard_level_group_1401.json").init()
BREAKING_RATE = ValueProvider("breaking_rate.json").init()
