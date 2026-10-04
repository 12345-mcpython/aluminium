"""Find 账账's servant entry (round 1669).

Measured leads: `Config/SummonUnitGlobalConfig.json` (a global summon table) and `Config/AssetPreload/ServantEffect/ServantEffect_114xx.json`
-- so the servant id range is 114xx and there may be a table that gives each servant its panel.
"""
import io
import os
import re

ROOT = "E:/turnbasedgamedata"
out = []


def read(path, limit=None):
    with io.open(path, encoding="utf-8", errors="replace") as handle:
        return handle.read() if limit is None else handle.read(limit)


# 1. the servant effect ids (small files, their names give the range)
servant_dir = os.path.join(ROOT, "Config/AssetPreload/ServantEffect")
if os.path.isdir(servant_dir):
    out.append("=== ServantEffect ids ===")
    out.append("  " + ", ".join(sorted(os.listdir(servant_dir))))

# 2. the global summon table
global_path = os.path.join(ROOT, "Config/SummonUnitGlobalConfig.json")
if os.path.exists(global_path):
    body = read(global_path)
    out.append("")
    out.append("=== SummonUnitGlobalConfig.json (%d chars) ===" % len(body))
    out.append("  head: %s" % body[:600].replace("\n", " "))
    for keyword in ("1112", "Numby", "\u8d26\u8d26", "114"):
        hits = [m.start() for m in re.finditer(re.escape(keyword), body)]
        out.append("  %-8s hits=%d" % (keyword, len(hits)))
        for start in hits[:2]:
            out.append("      ...%s..." % body[max(0, start - 200):start + 260].replace("\n", " ")[:420])

# 3. any file under Config that mentions both the servant word and 1112
out.append("")
out.append("=== files mentioning ServantID (any) ===")
found = 0
for base, dirs, files in os.walk(os.path.join(ROOT, "Config")):
    dirs[:] = [d for d in dirs if d not in (".git",)]
    for name in files:
        if not name.endswith(".json"):
            continue
        path = os.path.join(base, name)
        try:
            if os.path.getsize(path) > 8_000_000:
                continue
            body = read(path)
        except OSError:
            continue
        if "ServantID" in body or "ServantId" in body:
            out.append("  %s" % os.path.relpath(path, ROOT))
            found += 1
            if found >= 20:
                break
    if found >= 20:
        break

io.open("tools/_tmp_servant_entry.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written", len(out), "lines")
