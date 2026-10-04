"""Update the two transformation readings, which the new clause makes stale (round 1692).

Measured (and every number is a sentence):
  * at battle start: 873.18 = base 582.12 x 1.5 -- the trace 「进入战斗或变身结束时攻击力提高 50%」 fires here;
  * during 变身:   1338.876 = base x 2.3 = 1 + 0.5 (trace) + 0.8 (transformation) -- both are in effect, which is what `coexist`
    just made possible;
  * after 变身 ends: 1164.24 = base x 2.0 = 1 + 0.5 (battle start) + 0.5 (the end clause) -- the trace fires again, exactly as
    「或变身结束时」 says.

So the assertions stop deriving everything from `atk0` (which now already carries the trace) and name the base instead.
"""
import io
import re
import sys

STATS = "src/test/java/com/laosun/aluminium/test/TransformationStatsTest.java"
SCOPED = "src/test/java/com/laosun/aluminium/test/TransformationScopedStatsTest.java"

text = io.open(STATS, encoding="utf-8").read()
OLD = """        Assertions.assertEquals(atk0 * 1.8, owner.getAttribute(AttributeType.ATTACK).get(), atk0 * 0.001,
                "「攻击力提高 80%」 (before=" + atk0 + ")");"""
NEW = """        // \u26a0 Updated 2026-10-02: `atk0` already carries the trace's +50% (\u300c\u8fdb\u5165\u6218\u6597\u6216\u53d8\u8eab\u7ed3\u675f\u65f6\u653b\u51fb\u529b\u63d0\u9ad8 50%\u300d), so the
        // block is measured against the BASE: 1 + 0.5 (trace) + 0.8 (transformation) = 2.3.
        double base = atk0 / 1.5;
        Assertions.assertEquals(base * 2.3, owner.getAttribute(AttributeType.ATTACK).get(), atk0 * 0.001,
                "\u300c\u53d8\u8eab\u671f\u95f4\u653b\u51fb\u529b\u63d0\u9ad8 80%\u300d beside the trace's 50% (before=" + atk0 + ")");"""
if text.count(OLD) != 1:
    sys.exit("REFUSING: the stats anchor appears %d times" % text.count(OLD))
io.open(STATS, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW, 1))
print("ok   TransformationStatsTest measures against the base")

text = io.open(SCOPED, encoding="utf-8").read()
# the block's worth, and its absence once the state is gone
OLD2 = re.search(r"Assertions\.assertEquals\([^;]*1\.8[^;]*;", text)
if OLD2 is None:
    sys.exit("REFUSING: the scoped 1.8 assertion was not found")
text = text[:OLD2.start()] + OLD2.group(0).replace("1.8", "2.3") + text[OLD2.end():]
print("ok   the scoped block reads 2.3")

OLD3 = re.search(r"Assertions\.assertEquals\(atk0, [^;]*;", text)
if OLD3 is not None:
    text = text[:OLD3.start()] + (
        "// \u26a0 Updated 2026-10-02: the trace fires AGAIN when the transformation ends (\u300c\u6216\u53d8\u8eab\u7ed3\u675f\u65f6\u300d), so what is left\n"
        "        // is the battle-start 50% plus the end 50%. What this reading is about is unchanged: the TRANSFORMATION's own\n"
        "        // block is gone.\n"
        "        Assertions.assertEquals(atk0 * (2.0 / 1.5), owner.getAttribute(AttributeType.ATTACK).get(), atk0 * 0.001,\n"
        "                \"\u300c\u53d8\u8eab\u671f\u95f4\u300d-- the transformation's block must be gone with the state\");") + text[OLD3.end():]
    print("ok   the 'block dies with the state' reading accounts for the trace firing again")
io.open(SCOPED, "w", encoding="utf-8", newline="").write(text)
