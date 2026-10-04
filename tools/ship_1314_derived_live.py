"""Mark 1314's CRIT_ATTACK clause live and read it in the judge (round 1666).

The clause is spelled `scale: self_stacks:当品` with percent 0.024 -- the derived form, which is an absolute number in the
attribute's own units, and CRIT DMG is a ratio attribute, so its modifier is PURE. The judge reads the absolute value
(CRIT DMG's base is zero: the 50% everyone starts with is itself a modifier).
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1314.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/JadeLiveGoodsTest.java"

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rules = doc["rules"] if isinstance(doc, dict) and "rules" in doc else doc

marked = 0
for rule in rules:
    for effect in rule.get("do", []):
        if not isinstance(effect, dict):
            continue
        scale = effect.get("scale")
        if effect.get("op") == "MODIFY_ATTR" and isinstance(scale, str) and scale.startswith("self_stacks:"):
            effect["per_stack_live"] = True
            marked += 1
if marked < 1:
    sys.exit("REFUSING: no derived counter clause found")
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("marked %d derived clause(s)" % marked)

text = io.open(JUDGE, encoding="utf-8").read()
OLD = """        double attackShare = share(jade, AttributeType.ATTACK);"""
NEW = """        double attackShare = share(jade, AttributeType.ATTACK);
        // CRIT DMG's base is zero (the 50% everyone starts with is itself a modifier), so its reading is absolute
        double critBefore = jade.getAttribute(AttributeType.CRIT_ATTACK).get();"""
if text.count(OLD) != 1:
    sys.exit("REFUSING: the premise anchor")
text = text.replace(OLD, NEW, 1)

OLD2 = """        double attackAfter = share(jade, AttributeType.ATTACK);"""
NEW2 = """        double attackAfter = share(jade, AttributeType.ATTACK);
        double critAfter = jade.getAttribute(AttributeType.CRIT_ATTACK).get();"""
if text.count(OLD2) != 1:
    sys.exit("REFUSING: the after anchor")
text = text.replace(OLD2, NEW2, 1)

OLD3 = """        Assertions.assertEquals(0.005 * 4, attackAfter - attackShare, 1e-9,
                "the attack aura has to follow four more layers");"""
NEW3 = """        Assertions.assertEquals(0.005 * 4, attackAfter - attackShare, 1e-9,
                "the attack aura has to follow four more layers");
        System.out.println("[jade] crit " + critBefore + " -> " + critAfter);
        Assertions.assertEquals(0.024 * 4, critAfter - critBefore, 1e-9,
                "the derived crit-damage clause follows too, in absolute units");"""
if text.count(OLD3) != 1:
    sys.exit("REFUSING: the assertion anchor")
text = text.replace(OLD3, NEW3, 1)

io.open(JUDGE, "w", encoding="utf-8", newline="").write(text)
print("ok   the judge reads the derived clause as well")
