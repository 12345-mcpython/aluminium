"""`actor_attr:<ATTRIBUTE>`: a magnitude that is a share of the ACTOR's own attribute (2026-10-02).

Why, measured on 1415 memosprite skill 10 「献予「创世」之诗」:
  「使开拓者•记忆的攻击力提高，提高数值等同于<b>德谬歌生命上限</b>的 #1%」 -- the share is of the CASTER's Max HP (德谬歌 is the unit casting the
  memosprite skill). The shipped `summon_attr:` reads the RULE OWNER's memosprite, and the rule owner here is 开拓者•记忆, whose memosprite is
  迷迷 -- the wrong unit. `self_attr:` reads the owner too. The missing subject is the ACTOR, which is exactly what `CAST_SETUP` carries.
"""
import io
import sys

TAB = "src/main/java/com/laosun/aluminium/models/TriggerTable.java"
INT = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"

CONST_ANCHOR = 'static final String SUMMON_ATTR_PREFIX = "summon_attr:";'
CONST_BLOCK = '''
/**
 * {@code actor_attr:<ATTRIBUTE>} -- the ACTOR's own attribute, as a magnitude: 「提高数值等同于德谬歌生命上限的 #1%」, where 德谬歌 is the unit
 * casting (1415 memosprite skill 10). The sibling of {@link #SELF_ATTR_PREFIX} (the owner) and {@link #SUMMON_ATTR_PREFIX} (the owner's
 * memosprite), one subject across: this one is whoever the event is about.
 */
static final String ACTOR_ATTR_PREFIX = "actor_attr:";
'''

LOAD_ANCHOR = "        if (scale.startsWith(TriggerTable.SUMMON_ATTR_PREFIX)) {"
LOAD_BLOCK = '''        // ⭐ The ACTOR's own attribute (2026-10-02; 1415 memosprite skill 10, whose share is of the CASTER's Max HP). Accepted here for
        // the same reason as the line below: `scaleAttribute` is shared with the DAMAGE path, where the subject is already the attacker
        // and a second spelling there would be a second meaning.
        if (scale.startsWith(TriggerTable.ACTOR_ATTR_PREFIX)) {
            String actorAttr = scale.substring(TriggerTable.ACTOR_ATTR_PREFIX.length()).trim();
            if (actorAttr.isEmpty()) {
                throw new IllegalArgumentException(
                        "Op " + op + " scales off the actor but names no attribute: \\"" + scale
                                + "\\" (source: " + spec.getSource() + ")");
            }
            AttributeType.fromString(actorAttr);
            requirePercent(effect, op, spec);
            return;
        }
'''

RUN_ANCHOR = "        if (effect.getScale().trim().startsWith(TriggerTable.SUMMON_ATTR_PREFIX)) {"
RUN_BLOCK = '''        if (effect.getScale().trim().startsWith(TriggerTable.ACTOR_ATTR_PREFIX)) {
            // ⭐ The unit the event is ABOUT (1415 memosprite skill 10: 「等同于德谬歌生命上限的 #1%", and 德谬歌 is the actor of the
            // memosprite skill). Before the attribute branch below, which reads the rule OWNER.
            CanHit subject = ctx.actor();
            if (subject == null) {
                throw new IllegalStateException("the scale \\"" + effect.getScale()
                        + "\\" reads the actor's attribute, but this event has no actor");
            }
            AttributeType from = AttributeType.fromString(
                    effect.getScale().trim().substring(TriggerTable.ACTOR_ATTR_PREFIX.length()).trim());
            return effect.getPercent() * subject.getAttribute(from).get()
                    + (effect.getAmount() == null ? 0 : effect.getAmount());
        }
'''

tab = io.open(TAB, encoding="utf-8").read()
txt = io.open(INT, encoding="utf-8").read()

# ---------- phase 1 ----------
for body, anchor, label in ((tab, CONST_ANCHOR, "the constant anchor"),
                            (txt, LOAD_ANCHOR, "the load-time anchor"),
                            (txt, RUN_ANCHOR, "the run-time anchor")):
    n = body.count(anchor)
    print("anchor %-24s : %d" % (label, n))
    if n != 1:
        sys.exit("REFUSING: %s occurs %d times -- nothing written" % (label, n))

# ---------- phase 2 ----------
i = tab.index(CONST_ANCHOR)
tab = tab[:i] + CONST_BLOCK.lstrip("\n") + tab[i:]
io.open(TAB, "w", encoding="utf-8", newline="\n").write(tab)
print("ok   the prefix constant")

for anchor, block, label in ((LOAD_ANCHOR, LOAD_BLOCK, "the load-time branch"),
                             (RUN_ANCHOR, RUN_BLOCK, "the run-time branch")):
    i = txt.index(anchor)
    ls = txt.rfind("\n", 0, i) + 1
    txt = txt[:ls] + block + txt[ls:]
    print("ok   %s" % label)
io.open(INT, "w", encoding="utf-8", newline="\n").write(txt)
print("ok   written")
