import io, json
P = "src/main/resources/characters/1404.json"
d = json.load(io.open(P, encoding="utf-8"))
rules = d if isinstance(d, list) else d.get("rules", [])
if not any(r.get("id") == "memosprite_ode_raises_his_crit_damage_for_this_cast" for r in rules):
    import re
    bf = [w[len("self has_state "):] for r in rules if r.get("id") == "bloodfeud_godslayer_at_a_hundred_and_fifty"
          for w in r.get("when", []) if isinstance(w, str) and w.startswith("self has_state ")][0]
    J = "src/test/java/com/laosun/aluminium/test/OdeToStrifeCritTest.java"
    j = io.open(J, encoding="utf-8").read().replace("@BLOODFEUD@", bf)
    io.open(J, "w", encoding="utf-8", newline="\n").write(j)
    print("ok   the judge uses the file's own state name")
    rules.append({
        "id": "memosprite_ode_raises_his_crit_damage_for_this_cast",
        "on": "CAST_SETUP",
        "when": ["target == self", "actor is_summon", "from_skill_id == 16"],
        "do": [{"op": "MODIFY_ATTR", "attribute": "CRIT_ATTACK", "percent": 2.0, "until": "cast_end", "target": "self"}],
        "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 8 \u300c\u732e\u4e88\u300c\u7eb7\u4e89\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 16\uff0cSkillID 1141516\uff09\uff1a"
                   "\u300c**\u672c\u6b21\u653b\u51fb\u4e2d**\u4e07\u654c\u7684\u66b4\u51fb\u4f24\u5bb3\u63d0\u9ad8 `#1[i]%`\u300d\u3002"
                   "\uff08\u6ee1\u7ea7 `#1` = 2\uff0c\u5373 **+200%**\uff1b\u5c5e\u6027\u540d `CRIT_ATTACK` \u2014\u2014 \u6e38\u620f\u7684 `CriticalDamageBase` \u5c31\u6620\u5c04\u5230\u5b83\u3002\uff09"),
        "note": ("\u2605 \u300c\u672c\u6b21\u653b\u51fb\u4e2d\u300d\u662f\u4e00\u4e2a**\u5bff\u547d**\uff0c\u800c\u5f15\u64ce\u5df2\u7ecf\u6709\u8fd9\u4e2a\u62fc\u6cd5\uff1a`MODIFY_ATTR` \u63a5\u53d7 `until`\uff0c"
                 "\u5df2\u51fa\u8d27\u7684\u53d6\u503c **`cast_end`** \u6b63\u662f\u8fd9\u4e2a\u7a97\u53e3\uff08\u5149\u9525 20001 \u7528\u5b83\u8868\u8fbe\u540c\u4e00\u5f62\u72b6\uff09\u3002"
                 "\u26a0 \u672c\u6761**\u4e0d**\u53d7\u3010\u8840\u4ec7\u3011\u9650\u5236\uff08\u539f\u53e5\u5982\u6b64\uff09\uff0c\u6240\u4ee5\u5b83\u81ea\u6210\u4e00\u6761\u3002"),
    })
io.open(P, "w", encoding="utf-8", newline="\n").write(json.dumps(d if isinstance(d, dict) else rules, ensure_ascii=False, indent=2) + "\n")
print("ok   the crit clause is on 1404's table")
