"""`cast_skill_param:<index>`: a magnitude that is a parameter of the skill that produced the event (2026-10-02).

Reader: 1415's memosprite skill 10 「献予「创世」之诗」 -- 「使开拓者•记忆的攻击力提高，提高数值等同于德谬歌生命上限的 #1%」, where #1 is the
skill's own parameter and RUNS WITH ITS LEVEL (0.08, 0.096, ... 0.224 over ten levels). `MODIFY_ATTR` could only take a literal `percent`, so
the sentence could not be written without picking one level and freezing it -- which is an approximation.

The shape is the damage path's, measured: `multiplierOf` reads `skill.getData().getSkills()`, takes `attacker.skillLevel(skill)` as the row
(one-based levels, `level - 1` into the table) and `effect.getDamageParam()` as the index into that row. This is the same read, spelled as a
scale instead of as damage.

⚠ The subject is the event's ACTOR (the casting unit) and the skill is looked up by `ctx.skillId()`; a memosprite reaches its own skills
through `Summon.skillAt(slot)`, which is the accessor the existing tests use. Anything else -- no actor, not a summon, no such skill, an index
outside the row -- throws, because a share of a number nobody can name is not a number.
"""
import io
import sys

T = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
txt = io.open(T, encoding="utf-8").read()

LOAD_ANCHOR = "        if (scale.startsWith(TriggerTable.SUMMON_ATTR_PREFIX)) {"
RUN_ANCHOR = "        if (effect.getScale().trim().startsWith(TriggerTable.SUMMON_ATTR_PREFIX)) {"

LOAD_BLOCK = '''        // A parameter of the skill that produced the event (2026-10-02). 「等同于德谬歌生命上限的 #1%」: #1 is the skill own parameter
        // and it RUNS WITH ITS LEVEL, so a literal `percent` would freeze one level -- the damage path reads it the same way, through
        // `multiplierOf`. The index is checked here; whether the event has such a skill can only be known when it fires.
        if (scale.startsWith(TriggerTable.CAST_SKILL_PARAM_PREFIX)) {
            String raw2 = scale.substring(TriggerTable.CAST_SKILL_PARAM_PREFIX.length()).trim();
            if (raw2.isEmpty()) {
                throw new IllegalArgumentException(
                        "Op " + op + " scales off a skill parameter but names no index: \\"" + scale
                                + "\\" (source: " + spec.getSource() + ")");
            }
            try {
                if (Integer.parseInt(raw2) < 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException notAnIndex) {
                throw new IllegalArgumentException(
                        "Op " + op + " scales off skill parameter \\"" + raw2
                                + "\\", which is not a zero-based index (source: " + spec.getSource() + ")");
            }
            requirePercent(effect, op, spec);
            return;
        }
'''

RUN_BLOCK = '''        if (effect.getScale().trim().startsWith(TriggerTable.CAST_SKILL_PARAM_PREFIX)) {
            // ⭐ The skill that produced THIS event, and its parameter at the CURRENT level (1415 memosprite skill 10, data slot 13).
            int index = Integer.parseInt(
                    effect.getScale().trim().substring(TriggerTable.CAST_SKILL_PARAM_PREFIX.length()).trim());
            CanHit caster = ctx.actor();
            if (!(caster instanceof Summon from)) {
                throw new IllegalStateException("the scale \\"" + effect.getScale()
                        + "\\" reads the parameter of the skill that produced the event, but the actor is "
                        + (caster == null ? "nobody" : caster.getName() + ", which is not a memosprite"));
            }
            Skill casting = from.skillAt(ctx.skillId());
            if (casting == null || casting.getData() == null || !casting.getData().isLoaded()) {
                throw new IllegalStateException("the scale \\"" + effect.getScale()
                        + "\\" reads the skill that produced the event, but " + from.getName()
                        + " has no loaded skill at slot " + ctx.skillId());
            }
            var rows = casting.getData().getSkills();
            int row = caster.skillLevel(casting) - 1;
            if (row < 0 || row >= rows.size()) {
                throw new IllegalStateException("skill level " + caster.skillLevel(casting) + " is outside " + from.getName()
                        + " skill " + ctx.skillId() + " parameter table (rows=" + rows.size() + ")");
            }
            var values = rows.get(row);
            if (index >= values.size()) {
                throw new IllegalStateException("the scale \\"" + effect.getScale() + "\\" names index " + index
                        + ", which is outside that skill parameter row (size=" + values.size() + ")");
            }
            return effect.getPercent() * values.get(index)
                    + (effect.getAmount() == null ? 0 : effect.getAmount());
        }
'''

TAB = "src/main/java/com/laosun/aluminium/models/TriggerTable.java"
tab = io.open(TAB, encoding="utf-8").read()
CONST_ANCHOR = 'static final String SUMMON_ATTR_PREFIX = "summon_attr:";'
CONST_BLOCK = '''
/**
 * {@code cast_skill_param:<index>} -- a parameter of the skill that produced the event, at its CURRENT level: 「等同于德谬歌生命上限的 #1%"
 * (1415 memosprite skill 10). The damage path reads the same row through {@code multiplierOf}.
 */
static final String CAST_SKILL_PARAM_PREFIX = "cast_skill_param:";
'''

# ---------- phase 1: every anchor validated before anything is written ----------
checks = [(T, LOAD_ANCHOR, "the load-time anchor"), (T, RUN_ANCHOR, "the run-time anchor"),
          (TAB, CONST_ANCHOR, "the constant anchor")]
for path, anchor, label in checks:
    body = txt if path == T else tab
    n = body.count(anchor)
    print("anchor %-22s : %d" % (label, n))
    if n != 1:
        sys.exit("REFUSING: %s occurs %d times -- nothing has been written" % (label, n))

# ---------- phase 2 ----------
i = txt.index(LOAD_ANCHOR)
ls = txt.rfind("\n", 0, i) + 1
txt = txt[:ls] + LOAD_BLOCK + txt[ls:]
i = txt.index(RUN_ANCHOR)
ls = txt.rfind("\n", 0, i) + 1
txt = txt[:ls] + RUN_BLOCK + txt[ls:]
io.open(T, "w", encoding="utf-8", newline="\n").write(txt)
print("ok   both branches are in")

i = tab.index(CONST_ANCHOR)
le = tab.index("\n", i) + 1
tab = tab[:le] + CONST_BLOCK + tab[le:]
io.open(TAB, "w", encoding="utf-8", newline="\n").write(tab)
print("ok   the prefix constant is in")
