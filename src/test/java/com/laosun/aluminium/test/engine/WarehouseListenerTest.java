package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * "获得该角色即生效，无需上场" -- the load point.
 *
 * <p>Two claims, and both matter: a registered listener IS asked (its rule moves its own panel), and it does NOT take a turn of its
 * own (the queue never contains it). The second is why the listener cannot simply be added to `characters`.
 */
public class WarehouseListenerTest {
    private static final int FIGHTER = 1002;
    private static final int OWNED = 1407;
    private static final int MONSTER = 1002011;

    /** The listener's rule fires; the fighter's panel is untouched; the listener never acts. */
    @Test
    public void theListenerIsHeardAndNeverActs() {
        Character fighter = CharacterFactory.create(FIGHTER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(fighter),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));

        Character owned = CharacterFactory.create(OWNED, 80, false, null, null, 0);
        EffectSpec mark = new EffectSpec();
        TriggerSpecs.set(mark, "op", "MODIFY_ATTR");
        TriggerSpecs.set(mark, "attribute", "ATTACK");
        TriggerSpecs.set(mark, "percent", 0.5);
        TriggerSpecs.set(mark, "permanent", Boolean.TRUE);
        TriggerSpecs.set(mark, "target", "self");
        // Note: A BATTLE_START rule with NO condition: that event carries no actor and no target, and the loader refuses a condition
        // that asks about either.
        owned.setTriggerTable(new TriggerTable(OWNED,
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), mark)), List.of()));
        battle.registerWarehouseListener(owned);

        double ownedAttackBefore = owned.getAttribute(AttributeType.ATTACK).get();
        double fighterAttackBefore = fighter.getAttribute(AttributeType.ATTACK).get();
        battle.startBattle();
        battle.processRequests();
        double ownedGain = owned.getAttribute(AttributeType.ATTACK).get() - ownedAttackBefore;
        double fighterGain = fighter.getAttribute(AttributeType.ATTACK).get() - fighterAttackBefore;
        boolean ownedInQueue = battle.queue.snapshot().stream()
                .anyMatch(signal -> signal.getCanHit() == owned);
        System.out.println("[warehouse] listener gain=" + ownedGain + " fighter gain=" + fighterGain
                + " listener in queue=" + ownedInQueue);

        Assertions.assertTrue(ownedGain > 0,
                "「获得该角色即生效」-- the listener's own rule ran although it is not on the field");
        Assertions.assertEquals(0.0, fighterGain, 1e-9,
                "and the mark landed on the LISTENER's panel, whose 「self」 is the character that owns the clause");
        Assertions.assertFalse(ownedInQueue,
                "「无需上场」-- it must not take a turn, which is why it cannot ride in `characters`");
    }
}
