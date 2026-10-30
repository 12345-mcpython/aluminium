package com.laosun.aluminium.test.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A summon that arrives and leaves <b>before the settle</b> must not announce its arrival.
 *
 * <p><b>Why.</b> {@code Battle.fireSummoned} drains a buffer of units placed since the last settle, and the arrival event
 * fires then - not at the moment of placement. 1413's slot 7 does both halves inside one cast: it summons nothing, but the
 * fight it was recorded in brings her 长夜 back and then dismisses it with 「并使「长夜」消失」 before the next settle. The
 * buffered arrival then fired for a unit that was already gone, and every rule naming {@code summon} resolved to nothing:
 * <pre>Effect targets "summon" but Evernight has no summon on the field (rule "memosprite_advances_when_it_arrives")</pre>
 */
public class SummonedBufferStaleArrivalTest {

    private static Enemy dummy() {
        Enemy enemy = Enemy.fromAttributes("target", 900000, 200, 100, 100);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(900000));
        enemy.heal(900000);
        return enemy;
    }

    private static Random noCrit() {
        return new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
    }

    /**
     * Summon, dismiss before the settle, settle: the arrival event must not be fired for the corpse.
     */
    @Test
    public void anArrivalThatLeftBeforeTheSettleIsNotAnnounced() {
        Character evernight = CharacterFactory.create(1413, 80);
        Battle battle = new Battle(List.of(evernight), new ArrayList<>(List.of(dummy())), noCrit());
        battle.startBattle();

        Summon summon = battle.summonMemosprite(evernight);
        summon.perish();

        Assertions.assertDoesNotThrow(battle::processRequests,
                "the buffered arrival must not fire for a summon that already left");
        Assertions.assertNull(battle.memospriteOf(evernight), "and the field stays empty");
    }
}
