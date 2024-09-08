from .value_provider import ValueProvider

# replace in the future
HARD_LEVEL_GROUP = ValueProvider("hard_level_group.json").init()

BREAKING_RATE = ValueProvider("breaking_rate.json").init()

MAIN_ATTRIBUTE = ValueProvider("main_attribute.json").init()

SUB_ATTRIBUTE = ValueProvider("sub_attribute.json").init()

