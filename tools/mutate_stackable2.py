"""Mutation v2: drop BOTH the spelling and the cap, so the state is a plain one and only refreshes.

v1 removed only `stackable` and kept `maxStacks`, which the loader refuses loudly ("Op APPLY_BUFF does not support
max_stacks/stacks") -- a load failure, not a behavioural reading. Dropping both makes the three applications a plain
state's three refreshes, i.e. ONE instance where the judge demands three.
"""
import io

PATH = "src/test/java/com/laosun/aluminium/test/AttachedBuffProbeTest.java"
LINES = ['TriggerSpecs.set(apply, "stackable", Boolean.TRUE);\n',
         'TriggerSpecs.set(apply, "maxStacks", 9);\n']

text = io.open(PATH, encoding="utf-8").read()
for line in LINES:
    if text.count(line) != 1:
        raise SystemExit("REFUSING: %r appears %d times" % (line.strip(), text.count(line)))
    text = text.replace(line, "", 1)
io.open(PATH, "w", encoding="utf-8", newline="").write(text)
print("MUTATION removed the spelling AND the cap (a plain state)")
