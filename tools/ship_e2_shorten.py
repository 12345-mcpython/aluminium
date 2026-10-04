import io, json, sys
P = "src/main/resources/characters/1217.json"
d = json.load(io.open(P, encoding="utf-8"))
rules = d if isinstance(d, list) else d.get("rules", [])
r = [x for x in rules if x.get("id") == "e2_saves_an_ally_with_the_talisman"][0]
# the counter cap: measured, a strict `<` at the counter's own max_stacks never holds; `<= 1` is the same statement
r["when"] = ["actor is_ally", "self has_state \u7a79\u547d", "self_stacks:\u6551\u4e3b\u8ba1\u6570 <= 1"]
ops = [e.get("op") for e in r["do"]]
if "EXTEND_BUFF" not in ops:
    r["do"].append({"op": "EXTEND_BUFF", "buff": "\u7a79\u547d", "turns": -1, "target": "self"})
r["note"] = (r.get("note", "") +
    " \u2b50 2026-10-02: \u8865\u4e0a\u7b2c\u4e09\u53e5\u300c\u4f7f\u3010\u7a79\u547d\u3011\u7684\u6301\u7eed\u56de\u5408\u6570**\u51cf 1**\u300d\u21d2 "
    "`EXTEND_BUFF{buff: \u7a79\u547d, turns: -1, target: self}`\u3002\u26a0 \u5f62\u72b6\u53d6\u81ea**\u6e38\u620f\u81ea\u5df1\u7684\u914d\u7f6e**\uff1a"
    "`Avatar_Huohuo_00_Rank02_Insert` \u5bf9\u5177\u540d modifier \u505a `SetModifierValue{ModifyFunction: \"Add\", ValueType: \"LifeTime\"}`"
    "\uff08\u53e6\u4e00\u6761\u540c\u65cf\u4efb\u52a1\u662f `SetDynamicValueByAddValue{AddValue: -1, Min: 0}`\uff09\u2014\u2014 \u5373\u201c\u5bf9\u5177\u540d\u72b6\u6001\u5bff\u547d\u505a**\u5e26\u7b26\u53f7\u7684 Add**\u201d\uff0c"
    "\u6240\u4ee5\u662f**\u8d1f\u7684 `turns`**\uff0c\u800c\u4e0d\u65b0\u589e op\u3002\u2b50 \u5e76\u628a\u8ba1\u6570\u5668\u4e0a\u9650\u6539\u5199\u4e3a `<= 1`\uff1a"
    "\u5b9e\u6d4b\uff0c**\u4e25\u683c `<` \u4e14\u9608\u503c\u7b49\u4e8e\u8be5\u8ba1\u6570\u5668\u81ea\u5df1\u7684 `max_stacks` \u65f6\u6c38\u4e0d\u6210\u7acb**\uff0c\u800c `< 100`\u3001`<= 100`\u3001`<= 1` \u5747\u6210\u7acb\uff1b"
    "\u5bf9\u4e00\u4e2a\u8ba1\u6570\u5668\u800c\u8a00 `<= 1` \u4e0e `< 2` \u662f\u540c\u4e00\u53e5\u8bdd\u3002")
io.open(P, "w", encoding="utf-8", newline="\n").write(json.dumps(d, ensure_ascii=False, indent=2) + "\n")
print("ok   content: <= 1 and the signed EXTEND_BUFF")
