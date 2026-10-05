"""`skill_param_cid`: a share read out of ANOTHER character's skill row (2026-10-02, round 71).

Round 70 measured the exact shape by failing at it: putting the cid INSIDE the slot spelling (`"1415|SKILL"`) is refused by the loader with

    Op MODIFY_ATTR scales off skill slot "1415|SKILL", which is not a SkillType

-- and that refusal is right. So the cid is an independent field instead: the validator still sees a legal slot, and `ownerSkillParamValue(effect, ctx, spelled)` already receives the
`effect`, so the holder is chosen there. This unblocks three named sentences (slot 19's healing share, slot 24's first sentence, slot 22's last sentence).
"""
import io
import sys

SPEC = "src/main/java/com/laosun/aluminium/beans/EffectSpec.java"
INT = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
JUDGE = "src/test/java/com/laosun/aluminium/test/CrossCidSkillParamTest.java"

spec = io.open(SPEC, encoding="utf-8").read()
interp = io.open(INT, encoding="utf-8").read()

FIELD_ANCHOR = "    private Double amountPercent;"
FIELD_NEW = """    private Double amountPercent;

    /**
     * \u2b50 Whose skill row a {@code skill_param:} / {@code percent_from_skill_param} share is read from, when it is NOT the rule owner's own (2026-10-02).
     *
     * <p>Readers: 1415's odes -- \u300c\u63d0\u9ad8\u6570\u503c\u7b49\u540c\u4e8e\u672c\u6b21\u6cbb\u7597\u6570\u503c\u7684 #1%\u300d (the value is in slot 19's row; the healing is \u98ce\u5807's),
     * the time ode's memosprite boost (value in slot 24; the damage is the memosprite's skill 7), and the ocean ode's overflow sentence (value in slot 22; the attack is \u6d77\u745f\u97f3's).
     *
     * <p>\u26a0 It is a FIELD and not part of the spelling on purpose: measured, a cid inside the slot string is refused --
     * {@code Op MODIFY_ATTR scales off skill slot "1415|SKILL", which is not a SkillType}.
     */
    @com.google.gson.annotations.SerializedName("skill_param_cid")
    private Integer skillParamCid;"""

ACC_ANCHOR = "        copy.amountPercent = amountPercent;"
ACC_NEW = """        copy.amountPercent = amountPercent;
        copy.skillParamCid = skillParamCid;"""

HOLDER_ANCHOR = "            Character owner = requireCharacterOwner(effect, ctx);"
HOLDER_NEW = """            // \u2b50 a share may name ANOTHER character's row (2026-10-02): the field says whose, and the slot stays a legal SkillType so the loader check still means something
            Character owner;
            if (effect.getSkillParamCid() != null) {
                CanHit found = allyWithCid(ctx, effect.getSkillParamCid());
                if (!(found instanceof Character)) {
                    throw new IllegalStateException("the share names cid " + effect.getSkillParamCid()
                            + ", which is not a character in this battle's party");
                }
                owner = (Character) found;
            } else {
                owner = requireCharacterOwner(effect, ctx);
            }"""

for body, old, label in ((spec, FIELD_ANCHOR, "the amountPercent field"), (spec, ACC_ANCHOR, "the copy line")):
    n = body.count(old)
    print("anchor %-24s : %d" % (label, n))
    if n != 1:
        sys.exit("REFUSING: %s occurs %d times -- nothing written" % (label, n))

# \u26a0 the holder line is found by CONTENT and keeps its own indentation -- two anchors in this arc died on guessing whitespace
# \u26a0 the same line appears FIVE times (five readers) -- find the one inside ownerSkillParamValue(EffectSpec, TriggerContext, String)
lines = interp.split("\n")
sig = -1
for i, ln in enumerate(lines):
    if "private static double ownerSkillParamValue(EffectSpec effect, TriggerContext ctx, String spelled) {" in ln:
        sig = i
        break
if sig < 0:
    sys.exit("REFUSING: the three-argument reader was not found")
holder_lines = []
for i in range(sig, min(sig + 30, len(lines))):
    if "Character owner = requireCharacterOwner(effect, ctx);" in lines[i]:
        holder_lines.append(lines[i])
        break
print("anchor %-24s : %d (inside the reader at line %d)" % ("the holder line", len(holder_lines), sig + 1))
if len(holder_lines) != 1:
    sys.exit("REFUSING: no holder line inside that reader")
holder_indent = holder_lines[0][: len(holder_lines[0]) - len(holder_lines[0].lstrip())]
holder_new = "\n".join(
    (holder_indent + ln.strip()) if ln.strip() else ln
    for ln in HOLDER_NEW.split("\n"))

io.open(SPEC, "w", encoding="utf-8", newline="\n").write(spec.replace(FIELD_ANCHOR, FIELD_NEW).replace(ACC_ANCHOR, ACC_NEW))
new_lines = lines[:]
for i in range(sig, min(sig + 30, len(new_lines))):
    if new_lines[i] == holder_lines[0]:
        new_lines[i] = holder_new
        break
io.open(INT, "w", encoding="utf-8", newline="\n").write("\n".join(new_lines))
print("ok   skill_param_cid is wired")

J = '''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A share may be read out of ANOTHER character's skill row (2026-10-02).
 *
 * <p>The recurring structural gap: three sentences name a number that lives in one character's table while the event they modify belongs to another. Every share spelling read
 * either the CAST skill or the OWNER's own skill, so {@code skill_param_cid} now says whose row to read -- a FIELD, because putting the cid in the slot spelling is refused
 * (measured: {@code scales off skill slot "1415|SKILL", which is not a SkillType}).
 *
 * <p>\u2b50 The reading is discriminating by construction: the SAME rule runs twice, once with the field and once without, and the two rows hold different numbers -- so the gain over
 * the attribute's own base must equal each row's value.
 */
public class CrossCidSkillParamTest {
    private static final int LEVEL = 80;
    private static final int OWNER = 1410;
    private static final int OTHER = 1415;
    private static final int MONSTER = 1002011;

    @Test
    public void theShareCanComeFromAnotherCharactersRow() {
        double[] own = run(null);
        double[] cross = run(OTHER);
        System.out.println("[cross_cid] the owner's own row value " + own[1] + " gave a boost of " + own[0]
                + " ; naming cid " + OTHER + " (row value " + cross[1] + ") gave " + cross[0]);

        Assertions.assertEquals(own[1], own[0], 1e-6, "without the field the owner's own row is read");
        Assertions.assertEquals(cross[1], cross[0], 1e-6, "with the field, THAT character's row is read");
        Assertions.assertNotEquals(own[1], cross[1], 1e-9, "precondition: the two rows differ, so the reading discriminates");
    }

    /** [the boost as a share of her own attack, the row value being read] -- the owner's row, or {@code cid}'s. */
    private static double[] run(Integer cid) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character other = CharacterFactory.create(OTHER, LEVEL);

        SkillType slot = SkillType.SKILL;
        var ownSkill = owner.getSkills().get(slot);
        var otherSkill = other.getSkills().get(slot);
        var ownRow = ownSkill.getData().getSkills().get(owner.skillLevel(ownSkill) - 1);
        var otherRow = otherSkill.getData().getSkills().get(other.skillLevel(otherSkill) - 1);
        // \\u2b50 find an index whose two values DIFFER, so the reading can only be right one way
        int index = -1;
        for (int i = 0; i < Math.min(ownRow.size(), otherRow.size()); i++) {
            if (!ownRow.get(i).equals(otherRow.get(i))) {
                index = i;
                break;
            }
        }
        Assertions.assertTrue(index >= 0, "precondition: the two rows differ -- own " + ownRow + " vs other " + otherRow);
        double expected = cid == null ? ownRow.get(index) : otherRow.get(index);

        EffectSpec boost = new EffectSpec();
        TriggerSpecs.set(boost, "op", "MODIFY_ATTR");
        TriggerSpecs.set(boost, "attribute", "ATTACK");
        TriggerSpecs.set(boost, "scale", "skill_param:" + slot + ":" + index);
        TriggerSpecs.set(boost, "percent", 1.0);
        if (cid != null) {
            TriggerSpecs.set(boost, "skillParamCid", cid);
        }
        TriggerSpecs.set(boost, "permanent", Boolean.TRUE);
        TriggerSpecs.set(boost, "target", "self");

        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("TURN_START", List.of(), boost))));

        Battle battle = new Battle(List.of(owner, other),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character subject = battle.characters.get(0);
        double before = subject.getAttribute(AttributeType.ATTACK).get();
        battle.fireTriggers(TriggerEvent.TURN_START);
        battle.processRequests();
        double after = subject.getAttribute(AttributeType.ATTACK).get();
        return new double[]{after / before - 1.0, expected};
    }
}
'''
io.open(JUDGE, "w", encoding="utf-8", newline="\n").write(J)
print("ok   judge written")
