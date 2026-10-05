"""The same two edits, matched with flexible whitespace and using the accessor that exists.

`Battle.partyResource(id)` is what `gainResource` already calls, so the "does anyone declare it" question is
`partyResource(counter) != null` -- no new accessor needed.
"""
import io
import re
import sys

INTERP = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
text = io.open(INTERP, encoding="utf-8").read()

ANCHOR = re.compile(
    r"([ \t]*)String key = scale == null \? \"\" : scale\.trim\(\);\s*\n"
    r"\1Double fromStacks = stackScale\(key, percent, ctx\);\s*\n"
    r"\1if \(fromStacks != null\) \{\s*\n"
    r"\1    return fromStacks;\s*\n"
    r"\1\}\n")
match = ANCHOR.search(text)
if match is None:
    sys.stderr.write("REFUSING: the resolveScale head was not matched\n")
    raise SystemExit(1)
indent = match.group(1)
addition = (
    indent + "// ⭐ A battle-level PARTY counter as a magnitude (2026-10-02; reader: 1513's reward, which hands 【好活当赏】\n"
    + indent + "// the 【笑点】 the Aha moment spent). The counter lives on the battle, not on a unit, so `self_stacks:` cannot reach it.\n"
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

OLD_VALID = "if (!scales.contains(scale) && !scale.startsWith(TriggerTable.CAST_APPLIED_PREFIX)) {"
NEW_VALID = ("if (!scales.contains(scale) && !scale.startsWith(TriggerTable.CAST_APPLIED_PREFIX)\n"
             "                && !scale.startsWith(\"party_resource:\")) {")
if text.count(OLD_VALID) != 1:
    sys.stderr.write("REFUSING: the validator anchor appears %d times\n" % text.count(OLD_VALID))
    raise SystemExit(1)
text = text.replace(OLD_VALID, NEW_VALID, 1)
print("ok   the validator accepts the new scale")

io.open(INTERP, "w", encoding="utf-8", newline="\n").write(text)
