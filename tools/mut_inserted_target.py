"""Toggle the aimed unit in the firing: off restores the old `(actor, actor)` (the mutation).

Under `off` the event's target slot carries the caster again, so the mark lands on the owner instead of on the unit the
commanded cast was aimed at.
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
LIVE = "        CanHit aimed = victims == null || victims.isEmpty() ? actor : victims.getFirst();\n" \
       "        battle.fireTriggers(TriggerEvent.INSERTED_CAST_END, actor, aimed, 0, 0);"
MUTATED = "        battle.fireTriggers(TriggerEvent.INSERTED_CAST_END, actor, actor, 0, 0);"

mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
text = io.open(PATH, encoding="utf-8").read()
if mode == "off":
    if text.count(LIVE) != 1:
        sys.exit("REFUSING: the live block appears %d times" % text.count(LIVE))
    io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(LIVE, MUTATED, 1))
    print("MUTATION: the target slot carries the caster again")
elif mode == "on":
    if text.count(MUTATED) != 1:
        sys.exit("REFUSING: the mutated line appears %d times" % text.count(MUTATED))
    io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(MUTATED, LIVE, 1))
    print("restored: the target slot carries the aimed unit")
else:
    sys.exit("usage: mut_inserted_target.py on|off")
