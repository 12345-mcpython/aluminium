"""BISECT (throwaway): 1513's two new rules, one at a time, against AventurineWaveflairTest (2026-10-02).

Measured with both rules in: 「热意」 reads 13 where the existing judge expects 30 -- and neither of my rules obviously
spends a resource during a loop of ALLY_ATTACK firings (one is on CAST_SETUP with `from_category`, the other only deals
damage on RESOURCE_CHANGED). So run with ONLY the damage rule: is 30 restored, or is something else at work?

This drops `elation_spend_all_fervor` from her file after the engine + both rules are in place.
ASCII only.
"""
import io
import json

exec(io.open("tools/add_spend_all3.py", encoding="utf-8").read())

DATA = "src/main/resources/characters/1513.json"
doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
kept = [r for r in rules if not (isinstance(r, dict) and r.get("id") == "elation_spend_all_fervor")]
print("kept rules:", [r.get("id") for r in kept if isinstance(r, dict) and r.get("id")])
if isinstance(doc, dict):
    doc["rules"] = kept
    out = doc
else:
    out = kept
json.dump(out, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("BISECT: only fervor_extra_hit_per_point is present")
