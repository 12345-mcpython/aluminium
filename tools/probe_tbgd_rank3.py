"""Probe v3 (tbgd, 2026-10-02): where do damage multipliers actually live, and is anything called a "correction"?

v2 settled the eidolon table itself: `AvatarRankConfig` has no damage field (only `Param` and `SkillAddLevelList`).
This probe closes the question from the other side:
  1. which config FILES are named like a damage/ratio/modify/correct table;
  2. what a skill's own config looks like -- multipliers per level, which is what `SkillAddLevelList` moves.

Read-only, ASCII only, output to a UTF-8 file.
"""
import io
import json
import os

ROOT = r"E:\turnbasedgamedata"
EXCEL = os.path.join(ROOT, "ExcelOutput")
lines = []


def emit(text):
    lines.append(text)


names = os.listdir(EXCEL) if os.path.isdir(EXCEL) else []
interest = [n for n in names
            if any(w in n.lower() for w in ("damage", "modify", "adjust", "correct", "ratio", "skillconfig", "ability"))]
emit("config files whose name mentions damage/modify/adjust/correct/ratio/skill/ability: %d" % len(interest))
for n in sorted(interest)[:25]:
    emit("   " + n)


def load(name):
    path = os.path.join(EXCEL, name)
    if not os.path.exists(path):
        return None
    try:
        return json.load(io.open(path, encoding="utf-8"))
    except Exception as exc:
        emit("FAILED to load %s: %s" % (name, exc))
        return None


def entries(doc):
    if isinstance(doc, list):
        for item in doc:
            if isinstance(item, dict):
                yield item
    elif isinstance(doc, dict):
        for item in doc.values():
            if isinstance(item, dict):
                yield item


# The skill table: multipliers per level is what an eidolon's SkillAddLevelList moves.
for cand in ("AvatarSkillConfig.json", "SkillConfig.json"):
    doc = load(cand)
    if doc is None:
        continue
    items = list(entries(doc))
    keys = set()
    for item in items:
        keys.update(item.keys())
    emit("== %s: %d entries" % (cand, len(items)))
    emit("   fields: %s" % json.dumps(sorted(keys), ensure_ascii=True))
    shown = 0
    for item in items:
        blob = json.dumps(item, ensure_ascii=True)
        if "100103" in blob and shown < 1:  # 1001's Skill (Rank 3 raises it by 2)
            emit("   100103 entry: %s" % blob[:800])
            shown += 1
    break

io.open("tools/_tmp_tbgd_rank3.txt", "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("written", len(lines), "lines")
