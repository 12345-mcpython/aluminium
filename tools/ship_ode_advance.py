import io, json, sys
P = "src/main/resources/characters/1404.json"
d = json.load(io.open(P, encoding="utf-8"))
rules = d if isinstance(d, list) else d.get("rules", [])
bf = [w[len("self has_state "):] for r in rules if r.get("id") == "bloodfeud_godslayer_at_a_hundred_and_fifty"
      for w in r.get("when", []) if isinstance(w, str) and w.startswith("self has_state ")]
if len(bf) != 1:
    sys.exit("REFUSING: ambiguous state name")
bf = bf[0]

J = "src/test/java/com/laosun/aluminium/test/OdeToStrifeAdvanceTest.java"
j = io.open(J, encoding="utf-8").read().replace("@BLOODFEUD@", bf)
io.open(J, "w", encoding="utf-8", newline="\n").write(j)
print("ok   the judge uses the file's own state name")

if not any(r.get("id") == "memosprite_ode_advances_him_outside_bloodfeud" for r in rules):
    rules.append({
        "id": "memosprite_ode_advances_him_outside_bloodfeud",
        "on": "CAST_SETUP",
        "when": ["target == self", "actor is_summon", "from_skill_id == 16", "!self has_state " + bf],
        "do": [{"op": "ADVANCE", "percent": 1.0, "target": "self"}],
        "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 8 \u300c\u732e\u4e88\u300c\u7eb7\u4e89\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 16\uff0cSkillID 1141516\uff09\uff1a"
                   "\u300c\u82e5\u4e07\u654c**\u4e0d**\u5904\u4e8e\u3010\u8840\u4ec7\u3011\u72b6\u6001\uff0c\u5219\u4f7f\u4e07\u654c\u884c\u52a8\u63d0\u524d `#2[i]%`\u300d\u3002"
                   "\uff08`#2` \u5404\u7ea7\u6052\u4e3a 1\uff0c\u5373 **100%**\uff1b\u5f62\u72b6\u7167 1101\uff0f1212 \u7684 `ADVANCE{percent: 1.0}`\u3002\uff09"),
        "note": ("\u2605 \u5426\u5b9a\u7528\u7684\u662f**\u73b0\u6210\u7684** `!`\uff1a`HasState` \u672c\u5c31\u5b9e\u73b0\u4e86 `PartyCondition`\uff0c\u6240\u4ee5 `!self has_state <state>` "
                 "\u662f\u4e00\u4e2a\u666e\u901a\u7684\u5426\u5b9a\uff0c\u4e0d\u9700\u8981\u65b0\u8bcd\u6c47\u3002\uff08\u4e0a\u4e00\u8f6e\u6211\u636e\u6ce8\u91ca\u63a8\u65ad\u8bf4\u5199\u4e0d\u51fa\u6765\uff0c\u800c\u7c7b\u58f0\u660e\u624d\u662f\u5b9a\u8bba\u3002\uff09"),
    })
if isinstance(d, list):
    doc = rules
else:
    d["rules"] = rules
    doc = d
io.open(P, "w", encoding="utf-8", newline="\n").write(json.dumps(doc, ensure_ascii=False, indent=2) + "\n")
print("ok   the advance branch is on 1404's table")
