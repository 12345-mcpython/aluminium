"""Find every `from_skill_id` literal in 1506 that carries a decimal point, per RULE ID.

The coverage test reported `p1506_skill_grants_laughs` as `2.0`, so the decimal is somewhere in that rule - and a grep
for the text can be fooled by which of several rules holds it. This prints the rule, the index, and the exact condition.
"""
import json
import pathlib

PATH = pathlib.Path("src/main/resources/characters/1506.json")
OUT = pathlib.Path("tools/_1506_literals.txt")

data = json.loads(PATH.read_text(encoding="utf-8"))
lines = []
for rule in data["rules"]:
    for index, condition in enumerate(rule.get("when", [])):
        if "from_skill_id" in condition:
            flag = "  <-- HAS A DECIMAL" if "." in condition.split("==")[-1] else ""
            lines.append(f"{rule['id']:52s} [{index}] {condition}{flag}")
OUT.write_text("\n".join(lines), encoding="utf-8")
print(f"written {OUT} with {len(lines)} condition(s)")
