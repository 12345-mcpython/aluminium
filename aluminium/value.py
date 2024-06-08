from .values import ValueProvider

HARD_LEVEL_GROUP_WORLD = ValueProvider("hard_level_group_1.json").init()
HARD_LEVEL_GROUP_MAIN_LINE = ValueProvider("hard_level_group_2.json").init()
HARD_LEVEL_GROUP_VIRTUAL = ValueProvider("hard_level_group_3.json").init()
HARD_LEVEL_GROUP_SPECIAL = ValueProvider("hard_level_group_1401.json").init()

BREAKING_RATE = ValueProvider("breaking_rate.json").init()

RELIC_2_MAIN_ATTRIBUTE = ValueProvider("relic_2_main_attribute.json").init()
RELIC_3_MAIN_ATTRIBUTE = ValueProvider("relic_3_main_attribute.json").init()
RELIC_4_MAIN_ATTRIBUTE = ValueProvider("relic_4_main_attribute.json").init()
RELIC_5_MAIN_ATTRIBUTE = ValueProvider("relic_5_main_attribute.json").init()

RELIC_2_SUB_ATTRIBUTE = ValueProvider("relic_2_sub_attribute.json").init()
RELIC_3_SUB_ATTRIBUTE = ValueProvider("relic_3_sub_attribute.json").init()
RELIC_4_SUB_ATTRIBUTE = ValueProvider("relic_4_sub_attribute.json").init()
RELIC_5_SUB_ATTRIBUTE = ValueProvider("relic_5_sub_attribute.json").init()

RELIC_EXP = ValueProvider("relic_exp.json").init()