"""`percent_from_cast_param`: the SHARE itself comes from the cast skill's parameter (2026-10-02).

Why it is needed, measured on 1415 memosprite skill 10 「献予「创世」之诗」:
  「使开拓者•记忆的攻击力提高，提高数值等同于德谬歌生命上限的 #1%」 -- that magnitude is a PRODUCT: `#1` (a parameter of the casting skill, which
  RUNS WITH ITS LEVEL) times the memosprite's Max HP (a runtime attribute). A scale can supply one factor and `percent` the other, so a rule
  could state either one but not both -- `summon_attr:HEALTH` with a literal percent would freeze one level of `#1`.

Design, and why it is small:
  * `EffectSpec` gets `percentFromCastParam` beside `damageParam` / `damageLevel`, the family that already means "read this off the skill";
  * `requirePercent` accepts the new form in place of `percent` (exactly one), which covers every existing call site at once;
  * the value is read through a new `castParamValue`, which is the body of the existing `cast_skill_param:` branch lifted out so the two
    cannot drift -- and the `cast_skill_param:` branch keeps working when `percent` is absent (share defaults to 1).
  ⚠ Only the two branches I authored are touched; the rest of `derivedMagnitude` is left exactly as it was.
"""
import io
import sys

SPEC = "src/main/java/com/laosun/aluminium/beans/EffectSpec.java"
INT = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"

FIELD = '''
    /**
     * The share itself, read from the parameter table of the skill that produced the event (zero-based index into its row), at the
     * caster's CURRENT level -- 「等同于德谬歌生命上限的 <b>#1%</b>」, where #1 runs with the level. Exactly one of this and
     * {@code percent} may be stated. ⚠ It exists because some sentences multiply a skill parameter BY an attribute
     * ({@code "scale": "summon_attr:HEALTH"}), and a single {@code scale} can only name one factor.
     */
    @SerializedName("percent_from_cast_param")
    private Integer percentFromCastParam;
'''

HELPER_EARLY = '''        if (effect.getPercentFromCastParam() != null) {
            if (effect.getPercent() != null) {
                throw new IllegalArgumentException(
                        "Op " + op + " states BOTH \\"percent\\" and \\"percent_from_cast_param\\"; the share comes from one of them"
                                + " (source: " + spec.getSource() + ")");
            }
            return;
        }
'''

CAST_PARAM_HELPER = '''
    /**
     * The value of one parameter of the skill that produced the event, at the caster's CURRENT level (2026-10-02).
     *
     * <p>⚠ Shared by the {@code cast_skill_param:<index>} scale and by {@code percent_from_cast_param}, so the two cannot drift: the damage
     * path reads the same row the same way (`multiplierOf` -- `skill.getData().getSkills()`, `attacker.skillLevel(skill)`, `level - 1`).
     */
    private static double castParamValue(EffectSpec effect, TriggerContext ctx, String spelled) {
        int index = Integer.parseInt(spelled);
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
            throw new IllegalStateException("index " + index + " is outside that skill parameter row (size="
                    + values.size() + ")");
        }
        return values.get(index);
    }

    /** The share a magnitude is multiplied by: the stated `percent`, or the cast skill's own parameter. */
    private static double shareOf(EffectSpec effect, TriggerContext ctx) {
        if (effect.getPercent() != null) {
            return effect.getPercent();
        }
        String spelled = effect.getPercentFromCastParam() == null ? null
                : String.valueOf(effect.getPercentFromCastParam());
        if (spelled == null) {
            throw new IllegalStateException("a magnitude needs a share: neither \\"percent\\" nor \\"percent_from_cast_param\\" is stated");
        }
        return castParamValue(effect, ctx, spelled);
    }
'''

# ---------- phase 1 ----------
spec = io.open(SPEC, encoding="utf-8").read()
txt = io.open(INT, encoding="utf-8").read()
a1, a2, a3 = "    private Integer damageParam;", \
             "    private static void requirePercent(EffectSpec effect, String op, TriggerSpec spec) {", \
             "        if (effect.getScale().trim().startsWith(TriggerTable.CAST_SKILL_PARAM_PREFIX)) {"
for path, anchor, label in ((SPEC, a1, "the EffectSpec field"), (INT, a2, "requirePercent"), (INT, a3, "the cast_skill_param branch")):
    body = spec if path == SPEC else txt
    n = body.count(anchor)
    print("anchor %-28s : %d" % (label, n))
    if n != 1:
        sys.exit("REFUSING: %s occurs %d times -- nothing written" % (label, n))

# ---------- phase 2 ----------
# 1) the field
i = spec.index(a1)
le = spec.index("\n", i) + 1
spec = spec[:le] + FIELD + spec[le:]
io.open(SPEC, "w", encoding="utf-8", newline="\n").write(spec)
print("ok   the field")

# 2) requirePercent accepts the new form
i = txt.index(a2)
le = txt.index("\n", i) + 1
txt = txt[:le] + HELPER_EARLY + txt[le:]
print("ok   requirePercent")

# 3) the branch body becomes a call, and the helpers go in beside it
start = txt.index(a3)
end = txt.index("            return effect.getPercent() * values.get(index)", start)
old_body_end = txt.index("\n        }\n", end) + len("\n        }\n")   # Note: include the branch own closing brace, or a stray one closes the method
new_branch = ('        if (effect.getScale().trim().startsWith(TriggerTable.CAST_SKILL_PARAM_PREFIX)) {\n'
              '            // The skill that produced THIS event, and its parameter at the CURRENT level (1415 memosprite skill 10).\n'
              '            int index = Integer.parseInt(\n'
              '                    effect.getScale().trim().substring(TriggerTable.CAST_SKILL_PARAM_PREFIX.length()).trim());\n'
              '            double share = effect.getPercent() == null ? 1.0 : effect.getPercent();\n'
              '            return share * castParamValue(effect, ctx, String.valueOf(index))\n'
              '                    + (effect.getAmount() == null ? 0 : effect.getAmount());\n'
              '        }\n')
txt = txt[:start] + new_branch + txt[old_body_end:]
print("ok   the cast_skill_param branch")

# 4) the two helpers, just before `derivedMagnitude`
anchor = "    private static double derivedMagnitude(EffectSpec effect, TriggerContext ctx) {"
if txt.count(anchor) != 1:
    sys.exit("REFUSING: the derivedMagnitude anchor -- nothing more has been written")
i = txt.index(anchor)
ls = txt.rfind("\n", 0, i) + 1
txt = txt[:ls] + CAST_PARAM_HELPER + txt[ls:]
print("ok   the helpers")

# 5) the summon_attr branch uses the share
old_ret = "            return effect.getPercent() * fielded.getAttribute(from).get()"
if txt.count(old_ret) != 1:
    sys.exit("REFUSING: the summon_attr return -- the earlier pieces are in, this one is not")
txt = txt.replace(old_ret, "            return shareOf(effect, ctx) * fielded.getAttribute(from).get()")
io.open(INT, "w", encoding="utf-8", newline="\n").write(txt)
print("ok   the summon_attr share")
