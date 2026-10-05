import io, json, sys
P = "src/main/resources/characters/1217.json"
d = json.load(io.open(P, encoding="utf-8"))
rules = d if isinstance(d, list) else d.get("rules", [])
t = [r for r in rules if r.get("id") == "e2_saves_an_ally_with_the_talisman"]
if len(t) != 1:
    sys.exit("REFUSING: %d rules" % len(t))
r = t[0]
if "EXTEND_BUFF" not in [e.get("op") for e in r["do"]]:
    r["do"].append({"op": "EXTEND_BUFF", "buff": "穹命", "turns": -1, "target": "self"})
io.open(P, "w", encoding="utf-8", newline="\n").write(json.dumps(d, ensure_ascii=False, indent=2) + "\n")
print("ok   the E2 carries the third sentence")
