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
    "note": ("「**该效果对迷迷也生效。**」—— ⭐ 它是**独立一句**，"
             "所以也是**独立一条规则**：「对迷迷」只在**持有者身边确实有忆灵**时才成立，"
             "而检查它的就是 `self_summon_count >= 1`（引擎在目标落空时**自己报出这个门控的名字**）。"),
}
rules.insert(rules.index(rule) + 1, split)
if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(P, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   split into two rules: %d self effects, %d summon effects" % (len(self_effects), len(summon_effects)))
