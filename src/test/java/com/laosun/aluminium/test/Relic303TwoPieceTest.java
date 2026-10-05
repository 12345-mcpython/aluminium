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
 * Relic 303's two-piece: "使装备者的效果命中提高 10%。同时提高装备者等同于当前效果命中 25% 的攻击力，最多提高 25%".
 *
 * <p>An amount that SCALES off the wearer's own attribute and is CAPPED -- both pieces of vocabulary already existed, which is why this registry entry had gone stale.
 */
public class Relic303TwoPieceTest {
    private static final int SET = 303;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private TriggerTable.TriggerContext ctx() {
        var wearer = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        return new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0);
    }

    @Test
    public void theRuleStatesTheScaleTheShareAndTheCap() {
        double percent = -1;
        double cap = -1;
        String scale = null;
        for (var rule : RelicTriggerTables.of(SET).at(2).matching(TriggerEvent.BATTLE_START, ctx())) {
            for (var effect : rule.effects()) {
                if ("MODIFY_ATTR".equals(effect.getOp()) && "ATTACK".equals(effect.getAttribute())) {
                    percent = effect.getPercent();
                    cap = effect.getCapAmount();
                    scale = effect.getScale();
                    System.out.println("[303/2] percent=" + percent + " scale=" + scale + " capAmount=" + cap);
                }
            }
        }
        Assertions.assertEquals("self_attr:EFFECT_HIT_RATE", scale, "scales off the wearer's own EHR");
        Assertions.assertEquals(0.25, percent, 1e-9, "a quarter of it");
        Assertions.assertEquals(0.25, cap, 1e-9, "capped at 25%");
    }

    @Test
    public void theTierNeedsTwoPieces() {
        Assertions.assertFalse(RelicTriggerTables.of(SET).at(2).matching(TriggerEvent.BATTLE_START, ctx()).isEmpty(),
                "two pieces satisfies the tier");
        Assertions.assertTrue(RelicTriggerTables.of(SET).at(1).matching(TriggerEvent.BATTLE_START, ctx()).isEmpty(),
                "one piece is one short");
    }
}
