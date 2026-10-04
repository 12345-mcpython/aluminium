"""Restore the party branch, anchored on enough context to be unique.

`if (effect.getAmount() != null) {` alone appears four times in the file, which is why the toggle's restore step refused and the
mutation stayed in place.
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
MUTATED = """        if (Boolean.TRUE.equals(effect.getStackable())) {
            if (effect.getAmount() != null) {
                times = Math.max(1, (int) Math.round(effect.getAmount()));
            }
        }"""
LIVE = """        if (Boolean.TRUE.equals(effect.getStackable())) {
            if (effect.getScale() != null && effect.getScale().trim().startsWith("party_resource:")) {
                times = Math.max(1, (int) Math.round(resolveScale(effect.getScale(),
                        effect.getPercent() == null ? 1 : effect.getPercent(), ctx)));
            } else if (effect.getAmount() != null) {
                times = Math.max(1, (int) Math.round(effect.getAmount()));
            }
        }"""

text = io.open(PATH, encoding="utf-8").read()
if text.count(LIVE) == 1:
    print("already restored")
    raise SystemExit(0)
if text.count(MUTATED) != 1:
    sys.stderr.write("REFUSING: the mutated block appears %d times\n" % text.count(MUTATED))
    raise SystemExit(1)
io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(MUTATED, LIVE, 1))
print("restored: the party scale decides the count")
