"""Measure the 1408 row: 「自动施放【弑神登神】」 (round 3 of the goal).

Three readings, no hand-typed codepoints this time -- the terms are literal UTF-8 in this file, which is what the write tool
writes, and the lesson from last round is that escapes typed from memory are the unreliable part.
"""
import io
import os
import re
import sys

out = []

# (a) the skill slots the engine has
path = "src/main/java/com/laosun/aluminium/enums/SkillType.java"
text = io.open(path, encoding="utf-8").read()
constants = re.findall(r"^\s*([A-Z][A-Z0-9_]*)\s*[(,;]", text, re.M)
out.append("=== SkillType constants ===")
out.append("   " + ", ".join(constants))

# (b) what her file says about it today
blob = io.open("src/main/resources/characters/1408.json", encoding="utf-8").read()
out.append("")
out.append("=== mentions in 1408.json ===")
for term in ("弑神登神", "自动施放", "登神"):
    out.append("   %s -> %d" % (term, blob.count(term)))
for match in re.finditer("弑神登神", blob):
    out.append("   ...%s..." % blob[max(0, match.start() - 160):match.start() + 200].replace("\n", " "))

# (c) the corpus pages for 1408, everything about that skill
corpus = "E:/turnbasedgamedata/aluminium_texts"
pages = [name for name in sorted(os.listdir(corpus)) if name.startswith("1408") and name.endswith(".md")]
out.append("")
out.append("=== corpus pages: %s" % pages)
for name in pages:
    body = io.open(os.path.join(corpus, name), encoding="utf-8", errors="replace").read()
    flat = re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", body))
    for match in list(re.finditer("弑神登神", flat))[:4]:
        out.append("   [%s] ...%s..." % (name, flat[max(0, match.start() - 220):match.start() + 260]))

# (d) how many pages state "自动施放" at all -- the reader count for such a family
auto = []
for name in sorted(os.listdir(corpus)):
    if not name.endswith(".md"):
        continue
    body = io.open(os.path.join(corpus, name), encoding="utf-8", errors="replace").read()
    if "自动施放" in body:
        auto.append(name)
out.append("")
out.append("=== pages stating 自动施放: %d ===" % len(auto))
out.extend("   %s" % name for name in auto[:20])

io.open("tools/_probe_1408_skill.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written")
