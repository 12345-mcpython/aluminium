"""Judge for 1314's live per-stack clauses (round 1665, content side of item 63).

Her file records the gap verbatim (the trace 「绝当品：天赋中每层【当品】额外使翡翠的攻击力提高 0.50%」): "a share of the base, times the
count" had no spelling, and both of her per_stack clauses used to snapshot the count when they were attached.

The reading: start the battle (her rules grant the first stacks and attach the auras), then ADD stacks directly and check
that the auras followed -- with nothing re-attached. Removing `per_stack_live` must freeze both shares.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/JadeLiveGoodsTest.java"

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

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
 * 「每层【当品】额外使翡翠的攻击力提高 0.50%」 follows the layers (2026-10-02).
 *
 * <p>Measured before: the clause had no spelling at all -- ATTACK is a flat attribute, the derived form yields an absolute
 * number and a plain modifier yields ONE layer's share. Both of her per_stack clauses also froze the count at attach time,
 * so layers gained later did not follow. With `per_stack_live` the share is re-read.
 */
public class JadeLiveGoodsTest {
    private static final int JADE = 1314;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String GOODS = "\\u5f53\\u54c1";

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
        double critShare = share(jade, AttributeType.CRIT_ATTACK);
        System.out.println("[jade] layers=" + start + " attackShare=" + attackShare + " critShare=" + critShare);

        // four more layers, added directly: the auras are NOT re-applied
        for (int i = 0; i < 4; i++) {
            jade.getBuffManager().addBuff(new StackBuff(GOODS, 9, true, 50));
        }
        Assertions.assertEquals(start + 4, jade.getBuffManager().stacksOf(GOODS));

        double attackAfter = share(jade, AttributeType.ATTACK);
        double critAfter = share(jade, AttributeType.CRIT_ATTACK);
        System.out.println("[jade] after -> attackShare=" + attackAfter + " critShare=" + critAfter);
        Assertions.assertEquals(0.005 * 4, attackAfter - attackShare, 1e-9,
                "the attack aura has to follow four more layers");
        Assertions.assertEquals(0.024 * 4, critAfter - critShare, 1e-9,
                "and so does the crit-damage one");
    }

    private static double share(Character unit, AttributeType attribute) {
        double base = unit.getAttribute(attribute).baseValue();
        return (unit.getAttribute(attribute).get() - base) / base;
    }
}
''')
print("ok   judge written")
