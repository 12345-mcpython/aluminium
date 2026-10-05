"""Which characters own the servant ids, and what panels do their documents state? (round 1669)

Measured: `ServantID` appears only in 3.x avatar ability configs, and `ServantEffect_114xx` names the servant range. Two of
those ids (11409, 11415) have no file under `resources/memosprites/`, so they are candidate readers for the servant path.
"""
import io
import os
import re

ROOT = "E:/turnbasedgamedata"
CORPUS = os.path.join(ROOT, "aluminium_texts")
out = []

# 1. every ServantID value and the avatar config that states it
for name in sorted(os.listdir(os.path.join(ROOT, "Config/ConfigAbility/Avatar"))):
    if not name.endswith(".json"):
        continue
    path = os.path.join(ROOT, "Config/ConfigAbility/Avatar", name)
    try:
        body = io.open(path, encoding="utf-8", errors="replace").read()
    except OSError:
        continue
    values = sorted(set(re.findall(r'"ServantI[dD]"\s*:\s*"?(\d+)"?', body)))
    if values:
        out.append("%-44s -> %s" % (name, ", ".join(values)))

# 2. which corpus page mentions each id
out.append("")
for servant_id in ("11409", "11415", "11402", "11407"):
    hits = []
    if os.path.isdir(CORPUS):
        for name in sorted(os.listdir(CORPUS)):
            if not name.endswith((".md", ".html")):
                continue
            body = io.open(os.path.join(CORPUS, name), encoding="utf-8", errors="replace").read()
            if servant_id in body:
                hits.append(name)
    out.append("  id %s -> %s" % (servant_id, ", ".join(hits[:6]) or "(no page)"))

# 3. what the pages say about the servant panels: look for 忆灵/侍从 lines with percentages
out.append("")
for cid in ("1409", "1415"):
    if not os.path.isdir(CORPUS):
        break
    for name in sorted(os.listdir(CORPUS)):
        if not name.startswith(cid) or not name.endswith((".md", ".html")):
            continue
        body = io.open(os.path.join(CORPUS, name), encoding="utf-8", errors="replace").read()
        text = re.sub(r"<[^>]+>", " ", body)
        text = re.sub(r"\s+", " ", text)
        out.append("=== %s ===" % name)
        for keyword in ("侍从", "召唤", "初始拥有"):
            for match in list(re.finditer(keyword, text))[:2]:
                out.append("  ...%s..." % text[max(0, match.start() - 160):match.start() + 300])

io.open("tools/_tmp_servant_owners.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written", len(out), "lines")
