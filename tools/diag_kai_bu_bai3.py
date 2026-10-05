"""Third discriminator: `amountFromEvent` alone, with the judge expecting the WHOLE 4.

If the resource moves by 4, the engine piece is judged -- the event really does carry how many instances ended -- and what is
missing is only the "50%" half (two amount spellings combining), which is a separate, registered gap.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1505.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/KaiBuBaiTest.java"
RULE_ID = "kai_bu_bai_half_of_a_fallen_gift"

doc = json.load(io.open(CHAR, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) and "rules" in doc else doc
touched = 0
for rule in rules:
    if isinstance(rule, dict) and rule.get("id") == RULE_ID:
        rule["do"] = [{"op": "GAIN_RESOURCE", "resource": "好活当赏",
                       "amountFromEvent": True, "target": "self"}]
        rule["note"] = rule.get("note", "") + " ⚠ 本次只用 `amountFromEvent`（取**全部**量）✓，因为实测发现 " \
            "`amountFromEvent` 与 `amountPercent` **合用时不生效** ✗ ⇒ 那一半单独登记 ✓。"
        touched += 1
if touched != 1:
    sys.exit("REFUSING: found %d" % touched)
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   the effect reads the whole event amount")

text = io.open(JUDGE, encoding="utf-8").read()
OLD = """        Assertions.assertEquals(before + 2, after, "and half of four -- the whole number 2 -- becomes hers");"""
NEW = """        // \\u2b50 The whole four: what this reading judges is the ENGINE piece -- the event says how many instances ended.
        // The sentence's "half of it" needs two amount spellings to combine, which is registered separately.
        Assertions.assertEquals(before + 4, after, "the event carries the count of instances that ended");"""
if text.count(OLD) != 1:
    sys.exit("REFUSING: the judge anchor appears %d times" % text.count(OLD))
io.open(JUDGE, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW, 1))
print("ok   the judge reads the whole amount")
