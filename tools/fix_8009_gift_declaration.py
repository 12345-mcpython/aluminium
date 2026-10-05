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

GIFT = "好活当赏"

for cid in ("8009", "8010"):
    path = "src/main/resources/characters/%s.json" % cid
    doc = json.load(io.open(path, encoding="utf-8"))
    resources = doc.setdefault("resources", [])
    if not any(r.get("id") == GIFT for r in resources):
        resources.append({
            "id": GIFT,
            "max": 2147483647,
            "initial": 0,
            "source": ("%s 终结技：「若目标拥有欢愉技，目标额外获得 **10** 点【好活当赏】」。"
                       "⚠ 装载器**要求本文件声明它自己规则用到的每一个资源** ✓（与 `1505` 同名同声明 ✓）；"
                       "数据里没有上限 ⇒ `Integer.MAX_VALUE` ✓" % cid),
        })
        json.dump(doc, io.open(path, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
        print("ok   %s.json declares %s" % (cid, GIFT))
    else:
        print("skip %s.json: already declares it" % cid)

test = io.open("src/test/java/com/laosun/aluminium/test/SiblingElationTest.java", encoding="utf-8").read()
for line in test.split("\n"):
    if "TWO clauses" in line or "two clauses" in line or "assert" in line and "ult" in line.lower():
        print("PIN> " + line.strip()[:160])
