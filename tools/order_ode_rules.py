import io, json
P = "src/main/resources/characters/1404.json"
d = json.load(io.open(P, encoding="utf-8"))
rules = d if isinstance(d, list) else d.get("rules", [])
crit = [x for x in rules if x.get("id") == "memosprite_ode_raises_his_crit_damage_for_this_cast"]
if len(crit) != 1:
    raise SystemExit("REFUSING: the crit rule is not unique (%d)" % len(crit))
crit = crit[0]
rules.remove(crit)
idx = min(i for i, r in enumerate(rules) if str(r.get("id", "")).startswith("memosprite_ode_"))
rules.insert(idx, crit)
crit["note"] = ("\u2605 \u300c\u672c\u6b21\u653b\u51fb\u4e2d\u300d\u662f\u4e00\u4e2a**\u5bff\u547d**\uff1a`MODIFY_ATTR` \u63a5\u53d7 `until`\uff0c\u5df2\u51fa\u8d27\u7684 **`cast_end`** \u6b63\u662f\u8fd9\u4e2a\u7a97\u53e3"
               "\uff08\u5149\u9525 20001 \u540c\u5f62\uff09\u3002\u26a0 \u672c\u6761**\u4e0d**\u53d7\u3010\u884c\u4ec7\u3011\u9650\u5236\uff08\u539f\u53e5\u5982\u6b64\uff09\u3002\n"
               "\u2b50\u2b50 **\u4f4d\u7f6e\u662f\u5fc5\u8981\u7684**\uff1a\u540c\u4e00\u4e8b\u4ef6\u4e0a\u7684\u89c4\u5219\u6309**\u6587\u4ef6\u987a\u5e8f\u6d3e\u53d1**\uff08`byEvent...add(rule)`\uff09\uff0c"
               "\u800c `CAST_SKILL` \u662f**\u540c\u6b65**\u6267\u884c\u7684 \u2014\u2014 \u82e5\u672c\u6761\u6392\u5728\u547d\u4ee4\u90a3\u6761**\u4e4b\u540e**\uff0c\u90a3\u4e00\u51fb\u5df2\u7ecf\u7ed3\u7b97\u5b8c\u4e86\uff0c"
               "\u589e\u76ca\u624d\u8d34\u4e0a\u53bb\uff1b\u5b9e\u6d4b\u5dee\u522b\u662f **4391.48 \u2192 9080.73**\u3002")
io.open(P, "w", encoding="utf-8", newline="\n").write(json.dumps(d if isinstance(d, dict) else rules, ensure_ascii=False, indent=2) + "\n")
print("order: %s" % [r.get("id") for r in rules if str(r.get("id", "")).startswith("memosprite_ode_")])
