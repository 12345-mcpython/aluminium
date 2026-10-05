"""`skill_param:<SKILLTYPE>:<index>`: a magnitude read out of one of the RULE OWNER's own skills (2026-10-02).

Reader: 1403 缇宝's ultimate clause 「受到我方目标攻击后…造成 1 次等同于缇宝 #3% 生命上限的量子属性附加伤害」 -- where #3 is a parameter of HIS ULTIMATE, while the
rider that performs it hangs on an attack event. The two existing readers cannot say it:
  * `cast_skill_param:<index>` (and `percent_from_cast_param`) read the skill that PRODUCED the event -- here the follow-up attack, not the ultimate;
  * a plain `percent` would freeze one level of a value that runs 0.06 -> 0.126.

The shape to mirror is `castParamValue`, measured: `skill.getData().getSkills()`, `caster.skillLevel(skill)` for the row, and the index into that row.
The difference is only WHICH skill: this one names a slot of the owner's own table instead of asking the context.
"""
import io
import re
import sys

TAB = "src/main/java/com/laosun/aluminium/models/TriggerTable.java"
INT = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"

tab = io.open(TAB, encoding="utf-8").read()
interp = io.open(INT, encoding="utf-8").read()

CONST_ANCHOR_RE = re.compile(r'([ \t]*)static final String CAST_SKILL_PARAM_PREFIX = "cast_skill_param:";')
CONST_NEW = '''static final String CAST_SKILL_PARAM_PREFIX = "cast_skill_param:";

    /**
     * {@code skill_param:<SKILLTYPE>:<index>} -- a parameter of one of the RULE OWNER's OWN skills, at its current level (2026-10-02).
     *
     * <p>Reader: 1403 缇宝's ultimate, whose zone rider deals 「等同于缇宝 #3% 生命上限」 damage on somebody else's attack. `cast_skill_param:` reads the skill
     * that PRODUCED the event (the attack), which is the wrong one; this names the slot instead.
     */
    static final String SKILL_PARAM_PREFIX = "skill_param:";'''

LOAD_ANCHOR = "        if (scale.startsWith(TriggerTable.SUMMON_ATTR_PREFIX)) {"
LOAD_NEW = '''        // ⭐ The owner's OWN skill, named by slot (2026-10-02): `<SKILLTYPE>:<index>`. Checked here for the reason the family below gives --
        // `scaleAttribute` is shared with the DAMAGE path, where the subject is already the attacker.
        if (scale.startsWith(TriggerTable.SKILL_PARAM_PREFIX)) {
            String[] parts = scale.substring(TriggerTable.SKILL_PARAM_PREFIX.length()).split(":", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalArgumentException(
                        "Op " + op + " scales off one of the owner's skills but does not name it as <SKILLTYPE>:<index>: \\"" + scale
                                + "\\" (source: " + spec.getSource() + ")");
            }
            try {
                SkillType.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException notASlot) {
                throw new IllegalArgumentException(
                        "Op " + op + " scales off skill slot \\"" + parts[0] + "\\", which is not a SkillType (source: "
                                + spec.getSource() + ")");
            }
            requirePercent(effect, op, spec);
            return;
        }
        if (scale.startsWith(TriggerTable.SUMMON_ATTR_PREFIX)) {'''

RUN_ANCHOR = "        if (effect.getScale().trim().startsWith(TriggerTable.SUMMON_ATTR_PREFIX)) {"
RUN_NEW = '''        if (effect.getScale().trim().startsWith(TriggerTable.SKILL_PARAM_PREFIX)) {
            return shareOf(effect, ctx) * ownerSkillParamValue(effect, ctx) + (effect.getAmount() == null ? 0 : effect.getAmount());
        }
        if (effect.getScale().trim().startsWith(TriggerTable.SUMMON_ATTR_PREFIX)) {'''

HELPER_ANCHOR = "    private static double castParamValue(EffectSpec effect, TriggerContext ctx, String spelled) {"
HELPER_NEW = '''    /**
     * A parameter of one of the RULE OWNER's OWN skills, at that skill's current level (2026-10-02).
     *
     * <p>The sibling of {@link #castParamValue}, and the only difference is which skill: that one reads the skill that PRODUCED the event, this one reads
     * the slot the scale names. Same row lookup (`getData().getSkills()` at `skillLevel`, then the index), so the two cannot drift.
     */
    private static double ownerSkillParamValue(EffectSpec effect, TriggerContext ctx) {
        Character owner = requireCharacterOwner(effect, ctx);
        String[] parts = effect.getScale().trim().substring(TriggerTable.SKILL_PARAM_PREFIX.length()).split(":", 2);
        int index = Integer.parseInt(parts[1].trim());
        SkillType slot = SkillType.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
        Skill named = owner.getSkills().get(slot);
        if (named == null || named.getData() == null || !named.getData().isLoaded()) {
            throw new IllegalStateException("the scale \\"" + effect.getScale() + "\\" reads " + owner.getName()
                    + "'s own " + slot + " skill, and there is no loaded such skill");
        }
        var rows = named.getData().getSkills();
        int row = owner.skillLevel(named) - 1;
        if (row < 0 || row >= rows.size()) {
            throw new IllegalStateException("skill level " + owner.skillLevel(named) + " is outside " + owner.getName()
                    + "'s " + slot + " parameter table (rows=" + rows.size() + ")");
        }
        var values = rows.get(row);
        if (index >= values.size()) {
            throw new IllegalStateException("the scale \\"" + effect.getScale() + "\\" names index " + index
                    + ", which is outside that skill parameter row (size=" + values.size() + ")");
        }
        return values.get(index);
    }

''' + HELPER_ANCHOR

for body, old, label in ((tab, CONST_ANCHOR_RE, "the prefix constant"),
                         (interp, LOAD_ANCHOR, "the load-time anchor"),
                         (interp, RUN_ANCHOR, "the run-time anchor"),
                         (interp, HELPER_ANCHOR, "the sibling helper")):
    n = len(old.findall(body)) if hasattr(old, "findall") else body.count(old)
    print("anchor %-24s : %d" % (label, n))
    if n != 1:
        sys.exit("REFUSING: %s occurs %d times -- nothing written" % (label, n))

tab = CONST_ANCHOR_RE.sub(lambda m: m.group(1) + CONST_NEW, tab, count=1)
interp = interp.replace(LOAD_ANCHOR, LOAD_NEW).replace(RUN_ANCHOR, RUN_NEW).replace(HELPER_ANCHOR, HELPER_NEW)
io.open(TAB, "w", encoding="utf-8", newline="\n").write(tab)
io.open(INT, "w", encoding="utf-8", newline="\n").write(interp)
print("ok   skill_param:<SKILLTYPE>:<index> is wired")
