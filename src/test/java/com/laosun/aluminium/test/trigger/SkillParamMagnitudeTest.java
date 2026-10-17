package com.laosun.aluminium.test.trigger;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * A {@code HEAL} / {@code SHIELD} whose <b>share and constant both</b> come out of the rule owner's own skill row, at
 * that skill's <b>current level</b>: {@code percent_from_skill_param} + {@code amount_from_skill_param}.
 *
 * <p><b>Why both, and why now.</b> March 7th (1001)'s Skill states a shield of 「等同于三月七 #1% 防御力 + #4」 - the
 * share is #1 and the constant is #4, and both run with the Skill's level (0.38 / 190 at row 1, 0.57 / 760 at row 10).
 * Before this, {@code grantAmount} read the raw {@code percent} and the scale family was a closed set with no skill-row
 * spelling, so a shield could only be written as two literals: frozen at one row while every other number in the file
 * moved with the level. That is a <b>missing capability</b>, not an unwritable sentence - so it was added.
 *
 * <p><b>What each case is for.</b>
 * <ol>
 *   <li>the share follows the level (two levels, two different numbers, both read off the row);</li>
 *   <li>so does the constant, which is the half that used to be a literal - a case that only varied the share would pass
 *       with the constant still frozen;</li>
 *   <li>the flat spelling works on its own (a shield that is ONLY a skill parameter, no scale);</li>
 *   <li>every way of spelling it wrong is refused while the file is read.</li>
 * </ol>
 */
public class SkillParamMagnitudeTest {
    private static final double EPS = 1e-6;

    /**
     * March 7th - her Skill's row is {@code [0.38, 3, 0.3, 190, 5]} at Lv1, so #1 and #4 both change with level.
     */
    private static final int MARCH = 1001;
    private static final int LEVEL = 80;

    // ==================================================================
    // 1. The arithmetic, at two levels
    // ==================================================================

    /**
     * {@code row[0] x DEF + row[3]}, at row 1 and at row 10 - the two halves the document multiplies and adds.
     */
    @Test
    public void theShareAndTheConstantBothFollowTheSkillsOwnLevel() {
        double atOne = shieldOf(marchAtSkillLevel(1));
        double atTen = shieldOf(marchAtSkillLevel(10));

        Character reference = marchAtSkillLevel(1);
        var rows = reference.getSkills().get(SkillType.SKILL).getData().getSkills();
        double defence = reference.getAttribute(AttributeType.DEFENCE).get();

        Assertions.assertEquals(rows.get(0).get(0) * defence + rows.get(0).get(3), atOne, EPS,
                "row 1: " + rows.get(0));
        Assertions.assertEquals(rows.get(9).get(0) * defence + rows.get(9).get(3), atTen, EPS,
                "row 10: " + rows.get(9));
        Assertions.assertNotEquals(atOne, atTen, EPS,
                "if the two levels gave the same shield, nothing was reading the level");

        // The constant is what used to be frozen: a reader that took only the share from the row would land on
        // row[0] x DEF + 190 at BOTH levels, which is neither row's number.
        Assertions.assertNotEquals(rows.get(9).get(0) * defence + rows.get(0).get(3), atTen, EPS,
                "the constant must move with the row as well: " + rows.get(0).get(3) + " is row 1's, not row 10's");
    }

    /**
     * The flat spelling on its own: a shield whose whole value is one parameter of the owner's own skill.
     */
    @Test
    public void aFlatShieldCanBeASkillParameterToo() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "SHIELD");
        TriggerSpecs.set(effect, "amountFromSkillParam", "SKILL:3");
        TriggerSpecs.set(effect, "turns", 3);
        TriggerSpecs.set(effect, "target", "self");

        Character march = Character.builder().cid(MARCH).level(LEVEL)
                .triggerTable(new TriggerTable(MARCH, List.of(TriggerSpecs.rule("BATTLE_START", null, effect))))
                .build();
        Battle battle = new Battle(List.of(march), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();

        double rowValue = march.getSkills().get(SkillType.SKILL).getData().getSkills().get(0).get(3);
        Assertions.assertEquals(rowValue, march.getShield(), EPS,
                "a flat shield of #4 at the skill's own level (row 1 = 190)");
    }

    // ==================================================================
    // 2. Fail fast at load time
    // ==================================================================

    /**
     * Two answers to one question: a literal {@code amount} and a skill parameter at the same time.
     */
    @Test
    public void twoFlatSpellingsAreRefused() {
        EffectSpec effect = shieldEffect();
        TriggerSpecs.set(effect, "amount", 500.0);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(MARCH, List.of(TriggerSpecs.rule("BATTLE_START", null, effect))));
        Assertions.assertTrue(refused.getMessage().contains("amount_from_skill_param"), refused.getMessage());
    }

    /**
     * A constant is an addend to a share, not a magnitude: with a {@code scale} and no share at all, "what is it a share
     * OF" has no answer.
     */
    @Test
    public void aConstantWithoutAShareIsRefused() {
        EffectSpec effect = shieldEffect();
        TriggerSpecs.set(effect, "percentFromSkillParam", null);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(MARCH, List.of(TriggerSpecs.rule("BATTLE_START", null, effect))));
        Assertions.assertTrue(refused.getMessage().contains("share"), refused.getMessage());
    }

    /**
     * A share with no {@code scale} is refused before it becomes "requires amount": a share of nothing is not a number.
     */
    @Test
    public void aShareWithoutAScaleIsRefused() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "SHIELD");
        TriggerSpecs.set(effect, "percentFromSkillParam", "SKILL:0");
        TriggerSpecs.set(effect, "amountFromSkillParam", "SKILL:3");
        TriggerSpecs.set(effect, "turns", 3);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(MARCH, List.of(TriggerSpecs.rule("BATTLE_START", null, effect))));
        Assertions.assertTrue(refused.getMessage().contains("scale"), refused.getMessage());
    }

    /**
     * The spelling {@code "<SKILLTYPE>:<index>"} is checked where the file is read - both halves of it.
     */
    @Test
    public void aMisspelledSlotOrIndexIsRefused() {
        EffectSpec badSlot = shieldEffect();
        TriggerSpecs.set(badSlot, "amountFromSkillParam", "SKIL:3");
        IllegalArgumentException slot = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(MARCH, List.of(TriggerSpecs.rule("BATTLE_START", null, badSlot))));
        Assertions.assertTrue(slot.getMessage().contains("SKIL"), slot.getMessage());

        EffectSpec badIndex = shieldEffect();
        TriggerSpecs.set(badIndex, "amountFromSkillParam", "SKILL:x");
        IllegalArgumentException index = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(MARCH, List.of(TriggerSpecs.rule("BATTLE_START", null, badIndex))));
        Assertions.assertTrue(index.getMessage().contains("index"), index.getMessage());

        EffectSpec noColon = shieldEffect();
        TriggerSpecs.set(noColon, "amountFromSkillParam", "SKILL3");
        IllegalArgumentException shape = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(MARCH, List.of(TriggerSpecs.rule("BATTLE_START", null, noColon))));
        Assertions.assertTrue(shape.getMessage().contains("SKILLTYPE"), shape.getMessage());
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /**
     * Her, with the given Skill level, carrying the rule under test.
     */
    private static Character marchAtSkillLevel(int level) {
        Character.Builder builder = Character.builder().cid(MARCH).level(LEVEL)
                .triggerTable(new TriggerTable(MARCH, List.of(TriggerSpecs.rule("BATTLE_START", null, shieldEffect()))));
        for (int i = 1; i < level; i++) {
            builder = builder.skillLevel(SkillType.SKILL);
        }
        return builder.build();
    }

    /**
     * Starts a battle (so {@code BATTLE_START} fires) and reads the shield she put on herself.
     */
    private static double shieldOf(Character march) {
        Battle battle = new Battle(List.of(march), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        return march.getShield();
    }

    /**
     * {@code SHIELD scale: owner_def}, share from #1, constant from #4 - the sentence's own shape.
     */
    private static EffectSpec shieldEffect() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "SHIELD");
        TriggerSpecs.set(effect, "scale", "owner_def");
        TriggerSpecs.set(effect, "percentFromSkillParam", "SKILL:0");
        TriggerSpecs.set(effect, "amountFromSkillParam", "SKILL:3");
        TriggerSpecs.set(effect, "turns", 3);
        TriggerSpecs.set(effect, "target", "self");
        return effect;
    }
}
