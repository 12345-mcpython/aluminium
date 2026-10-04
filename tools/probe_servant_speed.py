"""Where does a servant's actual speed live? (round 1672)

`AvatarServantConfig.json` says HPInherit/SpeedInherit (shares) and HPBase/SpeedBase (parameters of a named skill). For 11409 and
11415 BOTH speed fields are "0", so the number must come from the servant's own character config -- the `Config` field of the
same table row points at it. This follows that pointer and dumps any stat-looking keys.
"""
import io
import json
import os
import re

ROOT = "E:/turnbasedgamedata"
table = json.load(io.open(os.path.join(ROOT, "ExcelOutput/AvatarServantConfig.json"), encoding="utf-8"))
rows = table if isinstance(table, list) else list(table.values())
out = []

for row in rows:
    if not isinstance(row, dict) or row.get("ServantID") not in (11409, 11415, 11413):
        continue
    config = row.get("Config")
    out.append("=== %s -> %s" % (row.get("ServantID"), config))
    if not config:
        continue
    path = os.path.join(ROOT, config)
    if not os.path.exists(path):
        out.append("  (missing on disk)")
        continue
    body = io.open(path, encoding="utf-8", errors="replace").read()
    out.append("  %d chars" % len(body))
    for key in ("HPBase", "SpeedBase", "Speed", "MaxHP", "BaseValue", "Attack", "Aggro", "InitSpeed"):
        for match in list(re.finditer(r'"%s"' % key, body))[:2]:
            snippet = re.sub(r"\s+", " ", body[max(0, match.start() - 120):match.start() + 220])
            out.append("  [%s] ...%s..." % (key, snippet[:300]))

    # and the skill the table points at, if any
    for skill_key in ("HPSkill", "SpeedSkill"):
        skill = row.get(skill_key)
        if isinstance(skill, int):
            out.append("  %s=%d (its parameter row is in the character's page)" % (skill_key, skill))

io.open("tools/_tmp_servant_speed.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written", len(out), "lines")
