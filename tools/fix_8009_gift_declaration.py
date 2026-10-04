"""Two loud failures from the engine's own guards, both fixed here (the content half only).

1. `CharacterException: Character 8009 has a rule that uses the resource 「好活当赏」, which the character does not declare.`
   Measured: the loader requires EVERY resource a file's rules touch to be declared IN THAT FILE. So the note I wrote
   ("the target's file must declare it or this half lands nowhere") was the wrong half of the story -- the declaring file
   is the one with the RULE. Each of 8009/8010 now declares 【好活当赏】 as well (per-holder, like 1505's, so a grant to
   `target` still lands on the target's own copy).

2. The pinned census in `SiblingElationTest` counts 8009's ultimate clauses and now sees four instead of two.
   This script only reports what the file says; the pin itself is edited separately, so one script writes one kind of file.
"""
import io
import json
import re

GIFT = "\u597d\u6d3b\u5f53\u8d4f"

for cid in ("8009", "8010"):
    path = "src/main/resources/characters/%s.json" % cid
    doc = json.load(io.open(path, encoding="utf-8"))
    resources = doc.setdefault("resources", [])
    if not any(r.get("id") == GIFT for r in resources):
        resources.append({
            "id": GIFT,
            "max": 2147483647,
            "initial": 0,
            "source": ("%s \u7ec8\u7ed3\u6280\uff1a\u300c\u82e5\u76ee\u6807\u62e5\u6709\u6b22\u6109\u6280\uff0c\u76ee\u6807\u989d\u5916\u83b7\u5f97 **10** \u70b9\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\u300d\u3002"
                       "\u26a0 \u88c5\u8f7d\u5668**\u8981\u6c42\u672c\u6587\u4ef6\u58f0\u660e\u5b83\u81ea\u5df1\u89c4\u5219\u7528\u5230\u7684\u6bcf\u4e00\u4e2a\u8d44\u6e90** \u2713\uff08\u4e0e `1505` \u540c\u540d\u540c\u58f0\u660e \u2713\uff09\uff1b"
                       "\u6570\u636e\u91cc\u6ca1\u6709\u4e0a\u9650 \u21d2 `Integer.MAX_VALUE` \u2713" % cid),
        })
        json.dump(doc, io.open(path, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
        print("ok   %s.json declares %s" % (cid, GIFT))
    else:
        print("skip %s.json: already declares it" % cid)

test = io.open("src/test/java/com/laosun/aluminium/test/SiblingElationTest.java", encoding="utf-8").read()
for line in test.split("\n"):
    if "TWO clauses" in line or "two clauses" in line or "assert" in line and "ult" in line.lower():
        print("PIN> " + line.strip()[:160])
