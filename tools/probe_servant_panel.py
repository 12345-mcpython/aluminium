"""The avatar configs carry `Speed` / `MaxHP` keys -- do they hold the SERVANT's panel? (round 1671)

The validator demands HEALTH and SPEED for a summon; the documents give only HEALTH for 小伊卡 and 德谬歌. Before concluding
"the data does not have it", this prints every Speed/MaxHP occurrence with its surrounding JSON in the two avatars' configs.
"""
import io
import os
import re

ROOT = "E:/turnbasedgamedata/Config/ConfigAbility/Avatar"
out = []

for name in ("Avatar_Hyacine_00_Ability.json", "Avatar_Cyrene_00_Ability.json"):
    path = os.path.join(ROOT, name)
    out.append("=== %s ===" % name)
    if not os.path.exists(path):
        out.append("  (missing)")
        continue
    body = io.open(path, encoding="utf-8", errors="replace").read()
    out.append("  %d chars" % len(body))
    for key in ('"Speed"', '"MaxHP"', '"HP"', '"Attack"', '"SPD"'):
        hits = [m.start() for m in re.finditer(re.escape(key), body)]
        out.append("  %s hits=%d" % (key, len(hits)))
        for start in hits[:3]:
            snippet = re.sub(r"\s+", " ", body[max(0, start - 260):start + 320])
            out.append("      ...%s..." % snippet[:520])
io.open("tools/_tmp_servant_panel.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written", len(out), "lines")
