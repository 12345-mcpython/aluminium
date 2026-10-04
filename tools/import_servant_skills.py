"""Import every memosprite's skills from the authoritative tables (2026-10-02).

Source (tbgd):
  * `ExcelOutput/AvatarServantConfig.json`   -- the servant roster: ServantID 11402 / 11407 / 11409 / 11413 / 11415 / 11512 / 18007
  * `ExcelOutput/AvatarServantSkillConfig.json` -- 480 rows; one row per (SkillID, Level). SkillID = ServantID * 100 + slot,
    and `SkillTriggerKey` is the game's own name for it (`Skill01`, `SkillCY04`, ...), `MaxLevel` its cap.

Measured mapping to our files: a servant's id is "1" + the master's cid (11402→1402, 18007→8007, 11512→1512). Our `8008` has NO
servant row at all, so it keeps no skills and that fact is recorded instead of invented.

⚠ THE CORRECTION THIS ROUND MAKES: the checklist named 「忆灵技能 8」 for 1415 by its PLAYER-FACING number, but the data slot is 16 --
`SkillID 1141516` / `SkillTriggerKey SkillCY04` carries `ExtraEffectIDList [10000001, 10000011]`, exactly the two effect ids the item
named. `slot: 8` and `skill_effects.json`'s `"8"` were both wrong, and the judge passed only because both sides agreed on the same
wrong number.
"""
import io
import json
import os

TBGD = "E:/turnbasedgamedata/ExcelOutput"
OURS = "src/main/resources/memosprites"
SERVANT_FOR = {cid: int("1" + str(cid)) for cid in (1402, 1407, 1409, 1413, 1415, 1512, 8007)}

roster = {row["ServantID"] for row in json.load(io.open(TBGD + "/AvatarServantConfig.json", encoding="utf-8"))}
rows = json.load(io.open(TBGD + "/AvatarServantSkillConfig.json", encoding="utf-8"))

by_servant = {}
for row in rows:
    sid = row["SkillID"]
    servant, slot = sid // 100, sid % 100
    entry = by_servant.setdefault(servant, {})
    level = max(entry.get(slot, {}).get("level", 0), row.get("MaxLevel") or 1)
    entry[slot] = {"level": level, "trigger": row.get("SkillTriggerKey")}

written = []
for cid, servant in sorted(SERVANT_FOR.items()):
    path = "%s/%d.json" % (OURS, cid)
    if not os.path.exists(path):
        written.append("%d: NO FILE" % cid)
        continue
    if servant not in roster:
        written.append("%d: servant %d not in the roster" % (cid, servant))
        continue
    spec = json.load(io.open(path, encoding="utf-8"))
    slots = by_servant.get(servant, {})
    spec["servant_id"] = servant
    spec["skills"] = [{"slot": slot, "level": slots[slot]["level"]} for slot in sorted(slots)]
    spec["note"] = (spec.get("note", "") +
                    " || 2026-10-02: servant_id = %d and all %d of its skills (data slots %s), imported from "
                    "ExcelOutput/AvatarServantSkillConfig.json. A memosprite's skills are ordinary skills: the engine addresses "
                    "them by (cid, slot) with cid = the ServantID." % (
                        servant, len(slots), sorted(slots)))
    io.open(path, "w", encoding="utf-8", newline="\n").write(json.dumps(spec, ensure_ascii=False, indent=2) + "\n")
    written.append("%d -> servant %d, %d skills %s" % (cid, servant, len(slots), sorted(slots)))

print("\n".join(written))
print("memosprites without a servant row: %s" % sorted(
    f[:-5] for f in os.listdir(OURS) if f.endswith(".json") and int(f[:-5]) not in SERVANT_FOR))

# the delivery entry must name the DATA slot the table uses, not the player-facing number
SE = "src/main/resources/data/skill_effects.json"
table = json.load(io.open(SE, encoding="utf-8"))
entry = table.pop("11415", {}).pop("8", None)
if entry is None:
    entry = {"effect": "Rules",
             "source": "1415 Cyrene's memosprite Demiurge, skill 16 in the data (SkillID 1141516, SkillTriggerKey SkillCY04; the "
                       "checklist calls it 忆灵技能 8): ExtraEffectIDList [10000001, 10000011]. Its work is on the rule table, so the "
                       "Rules shape is what makes it deliverable.",
             "note": "Keyed by the SERVANT's cid and the DATA slot: SkillEffects.forSkill keys on the skill's own cid and slot, and "
                     "the data slot of 忆灵技能 8 is 16, not 8."}
table.setdefault("11415", {})["16"] = entry
io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(table, ensure_ascii=False, indent=2) + "\n")
print("skill_effects.json: 11415 slot 16 = %s" % entry["effect"])
