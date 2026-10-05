"""Retry the `summon_attr` landing with a regex-located anchor (the literal one did not match).

⚠ `compileJava` printing BUILD SUCCESSFUL means nothing here: the first attempt applied NOTHING, so the tree it compiled was the unpatched
one. The script's own `ok` lines are what say a patch landed.
"""
import io
import re
import sys


def patch(path, anchor, addition, label, before=True):
    txt = io.open(path, encoding="utf-8").read()
    if txt.count(anchor) != 1:
        sys.exit("REFUSING %s : %d occurrences" % (label, txt.count(anchor)))
    i = txt.index(anchor)
    line_start = txt.rfind("\n", 0, i) + 1
    indent = txt[line_start:i] if before else ""
    block = "".join((indent + l).rstrip() + "\n" if l.strip() else "\n" for l in addition.split("\n"))
    out = txt[:line_start] + block + txt[line_start:] if before else txt[:i] + block + txt[i:]
    io.open(path, "w", encoding="utf-8", newline="\n").write(out)
    print("ok   %s" % label)


TAB = "src/main/java/com/laosun/aluminium/models/TriggerTable.java"
txt = io.open(TAB, encoding="utf-8").read()
m = re.search(r"^(\s*)static final String SELF_ATTR_PREFIX\b.*$", txt, re.M)
if not m:
    print("candidates:")
    for line in txt.split("\n"):
        if "PREFIX" in line and "String" in line:
            print("   %r" % line)
    sys.exit("REFUSING: no SELF_ATTR_PREFIX declaration")
decl = m.group(0).strip()
print("the declaration is: %r" % decl)
patch(TAB, decl,
      "/**\n"
      " * {@code summon_attr:<ATTRIBUTE>} -- the rule owner MEMOSPRITE's own attribute, as a magnitude: \"等同于德谬歌生命上限的 #1%\"\n"
      " * (1415 memosprite skill 10, data slot 13). The sibling of {@link #SELF_ATTR_PREFIX}, one subject further out.\n"
      " */\n"
      "static final String SUMMON_ATTR_PREFIX = \"summon_attr:\";",
      "the prefix constant")

T = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
patch(T, "        // The spelling and (for the attribute family) the name; also re-checks `percent`, because a scale with no",
      "        // The owner MEMOSPRITE's attribute (2026-10-02). Accepted HERE and not in `scaleAttribute`, because that one is\n"
      "        // shared with the DAMAGE path, where the subject is the attacker -- teaching it this spelling there would make\n"
      "        // damage quietly read the wrong unit. The attribute name is still checked, so a typo is loud at load time.\n"
      "        if (scale.startsWith(TriggerTable.SUMMON_ATTR_PREFIX)) {\n"
      "            String summonAttr = scale.substring(TriggerTable.SUMMON_ATTR_PREFIX.length()).trim();\n"
      "            if (summonAttr.isEmpty()) {\n"
      "                throw new IllegalArgumentException(\n"
      "                        \"Op \" + op + \" scales off the owner's memosprite but names no attribute: \\\"\" + scale\n"
      "                                + \"\\\" (source: \" + spec.getSource() + \")\");\n"
      "            }\n"
      "            AttributeType.fromString(summonAttr);\n"
      "            requirePercent(effect, op, spec);\n"
      "            return;\n"
      "        }\n"
      "        // The spelling and (for the attribute family) the name; also re-checks `percent`, because a scale with no",
      "the load-time branch")

patch(T, "        if (effect.getScale().trim().startsWith(ABOVE_PREFIX)) {",
      "        if (effect.getScale().trim().startsWith(TriggerTable.SUMMON_ATTR_PREFIX)) {\n"
      "            // The owner's MEMOSPRITE (1415 memosprite skill 10). Resolved through `Battle.summonOf`, the same accessor the\n"
      "            // `summon` target selector reads, and before the attribute branch because the subject here is not the owner.\n"
      "            CanHit fielded = ctx.battle() == null ? null : ctx.battle().summonOf(owner);\n"
      "            if (fielded == null) {\n"
      "                throw new IllegalStateException(\"the scale \\\"\" + effect.getScale()\n"
      "                        + \"\\\" reads the owner's memosprite, but \" + owner.getName()\n"
      "                        + \" has none on the field; a share of a unit that is not there is not a number\");\n"
      "            }\n"
      "            AttributeType from = AttributeType.fromString(\n"
      "                    effect.getScale().trim().substring(TriggerTable.SUMMON_ATTR_PREFIX.length()).trim());\n"
      "            return effect.getPercent() * fielded.getAttribute(from).get()\n"
      "                    + (effect.getAmount() == null ? 0 : effect.getAmount());\n"
      "        }\n"
      "        if (effect.getScale().trim().startsWith(ABOVE_PREFIX)) {",
      "the run-time branch")
