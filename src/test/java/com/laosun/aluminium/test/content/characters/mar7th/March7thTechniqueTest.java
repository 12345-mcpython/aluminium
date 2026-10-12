package com.laosun.aluminium.test.content.characters.mar7th;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.buff.AbstractBuff;
import com.laosun.aluminium.models.buff.ControlBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * March 7th's technique (秘技 冻人的瞬间): a battle-start Freeze on one random enemy.
 *
 * <p><b>Why this needed writing at all.</b> Her file carried fifteen rules and not one of them was the
 * technique, so 「使用秘技后…使随机敌方单体陷入冻结状态」 did nothing in this engine. It was the last clause
 * of her kit that was missing: the shield, the soft aggro, the dispel, the counter, the ultimate's freeze,
 * all six Eidolons and the two trace clauses were already there.
 *
 * <p><b>What it is, in the file.</b>
 * {@code on: BATTLE_START, when: ["self has_state 秘技"], op: APPLY_CONTROL, control: 冻结, turns: 1,
 * base_chance: 1.0, element: Ice, scale: self_attr:ATTACK, percent: 0.5, target: random_enemy} -- the same
 * vocabulary as her ultimate's freeze, which is the point: 「冻结状态」 must not mean one thing when her
 * ultimate applies it and another when her technique does.
 *
 * <p><b>Why the cases are shaped this way.</b>
 * A technique rule only fires when the battle was told the technique was used
 * ({@code Battle.markTechniqueUsed}, then {@code startBattle} turns it into the `秘技` state before any
 * BATTLE_START rule runs). So every case here is a <b>contrast</b> against the same battle without the
 * mark: "the freeze landed" alone would pass even if the rule ignored the gate, because a 100% base chance
 * lands anyway. The difference is what proves the gate.
 *
 * <p>No test anywhere else in the suite covers a technique rule, so this file is also the first pin on
 * that mechanism.
 */
public class March7thTechniqueTest {

    private static final int MARCH_7TH = 1001;
    private static final int LEVEL = 80;

    /**
     * 1003010 states no {@code STAT_CTRL_Frozen} resistance -- unlike Ice Edge (冰锋), which cannot be
     * frozen at all. Sharing this choice with {@code CastAppliedCountTest} keeps the two readers of her
     * freeze comparable.
     */
    private static final int MONSTER = 1003010;

    /**
     * The technique freezes an enemy, and the gate is what pays for it.
     *
     * <p>Stated as the difference between two otherwise identical battles, because the freeze is the only
     * control either battle can produce and the only difference between them is the mark.
     */
    @Test
    public void theTechniqueFreezesAnEnemyAndTheGateIsWhatDidIt() {
        Assertions.assertTrue(frozenCount(true) >= 1,
                "with the technique marked, the battle-start rule must freeze an enemy");
        Assertions.assertEquals(0, frozenCount(false),
                "without the technique the same battle must freeze nobody -- the `self has_state 秘技` "
                        + "gate is the whole reason the freeze happened");
    }

    /**
     * The frozen enemy cannot act: 「冻结状态下，敌方目标不能行动」.
     *
     * <p>Asserted on {@code canAct()} rather than on the buff's presence, because "a control is attached"
     * and "the control stops the turn" are different claims and only the second is the sentence.
     */
    @Test
    public void theFrozenEnemyCannotAct() {
        Battle battle = techniqueBattle(true);
        battle.startBattle();

        Enemy victim = frozenEnemy(battle);
        Assertions.assertNotNull(victim, "precondition: the technique froze somebody");
        Assertions.assertFalse(victim.getBuffManager().canAct(),
                "a frozen unit must not be able to act");
    }

    /**
     * The freeze lasts one turn and carries the 50%-of-ATK Ice damage that rides with it.
     *
     * <p>Two clauses of one sentence: 「持续1回合」 and 「每回合开始时受到等同于三月七50%攻击力的冰属性附加
     * 伤害」. The magnitude is read when the state lands, so it is compared against her own ATK at that
     * moment rather than against a constant.
     */
    @Test
    public void theFreezeCarriesItsDurationAndItsPerTurnDamage() {
        Battle battle = techniqueBattle(true);
        Character march = battle.characters.getFirst();
        battle.startBattle();

        Enemy victim = frozenEnemy(battle);
        Assertions.assertNotNull(victim, "precondition: the technique froze somebody");

        ControlBuff freeze = controlOn(victim);
        Assertions.assertNotNull(freeze, "the control must be a ControlBuff");
        Assertions.assertEquals(1, freeze.duration(), "「持续1回合」");
        Assertions.assertEquals(DamageElement.ICE, freeze.getDotElement(),
                "「冰属性附加伤害」");
        Assertions.assertEquals(march.getAttribute(AttributeType.ATTACK).get() * 0.5, freeze.getDotDamage(),
                1e-6, "「等同于三月七50%攻击力的冰属性附加伤害」 -- read from her ATK when it landed");
    }

    /**
     * The freeze does not survive its own turn, so the survivor acts again.
     *
     * <p>One turn of duration means the state is spent at its first turn boundary; without this case a
     * `turns` value that was written but never consumed would look correct forever -- the same failure the
     * shield had before {@code ShieldBuff} counted its duration.
     */
    @Test
    public void theFreezeIsSpentAfterOneTurn() {
        Battle battle = techniqueBattle(true);
        battle.startBattle();
        Assertions.assertNotNull(frozenEnemy(battle), "precondition: frozen at the start");

        // Play turns until the victim's own turn has come and gone.
        for (int i = 0; i < 6 && !battle.isOver(); i++) {
            battle.stepForward();
            battle.beforeMove();
            battle.afterMove();
        }

        Enemy victim = firstEnemy(battle);
        Assertions.assertNull(controlOn(victim),
                "one turn of freeze must not still be up several turns later");
    }

    /**
     * How many enemies are frozen after a battle start, optionally without the technique.
     *
     * @param withTechnique whether the battle is told the technique was used
     * @return the number of frozen enemies
     */
    private static int frozenCount(boolean withTechnique) {
        Battle battle = techniqueBattle(withTechnique);
        battle.startBattle();
        int n = 0;
        for (Enemy enemy : battle.enemyUnits()) {
            if (controlOn(enemy) != null) {
                n++;
            }
        }
        return n;
    }

    /**
     * A battle against one freezable monster, with the technique marked or not.
     *
     * <p>The RNG is fixed at 0.0 so the base-chance roll always passes and the case measures the rule
     * rather than luck. Note this pins only {@code nextDouble}: {@code random_enemy} draws its target with
     * {@code nextInt}, which still varies -- harmless here because there is a single enemy, and the reason
     * the case with several enemies is not written.
     *
     * @param withTechnique whether to mark the technique as used
     * @return the battle, not started
     */
    private static Battle techniqueBattle(boolean withTechnique) {
        Character march = CharacterFactory.create(MARCH_7TH, LEVEL, false, null, null, 0);
        Enemy monster = EnemyFactory.create(MONSTER, LEVEL, 1);
        monster.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(0));
        monster.setDebuffResist(Map.of());

        Battle battle = new Battle(List.of(march), List.of(monster), BattleFixture.alwaysRoll(0.0));
        if (withTechnique) {
            battle.markTechniqueUsed(march);
        }
        return battle;
    }

    /**
     * The first enemy of a started battle.
     *
     * @param battle the battle
     * @return its first enemy
     */
    private static Enemy firstEnemy(Battle battle) {
        return battle.enemyUnits().getFirst();
    }

    /**
     * The frozen enemy, or {@code null} when none is frozen.
     *
     * @param battle the battle
     * @return the frozen enemy
     */
    private static Enemy frozenEnemy(Battle battle) {
        for (Enemy enemy : battle.enemyUnits()) {
            if (controlOn(enemy) != null) {
                return enemy;
            }
        }
        return null;
    }

    /**
     * The control buff on a unit, or {@code null}.
     *
     * @param unit the unit to inspect
     * @return its control buff
     */
    private static ControlBuff controlOn(Enemy unit) {
        for (AbstractBuff buff : unit.getBuffManager().allBuffsOf(ControlBuff.class)) {
            return (ControlBuff) buff;
        }
        return null;
    }
}
