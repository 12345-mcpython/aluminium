package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.buff.StackBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * "每层[当品]额外使翡翠的攻击力提高 0.50%" follows the layers (2026-10-02).
 *
 * <p>Measured before: the clause had no spelling at all -- ATTACK is a flat attribute, the derived form yields an absolute
 * number and a plain modifier yields ONE layer's share. Both of her per_stack clauses also froze the count at attach time,
 * so layers gained later did not follow. With `per_stack_live` the share is re-read.
 */
public class JadeLiveGoodsTest {
    private static final int JADE = 1314;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String GOODS = "当品";

    /** Four more layers mean four more 0.50% on ATTACK and four more 2.40% on CRIT DMG. */
    @Test
    public void theAurasFollowTheLayers() {
        Character jade = CharacterFactory.create(JADE, LEVEL, false, null, null, 0);
        Battle battle = new Battle(List.of(jade),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        int start = jade.getBuffManager().stacksOf(GOODS);
        Assertions.assertTrue(start > 0, "precondition: her rules grant the first layers");
        double attackShare = share(jade, AttributeType.ATTACK);
        // CRIT DMG's base is zero (the 50% everyone starts with is itself a modifier), so its reading is absolute
        double critBefore = jade.getAttribute(AttributeType.CRIT_ATTACK).get();
        System.out.println("[jade] layers=" + start + " attackShare=" + attackShare);

        // four more layers, added directly: the auras are NOT re-applied
        for (int i = 0; i < 4; i++) {
            jade.getBuffManager().addBuff(new StackBuff(GOODS, 9, true, 50));
        }
        Assertions.assertEquals(start + 4, jade.getBuffManager().stacksOf(GOODS));

        double attackAfter = share(jade, AttributeType.ATTACK);
        double critAfter = jade.getAttribute(AttributeType.CRIT_ATTACK).get();
        System.out.println("[jade] after -> attackShare=" + attackAfter);
        Assertions.assertEquals(0.005 * 4, attackAfter - attackShare, 1e-9,
                "the attack aura has to follow four more layers");
        System.out.println("[jade] crit " + critBefore + " -> " + critAfter);
        Assertions.assertEquals(0.024 * 4, critAfter - critBefore, 1e-9,
                "the derived crit-damage clause follows too, in absolute units");
    }

    private static double share(Character unit, AttributeType attribute) {
        double base = unit.getAttribute(attribute).baseValue();
        return (unit.getAttribute(attribute).get() - base) / base;
    }
}
