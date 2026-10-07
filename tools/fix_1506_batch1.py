"""Three fixes after shipping 1506's first batch.

1. `counted_laughs` is a NEW numeric variable, and `UnitDisciplineTest` keeps the registry of every spelling content may
   state ("content must state its numbers in the engine's own units"). A new variable has to be added there or content
   cannot legitimately use it.
2. `from_skill_id == 2.0` must be written `2`: the condition DSL compares a skill SLOT, which is an integer, and
   `CharacterConditionCoverageTest` pins the literals content actually uses.
3. `SilverWolfLV999Test` asserts "the ultimate's advance is one rule" - a count that was true when her file held 2 rules
   and is now stale, which is exactly what a breadth assertion is for.
"""
import json
import pathlib
import re

CHARS = pathlib.Path("src/main/resources/characters/1506.json")
DISCIPLINE = pathlib.Path("src/test/java/com/laosun/aluminium/test/engine/UnitDisciplineTest.java")
COVERAGE = pathlib.Path("src/test/java/com/laosun/aluminium/test/trigger/CharacterConditionCoverageTest.java")
OLD_JUDGE = pathlib.Path("src/test/java/com/laosun/aluminium/test/content/characters/SilverWolfLV999Test.java")

# 1 + 2: the content file.
data = json.loads(CHARS.read_text(encoding="utf-8"))
skills_fixed = 0
for rule in data["rules"]:
    fixed = []
    for condition in rule.get("when", []):
        new = condition.replace("from_skill_id == 2.0", "from_skill_id == 2")
        if new != condition:
            skills_fixed += 1
        fixed.append(new)
    rule["when"] = fixed
CHARS.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"1506: normalised {skills_fixed} skill-slot literal(s)")

# Also fix the script that writes them, so a re-ship stays correct.
SCRIPT = pathlib.Path("tools/ship_1506.py")
text = SCRIPT.read_text(encoding="utf-8")
before = text.count('"from_skill_id == 2"')
text = text.replace('"from_skill_id == 2.0"', '"from_skill_id == 2"')
SCRIPT.write_text(text, encoding="utf-8")
print(f"script: integer literals now {text.count('from_skill_id == 2')} (was {before})")

# 1: register the variable where the discipline test looks for it.
if DISCIPLINE.exists():
    body = DISCIPLINE.read_text(encoding="utf-8")
    print("UnitDisciplineTest mentions counted_laughs:", "counted_laughs" in body)
    # Print the neighbourhood of the registry so the right anchor can be chosen.
    for index, line in enumerate(body.splitlines(), 1):
        if "hp_percent" in line or "eidolon" in line or "NUMERIC" in line:
            print(f"   {index}: {line.strip()[:120]}")
else:
    print("UnitDisciplineTest not found at", DISCIPLINE)

# 3: the stale breadth assertion.
if OLD_JUDGE.exists():
    body = OLD_JUDGE.read_text(encoding="utf-8")
    print("old judge still present, expects 1 rule:", "one rule" in body)
