"""Probe (2026-10-02, round 1642): read 1008's eidolon four in full, and see what the shipped rule already states.

The sentence of interest is 「该效果在触发 1 次后或持续 2 回合后自动解除」 -- two expiries joined by OR. The question this
probe answers is a READING question, not a coding one: what does the document say the effect IS, where does it come from,
and which of the two expiries (if any) has a spelling in the tree already.

Output goes to a UTF-8 file because this shell mangles Chinese.
"""
import io
import json
import os

out = []


def emit(text):
    out.append(text)


# 1. the eidolon table's own text and numbers for 1008 rank 4
table = json.load(io.open("src/main/resources/data/eidolons.json", encoding="utf-8"))
entry = None
ranks = table.get("1008")
if isinstance(ranks, dict):
    for key, value in ranks.items():
        if str(key) in ("4", "100804") or (isinstance(value, dict) and value.get("rank_id") == 100804):
            entry = value
            break
emit("== eidolons.json 1008 rank 4 ==")
emit(json.dumps(entry, ensure_ascii=False, indent=2) if entry else "not found under that key; keys: " + str(list(ranks.keys()) if isinstance(ranks, dict) else type(ranks)))

# 2. what 1008.json already ships for the lethal save
doc = json.load(io.open("src/main/resources/characters/1008.json", encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
emit("")
emit("== characters/1008.json ==")
emit("file shape: %s | rules: %d" % ("object" if isinstance(doc, dict) else "list", len(rules)))
for rule in rules:
    if not isinstance(rule, dict):
        continue
    blob = json.dumps(rule, ensure_ascii=False)
    if "LETHAL" in blob or "eidolon" in blob.lower() or "min_eidolon" in rule:
        emit(json.dumps(rule, ensure_ascii=False, indent=2))

io.open("tools/_tmp_1008.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written", len(out), "blocks")
