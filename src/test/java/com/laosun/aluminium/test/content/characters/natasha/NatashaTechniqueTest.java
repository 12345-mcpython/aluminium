package com.laosun.aluminium.test.content.characters.natasha;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Natasha's technique (秘技 催眠研习): a random enemy struck, every enemy Weakened.
 *
 * <p><b>What it says.</b> 「立即攻击敌人，进入战斗后对敌方随机单体造成等同于娜塔莎80%攻击力的物理属性伤害，
 * 并有100%的基础概率使敌方每个单体目标陷入虚弱状态。虚弱状态下的敌方目标对我方造成的伤害降低30%，持续1回合。」
 *
 * <p><b>Why it is two different selectors, and why that is the case under test.</b> 「随机单体」 is ONE enemy;
 * 「每个单体目标」 is EVERY enemy. A rule that used one selector for both would be half wrong in a way no other
 * test would notice, so the two halves are measured separately.
 *
 * <p><b>What the Weaken half needs.</b> {@code APPLY_BUFF 虚弱} carries no numbers -- 1406 and 1503 both apply
 * it by name and neither gives it a value. The number lives in {@code MODIFY_ATTR ALL_DAMAGE_TYPE_BOOST -0.3},
 * which is how 1406 writes her own 10%. So the assertion is on the ATTRIBUTE, because a rule that only
 * applied the buff would produce a named state with no effect, and reading the buff's presence would pass.
 *
 * <p><b>Where the gate comes from.</b> {@code self has_state 秘技}, satisfied by
 * {@code Battle.applyTechniqueStates()} before any BATTLE_START rule runs -- so the battle has to be told the
 * technique was used. Every case here is therefore a contrast against the same battle without it.
 */
public class NatashaTechniqueTest {

    private static final int NATASHA = 1105;
    private static final int LEVEL = 80;

    /** A monster with no special resistance, so only the technique is being measured. */
    private static final int MONSTER = 1003010;

    /**
     * Every enemy is Weakened, and the gate is what did it.
     */
    @Test
    public void everyEnemyIsWeakenedWhenTheTechniqueWasUsed() {
        Battle with = techniqueBattle(true);
        Battle without = techniqueBattle(false);
        with.startBattle();
        without.startBattle();

        Assertions.assertEquals(3, weakened(with),
                "「使敌方每个单体目标陷入虚弱状态」 -- all three enemies, not just the one hit");
        Assertions.assertEquals(0, weakened(without),
                "without the technique the same battle must weaken nobody -- the `self has_state 秘技` "
                        + "gate is why it happened");
    }

    /**
     * The Weakened enemies deal less damage, and the reduction is the one the sentence states.
     *
     * <p>Asserted on {@code ALL_DAMAGE_TYPE_BOOST} rather than on the {@code 虚弱} buff, because the buff is a
     * name without a value: a rule that applied only the buff would show the state and change no number, and
     * a case that checked for the state would pass.
     */
    @Test
    public void theWeakenedEnemiesDealThirtyPercentLessDamage() {
        Battle battle = techniqueBattle(true);
        battle.startBattle();

        for (Enemy enemy : battle.enemyUnits()) {
            double boost = enemy.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
            Assertions.assertEquals(-0.3, boost, 1e-9,
                    "「虚弱状态下的敌方目标对我方造成的伤害降低30%」 on " + enemy.getName()
                            + " -- the buff alone would carry no number");
        }
    }

    /**
     * Only ONE enemy takes the strike, not all of them.
     *
     * <p>The other half of the sentence uses a different selector: 「随机单体」 versus 「每个单体目标」. The
     * damage is dealt to one, so exactly one enemy must have lost HP.
     */
    @Test
    public void onlyOneEnemyTakesTheStrike() {
        Battle battle = techniqueBattle(true);
        battle.startBattle();

        int hurt = 0;
        for (Enemy enemy : battle.enemyUnits()) {
            if (enemy.getCurrentHp() < enemy.getMaxHp()) {
                hurt++;
            }
        }
        Assertions.assertEquals(1, hurt,
                "「对敌方随机单体造成…物理属性伤害」 -- one enemy, while the Weaken goes to every enemy");
    }

    /**
     * The strike is Physical and scales from her ATK, so it lands at all.
     *
     * <p>Measured as "somebody lost HP" rather than as a figure: the amount that lands is post-mitigation, and
     * pinning it here would pin the defence formula rather than this rule.
     */
    @Test
    public void theStrikeActuallyLands() {
        Battle battle = techniqueBattle(true);
        battle.startBattle();

        Assertions.assertTrue(battle.enemyUnits().stream().anyMatch(e -> e.getCurrentHp() < e.getMaxHp()),
                "the technique's damage half must deal damage");
    }

    /**
     * Three freezable-in-no-way monsters with no resistances, at the given technique state.
     *
     * @param withTechnique whether the battle is told the technique was used
     * @return the battle, not started
     */
    private static Battle techniqueBattle(boolean withTechnique) {
        Character natasha = CharacterFactory.create(NATASHA, LEVEL, false, null, null, 0);
        List<Enemy> enemies = List.of(
                EnemyFactory.create(MONSTER, LEVEL, 1),
                EnemyFactory.create(MONSTER, LEVEL, 1),
                EnemyFactory.create(MONSTER, LEVEL, 1));

        Battle battle = new Battle(List.of(natasha), enemies, BattleFixture.alwaysRoll(0.0),
                withTechnique ? Battle.Techniques.released(NATASHA) : null);
        return battle;
    }

    /**
     * How many of the battle's enemies carry the {@code 虚弱} state.
     *
     * @param battle the battle
     * @return the count
     */
    private static int weakened(Battle battle) {
        int n = 0;
        for (Enemy enemy : battle.enemyUnits()) {
            if (enemy.getBuffManager().hasState("虚弱")) {
                n++;
            }
        }
        return n;
    }
}
