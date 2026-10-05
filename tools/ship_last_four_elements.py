"""Write the last four elements and pin them in the judge (2026-10-02).

Measured from each skill's OWN ability subtree (an ability is named after the skill's `SkillTriggerKey`):
    11407 Skill21 (slot 10) -> Quantum     11407 Skill22 (slot 11) -> Quantum
    11407 Skill23 (slot 12) -> Quantum     11409 Skill01 (slot  1) -> Wind
All four are consistent with what the same servants' other skills already say (Castorice is Quantum on slots 1/2/6; Hyacine's one skill is
Wind, and Hyacine is 风堇).
"""
import io
import json
import sys

SKILLS = "src/main/resources/data/skills.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/MemospriteSkillTest.java"

WANT = [("11407", "10", "Quantum"), ("11407", "11", "Quantum"), ("11407", "12", "Quantum"), ("11409", "1", "Wind")]

table = json.load(io.open(SKILLS, encoding="utf-8"))
for servant, slot, element in WANT:
    row = table.get(servant, {}).get(slot)
    if row is None:
        sys.exit("REFUSING: no row for %s/%s" % (servant, slot))
    print("%s/%s : %s -> %s" % (servant, slot, row.get("element"), element))
    row["element"] = element
io.open(SKILLS, "w", encoding="utf-8", newline="\n").write(json.dumps(table, ensure_ascii=False, indent=2) + "\n")

# the judge's rows are keyed by the MASTER's cid, the same way its other seven are
judge = io.open(JUDGE, encoding="utf-8").read()
anchor = '                {"1415", "1", "ICE"},\n'
if judge.count(anchor) != 1:
    sys.exit("REFUSING: the judge's element array anchor was not found")
extra = ('                // the second pass, done again from each skill OWN ability subtree instead of a byte window\n'
         '                {"1407", "10", "QUANTUM"},\n'
         '                {"1407", "11", "QUANTUM"},\n'
         '                {"1407", "12", "QUANTUM"},\n'
         '                {"1409", "1", "WIND"},\n')
io.open(JUDGE, "w", encoding="utf-8", newline="\n").write(judge.replace(anchor, anchor + extra))
print("ok   skills.json and the judge both carry the four")
