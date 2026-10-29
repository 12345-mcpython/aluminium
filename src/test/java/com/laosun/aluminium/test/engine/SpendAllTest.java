package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.skill.SkillExecutor;
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
     * The cast route scales with the amount: seven points add far more than one.
     */
    @Test
    public void theRuleScalesWithTheAmount() {
        double one = lossByCast(1);
        double seven = lossByCast(7);
        // Note: Asserted as a DIRECTION, not a multiple: this judge once measured 38.808 for one point and 329.868 for
        // seven through the hand-fired route, and the exact multiple is still not a clean x through this one either
        // (the per-point instants settle through the same zones as the cast around them). Registered, not asserted.
        System.out.println("[spendAll] one point " + one + " ; seven points " + seven);
        Assertions.assertTrue(one > 0, "precondition: the cast with one point lands (" + one + ")");
        Assertions.assertTrue(seven > 5 * one,
                "seven points must add far more than one (" + one + " -> " + seven + ")");
    }

    // ==================================================================

    private static Battle primed(int start) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Character ally = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Battle battle = BattleFixture.battle(List.of(owner, ally), List.of(BattleFixture.monster()));
        battle.startBattle();
        for (int i = 0; i < start; i++) {
            battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, ally, 0, 0));
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
        SkillExecutor.execute(battle, owner.getSkills().get(SkillType.ELATION_SKILL), owner, List.of(enemy));
        battle.processRequests();
        Assertions.assertEquals(0, owner.getResources().value(RES),
                "the cast spends ALL of it (" + start + " -> 0)");
        return before - enemy.getCurrentHp();
    }

}
