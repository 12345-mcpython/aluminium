"""Evidence sweep before any completion claim (round 1689).

For each item the objective names, this prints what the TREE actually holds: the rule ids, the events they subscribe to, and
whether a judge file mentions them. Nothing here trusts the session's narrative.
"""
import io
import glob
import json
import os
import re

out = []


def rules_of(cid):
    path = "src/main/resources/characters/%s.json" % cid
    if not os.path.exists(path):
        return []
    doc = json.load(io.open(path, encoding="utf-8"))
    return doc["rules"] if isinstance(doc, dict) and "rules" in doc else doc


out.append("=== ① STATE_ENDED subscribers in the tree ===")
for cid in ("1211", "1501", "1505", "1513", "1415", "1412"):
    for rule in rules_of(cid):
        if isinstance(rule, dict) and rule.get("on") == "STATE_ENDED":
            out.append("  %s  %-34s when=%s" % (cid, rule.get("id"),
                                                json.dumps(rule.get("when"), ensure_ascii=False)))
        if isinstance(rule, dict) and rule.get("on") == "INSERTED_CAST_END":
            out.append("  %s  %-34s when=%s   (the coup moment)" % (cid, rule.get("id"),
                                                                    json.dumps(rule.get("when"), ensure_ascii=False)))

out.append("")
out.append("=== ② 1412's peerage chain ===")
for rule in rules_of("1412"):
    if isinstance(rule, dict) and ("coup" in str(rule.get("id")) or "peerage" in str(rule.get("id"))):
        out.append("  %-30s on=%-18s do=%s" % (rule.get("id"), rule.get("on"),
                                               json.dumps(rule.get("do"), ensure_ascii=False)[:120]))

out.append("")
out.append("=== ③ the memosprite files ===")
for name in sorted(os.listdir("src/main/resources/memosprites")):
    out.append("  %s" % name)

out.append("")
out.append("=== judges for the pieces (mentioned in a test file?) ===")
tests = glob.glob("src/test/java/com/laosun/aluminium/test/*.java")
for needle in ("STATE_ENDED", "kai_bu_bai", "per_stack_live", "party_resource", "Rules", "by_ability",
               "stackable", "max_stacks"):
    hits = [os.path.basename(path) for path in tests
            if needle in io.open(path, encoding="utf-8").read()]
    out.append("  %-16s -> %s" % (needle, ", ".join(sorted(hits)[:6]) or "(none)"))

io.open("tools/_tmp_evidence.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written", len(out), "lines")
