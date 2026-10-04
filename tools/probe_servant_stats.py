"""Find a table that gives servant 11409 / 11415 actual stats (round 1672).

The ids look like the monster-id scheme, and the earlier sweep only listed which FILES mention them. This one looks for the
numbers: any occurrence of the id near a stat key (HP / Speed / Attack / MaxHP / BaseValue) anywhere in the data, skipping the
huge TextMaps.
"""
import io
import os
import re

ROOT = "E:/turnbasedgamedata"
SKIP_DIRS = {".git"}
IDS = ("11409", "11415")
STAT_KEYS = ("HP", "MaxHP", "Speed", "SPD", "Attack", "ATK", "BaseValue", "BaseHP", "BaseSpeed")

out = []
hits = 0
for base, dirs, files in os.walk(ROOT):
    dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
    for name in files:
        if not name.lower().endswith(".json"):
            continue
        if "TextMap" in name:
            continue
        path = os.path.join(base, name)
        try:
            if os.path.getsize(path) > 20_000_000:
                continue
            body = io.open(path, encoding="utf-8", errors="replace").read()
        except OSError:
            continue
        for identifier in IDS:
            if identifier not in body:
                continue
            for match in list(re.finditer(identifier, body))[:3]:
                window = body[max(0, match.start() - 400):match.start() + 400]
                keys = [key for key in STAT_KEYS if key in window]
                if not keys:
                    continue
                rel = os.path.relpath(path, ROOT)
                out.append("HIT %-66s id=%s keys=%s" % (rel, identifier, ",".join(sorted(set(keys))[:5])))
                out.append("     ...%s..." % re.sub(r"\s+", " ", window)[:520])
                hits += 1
            if hits >= 14:
                break
        if hits >= 14:
            break
    if hits >= 14:
        break

os.makedirs("tools", exist_ok=True)
io.open("tools/_tmp_servant_stats.txt", "w", encoding="utf-8", newline="\n").write(
    "hits %d\n\n%s" % (hits, "\n".join(out)))
print("written", len(out), "lines, hits", hits)
