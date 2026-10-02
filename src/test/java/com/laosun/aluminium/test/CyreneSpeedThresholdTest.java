package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Her talent and her trace both write +20% to the party; both must be counted (1415, 2026-10-02).
 *
 * <p>⭐ FILE-DRIVEN, and the TOTAL is the claim: 0.2 below the threshold (talent alone), 0.4 past it. The measured switch is
 * `max_stacks > 1` -- `StatModifierBuff.isStackable()` is exactly that, and `stackGroupKey()` is the attribute, the
 * modifier type and the source role -- so both writers state 2.
 */
public class CyreneSpeedThresholdTest {
    private static final int OWNER = 1415;
    private static final int MONSTER = 1002011;

    /** ⭐ Talent alone below 180; talent plus trace past it. */
    @Test
    public void theTwoWritersAdd() {
        Assertions.assertEquals(0.2, total(0), 1e-6, "below the threshold, her talent alone");
        Assertions.assertEquals(0.4, total(100), 1e-6, "past 180, both writers must be counted");
    }

    // ==================================================================

    private static double total(double extraSpeed) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        if (extraSpeed > 0) {
            EffectSpec raise = new EffectSpec();
            TriggerSpecs.set(raise, "op", "MODIFY_ATTR");
            TriggerSpecs.set(raise, "attribute", "SPEED");
            TriggerSpecs.set(raise, "amount", extraSpeed);
            TriggerSpecs.set(raise, "permanent", true);
            TriggerSpecs.set(raise, "target", "self");
            owner.setTriggerTable(owner.getTriggerTable()
                    .plus(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("BATTLE_START", List.of(), raise)))));
        }
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        battle.fireTriggers(TriggerEvent.TURN_START, owner, owner, 0, 0);
        battle.processRequests();
        return owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }
}
