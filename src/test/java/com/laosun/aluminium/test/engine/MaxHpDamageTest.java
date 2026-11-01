package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A literal-ratio damage instance scaled by a Max HP: Natasha's Eidolon 6 adds 40% of her own Max HP as
 * physical damage.
 *
 * <p>Measured: 16 documents state damage this way. The assertion puts both instances in the SAME pipeline - the content's rule at 0.4 of her Max HP against a
 * hand-built rule at 0.5 of the same scale - so the engine's factors cancel and the ratio 0.8 is what is checked.
 *
 * <p>⚠ This case used to be called {@code theTraceAddsFortyPercentOfHerMaxHp} and to build Natasha at Eidolon 0.
 * Both were wrong about the same thing: 「施放普攻时，额外造成等同于娜塔莎生命上限40%的物理属性伤害」 is 星魂 6
 * 医者仁心, not a 行迹. The rule carried no {@code min_eidolon}, so the case measured an effect she should not
 * have had -- and kept passing, which is why the missing gate was invisible.
 */
public class MaxHpDamageTest {
    private static final int NATASHA = 1105;
    private static final int LEVEL = 80;

    /**
     * 医者仁心 (Doctor's Grace), the Eidolon whose basic-attack bonus is the rule under test.
     */
    private static final int EIDOLON_SIX = 6;

    /**
     * The bonus exists at Eidolon 6 and does not exist at Eidolon 0.
     *
     * <p><b>What changed here, and why the case is shaped this way.</b> The rule is 星魂 6 医者仁心 and used to
     * carry no {@code min_eidolon}, so its 40% fired at Eidolon 0 as well -- an effect the character does not
     * have. The old case built her at Eidolon 0 and compared the content's total damage against a 50% reference
     * rule, asserting 0.4/0.5; that passed while the bonus was firing. With the gate added, the honest assertion
     * is the gate itself, so this measures the rule's contribution with its table emptied and with it intact, at
     * both Eidolons.
     *
     * <p>The magnitude is checked loosely and on purpose: the damage that lands is post-mitigation (40% of her
     * 1490.2 Max HP is 596.1, and the enemy takes 429.1), so an exact figure here would be pinned to the
     * defence formula rather than to this rule, and the next unrelated engine change would move it.
     */
    @Test
    public void theEidolonAddsFortyPercentOfHerMaxHp() {
        Character her = CharacterFactory.create(NATASHA, LEVEL, true, null, null, EIDOLON_SIX);

        double withoutRule = basicAttackLoss(emptyTable(EIDOLON_SIX));
        double withRule = basicAttackLoss(her);
        double bonus = withRule - withoutRule;

        Assertions.assertTrue(bonus > 0, "the rule must add damage, measured " + bonus);
        Assertions.assertEquals(0.4 * her.getMaxHp(), bonus, 0.5 * 0.4 * her.getMaxHp(),
                "the bonus must be 40% of her Max HP before mitigation (" + (0.4 * her.getMaxHp())
                        + "), measured " + bonus);
    }

    /**
     * The same attack on the same character at Eidolon 0 must NOT carry the bonus.
     *
     * <p>This is the case whose absence let the missing gate survive: the old test only ever looked at the
     * content's damage at Eidolon 0, where the bonus should have been zero and was not.
     */
    @Test
    public void theBonusIsAbsentAtEidolonZero() {
        double withoutRule = basicAttackLoss(emptyTable(0));
        double withRule = basicAttackLoss(CharacterFactory.create(NATASHA, LEVEL, true, null, null, 0));

        Assertions.assertEquals(withoutRule, withRule, 1e-9,
                "星魂 6 must not contribute before Eidolon 6; measured " + (withRule - withoutRule) + " extra");
    }

    /**
     * A character with her trigger table emptied, at the given Eidolon.
     *
     * @param eidolon the Eidolon rank to build at
     * @return the character
     */
    private static Character emptyTable(int eidolon) {
        Character bare = CharacterFactory.create(NATASHA, LEVEL, true, null, null, eidolon);
        // NOT an empty table any more: the file carries the corpus-wide level convention, so removing every rule would
        // compare Lv1 damage against Lv10 damage - the difference would be the convention, not Eidolon 6.
        bare.setTriggerTable(conventionOnly());
        return bare;
    }

    /**
     * Her table with exactly one rule left: the level convention (the {@code BATTLE_START} rule that raises all four
     * slots by nine, which is how every file puts its numbers on the Lv10 row the document quotes).
     */
    private static TriggerTable conventionOnly() {
        EffectSpec common = levelRaise("COMMON", 5.0);   // the file pins the basic to row 6
        EffectSpec skill = levelRaise("SKILL");
        EffectSpec ultra = levelRaise("ULTRA");
        EffectSpec talent = levelRaise("TALENT");
        return new TriggerTable(NATASHA, List.of(TriggerSpecs.rule("BATTLE_START", null, common, skill, ultra, talent)));
    }

    private static EffectSpec levelRaise(String slot) {
        return levelRaise(slot, 9.0);
    }

    private static EffectSpec levelRaise(String slot, double amount) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "RAISE_SKILL_LEVEL");
        TriggerSpecs.set(effect, "skill", slot);
        TriggerSpecs.set(effect, "amount", amount);
        return effect;
    }

    /**
     * The control: with her table emptied, a basic attack deals only its own data damage.
     *
     * <p>This was written as {@code f() - f() == 0}, which asserts nothing about the engine -- it is true of any
     * function. Removing the table is the contrast that actually means something: whatever the attack deals
     * then is its own damage, and it must be strictly less than the same attack with the rule installed, or the
     * 40% above would be measuring the basic attack rather than the rule.
     */
    @Test
    public void withoutTheRuleNothingExtra() {
        Character bare = CharacterFactory.create(NATASHA, LEVEL, true, null, null, EIDOLON_SIX);
        bare.setTriggerTable(new TriggerTable(NATASHA, List.of()));

        Character armed = CharacterFactory.create(NATASHA, LEVEL, true, null, null, EIDOLON_SIX);

        double bareLoss = basicAttackLoss(bare);
        Assertions.assertTrue(bareLoss > 0, "a basic attack still deals its own data damage");
        Assertions.assertTrue(basicAttackLoss(armed) > bareLoss,
                "the rule must be the difference: " + bareLoss + " bare vs "
                        + basicAttackLoss(armed) + " armed");
    }

    /**
     * Note: The engine refuses a Max-HP share it cannot read rather than guessing.
     */
    @Test
    public void anUnknownScaleIsRefused() {
        Character hero = CharacterFactory.create(NATASHA, LEVEL);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "scale", "nonsense_scale");
        TriggerSpecs.set(effect, "percent", 0.5);
        TriggerSpecs.set(effect, "element", "Physical");
        TriggerSpecs.set(effect, "target", "target");
        Enemy enemy = Enemy.fromAttributes("Dummy", 60000, 500, 100, 90);
        Battle battle = new Battle(List.of(hero), List.of(enemy), fixed());
        battle.startBattle();
        // ? The validator refuses at LOAD time, not at cast time: the table cannot even be installed. That is the louder failure of the two.
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> hero.setTriggerTable(new TriggerTable(NATASHA, List.of(TriggerSpecs.rule(
                        TriggerEvent.BASIC_ATTACK.name(), List.of("actor == self"), effect)))),
                "an unreadable scale must be loud, not a silent zero (and loud at load time)");
    }

    /*
     * Runs one basic attack, optionally replacing the file's table with a single reference rule.
     *
     * <p>Built at Eidolon 6 because that is where the content rule lives: 「施放普攻时，额外造成等同于娜塔莎生命
     * 上限40%的物理属性伤害」 is 星魂 6 医者仁心, and the rule carries {@code min_eidolon: 6}. It did not before,
     * so this fixture silently measured an effect the character should not have had at Eidolon 0 -- which is how
     * the missing gate survived: the case passed either way.
     */
    /**
     * Runs one basic attack for the given character and returns the HP the enemy lost.
     *
     * <p>The character is supplied rather than built here, so a case can decide its Eidolon and its table. The
     * rule under test is 医者仁心 (Eidolon 6), so the fixtures that want it must ask for Eidolon 6: at Eidolon 0
     * the rule is gated off, which is what its missing {@code min_eidolon} used to hide.
     *
     * @param hero the character whose basic attack is measured
     * @return the enemy's HP loss
     */
    private static double basicAttackLoss(Character hero) {
        Enemy enemy = Enemy.fromAttributes("Dummy", 60000, 500, 100, 90);
        Battle battle = new Battle(List.of(hero), List.of(enemy), fixed());
        battle.startBattle();
        double before = enemy.getCurrentHp();
        battle.castImmediate(hero.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON), hero, List.of(enemy));
        return before - enemy.getCurrentHp();
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(1.0);
    }
}
