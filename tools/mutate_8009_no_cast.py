"""Mutation: strip the CAST_SKILL effect from 8009/8010's ult rule, so the commanded Elation cast never happens.

Kept as a script (not an inline one-liner) because this round has already shown twice that PowerShell + inline Python
quoting destroys Chinese literals. Restore by re-running `ship_8009_ult_elation.py`, which is idempotent.
"""
import io
import json

for cid in ("8009", "8010"):
    path = "src/main/resources/characters/%s.json" % cid
    doc = json.load(io.open(path, encoding="utf-8"))
    removed = 0
    for rule in doc.get("rules", []):
        if isinstance(rule, dict) and rule.get("id") == "ult_elation_gift_and_cast":
            before = len(rule.get("do", []))
            rule["do"] = [e for e in rule.get("do", []) if e.get("op") != "CAST_SKILL"]
            removed += before - len(rule.get("do", []))
    json.dump(doc, io.open(path, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
    print("MUTATION %s: removed %d CAST_SKILL effect(s); the gift grant stays" % (cid, removed))
