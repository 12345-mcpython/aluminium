"""Mutation v3: remove the leftover cap, so the state is PLAIN and the three applications become refreshes.

State of the tree: mutation v1 already removed the `stackable` line, v2 refused because of that. Removing `maxStacks` as
well leaves a plain APPLY_BUFF, which the loader accepts -- so this is a behavioural reading (three refreshes, one
instance) rather than the load refusal v1 produced.
"""
import io

PATH = "src/test/java/com/laosun/aluminium/test/AttachedBuffProbeTest.java"
LINE = 'TriggerSpecs.set(apply, "maxStacks", 9);\n'

text = io.open(PATH, encoding="utf-8").read()
if text.count(LINE) != 1:
    raise SystemExit("REFUSING: the cap line appears %d times" % text.count(LINE))
io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(LINE, "", 1))
print("MUTATION removed the cap; the state is plain now")
