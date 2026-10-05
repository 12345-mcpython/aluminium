"""Land `summon_attr:<ATTRIBUTE>` -- two phases: validate EVERY anchor, then write (2026-10-02).

The lesson this file is built around: last round the patch script inserted into a doc comment (the anchor line occurred twice), and the
correction script deleted the misplaced block, then bailed out on a failed uniqueness check BEFORE writing -- leaving the file half-edited.
So: phase 1 collects every (file, index, text) and refuses if anything is ambiguous; phase 2 touches the files.

Three anchors, measured:
  * `TriggerTable`: `static final String SELF_ATTR_PREFIX = "self_attr:";` (package-private, no `public`);
  * `requireDerivedScale`: the `SELF_STACKS_PREFIX` check that follows the `String scale = ...` line -- the bare `String scale = ...` line
    occurs twice in the file, so it is not usable alone;
  * `derivedMagnitude`: `if (effect.getScale().trim().startsWith(ABOVE_PREFIX)) {`.
"""
import io
import sys

TAB = "src/main/java/com/laosun/aluminium/models/TriggerTable.java"
INT = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"

PREFIX_CONST = '''/**
 * {@code summon_attr:<ATTRIBUTE>} -- the rule owner MEMOSPRITE's own attribute, as a magnitude: "等同于德谬歌生命上限的 #1%"
 * (1415 memosprite skill 10, data slot 13). The sibling of {@link #SELF_ATTR_PREFIX}, one subject further out.
 */
static final String SUMMON_ATTR_PREFIX = "summon_attr:";
'''

LOAD_TIME = '''        // The owner MEMOSPRITE's attribute (2026-10-02). Accepted HERE and not in `scaleAttribute`, because that one is shared
        // with the DAMAGE path, where the subject is the attacker -- teaching it this spelling there would make damage quietly
        // read the wrong unit. The attribute name is still checked, so a typo is loud at load time.
        if (scale.startsWith(TriggerTable.SUMMON_ATTR_PREFIX)) {
            String summonAttr = scale.substring(TriggerTable.SUMMON_ATTR_PREFIX.length()).trim();
            if (summonAttr.isEmpty()) {
                throw new IllegalArgumentException(
                        "Op " + op + " scales off the owner's memosprite but names no attribute: \\"" + scale
                                + "\\" (source: " + spec.getSource() + ")");
            }
            AttributeType.fromString(summonAttr);
            requirePercent(effect, op, spec);
            return;
        }
'''

RUN_TIME = '''        if (effect.getScale().trim().startsWith(TriggerTable.SUMMON_ATTR_PREFIX)) {
            // The owner's MEMOSPRITE (1415 memosprite skill 10). Resolved through `Battle.summonOf`, the same accessor the `summon`
            // target selector reads, and before the attribute branch because the subject here is not the owner.
            CanHit fielded = ctx.battle() == null ? null : ctx.battle().summonOf(owner);
            if (fielded == null) {
                throw new IllegalStateException("the scale \\"" + effect.getScale()
                        + "\\" reads the owner's memosprite, but " + owner.getName()
                        + " has none on the field; a share of a unit that is not there is not a number");
            }
            AttributeType from = AttributeType.fromString(
                    effect.getScale().trim().substring(TriggerTable.SUMMON_ATTR_PREFIX.length()).trim());
            return effect.getPercent() * fielded.getAttribute(from).get()
                    + (effect.getAmount() == null ? 0 : effect.getAmount());
        }
'''

ANCHORS = [
    (TAB, 'static final String SELF_ATTR_PREFIX = "self_attr:";', PREFIX_CONST, "the prefix constant"),
    (INT, "        if (scale.startsWith(TriggerTable.SELF_STACKS_PREFIX)", LOAD_TIME, "the load-time branch"),
    (INT, "        if (effect.getScale().trim().startsWith(ABOVE_PREFIX)) {", RUN_TIME, "the run-time branch"),
]

# ---------- phase 1: validate everything, touch nothing ----------
texts = {path: io.open(path, encoding="utf-8").read() for path in (TAB, INT)}
plan = []
for path, anchor, block, label in ANCHORS:
    count = texts[path].count(anchor)
    print("anchor %-24s in %-58s : %d" % (label, path.split("/")[-1], count))
    if count != 1:
        sys.exit("REFUSING: the anchor for %s occurs %d times -- nothing has been written" % (label, count))
    plan.append((path, anchor, block, label))

# ---------- phase 2: apply ----------
for path, anchor, block, label in plan:
    txt = texts[path]
    line_start = txt.rfind("\n", 0, txt.index(anchor)) + 1
    texts[path] = txt[:line_start] + block + txt[line_start:]
    print("ok   %s" % label)
for path, txt in texts.items():
    io.open(path, "w", encoding="utf-8", newline="\n").write(txt)
print("ok   both files written")
