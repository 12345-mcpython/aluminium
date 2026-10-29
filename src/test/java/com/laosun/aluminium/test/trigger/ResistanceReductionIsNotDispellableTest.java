package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.DoubleValue;

/**
 * 「全属性抗性降低 X%」 must not be dispellable as a 增益.
 *
 * <p><b>The bug this pins.</b> {@code StatModifierBuff.of} decides debuff-ness from the sign of the value
 * ({@code percent < 0}), and {@code BuffManager.removeBuffs} -- the op behind 「解除敌方 N 个增益效果」 -- skips
 * debuffs. But {@code RESISTANCE_REDUCTION} is subtracted inside the resistance formula
 * ({@code rawResist -= defender.getAttribute(RESISTANCE_REDUCTION).get()}), so a <b>positive</b> value there means
 * "their resistance is lower": a debuff on the victim. Read by sign alone, every reader of that attribute was
 * filed as a BUFF and could be stripped by any 解除增益.
 *
 * <p><b>Why it was invisible.</b> It needs a unit that both lowers resistance and dispels in one cast, which only
 * 罗刹's Ultimate does -- and there it wiped his own 星魂 6 immediately after applying it. All seven shipped
 * readers carried the same latent defect; only this one had a way to expose it.
 *
 * <p>This is asserted as a <b>contrast inside one cast</b>: the reduction is applied, the dispel runs, and the
 * reduction is still there. Either half alone would pass against the broken build.
 */
public class ResistanceReductionIsNotDispellableTest {

    /** 罗刹 1203: his Ultimate lowers all enemies' resistance (星魂 6) AND dispels one of their buffs. */
    private static final int LUOCHA = 1203;
    private static final int LEVEL = 80;

    /**
     * After his Ultimate, the resistance reduction is still on the enemy.
     */
    @Test
    public void theUltimatesOwnReductionSurvivesItsOwnDispel() {
        Character luocha = CharacterFactory.create(LUOCHA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(luocha), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();

        battle.castImmediate(luocha.getSkills().get(SkillType.ULTRA), luocha, List.of(enemy));

        Assertions.assertEquals(0.2, enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get(), 1e-9,
                "「使敌方全体全属性抗性降低20%」 is a DEBUFF on the enemy, so the same Ultimate's "
                        + "「解除敌方全体1个增益效果」 must not remove it");
    }

    /**
     * The reduction actually reaches the damage formula, rather than sitting on an attribute nothing reads.
     *
     * <p>Guards the other way the fix could be wrong: a modifier filed correctly but landing in the wrong half of
     * the attribute would leave the number present and the damage unchanged.
     */
    @Test
    public void theReductionReachesTheDamageFormula() {
        double plain = damageTaken(false);
        double reduced = damageTaken(true);

        Assertions.assertTrue(plain > 0, "precondition: the hit lands");
        Assertions.assertTrue(reduced > plain,
                "lowering the victim's resistance must raise the damage it takes: " + reduced + " vs " + plain);
    }

    /**
     * Damage one hit of a fixed size deals to a monster, optionally with its all-type resistance reduced.
     *
     * @param reduceResistance whether to apply the reduction first
     * @return the HP lost
     */
    private static double damageTaken(boolean reduceResistance) {
        Character attacker = CharacterFactory.create(LUOCHA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(attacker), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();

        if (reduceResistance) {
            enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION)
                    .addModifier(com.laosun.aluminium.DoubleValue.Modifier.pure(
                            0.2, com.laosun.aluminium.DoubleValue.Modifier.ModifierSource.DEBUFF, 0));
        }

        double before = enemy.getCurrentHp();
        battle.applyDamage(enemy, new com.laosun.aluminium.damage.Damage(attacker, enemy,
                com.laosun.aluminium.enums.DamageElement.IMAGINARY,
                com.laosun.aluminium.enums.DamageType.NORMAL, 1000));
        return before - enemy.getCurrentHp();
    }
}
