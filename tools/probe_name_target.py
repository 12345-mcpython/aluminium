"""Two measurements for objective ①-b's last piece (round 1675).

1. The whole target vocabulary: does anything already name a SPECIFIC character (by id or by name) from another character's
   rule? The clause is 「奇袭结束后，使**刻律德菈**获得 1 点充能」 -- 1415's rule must reach 1412, another party member.
2. How many documents say 「使<某角色名>获得…」? A capability needs readers, so the count decides whether this is worth
   building at all.
"""
import io
import os
import re

out = []

# 1. the target vocabulary, in full
interp = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
body = io.open(interp, encoding="utf-8").read()
for match in re.finditer(r"Set<String> TARGETS?\w*\s*=\s*Set\.of\((.*?)\);", body, re.S):
    out.append("=== a target set ===")
    out.append("  " + re.sub(r"\s+", " ", match.group(1))[:900])
for keyword in ("character:", "ally_with", "has_state", "by_id", "the_duke"):
    out.append("  keyword %-12s in the interpreter: %s" % (keyword, keyword in body))

# 2. readers: "使<name>获得"
corpus = "E:/turnbasedgamedata/aluminium_texts"
NAMES = ["刻律德菈", "托帕", "景元", "灵砂", "昔涟", "风堇",
         "阿格莱雅", "遐蝶", "长夜月", "知更鸟"]
counts = {}
examples = []
if os.path.isdir(corpus):
    for name in sorted(os.listdir(corpus)):
        if not name.endswith((".md", ".html")):
            continue
        text = io.open(os.path.join(corpus, name), encoding="utf-8", errors="replace").read()
        text = re.sub(r"<[^>]+>", " ", text)
        text = re.sub(r"\s+", " ", text)
        for character in NAMES:
            for pattern in ("使" + character + "获得", character + "获得"):
                hits = text.count(pattern)
                if hits:
                    counts[pattern] = counts.get(pattern, 0) + hits
                    if len(examples) < 6:
                        index = text.find(pattern)
                        examples.append("%-24s [%s] ...%s..." % (pattern, name, text[max(0, index - 90):index + 110]))
out.append("")
out.append("=== 「使<角色>获得」 counts ===")
for pattern, count in sorted(counts.items(), key=lambda item: -item[1]):
    out.append("  %-28s %d" % (pattern, count))
out.extend(examples)

io.open("tools/_tmp_name_target.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written", len(out), "lines")
