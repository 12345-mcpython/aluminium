"""Where would a servant's panel live in the game data? (round 1669)

Lists the data tree one and two levels deep, sizes included, skipping the huge TextMap files, and flags any name that looks
like a servant/summon/monster table.
"""
import io
import os

ROOT = "E:/turnbasedgamedata"
out = []
if not os.path.isdir(ROOT):
    raise SystemExit("no data root")

INTERESTING = ("servant", "summon", "monster", "npc", "unit", "avatar", "ability", "excel")

for entry in sorted(os.listdir(ROOT)):
    path = os.path.join(ROOT, entry)
    if os.path.isfile(path):
        out.append("FILE %-44s %8.1f MB" % (entry, os.path.getsize(path) / 1e6))
        continue
    out.append("DIR  %s" % entry)
    try:
        children = sorted(os.listdir(path))
    except OSError:
        continue
    for child in children[:40]:
        child_path = os.path.join(path, child)
        if os.path.isdir(child_path):
            out.append("   DIR  %s/" % child)
        else:
            mark = "  <== interesting" if any(word in child.lower() for word in INTERESTING) else ""
            out.append("   %-46s %8.2f MB%s" % (child, os.path.getsize(child_path) / 1e6, mark))
    if len(children) > 40:
        out.append("   ... and %d more" % (len(children) - 40))

io.open("tools/_tmp_data_tree.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written", len(out), "lines")
