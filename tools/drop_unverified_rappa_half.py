"""Take the unverified half back out of the tree (2026-10-02).

Measured: with Rappa at Lv80 her ATTACK is below 2400, so the extra rule's excess is 0 -- the judge cannot reach it, and
removing `cap_amount` from it reddened nothing (0 red). The house rule is that nothing unverified stays in the tree, and
the remedy for a rule the judge cannot exercise is to withdraw it and register it, not to keep it and hope.

The BASE rule (2% break damage taken) stays: the judge binds it by selectivity, and it is what the sentence's first half
says. The extra half (「若乱破当前攻击力高于2400点，每超过100点攻击力…最多额外提高8%」) goes back to the register, with the
reason: the judge needs a way to raise ATTACK first.

ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1317.json"
ID = "trace_break_damage_taken_up_above_attack"

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
kept = [r for r in rules if not (isinstance(r, dict) and r.get("id") == ID)]
print("dropped:", len(rules) - len(kept), "rule(s)")
if isinstance(doc, dict):
    doc["rules"] = kept
    out = doc
else:
    out = kept
json.dump(out, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1317.json now holds only the judge-bound half")
