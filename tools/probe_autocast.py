"""Who really states 「自动施放」, and is it already written? (round 3 of the goal)

Measured so far: `SkillType` has no eleventh slot, 1408's page has no 「弑神登神」 at all, and her file never mentions it -- so that §3
row's premise needs checking against the data rather than assumed. Meanwhile 「自动施放」 is a family with six pages.
"""
import io
import os
import re

out = []
REPO = "src/main/resources/characters"
corpus = "E:/turnbasedgamedata/aluminium_texts"

# (a) does ANY corpus page or repo file mention 弑神登神?
where = []
for name in sorted(os.listdir(corpus)):
    if name.endswith((".md", ".html")):
        body = io.open(os.path.join(corpus, name), encoding="utf-8", errors="replace").read()
        if "弑神登神" in body:
            where.append("corpus:" + name)
for name in sorted(os.listdir(REPO)):
    body = io.open(os.path.join(REPO, name), encoding="utf-8").read()
    if "弑神登神" in body:
        where.append("repo:" + name)
for extra in ("GAPS.md", "EXPRESSION.md"):
    if "弑神登神" in io.open(extra, encoding="utf-8").read():
        where.append(extra)
out.append("=== 弑神登神 appears in: %s" % (where or "(nowhere)"))

# (b) the six pages' sentences, and whether the repo already writes that clause
pages = ["1402_阿格莱雅.md", "1404_万敌.md", "1409_风堇.md", "1415_昔涟.md", "1509_吉尔伽美什.md", "8007_开拓者.md"]
out.append("")
for name in pages:
    path = os.path.join(corpus, name)
    if not os.path.isfile(path):
        out.append("[%s] (no such page)" % name)
        continue
    flat = re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", io.open(path, encoding="utf-8", errors="replace").read()))
    for match in list(re.finditer("自动施放", flat))[:2]:
        out.append("[%s] ...%s..." % (name, flat[max(0, match.start() - 150):match.start() + 190]))

# (c) what the repo does with CAST_SKILL today
out.append("")
out.append("=== CAST_SKILL in content ===")
for name in sorted(os.listdir(REPO)):
    blob = io.open(os.path.join(REPO, name), encoding="utf-8").read()
    for match in re.finditer("CAST_SKILL", blob):
        snippet = re.sub(r"\s+", " ", blob[max(0, match.start() - 120):match.start() + 160])
        out.append("   %s: ...%s..." % (name, snippet))
        break

io.open("tools/_probe_autocast.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written")
