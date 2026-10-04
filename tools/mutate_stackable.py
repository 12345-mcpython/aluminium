"""Mutation for the stackable-state capability: drop the spelling, so a plain state refreshes.

Expected effect: three applications leave ONE plain state instead of three stackable ones, so the judge's
`countBuffs(StackableStateBuff.class) == 3` fails. Restore with `restore_stackable_probe.py`.
"""
import io

PATH = "src/test/java/com/laosun/aluminium/test/AttachedBuffProbeTest.java"
LINE = 'TriggerSpecs.set(apply, "stackable", Boolean.TRUE);'

text = io.open(PATH, encoding="utf-8").read()
if text.count(LINE) != 1:
    raise SystemExit("REFUSING: the line appears %d times" % text.count(LINE))
io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(LINE, "", 1))
print("MUTATION removed the stackable spelling")
