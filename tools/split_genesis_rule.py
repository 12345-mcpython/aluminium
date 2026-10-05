import io, json, sys
P = "src/main/resources/characters/8007.json"
doc = json.load(io.open(P, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
rid = "memosprite_ode_of_genesis_raises_his_attack_and_crit"
target = [r for r in rules if r.get("id") == rid]
if len(target) != 1:
    sys.exit("REFUSING: found %d rules with that id" % len(target))
rule = target[0]
self_effects = [e for e in rule["do"] if e.get("target") == "self"]
summon_effects = [e for e in rule["do"] if e.get("target") == "summon"]
if len(self_effects) != 2 or len(summon_effects) != 2:
    sys.exit("REFUSING: expected two of each, got %d and %d" % (len(self_effects), len(summon_effects)))

rule["do"] = self_effects
split = {
    "id": "memosprite_ode_of_genesis_also_reaches_his_memosprite",
    "on": "CAST_SETUP",
    # the engine names the gate itself when `target: "summon"` has nothing to aim at
    "when": list(rule["when"]) + ["self_summon_count >= 1"],
    "do": summon_effects,
    "source": rule["source"],
    "note": ("\u300c**\u8be5\u6548\u679c\u5bf9\u8ff7\u8ff7\u4e5f\u751f\u6548\u3002**\u300d\u2014\u2014 \u2b50 \u5b83\u662f**\u72ec\u7acb\u4e00\u53e5**\uff0c"
             "\u6240\u4ee5\u4e5f\u662f**\u72ec\u7acb\u4e00\u6761\u89c4\u5219**\uff1a\u300c\u5bf9\u8ff7\u8ff7\u300d\u53ea\u5728**\u6301\u6709\u8005\u8eab\u8fb9\u786e\u5b9e\u6709\u5fc6\u7075**\u65f6\u624d\u6210\u7acb\uff0c"
             "\u800c\u68c0\u67e5\u5b83\u7684\u5c31\u662f `self_summon_count >= 1`\uff08\u5f15\u64ce\u5728\u76ee\u6807\u843d\u7a7a\u65f6**\u81ea\u5df1\u62a5\u51fa\u8fd9\u4e2a\u95e8\u63a7\u7684\u540d\u5b57**\uff09\u3002"),
}
rules.insert(rules.index(rule) + 1, split)
if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(P, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   split into two rules: %d self effects, %d summon effects" % (len(self_effects), len(summon_effects)))
