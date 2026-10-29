package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Light cone 21018: after the wearer's ultimate, the whole party's action is advanced.
 */
public class DanceDanceDanceTest {
    private static final int CONE = 21018;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    /**
     * Copied from the existing ADVANCE test: a unit's remaining time in the action queue.
     */
    private static double timeRemaining(Battle battle, CanHit target) {
        for (Signal signal : battle.queue().snapshot()) {
            if (signal.getCanHit() == target) {
                return battle.queue().getTimeRemaining(signal);
            }
        }
        return Double.NaN;
    }

    @Test
    public void theSpecCarriesOneInstantPartyAdvance() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
        int pinned = 0;
        for (var rule : unit.getTriggerTable().matching(TriggerEvent.ULT_CAST,
                new TriggerTable.TriggerContext(unit, unit, unit, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("ADVANCE".equals(effect.getOp())) {
                    pinned++;
                    System.out.println("[21018] spec percent=" + effect.getPercent() + " target=" + effect.getTarget());
                    Assertions.assertEquals(0.16, effect.getPercent(), 1e-9, "rank 1 states 16%");
                    Assertions.assertEquals("all_allies", effect.getTarget(), "the whole party");
                }
            }
        }
        Assertions.assertEquals(1, pinned, "exactly one advance rule from this cone");
    }

    @Test
    public void thePartyActsEarlierAndABattleWithoutTheConeDoesNot() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        double before = timeRemaining(battle, ally);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, wearer, enemy, 0, 0));
        double after = timeRemaining(battle, ally);
        System.out.println("[21018] ally av " + before + " -> " + after + " ratio=" + (after / before));
        Assertions.assertFalse(Double.isNaN(before), "precondition: the ally is in the queue");
        // Measured semantics of ADVANCE (established by the 20015 test): a SHARE OF THE CURRENT action value.
        Assertions.assertEquals(1 - 0.16, after / before, 1e-6, "16% off the ally's current action value");

        Character plainAlly = CharacterFactory.create(ALLY, LEVEL);
        Character plainWearer = CharacterFactory.create(WEARER, LEVEL);
        Enemy other = BattleFixture.monster();
        Battle plain = new Battle(List.of(plainWearer, plainAlly), List.of(other), BattleFixture.seeded());
        plain.startBattle();
        double plainBefore = timeRemaining(plain, plainAlly);
        plain.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, plainWearer, other, 0, 0));
        double plainAfter = timeRemaining(plain, plainAlly);
        System.out.println("[21018] without the cone: " + plainBefore + " -> " + plainAfter);
        Assertions.assertEquals(plainBefore, plainAfter, 1e-9, "no cone, no advance (false case)");
    }
}
