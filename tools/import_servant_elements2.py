"""Finish the element import for the three servants whose ability file was unpaired (2026-10-02).

Why the earlier pass missed them, measured: the ability files are named after the SUMMONER, not the memosprite --
`Servant_AglaeaServant_00` is 阿格莱亚's (1402), `CastoriceServant` is 遐蝶's (1407), `CyreneServant` is 昔涟's (1415), `HyacineServant` is
风堇's (1409), `EvernightServant` is 长夜月's (1413), `RobinSServant` is 知更鸟's (1512), `PlayerBoyServant` is the Trailblazer's (8007).
So matching the memosprite's own name (`Demiurge`, `Little Ica`, ...) finds nothing, which is what happened.

The pairing is therefore done through the MASTER's English name, which is data:
  servant id  = "1" + master cid,  master cid -> its English name (from our own character data) -> the ability file that starts with it.

Only the DOMINANT element of the ability that the skill's own `SkillTriggerKey` names is written, and only when that window holds exactly
one element; anything else stays unset and recorded.
"""
import collections
import glob
import io
import json
import os
import re
import sys

TB = "E:/turnbasedgamedata"
ABIL = TB + "/Config/ConfigAbility/Servant"
OURS = "src/main/resources/data/skills.json"
DAMAGING = {"SingleAttack", "AoEAttack", "Blast", "Bounce", "MazeAttack"}
ELEMENTS = ("Ice", "Fire", "Wind", "Thunder", "Lightning", "Quantum", "Imaginary", "Physical")
UNPAIRED = {11409: 1409, 11413: 1413, 11415: 1415}

# our own character data carries the English name of every character
chars = json.load(io.open("src/main/resources/data/character_data.json", encoding="utf-8"))
text_en = json.load(io.open(TB + "/TextMap/TextMapEN.json", encoding="utf-8"))


def english_name(cid):
    row = chars.get(str(cid)) or {}
    for key in ("name", "Name", "name_en"):
        v = row.get(key)
        if isinstance(v, dict):
            for inner in ("english", "en", "English"):
                if v.get(inner):
                    return v[inner]
        if isinstance(v, str) and v:
            return v
    h = (row.get("name_hash") or row.get("NameHash") or {})
    if isinstance(h, dict) and h.get("Hash") is not None:
        return text_en.get(str(h["Hash"]))
    return None


files = [f for f in os.listdir(ABIL) if f.endswith(".json") and "layout" not in f]
rows = json.load(io.open(TB + "/ExcelOutput/AvatarServantSkillConfig.json", encoding="utf-8"))
table = json.load(io.open(OURS, encoding="utf-8"))

report = []
for servant, master in sorted(UNPAIRED.items()):
    name = english_name(master)
    hit = [f for f in files if name and name.split()[0].lower() in f.lower()]
    report.append("servant %d master %d (%s) -> %s" % (servant, master, name, hit or "NONE"))
    if len(hit) != 1:
        continue
    raw = io.open(os.path.join(ABIL, hit[0]), encoding="utf-8").read()
    mine = [r for r in rows if r["SkillID"] // 100 == servant and r.get("Level") == 1]
    for r in sorted(mine, key=lambda x: x["SkillID"]):
        slot = r["SkillID"] % 100
        if r.get("SkillEffect") not in DAMAGING:
            continue
        trig = r.get("SkillTriggerKey") or "@@"
        hits = collections.Counter()
        for m in re.finditer(re.escape(trig), raw):
            window = raw[max(0, m.start() - 2500): m.start() + 2500]
            for e in re.findall(r'"DamageType"\s*:\s*"?(%s)"?' % "|".join(ELEMENTS), window):
                hits[e] += 1
        row = table.get(str(servant), {}).get(str(slot))
        if row is None:
            report.append("   slot %d -> NO ROW" % slot)
        elif len(hits) == 1:
            row["element"] = hits.most_common(1)[0][0]
            report.append("   slot %d (%s) -> %s (set)" % (slot, r.get("SkillEffect"), row["element"]))
        else:
            report.append("   slot %d (%s) -> %s (LEFT ALONE)" % (slot, r.get("SkillEffect"), hits.most_common(3) or "none"))

io.open(OURS, "w", encoding="utf-8", newline="\n").write(json.dumps(table, ensure_ascii=False, indent=2) + "\n")
io.open("tools/_elems2.txt", "w", encoding="utf-8", newline="\n").write("\n".join(report))
print("\n".join(l for l in report if l.startswith("servant")))
