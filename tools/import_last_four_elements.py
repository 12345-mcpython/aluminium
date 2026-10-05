"""The last four memosprite skill elements, read from the SKILL'S OWN ability subtree (2026-10-02).

The earlier pass read a byte window around the trigger key, which for these four held more than one element, so they were left unset rather
than guessed. The precise route: an ability is named after the skill's own `SkillTriggerKey` (`Servant_CastoriceServant_Skill21_Phase01`,
`Servant_HyacineServant_00_Skill01_Phase02`, `Servant_CyreneServant_00_SkillCY04`), so the element can be read from THAT node's subtree
instead of a window. Anything still ambiguous stays unset.
"""
import collections
import io
import json
import os
import re
import sys

TB = "E:/turnbasedgamedata"
ABIL = TB + "/Config/ConfigAbility/Servant"
OURS = "src/main/resources/data/skills.json"
ELEMENTS = ("Ice", "Fire", "Wind", "Thunder", "Lightning", "Quantum", "Imaginary", "Physical")
WANTED = {11407: ("Skill21", "Skill22", "Skill23"), 11409: ("Skill01",)}
FILES = {11407: "Servant_CastoriceServant_00_Ability.json", 11409: "Servant_HyacineServant_00_Ability.json"}

table = json.load(io.open(OURS, encoding="utf-8"))
report = []
for servant, keys in WANTED.items():
    d = json.load(io.open(os.path.join(ABIL, FILES[servant]), encoding="utf-8"))
    abilities = [a for a in (d.get("AbilityList") or []) if isinstance(a, dict)]
    for key in keys:
        nodes = [a for a in abilities if key in str(a.get("Name", ""))]
        hits = collections.Counter()
        for node in nodes:
            txt = json.dumps(node, ensure_ascii=False)
            for e in re.findall(r'"DamageType"\s*:\s*"?(%s)"?' % "|".join(ELEMENTS), txt):
                hits[e] += 1
        # the slot whose trigger key this is
        slots = [s for s in table.get(str(servant), {}) if True]
        target = None
        for slot in slots:
            row = table[str(servant)][slot]
            if row.get("skill_id", 0) % 100 == int(key[-2:]) or key[-2:] in str(row.get("skill_id")):
                target = slot
        report.append("%d %s -> nodes=%d %s" % (servant, key, len(nodes), hits.most_common(3) or "none"))

io.open("tools/_last4.txt", "w", encoding="utf-8", newline="\n").write("\n".join(report))
print("\n".join(report))
