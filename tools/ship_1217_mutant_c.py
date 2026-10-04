"""A third mutant: point the clock at a SUMMON instead of her (round 2 of the goal).

Mutant B showed that dropping `ticks_on` changes nothing, because the wearer is already the default clock. That leaves the
sentence's other half -- 「藿藿每回合开始时」, i.e. WHOSE clock -- pinned only against engine drift. `c` moves the clock to another
anchor: if the state then stops expiring on her turns, the "her clock" half has a mutant of its own.
"""
import io
import sys

MUTATOR = "tools/mut_huohuo_clock.py"
text = io.open(MUTATOR, encoding="utf-8").read()
OLD = '''if mode not in ("a", "b", "off", "on"):'''
NEW = '''if mode not in ("a", "b", "c", "off", "on"):'''
if text.count(OLD) != 1:
    sys.exit("REFUSING: the mode line appears %d times" % text.count(OLD))
text = text.replace(OLD, NEW, 1)

OLD2 = '''if mode in ("b", "off"):
    effect.pop("ticks_on")'''
NEW2 = '''if mode in ("b", "off"):
    effect.pop("ticks_on")
if mode == "c":
    effect["ticks_on"] = "summon"'''
if text.count(OLD2) != 1:
    sys.exit("REFUSING: the ticks_on branch appears %d times" % text.count(OLD2))
text = text.replace(OLD2, NEW2, 1)
io.open(MUTATOR, "w", encoding="utf-8", newline="\n").write(text)
print("ok   the mutator takes mode c (the clock points at a summon)")
