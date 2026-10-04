"""Mutation: the repeat ignores the party scale, so a stackable state is applied once.

(Chosen after checking that dropping the `stackable` guard would NOT fail the judge: repeating a plain state gives several
plain states, and `stacksOf` counts only NAMED ones -- so the control would still read 0. The reading that can fail is the
first test's count.)
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
LIVE = """            if (effect.getScale() != null && effect.getScale().trim().startsWith("party_resource:")) {
                times = Math.max(1, (int) Math.round(resolveScale(effect.getScale(),
                        effect.getPercent() == null ? 1 : effect.getPercent(), ctx)));
            } else if (effect.getAmount() != null) {"""
MUTATED = """            if (effect.getAmount() != null) {"""
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
text = io.open(PATH, encoding="utf-8").read()
if mode == "off":
    if text.count(LIVE) != 1:
        sys.exit("REFUSING: the live block appears %d times" % text.count(LIVE))
    io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(LIVE, MUTATED, 1))
    print("MUTATION: the party scale is ignored")
elif mode == "on":
    if text.count(MUTATED) != 1:
        sys.exit("REFUSING: the mutated block appears %d times" % text.count(MUTATED))
    io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(MUTATED, LIVE, 1))
    print("restored: the party scale decides the count")
else:
    sys.exit("usage: mut_stack_times.py on|off")
