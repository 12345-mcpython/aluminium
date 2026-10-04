"""Three exact edits: the scale resolves, and BOTH validators accept it (2026-10-02, item 69).

Measured this round rather than guessed:
  * `scaleAttribute` (the op-specific whitelist that rejected round 1682's attempt) has an EARLY-RETURN list for scales that
    are "handled by derivedMagnitude; not an AttributeType" -- SELF_MAX_ENERGY / EVENT_AMOUNT / above:. The clean fix is to
    join that list, exactly as EVENT_AMOUNT does, instead of rewriting the check.
  * the second whitelist (`requireAmountOrScale`'s caller) and `resolveScale`'s branch head are the other two sites.
No code is reconstructed from pieces here: each edit is one exact string.
"""
import io
import re
import sys

INTERP = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
text = io.open(INTERP, encoding="utf-8").read()


def patch(old, new, label, count=1):
    global text
    found = text.count(old)
    if found != count:
        sys.stderr.write("REFUSING %s: the anchor appears %d times\n" % (label, found))
        raise SystemExit(1)
    text = text.replace(old, new, count)
    print("ok   %s" % label)


# 1. the op-specific whitelist's early-return list
patch(
    """        if (SELF_MAX_ENERGY.equals(raw) || EVENT_AMOUNT.equals(raw) || raw.startsWith(ABOVE_PREFIX)) {
            return null;                      // handled by derivedMagnitude; not an AttributeType
        }""",
    """        // \u2b50 `party_resource:<name>` joins this list (2026-10-02): like EVENT_AMOUNT it is a magnitude the op can read
        // but not an AttributeType, so the question "which attribute does this scale name" has no answer for it.
        if (SELF_MAX_ENERGY.equals(raw) || EVENT_AMOUNT.equals(raw) || raw.startsWith(ABOVE_PREFIX)
                || raw.startsWith("party_resource:")) {
            return null;                      // handled by derivedMagnitude; not an AttributeType
        }""",
    "scaleAttribute's early-return list",
)

# 2. the generic whitelist
patch(
    "if (!scales.contains(scale) && !scale.startsWith(TriggerTable.CAST_APPLIED_PREFIX)) {",
    "if (!scales.contains(scale) && !scale.startsWith(TriggerTable.CAST_APPLIED_PREFIX)\n"
    "                && !scale.startsWith(\"party_resource:\")) {",
    "requireAmountOrScale's whitelist",
)

# 3. the branch itself
ANCHOR = re.compile(
    r"([ \t]*)String key = scale == null \? \"\" : scale\.trim\(\);\s*\n"
    r"\1Double fromStacks = stackScale\(key, percent, ctx\);\s*\n"
    r"\1if \(fromStacks != null\) \{\s*\n"
    r"\1    return fromStacks;\s*\n"
    r"\1\}\n")
match = ANCHOR.search(text)
if match is None:
    sys.exit("REFUSING: resolveScale's head was not matched")
indent = match.group(1)
addition = (
    indent + "// \u2b50 A battle-level PARTY counter as a magnitude (2026-10-02; reader: 1513's reward, which hands\n"
    + indent + "// \u3010\u597d\u6d3b\u5f53\u8d4f\u3011 the \u3010\u7b11\u70b9\u3011 the Aha moment spent). The counter lives on the battle, not on a unit.\n"
    + indent + "if (key.startsWith(\"party_resource:\")) {\n"
    + indent + "    String counter = key.substring(\"party_resource:\".length()).trim();\n"
    + indent + "    if (ctx.battle() == null || ctx.battle().partyResource(counter) == null) {\n"
    + indent + "        throw new IllegalStateException(\n"
    + indent + "                \"a magnitude scales off the party counter '\" + counter + \"', which no file declares or which\"\n"
    + indent + "                        + \" has no battle to live on -- a counter that answers 0 is a wrong number with no symptom\");\n"
    + indent + "    }\n"
    + indent + "    return percent * ctx.battle().partyResourceValue(counter);\n"
    + indent + "}\n")
text = text[:match.end()] + addition + text[match.end():]
print("ok   resolveScale knows the party counter")

io.open(INTERP, "w", encoding="utf-8", newline="\n").write(text)
print("done")
