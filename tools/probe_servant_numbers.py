"""What do the data's own servant files say about 11409 / 11415? (round 1669)

The loader wants a SPEED the documents do not state for 小伊卡 or 德谬歌. The config has `ServantEffect_11409.json` and
`ServantEffect_11415.json`, plus the avatars' ability configs that name those ServantIDs -- one of them should carry the
panel numbers, and a measured number beats an invented one.
"""
import io
import json
import os
import re

ROOT = "E:/turnbasedgamedata"
out = []

for servant_id in ("11409", "11415"):
    path = os.path.join(ROOT, "Config/AssetPreload/ServantEffect", "ServantEffect_%s.json" % servant_id)
    out.append("=== %s ===" % os.path.basename(path))
    if not os.path.exists(path):
        out.append("  (missing)")
        continue
    body = io.open(path, encoding="utf-8", errors="replace").read()
    out.append("  %d chars" % len(body))
    out.append("  head: %s" % body[:900].replace("\n", " "))

# the avatar ability configs that state those ServantIDs: look for a radius of lines around the id
for name in ("Avatar_Hyacine_00_Ability.json", "Avatar_Cyrene_00_Ability.json"):
    path = os.path.join(ROOT, "Config/ConfigAbility/Avatar", name)
    out.append("")
    out.append("=== %s ===" % name)
    if not os.path.exists(path):
        out.append("  (missing)")
        continue
    body = io.open(path, encoding="utf-8", errors="replace").read()
    for match in list(re.finditer(r"ServantI[dD]", body))[:3]:
        out.append("  ...%s..." % body[max(0, match.start() - 500):match.start() + 700].replace("\n", " ")[:1200])

# any config that pairs a servant id with HP/SPD numbers
out.append("")
out.append("=== config files mentioning both 11409/11415 and a speed-ish key ===")
for base, dirs, files in os.walk(os.path.join(ROOT, "Config")):
    dirs[:] = [d for d in dirs if d != ".git"]
    for name in files:
        if not name.endswith(".json") or "layout" in name:
            continue
        path = os.path.join(base, name)
        try:
            if os.path.getsize(path) > 6_000_000:
                continue
            body = io.open(path, encoding="utf-8", errors="replace").read()
        except OSError:
            continue
        if ("11409" in body or "11415" in body):
            keys = [key for key in ("SPD", "Speed", "HP", "MaxHP", "Attack") if '"%s"' % key in body]
            out.append("  %-70s keys=%s" % (os.path.relpath(path, ROOT), ",".join(keys[:6])))

io.open("tools/_tmp_servant_numbers.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written", len(out), "lines")
