package com.laosun.aluminium.test;

import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Relic 113's four-piece: "当装备者受到攻击或被我方目标消耗生命值后，暴击率提高 8%，持续 2 回合，最多 2 层".
 *
 * <p>Read through the real assembly path. Four things had to agree before this could pass: the rule file, the registry (113 had to leave _unmodelled.json), the pinned
 * census, and the test fixture -- which sat on set 113 and SHADOWED the shipped file, exactly as its own note records happening to set 103.
 */
public class Relic113FourPieceTest {
    private static final int SET = 113;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private TriggerTable.TriggerContext ctx() {
        var wearer = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        return new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0);
    }

    @Test
    public void theFourPieceCarriesTheAuthoredNumbers() {
        double percent = -1;
        int turns = -1;
        int maxStacks = -1;
        for (var rule : RelicTriggerTables.of(SET).at(4).matching(TriggerEvent.TAKING_HIT, ctx())) {
            for (var effect : rule.effects()) {
                if ("MODIFY_ATTR".equals(effect.getOp()) && "CRIT_CHANCE".equals(effect.getAttribute())) {
                    percent = effect.getPercent();
                    turns = effect.getTurns();
                    maxStacks = effect.getMaxStacks();
                    System.out.println("[113/4] id=" + rule.id() + " percent=" + percent + " turns=" + turns
                            + " maxStacks=" + maxStacks);
                }
            }
        }
        Assertions.assertEquals(0.08, percent, 1e-9, "the data row states 8%");
        Assertions.assertEquals(2, turns, 1e-9, "for two turns");
        Assertions.assertEquals(2, maxStacks, 1e-9, "up to two layers");
    }

    @Test
    public void thePriceTriggerReachesTheSameRule() {
        var byHit = RelicTriggerTables.of(SET).at(4).matching(TriggerEvent.TAKING_HIT, ctx());
        var byPrice = RelicTriggerTables.of(SET).at(4).matching(TriggerEvent.HP_CONSUMED, ctx());
        System.out.println("[113/4] rules by hit=" + byHit.size() + " by price=" + byPrice.size());
        Assertions.assertFalse(byHit.isEmpty(), "the hit half exists");
        Assertions.assertFalse(byPrice.isEmpty(), "the price half reaches the same rule");
    }

    @Test
    public void threePiecesYieldNothingAndFourYieldTheRule() {
        Assertions.assertTrue(RelicTriggerTables.of(SET).at(3).matching(TriggerEvent.TAKING_HIT, ctx()).isEmpty(),
                "three pieces is one short of the four-piece bonus");
        Assertions.assertFalse(RelicTriggerTables.of(SET).at(4).matching(TriggerEvent.TAKING_HIT, ctx()).isEmpty(),
                "four pieces satisfies it");
    }
}
