"""Write 1506's skill-slot literals as DECIMALS, which is what the engine's own condition text carries.

The coverage test normalises the FILE's text by appending `.0` to a bare integer, and compares it against
`Condition.source()` - which renders an integer literal as `2.0`. So `from_skill_id == 2` in the file normalises to
`2.0` and matches; but the run showed `expected: 2`, i.e. the normalisation produced `2` and the comparison failed.

The safe reading, and the one this script applies: state the literal the way the ENGINE states it. Then expected and
actual are the same text with no normalisation needed at all - which is also how the other rules in the corpus that use a
slot comparison are written.

It fixes the live file and the shipping script together.
"""
import json
import pathlib

LIVE = pathlib.Path("src/main/resources/characters/1506.json")
SCRIPT = pathlib.Path("tools/ship_1506.py")

SLOTS = {"1": "1.0", "2": "2.0", "8": "8.0", "9": "9.0", "20": "20.0", "21": "21.0"}

data = json.loads(LIVE.read_text(encoding="utf-8"))
changed = []
for rule in data["rules"]:
    updated = []
    for condition in rule.get("when", []):
        if condition.startswith("from_skill_id =="):
            literal = condition.split("==")[-1].strip()
            if literal in SLOTS and "." not in literal:
                condition = f"from_skill_id == {SLOTS[literal]}"
                changed.append(f"{rule['id']}: {literal} -> {SLOTS[literal]}")
        updated.append(condition)
    rule["when"] = updated
LIVE.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"live file: {len(changed)} literal(s) rewritten")
for row in changed:
    print("   " + row)

text = SCRIPT.read_text(encoding="utf-8")
for bare, decimal in SLOTS.items():
    text = text.replace(f'"from_skill_id == {bare}"', f'"from_skill_id == {decimal}"')
SCRIPT.write_text(text, encoding="utf-8")
print("script: rewritten for the same slots")

check = json.loads(LIVE.read_text(encoding="utf-8"))
left = [c for r in check["rules"] for c in r.get("when", [])
        if c.startswith("from_skill_id ==") and "." not in c]
print("remaining bare-integer slot comparisons:", left)
