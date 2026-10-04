"""Find the servant's panel numbers in the game config (round 1669).

Two angles: file names that mention servant/summon, and any ability config mentioning 1112 (her talent summons 账账, so its
parameters -- including whatever panel the game gives the servant -- should be in there).
"""
import io
import os

ROOT = "E:/turnbasedgamedata"
out = []

names = []
with1112 = []
for base, dirs, files in os.walk(ROOT):
    if ".git" in base:
        continue
    for name in files:
        lower = name.lower()
        path = os.path.join(base, name)
        if any(word in lower for word in ("servant", "summon")):
            names.append(os.path.relpath(path, ROOT))
        if "1112" in name and lower.endswith((".json", ".txt")):
            with1112.append(os.path.relpath(path, ROOT))

out.append("=== file names mentioning servant/summon (%d) ===" % len(names))
out.extend("  " + item for item in names[:30])
out.append("")
out.append("=== files named with 1112 (%d) ===" % len(with1112))
out.extend("  " + item for item in with1112[:30])

# the ability config directory: what is in it, and does anything mention 账账's talent id?
for sub in ("Config/ConfigAbility", "Config/ConfigCharacter"):
    path = os.path.join(ROOT, sub)
    if os.path.isdir(path):
        children = sorted(os.listdir(path))
        out.append("")
        out.append("=== %s (%d entries) ===" % (sub, len(children)))
        out.extend("  " + child for child in children[:25])

io.open("tools/_tmp_servant_tables.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written", len(out), "lines")
