"""Probe (round 1653, part 2): the exact effects of the rules that grant 【好活当赏】.

The corpus says it is a STATE that carries a 笑点 count and lasts 2 turns, so the question is what our rules actually do:
which of them APPLY_BUFF (state) and which GAIN_RESOURCE (resource), and whether a duration is stated.
"""
import io
import json

TARGETS = [("1513", "elation_moment_reward"), ("1505", "p1505_technique_gift"),
           ("1505", "p1505_energy_sync"), ("8009", "ult_elation_gift_and_cast"),
           ("8010", "ult_elation_gift_and_cast")]
out = []
for cid, rule_id in TARGETS:
    doc = json.load(io.open("src/main/resources/characters/%s.json" % cid, encoding="utf-8"))
    rules = doc.get("rules") if isinstance(doc, dict) else doc
    for rule in rules:
        if isinstance(rule, dict) and rule.get("id") == rule_id:
            out.append("== %s / %s on=%s" % (cid, rule_id, rule.get("on")))
            out.append("   when: %s" % json.dumps(rule.get("when"), ensure_ascii=False))
            out.append("   do:   %s" % json.dumps(rule.get("do"), ensure_ascii=False, indent=1))
            out.append("   note: %s" % (rule.get("note") or "")[:400])
io.open("tools/_tmp_grants.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written", len(out), "lines")
