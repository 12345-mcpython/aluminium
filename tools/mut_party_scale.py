"""Mutation: the party scale ignores `percent`, so the magnitude becomes the raw counter instead of a share of it."""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
LIVE = "    return percent * ctx.battle().partyResourceValue(counter);"
MUTATED = "    return ctx.battle().partyResourceValue(counter);"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
text = io.open(PATH, encoding="utf-8").read()
if mode == "off":
    if text.count(LIVE) != 1:
        sys.exit("REFUSING: the live line appears %d times" % text.count(LIVE))
    io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(LIVE, MUTATED, 1))
    print("MUTATION: percent is ignored")
elif mode == "on":
    if text.count(MUTATED) != 1:
        sys.exit("REFUSING: the mutated line appears %d times" % text.count(MUTATED))
    io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(MUTATED, LIVE, 1))
    print("restored: percent multiplies")
else:
    sys.exit("usage: mut_party_scale.py on|off")
