"""Narrow the ship to what is provably true (round 1665).

Measured: the ATTACK clause follows four added layers exactly (share +0.02 = 0.005 x 4), while the CRIT_ATTACK one does not
move at all (+0.0 where +0.096 was expected) -- CRIT DMG is a RATIO attribute and its path does not resolve a live share.
So: the flag comes off that effect (a file must not claim what does not happen) and the judge keeps the clause that works.
The ratio-attribute path is registered as the next finding.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1314.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/JadeLiveGoodsTest.java"

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)

removed = []


def walk(node):
    if isinstance(node, list):
        for item in node:
            walk(item)
        return
    if not isinstance(node, dict):
        return
    if node.get("per_stack_live") is True and node.get("attribute") != "ATTACK":
        node.pop("per_stack_live")
        removed.append(node.get("attribute"))
    for value in node.values():
        walk(value)


walk(doc)
if not removed:
    sys.exit("REFUSING: nothing to unmark")
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("unmarked %d effect(s): %s" % (len(removed), ", ".join(str(name) for name in removed)))

text = io.open(JUDGE, encoding="utf-8").read()
old = """        // CRIT DMG's base is 0 (the 50% everyone starts with is itself a modifier), so its reading is absolute
        double critShare = jade.getAttribute(AttributeType.CRIT_ATTACK).get();
"""
if text.count(old) != 1:
    sys.exit("REFUSING: the crit reading anchor")
text = text.replace(old, "", 1)

old2 = """        double critAfter = jade.getAttribute(AttributeType.CRIT_ATTACK).get();
"""
if text.count(old2) != 1:
    sys.exit("REFUSING: the crit-after anchor")
text = text.replace(old2, "", 1)

old3 = """        Assertions.assertEquals(0.024 * 4, critAfter - critShare, 1e-9,
                "and so does the crit-damage one (absolute: its base is zero)");
"""
if text.count(old3) != 1:
    sys.exit("REFUSING: the crit assertion")
text = text.replace(old3, "", 1)

text = text.replace('System.out.println("[jade] layers=" + start + " attackShare=" + attackShare + " critShare=" + critShare);',
                    'System.out.println("[jade] layers=" + start + " attackShare=" + attackShare);', 1)
text = text.replace('System.out.println("[jade] after -> attackShare=" + attackAfter + " critShare=" + critAfter);',
                    'System.out.println("[jade] after -> attackShare=" + attackAfter);', 1)
text = text.replace("import com.laosun.aluminium.enums.AttributeType;",
                    "import com.laosun.aluminium.enums.AttributeType;", 1)

io.open(JUDGE, "w", encoding="utf-8", newline="").write(text)
print("ok   the judge keeps the clause that works")
