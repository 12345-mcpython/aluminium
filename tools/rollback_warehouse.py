"""Roll back the load point, and record the refutation that came with it (round 17 of the goal).

The judge passed, but its mutant did not move the reading: with the second pass asking NOBODY, the listener's rule still ran
(`listener gain = 261.954`, unchanged). So the listener's rule is being delivered by some OTHER path, and the load point I added is
not what makes it work -- the judge was passing for a reason it did not name, which is exactly the class of false green this arc
has been hunting.

So both the engine patch and the judge come out, and the finding is recorded with its next step: find what already dispatches to a
character that is not on the field.
"""
import io
import os
import sys

BATTLE = "src/main/java/com/laosun/aluminium/Battle.java"


def drop(path, block, label):
    text = io.open(path, encoding="utf-8").read()
    if text.count(block) != 1:
        sys.stderr.write("REFUSING %s: the block appears %d times\n" % (label, text.count(block)))
        raise SystemExit(1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(text.replace(block, "", 1))
    print("ok   removed %s" % label)


drop(BATTLE, '\n\n    /**\n     * ⭐ Units that are OWNED but NOT DEPLOYED', "the warehouse field")
text = io.open(BATTLE, encoding="utf-8").read()
start = text.index("    private final java.util.List<Character> warehouseListeners")
end = text.index("    public void noteChangedResource(String resource) {")
io.open(BATTLE, "w", encoding="utf-8", newline="\n").write(text[:start] + text[end:])
print("ok   removed the registration API")

text = io.open(BATTLE, encoding="utf-8").read()
start = text.index("            // ⭐ 获得该角色即生效")
end = text.index("            return fired;\n        } finally {\n            triggerDepth--;")
io.open(BATTLE, "w", encoding="utf-8", newline="\n").write(text[:start] + text[end:])
print("ok   removed the second dispatch pass")

for path in ("src/test/java/com/laosun/aluminium/test/WarehouseListenerTest.java",):
    if os.path.exists(path):
        os.remove(path)
        print("ok   removed the judge (it passed for a reason it did not name)")
