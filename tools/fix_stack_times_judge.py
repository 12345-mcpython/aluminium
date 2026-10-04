"""Fix the control: `max_stacks` is refused unless the effect is stackable, so the plain case omits it.

(That refusal is item 60's relaxation, which keys on `stackable` -- the control must not state a cap it cannot hold.)
"""
import io
import sys

PATH = "src/test/java/com/laosun/aluminium/test/StackTimesTest.java"
text = io.open(PATH, encoding="utf-8").read()
OLD = """        EffectSpec apply = effect("APPLY_BUFF", "buff", STATE, "permanent", Boolean.TRUE, "target", "self",
                "stackable", stackable ? Boolean.TRUE : null,
                "maxStacks", 99,
                "scale", "party_resource:" + COUNTER, "percent", 1.0);"""
NEW = """        // \\u26a0 `max_stacks` is only allowed beside `stackable` (item 60), and the scale only matters for a state that can
        // hold a count -- so the control states neither.
        EffectSpec apply = stackable
                ? effect("APPLY_BUFF", "buff", STATE, "permanent", Boolean.TRUE, "target", "self",
                        "stackable", Boolean.TRUE, "maxStacks", 99,
                        "scale", "party_resource:" + COUNTER, "percent", 1.0)
                : effect("APPLY_BUFF", "buff", STATE, "permanent", Boolean.TRUE, "target", "self");"""
if text.count(OLD) != 1:
    sys.exit("REFUSING: the anchor appears %d times" % text.count(OLD))
io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW, 1))
print("ok   the control states neither a cap nor a scale")
