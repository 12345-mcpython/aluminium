"""Import the 13 damaging memosprite skills' element (2026-10-02).

Where the element lives, measured:
  * NOT in `AvatarServantSkillConfig` -- that table has `SkillEffect` (the delivery shape) and `StanceDamageType` (a toughness type), but
    no damage element. A made-up default would settle 德谬歌's Ice damage as Physical, so it is not invented.
  * It IS in `Config/ConfigAbility/Servant/Servant_*_Ability.json`, as `AttackProperty/DamageType/DamageType`, inside the ability that
    belongs to that skill.
  * The ability files are named after the CHARACTER, not the ServantID, and the skill ids never appear in them. Two measured links make
    the pairing authoritative rather than guessed:
      - `AvatarServantConfig` lists the ServantIDs;
      - each skill row's `SkillIcon` is `SpriteOutput/SkillIcons/Avatar/<MASTER CID>/SkillIcon_<ServantID>_Servant.png`, which names the
        master directly;
      - inside a file, an ability is found by the skill's own `SkillTriggerKey` (`Skill01`, `SkillCY04`, ...), the same key the skill
        table states.
"""
import collections
import io
import json
import os
import re

TBGD = "E:/turnbasedgamedata"
SKILLS = TBGD + "/ExcelOutput/AvatarServantSkillConfig.json"
ABIL = TBGD + "/Config/ConfigAbility/Servant"
OURS = "src/main/resources/data/skills.json"
DAMAGING = {"SingleAttack", "AoEAttack", "Blast", "Bounce", "MazeAttack"}
ELEMENTS = ("Ice", "Fire", "Wind", "Thunder", "Lightning", "Quantum", "Imaginary", "Physical")

rows = json.load(io.open(SKILLS, encoding="utf-8"))

# ---- pair each servant with its ability file through the icon path (data, not a name guess) ----
servant_to_master = {}
servant_to_file = {}
for r in rows:
    sid = r["SkillID"]
    sv = sid // 100
    m = re.search(r"/Avatar/(\d+)/", r.get("SkillIcon") or "")
    if m:
        servant_to_master[sv] = int(m.group(1))
files = [f for f in os.listdir(ABIL) if f.endswith(".json") and "layout" not in f]
for sv in sorted(servant_to_master):
    want = str(sv)
    for f in files:
        raw = io.open(os.path.join(ABIL, f), encoding="utf-8").read()
        if want in raw:
            servant_to_file[sv] = f
            break

table = json.load(io.open(OURS, encoding="utf-8"))
report = []
for sv in sorted(servant_to_master):
    fname = servant_to_file.get(sv)
    if not fname:
        report.append("servant %d -> NO ABILITY FILE" % sv)
        continue
    raw = io.open(os.path.join(ABIL, fname), encoding="utf-8").read()
    mine = [r for r in rows if r["SkillID"] // 100 == sv and r.get("Level") == 1]
    for r in sorted(mine, key=lambda x: x["SkillID"]):
        slot = r["SkillID"] % 100
        if r.get("SkillEffect") not in DAMAGING:
            continue
        trig = r.get("SkillTriggerKey")
        hits = collections.Counter()
        for m in re.finditer(re.escape(trig or "@@"), raw):
            # the ability's own subtree: the nearest brace-delimited block around the trigger key
            start = raw.rfind("{", 0, m.start())
            window = raw[max(0, m.start() - 2500): m.start() + 2500]
            for e in re.findall(r'"DamageType"\s*:\s*"?(%s)"?' % "|".join(ELEMENTS), window):
                hits[e] += 1
        row = table.get(str(sv), {}).get(str(slot))
        if row is None:
            report.append("slot %-3d %-10s %-8s -> NO ROW" % (slot, r.get("SkillEffect"), trig))
            continue
        if len(hits) == 1:
            row["element"] = hits.most_common(1)[0][0]
            report.append("slot %-3d %-10s %-8s -> %s (set)" % (slot, r.get("SkillEffect"), trig, row["element"]))
        else:
            report.append("slot %-3d %-10s %-8s -> %s (LEFT ALONE)" % (slot, r.get("SkillEffect"), trig, hits.most_common(3) or "none"))
    report.append("  servant %d <- %s (master %d)" % (sv, fname, servant_to_master[sv]))

io.open(OURS, "w", encoding="utf-8", newline="\n").write(json.dumps(table, ensure_ascii=False, indent=2) + "\n")
print("\n".join(report))
