import io, json, re, sys
P = "src/main/resources/characters/1217.json"
raw = io.open(P, encoding="utf-8").read()
# ⚠ READ the name out of the file instead of typing the codepoint: that mistake has now cost two rounds.
m = re.search(r'"op": "APPLY_BUFF",\s*"buff": "([^"]+)"', raw)
if not m:
    sys.exit("REFUSING: no APPLY_BUFF name to copy")
name = m.group(1)
print("the state name in the file is: %s (len %d)" % (name, len(name)))
d = json.loads(raw)
rules = d if isinstance(d, list) else d.get("rules", [])
r = [x for x in rules if x.get("id") == "e2_saves_an_ally_with_the_talisman"][0]
for e in r["do"]:
    if e.get("op") == "EXTEND_BUFF":
        e["buff"] = name          # the name as the FILE spells it
if not any(e.get("op") == "EXTEND_BUFF" for e in r["do"]):
    r["do"].append({"op": "EXTEND_BUFF", "buff": name, "turns": -1, "target": "self"})
io.open(P, "w", encoding="utf-8", newline="\n").write(json.dumps(d, ensure_ascii=False, indent=2) + "\n")
print("ok   EXTEND_BUFF now names %s" % name)
