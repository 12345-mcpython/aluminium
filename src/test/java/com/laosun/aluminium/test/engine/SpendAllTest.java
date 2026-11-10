package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 「每消耗1点[热意]额外 1 次 21%」 - the points a cast is about to spend.
 *
 * <p>Note: This class used to ask WHICH ROUTE serves the clause, and the answer it recorded was the hand-fired
 * {@code RESOURCE_CHANGED} one. That route is gone: the clause now lives on the cast itself (its own sentence is
 * "施放时会消耗所有【热意】，每消耗1点…"), where the counter is read at dispatch time - the value that cast is about to
 * spend. Both cases here therefore drive the real cast, at two amounts.
 *
 * <p>⚠ 2026-11-11: that sentence is the <b>enhanced</b> row (151321). The base 【举杯！敬炽烈一夏】(151320) carries no
 * spend clause, so the cast driven below is data slot 21 - before the correction this case measured the base row.
 */
public class SpendAllTest {
    private static final int OWNER = 1513;
    private static final int ALLY = 1404;
    private static final String RES = "热意";

    /**
     * The cast spends everything.
     */
    @Test
    public void theCastSpendsAllFervor() {
        Assertions.assertTrue(lossByCast(1) > 0, "precondition: the cast lands");
        Assertions.assertTrue(lossByCast(7) > 0, "and for seven points too");
    }

    /**
     * The cast route scales with the amount: more points pay strictly more.
     *
     * <p>⚠ The measured shape, not a guess: the per-point instance is ELATION damage, so its SIZE is multiplied by the
     * laugh snapshot of the moment - and that snapshot grows with the 笑点 the same priming grants. So there is no clean
     * "one unit per point" here (measured: the 1-point delta was 62.4 and the 7-point delta 1207.7, a 19.3x on a 6x
     * difference in points). What can be asserted honestly is monotone growth plus "far more than one extra point".
     */
    @Test
    public void theRuleScalesWithTheAmount() {
        double one = lossByCast(1);
        double two = lossByCast(2);
        double seven = lossByCast(7);
        System.out.println("[spendAll] one point " + one + " ; two points " + two + " ; seven points " + seven);
        Assertions.assertTrue(one > 0, "precondition: the cast with one point lands (" + one + ")");
        Assertions.assertTrue(two > one, "two points pay strictly more than one (" + one + " -> " + two + ")");
        Assertions.assertTrue(seven > two, "seven points pay more again (" + two + " -> " + seven + ")");
        Assertions.assertTrue(seven - one > 5 * (two - one),
                "seven points pay far more than a single extra instance (" + (one) + " -> " + (seven) + ")");
    }

    // ==================================================================

    private static Battle primed(int start) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Character ally = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Battle battle = BattleFixture.battle(List.of(owner, ally), List.of(BattleFixture.monster()));
        battle.startBattle();
        for (int i = 0; i < start; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, ally, 0, 0));
        }
        battle.processRequests();
        return battle;
    }

    private static double lossByCast(int start) {
        Battle battle = primed(start);
        Character owner = (Character) battle.allies().get(0);
        Enemy enemy = (Enemy) battle.enemies().get(0);
        Assertions.assertEquals(start, owner.getResources().value(RES),
                "precondition: her own rule primed it to " + start);
        double before = enemy.getCurrentHp();
        // The clause 「施放时会消耗所有【热意】，每消耗1点…」 belongs to the ENHANCED row (151321), not to the base
        // 【举杯！敬炽烈一夏】(151320): only the 「All in！」 block carries it. The cast driven here is data slot 21, the
        // same row the talent swaps in for the next Elation cast.
        SkillExecutor.execute(battle, new com.laosun.aluminium.skill.DefaultSkill(OWNER, 21, 80), owner,
                List.of(enemy));
        battle.processRequests();
        Assertions.assertEquals(0, owner.getResources().value(RES),
                "the cast spends ALL of it (" + start + " -> 0)");
        return before - enemy.getCurrentHp();
    }

}
