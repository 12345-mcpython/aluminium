"""The third site: `derivedMagnitude` must handle the party counter before it asks which attribute a scale names.

Measured by the judge: with `scaleAttribute` returning null (as EVENT_AMOUNT does), the caller still used the attribute and
threw a NullPointerException. `derivedMagnitude` has the branches for the non-attribute scales -- the party counter joins
them.
"""
import io
import re
import sys

INTERP = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
text = io.open(INTERP, encoding="utf-8").read()

ANCHOR = re.compile(
    r"([ \t]*)if \(SELF_MAX_ENERGY\.equals\(effect\.getScale\(\)\.trim\(\)\)\) \{")
match = ANCHOR.search(text)
if match is None:
    sys.exit("REFUSING: the SELF_MAX_ENERGY branch was not found")
indent = match.group(1)
addition = (
    indent + "// \u2b50 A battle-level PARTY counter (2026-10-02; reader: 1513's reward): the counter lives on the battle, so it is\n"
    + indent + "// resolved here rather than through `scaleAttribute`, which answers \"which attribute\" and has no answer for it.\n"
    + indent + "if (effect.getScale().trim().startsWith(\"party_resource:\")) {\n"
    + indent + "    return resolveScale(effect.getScale(),\n"
    + indent + "            effect.getPercent() == null ? 1 : effect.getPercent(), ctx)\n"
    + indent + "            + (effect.getAmount() == null ? 0 : effect.getAmount());\n"
    + indent + "}\n")
text = text[:match.start()] + addition + text[match.start():]
io.open(INTERP, "w", encoding="utf-8", newline="\n").write(text)
print("ok   derivedMagnitude handles the party counter")
