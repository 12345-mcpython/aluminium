"""Import the servants' skill ROWS into data/skills.json (2026-10-02).

Why: our `skills.json` has no key for a ServantID, so every memosprite skill resolved to the placeholder -- `isDamaging()` was false for
all 48 of them, which made all 48 undeliverable unless each had a `skill_effects.json` entry. `SkillData`'s own shape says how a row is
written, and the game's servant table states every field it needs except one:

    ours                    game (ExcelOutput/AvatarServantSkillConfig)
    attack_type             AttackType
    max_level               MaxLevel
    name{chinese,english}   SkillName.Hash  -> TextMap
    param_list              ParamList       (one row per Level, so they are grouped and ordered)
    skill_effect            SkillEffect     <-- what `isDamaging()` reads
    skill_id                SkillID
    skill_introduction      SkillDesc.Hash  -> TextMap
    stance_list             ShowStanceList  (single / all / spread)
    element                 *** NOT IN THIS TABLE ***  -> recorded, not invented

⚠ The element is deliberately left out: it is not in the skill table (only `StanceDamageType`, a toughness type), and a made-up default
would settle a memosprite's damage as Physical when the game says Ice. `Config/ConfigAbility/Servant/*` does carry `DamageType`, so that
is where it must come from -- a separate step, recorded rather than guessed.
"""
import io
import json
import collections

TBGD = "E:/turnbasedgamedata"
OURS = "src/main/resources/data/skills.json"
SERVANTS = [11402, 11407, 11409, 11413, 11415, 11512, 18007]

rows = json.load(io.open(TBGD + "/ExcelOutput/AvatarServantSkillConfig.json", encoding="utf-8"))
text_chs = json.load(io.open(TBGD + "/TextMap/TextMapCHS.json", encoding="utf-8"))
text_en = json.load(io.open(TBGD + "/TextMap/TextMapEN.json", encoding="utf-8"))


def text(store, field):
    if not isinstance(field, dict):
        return None
    h = field.get("Hash")
    return store.get(str(h)) if h is not None else None


by_skill = collections.defaultdict(lambda: {"levels": {}, "row": None})
for r in rows:
    sid = r["SkillID"]
    if sid // 100 not in SERVANTS:
        continue
    bucket = by_skill[sid]
    bucket["row"] = r                      # any row: the per-skill fields repeat across levels
    bucket["levels"][r.get("Level") or 1] = [p.get("Value") for p in (r.get("ParamList") or [])]

table = json.load(io.open(OURS, encoding="utf-8"))
effects = collections.Counter()
written = 0
for sid in sorted(by_skill):
    servant, slot = sid // 100, sid % 100
    bucket = by_skill[sid]
    r = bucket["row"]
    stance = [p.get("Value") for p in (r.get("ShowStanceList") or [])]
    stance_list = None
    if len(stance) >= 3:
        stance_list = {"single": stance[0], "all": stance[1], "spread": stance[2]}
    entry = {
        "attack_type": r.get("AttackType"),
        "max_level": r.get("MaxLevel"),
        "name": {"chinese": text(text_chs, r.get("SkillName")), "english": text(text_en, r.get("SkillName"))},
        "param_list": [bucket["levels"][lv] for lv in sorted(bucket["levels"])],
        "skill_effect": r.get("SkillEffect"),
        "skill_id": sid,
        "skill_introduction": {"chinese": text(text_chs, r.get("SkillDesc")), "english": text(text_en, r.get("SkillDesc"))},
        "stance_list": stance_list,
        "element": None,          # NOT in this table; see the module docstring
        "sp_base": None,
        "sp_need": None,
    }
    table.setdefault(str(servant), {})[str(slot)] = entry
    effects[r.get("SkillEffect")] += 1
    written += 1

io.open(OURS, "w", encoding="utf-8", newline="\n").write(json.dumps(table, ensure_ascii=False, indent=2) + "\n")
print("imported %d servant skill rows into %d servant keys" % (written, len(SERVANTS)))
print("skill_effect tally: %s" % effects.most_common())
print("cid keys now: %d" % len(table))
