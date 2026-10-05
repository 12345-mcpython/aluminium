import io, json, sys
P = "src/main/resources/characters/1404.json"
d = json.load(io.open(P, encoding="utf-8"))
rules = d if isinstance(d, list) else d.get("rules", [])
if not any(r.get("id") == "memosprite_ode_dispels_his_control_debuffs" for r in rules):
    bf = [w[len("self has_state "):] for r in rules if r.get("id") == "bloodfeud_godslayer_at_a_hundred_and_fifty"
          for w in r.get("when", []) if isinstance(w, str) and w.startswith("self has_state ")][0]
    rules.append({
        "id": "memosprite_ode_dispels_his_control_debuffs",
        "on": "CAST_SETUP",
        "when": ["target == self", "actor is_summon", "from_skill_id == 16"],
        "do": [{"op": "DISPEL", "kind": "control", "target": "self"}],
        "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 8 \u300c\u732e\u4e88\u300c\u7eb7\u4e89\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 16\uff0cSkillID 1141516\uff09\uff1a"
                   "\u300c\u5bf9\u4e07\u654c\u65bd\u653e\u65f6**\u89e3\u9664\u4e07\u654c\u9677\u5165\u7684\u6240\u6709\u63a7\u5236\u7c7b\u8d1f\u9762\u72b6\u6001**\u300d\u3002"
                   "\uff08\u672c\u6761\u4e0d\u5e26\u91cf\uff1a\u539f\u53e5\u7684\u300c**\u6240\u6709**\u300d\u4e0d\u8bf4\u6570\u91cf\uff0c\u800c `kind` \u5355\u72ec\u51fa\u73b0\u5373\u8868\u793a\u201c\u8be5\u7c7b\u5168\u90e8\u201d\u3002\uff09"),
        "note": ("\u2605 \u672c\u8f6e\u7ed9 `DISPEL` \u52a0\u4e86**\u7c7b\u522b\u8fc7\u6ee4**\uff1a\u5b83\u4e4b\u524d\u53ea\u80fd\u79fb\u9664\u6700\u8fd1\u7684 N \u4e2a\uff0c\u800c\u7c7b\u522b\u662f\u72b6\u6001\u81ea\u5df1\u7684\u5c5e\u6027"
                "`AbstractBuff.debuffClass()`\u3002\u8bcd\u6c47\u672c\u6765\u5c31\u6709\uff08\u6761\u4ef6 `debuff_class:control`\uff0c\u4ee5\u53ca `RESIST_DEBUFF` \u7684 `kind`\uff09\u3002"),
    })
if isinstance(d, list):
    doc = rules
else:
    d["rules"] = rules
    doc = d
io.open(P, "w", encoding="utf-8", newline="\n").write(json.dumps(doc, ensure_ascii=False, indent=2) + "\n")
print("ok   the dispel clause is on 1404's table")
