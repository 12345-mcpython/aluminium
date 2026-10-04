"""Mutant for the debuff-class filter (round 9 of the goal).

The condition ignores the class, so both families fire the same rule and the two "does NOT fire" halves fail.
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/models/TriggerTable.java"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
if mode not in ("on", "off"):
    sys.exit("usage: mut_debuff_class.py on|off")

LIVE = "            return ctx.battle() != null && expected == ctx.battle().lastAppliedDebuffClass();"
MUTATED = "            return ctx.battle() != null && ctx.battle().lastAppliedDebuffClass() != null;"
text = io.open(PATH, encoding="utf-8").read()
if mode == "off":
    if text.count(LIVE) != 1:
        sys.exit("REFUSING: the live line appears %d times" % text.count(LIVE))
    io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(LIVE, MUTATED, 1))
    print("MUTATION: the filter ignores which class landed")
else:
    if text.count(MUTATED) != 1:
        sys.exit("REFUSING: the mutated line appears %d times" % text.count(MUTATED))
    io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(MUTATED, LIVE, 1))
    print("restored: the filter compares the class")
