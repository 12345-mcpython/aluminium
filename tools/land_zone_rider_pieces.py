"""Two small pieces the zone rider needs (2026-10-02).

Reader: 1403 缇宝's ultimate (140303) -- 「受到我方目标攻击后，<b>每有1名目标受到攻击</b>，会对**被攻击目标中当前生命值最高的目标**造成 1 次<b>等同于缇宝 #3% 生命上限</b>的量子属性附加伤害」.

  * `times_from: "hit_count"` -- the clause repeats ONCE PER TARGET THAT WAS ATTACKED, which is exactly the event's hit count, and `triggerCount` already
    carries `ctx.hitCount()` (`per_target` multiplies a magnitude by it: `amount * ctx.hitCount()`). `times_from` accepted only `"event_amount"`; this is
    the second reading of "how many times", and putting the count here keeps it a COUNT (the `DAMAGE` op refuses `times` next to `per_target` on purpose).
  * `percent_from_skill_param: "<SKILLTYPE>:<index>"` -- the SHARE itself out of one of the owner's own skills, the sibling of `percent_from_cast_param`
    (which reads the skill that produced the event). 「等同于生命上限的 #3%」 is a PRODUCT of two things the engine can each name: the Max HP (a scale) and
    #3 (a parameter of HIS ultimate).
"""
import io
import re
import sys

SPEC = "src/main/java/com/laosun/aluminium/beans/EffectSpec.java"
INT = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"

spec = io.open(SPEC, encoding="utf-8").read()
interp = io.open(INT, encoding="utf-8").read()

# ---------- 1) the new share field (and its copy) ----------
FIELD_ANCHOR = "    @SerializedName(\"percent_from_cast_param\")"
FIELD_NEW = '''    /**
     * The share itself, read from one of the RULE OWNER's OWN skills as {@code "<SKILLTYPE>:<index>"} (2026-10-02).
     *
     * <p>The sibling of {@link #percentFromCastParam}: that one reads the skill that produced the event, this one reads a slot the rule names -- which is
     * what 「造成 1 次等同于缇宝 #3% 生命上限的…附加伤害」 needs, since #3 belongs to his ULTIMATE while the rider hangs on somebody else's attack.
     */
    @SerializedName("percent_from_skill_param")
    private String percentFromSkillParam;

    @SerializedName("percent_from_cast_param")'''

COPY_ANCHOR = "        copy.percentFromCastParam = this.percentFromCastParam;"
COPY_NEW = """        copy.percentFromCastParam = this.percentFromCastParam;
        copy.percentFromSkillParam = this.percentFromSkillParam;"""

# ---------- 2) requirePercent accepts it ----------
REQ_ANCHOR = "        if (effect.getPercentFromCastParam() != null) {"
REQ_NEW = """        if (effect.getPercentFromSkillParam() != null) {
            if (effect.getPercent() != null || effect.getPercentFromCastParam() != null) {
                throw new IllegalArgumentException(
                        "Op " + op + " states more than one share (\\"percent\\" / \\"percent_from_cast_param\\" / "
                                + "\\"percent_from_skill_param\\"); the share comes from exactly one of them (source: "
                                + spec.getSource() + ")");
            }
            return;
        }
        if (effect.getPercentFromCastParam() != null) {"""

# ---------- 3) shareOf reads it ----------
SHARE_ANCHOR = """    private static double shareOf(EffectSpec effect, TriggerContext ctx) {
        if (effect.getPercent() != null) {
            return effect.getPercent();
        }"""
SHARE_NEW = """    private static double shareOf(EffectSpec effect, TriggerContext ctx) {
        if (effect.getPercent() != null) {
            return effect.getPercent();
        }
        if (effect.getPercentFromSkillParam() != null) {
            // \u2b50 The share out of one of the owner's OWN skills (2026-10-02): 「等同于缇宝 #3% 生命上限」, where #3 lives in HIS ultimate.
            return ownerSkillParamValue(effect, ctx, effect.getPercentFromSkillParam().trim());
        }"""

# ---------- 4) the helper takes the spelled slot (so both callers share it) ----------
HELPER_ANCHOR = """    private static double ownerSkillParamValue(EffectSpec effect, TriggerContext ctx) {
        Character owner = requireCharacterOwner(effect, ctx);
        String[] parts = effect.getScale().trim().substring(TriggerTable.SKILL_PARAM_PREFIX.length()).split(":", 2);"""
HELPER_NEW = """    private static double ownerSkillParamValue(EffectSpec effect, TriggerContext ctx) {
        return ownerSkillParamValue(effect, ctx,
                effect.getScale().trim().substring(TriggerTable.SKILL_PARAM_PREFIX.length()));
    }

    /** The same read, for a caller that spells the slot itself (`percent_from_skill_param`). */
    private static double ownerSkillParamValue(EffectSpec effect, TriggerContext ctx, String spelled) {
        Character owner = requireCharacterOwner(effect, ctx);
        String[] parts = spelled.split(":", 2);"""

# ---------- 5) times_from accepts hit_count ----------
TIMES_ANCHOR = """                    if (!"event_amount".equals(effect.getTimesFrom().trim())) {
                        throw new IllegalStateException("times_from '" + effect.getTimesFrom()
                                + "' is not a spelling this engine has: only \\"event_amount\\"");
                    }
                    times = (int) Math.abs(ctx.amount());
                    if (times <= 0) {
                        return;
                    }"""
TIMES_NEW = """                    String from = effect.getTimesFrom().trim();
                    if ("event_amount".equals(from)) {
                        times = (int) Math.abs(ctx.amount());
                    } else if ("hit_count".equals(from)) {
                        // \u2b50 「每有 1 名目标受到攻击，会…造成 1 次」 (2026-10-02; reader: 1403 \u7f07\u5b9d's zone rider): the repeat count is how many
                        // targets this attack connected with. It belongs HERE and not in `per_target`, which multiplies a magnitude.
                        times = Math.max(0, ctx.hitCount());
                    } else {
                        throw new IllegalStateException("times_from '" + effect.getTimesFrom()
                                + "' is not a spelling this engine has: only \\"event_amount\\" and \\"hit_count\\"");
                    }
                    if (times <= 0) {
                        return;
                    }"""

checks = [(spec, FIELD_ANCHOR, "the field"), (spec, COPY_ANCHOR, "the copy line"),
          (interp, REQ_ANCHOR, "requirePercent"), (interp, SHARE_ANCHOR, "shareOf"),
          (interp, HELPER_ANCHOR, "the helper head"), (interp, TIMES_ANCHOR, "the times_from branch")]
for body, old, label in checks:
    n = body.count(old)
    print("anchor %-20s : %d" % (label, n))
    if n != 1:
        sys.exit("REFUSING: %s occurs %d times -- nothing written" % (label, n))

spec = spec.replace(FIELD_ANCHOR, FIELD_NEW).replace(COPY_ANCHOR, COPY_NEW)
interp = (interp.replace(REQ_ANCHOR, REQ_NEW).replace(SHARE_ANCHOR, SHARE_NEW)
                .replace(HELPER_ANCHOR, HELPER_NEW).replace(TIMES_ANCHOR, TIMES_NEW))
io.open(SPEC, "w", encoding="utf-8", newline="\n").write(spec)
io.open(INT, "w", encoding="utf-8", newline="\n").write(interp)
print("ok   percent_from_skill_param and times_from:hit_count are wired")
