"""`percent_from_skill_param`, and the one line `modifyAttr` needed for it (2026-10-02).

Reader: 1403 缇宝's ultimate (140303) -- 「受到我方目标攻击后…造成 1 次<b>等同于缇宝 #3% 生命上限</b>的量子属性附加伤害」. #3 is a parameter of HIS ultimate while the rider
hangs on somebody else's attack, so `percent_from_cast_param` (the skill that produced the event) cannot name it, and a literal would freeze one level of a value
that runs 0.06 -> 0.15.

⚠ The NPE this fixes is the SAME one the neighbouring comment already describes: 「The share may come from the skill parameter now (2026-10-02), so "is a share
stated" is NOT `percent != null` -- asking only that sent a `percent_from_cast_param` modifier down the flat `amount` arm and unboxed a null.」 The condition at
`modifyAttr` was widened for that spelling and not for this one, so a rule using this share crashed on a null `amount`.

⚠ NOT landed, and this is a measurement not a guess: `times_from: "hit_count"`. 「每有 1 名目标受到攻击」 needs no repetition, because a rule hanging on a per-hit
event already fires once per target the attack connected with -- measured: a cast hitting one target and a cast hitting two both settled 276.6234305862745 for a
`times_from: "hit_count"` rider, i.e. `hitCount` is 1 on each per-hit context.
"""
import io
import sys

SPEC = "src/main/java/com/laosun/aluminium/beans/EffectSpec.java"
INT = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"

spec = io.open(SPEC, encoding="utf-8").read()
interp = io.open(INT, encoding="utf-8").read()

FIELD_ANCHOR = "    @SerializedName(\"percent_from_cast_param\")"
FIELD_NEW = '''    /**
     * The share itself, read from one of the RULE OWNER's OWN skills as {@code "<SKILLTYPE>:<index>"} (2026-10-02).
     *
     * <p>The sibling of {@link #percentFromCastParam}: that one reads the skill that produced the event, this one reads a slot the rule names -- which is what
     * 「造成 1 次等同于缇宝 #3% 生命上限的…附加伤害」 needs, since #3 belongs to his ULTIMATE while the rider hangs on somebody else's attack.
     */
    @SerializedName("percent_from_skill_param")
    private String percentFromSkillParam;

    @SerializedName("percent_from_cast_param")'''

COPY_ANCHOR = "        copy.percentFromCastParam = this.percentFromCastParam;"
COPY_NEW = """        copy.percentFromCastParam = this.percentFromCastParam;
        copy.percentFromSkillParam = this.percentFromSkillParam;"""

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

# \u26a0 the line the NPE came from: the share may also come from the owner's own skill
MAG_ANCHOR = "        if (effect.getPercent() != null || effect.getPercentFromCastParam() != null) {\n            magnitude = derived ? derivedMagnitude(effect, ctx) : effect.getPercent();"
MAG_NEW = ("        // \u26a0\u26a0 `percent_from_skill_param` is the THIRD way to state a share (2026-10-02), and this condition is the one the comment above warns\n"
           "        // about: leaving a share spelling out of it sends the effect down the flat `amount` arm and unboxes a null. That is exactly what happened here.\n"
           "        if (effect.getPercent() != null || effect.getPercentFromCastParam() != null\n"
           "                || effect.getPercentFromSkillParam() != null) {\n"
           "            magnitude = derived ? derivedMagnitude(effect, ctx) : effect.getPercent();")

# the "exactly one share" check must count the new spelling too
CHECK_ANCHOR = "                } else if ((effect.getPercent() == null && effect.getPercentFromCastParam() == null)"
CHECK_NEW = ("                } else if ((effect.getPercent() == null && effect.getPercentFromCastParam() == null\n"
             "                        && effect.getPercentFromSkillParam() == null)")

for body, old, label in ((spec, FIELD_ANCHOR, "the field"), (spec, COPY_ANCHOR, "the copy line"),
                         (interp, REQ_ANCHOR, "requirePercent"), (interp, SHARE_ANCHOR, "shareOf"),
                         (interp, HELPER_ANCHOR, "the helper head"), (interp, MAG_ANCHOR, "the magnitude arm"),
                         (interp, CHECK_ANCHOR, "the exactly-one-share check")):
    n = body.count(old)
    print("anchor %-28s : %d" % (label, n))
    if n != 1:
        sys.exit("REFUSING: %s occurs %d times -- nothing written" % (label, n))

spec = spec.replace(FIELD_ANCHOR, FIELD_NEW).replace(COPY_ANCHOR, COPY_NEW)
interp = (interp.replace(REQ_ANCHOR, REQ_NEW).replace(SHARE_ANCHOR, SHARE_NEW)
                .replace(HELPER_ANCHOR, HELPER_NEW).replace(MAG_ANCHOR, MAG_NEW).replace(CHECK_ANCHOR, CHECK_NEW))
io.open(SPEC, "w", encoding="utf-8", newline="\n").write(spec)
io.open(INT, "w", encoding="utf-8", newline="\n").write(interp)
print("ok   percent_from_skill_param is wired, with the modifyAttr arm and the share check")

# ---- the judge: the share, and the index that makes it load-bearing ----
JUDGE = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * `percent_from_skill_param: "<SKILLTYPE>:<index>"` (2026-10-02).
 *
 * <p>Reader: 1403 \u7f07\u5b9d's ultimate, whose zone rider deals \u300c\u7b49\u540c\u4e8e\u7f07\u5b9d #3% \u751f\u547d\u4e0a\u9650\u300d damage on somebody
 * else's attack. #3 lives in HIS ultimate, and it runs with level (0.06 -> 0.15), so neither `percent_from_cast_param` (the skill that produced the event) nor a
 * literal can say it.
 *
 * <p>\u2b50 Two readings: the share is multiplied by 生命上限 as the sentence says, and the INDEX is load-bearing -- the neighbouring member of the same row is a
 * different number, so a reader that grabbed the wrong member cannot pass.
 *
 * <p>\u26a0 The first attempt at this crashed with an NPE on a null `amount`, because `modifyAttr` asked "is a share stated?" as `percent != null ||
 * percent_from_cast_param != null` -- the very line its own comment warns about. This judge is what caught it.
 */
public class PercentFromSkillParamTest {
    private static final int LEVEL = 80;
    private static final int TRIBBIE = 1403;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;

    @Test
    public void theShareComesFromHisOwnUltimateAtItsOwnLevel() {
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        Battle battle = new Battle(List.of(tribbie, CharacterFactory.create(ALLY, LEVEL)),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        tribbie = battle.characters.getFirst();

        Skill ultra = tribbie.getSkills().get(SkillType.ULTRA);
        Assertions.assertNotNull(ultra, "precondition: 1403 has an ULTRA skill");
        var row = ultra.getData().getSkills().get(tribbie.skillLevel(ultra) - 1);
        double maxHp = tribbie.getAttribute(AttributeType.HEALTH).get();
        double expected = row.get(2) * maxHp;
        double neighbour = row.get(1) * maxHp;

        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ATTACK");
        TriggerSpecs.set(effect, "scale", "self_attr:HEALTH");
        TriggerSpecs.set(effect, "percentFromSkillParam", "ULTRA:2");
        TriggerSpecs.set(effect, "permanent", Boolean.TRUE);
        TriggerSpecs.set(effect, "target", "self");
        tribbie.setTriggerTable(new TriggerTable(TRIBBIE, List.of(
                TriggerSpecs.rule("TURN_START", List.of(), effect))));

        double before = tribbie.getAttribute(AttributeType.ATTACK).get();
        battle.fireTriggers(TriggerEvent.TURN_START);
        battle.processRequests();
        double gained = tribbie.getAttribute(AttributeType.ATTACK).get() - before;
        System.out.println("[skill_share] row = " + row + " ; maxHp = " + maxHp + " ; expected " + expected
                + " (neighbour " + neighbour + ") ; gained " + gained);

        Assertions.assertEquals(expected, gained, Math.abs(expected) * 1e-6,
                "\\u300c\\u7b49\\u540c\\u4e8e\\u7f07\\u5b9d #3% \\u751f\\u547d\\u4e0a\\u9650\\u300d-- #3 of HIS ultimate, times 生命上限");
        Assertions.assertNotEquals(neighbour, gained, Math.abs(expected) * 1e-6, "and the index is load-bearing");
    }
}
'''
io.open("src/test/java/com/laosun/aluminium/test/PercentFromSkillParamTest.java", "w", encoding="utf-8", newline="\n").write(JUDGE)
print("ok   judge written")
