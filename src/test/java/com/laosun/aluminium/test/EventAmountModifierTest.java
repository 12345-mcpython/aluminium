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
 * "每消耗1点战技点…造成的伤害提高 6%" (1306:159, 2026-10-02): `scale: "event_amount"` on a MODIFIER.
 *
 * <p>File-driven character, one hand-built rule on the existing `SKILL_POINT_SPENT` event, and the amount spent is the
 * magnitude -- so 3 points is 3x one point, and no spend is no change at all.
 */
public class EventAmountModifierTest {
    private static final int OWNER = 1306;
    private static final int MONSTER = 1002011;

    /** Three points spent raise the modifier three times as far as one. */
    @Test
    public void theModifierFollowsTheEvent() {
        double one = boostAfterSpending(1);
        double three = boostAfterSpending(3);
        Assertions.assertTrue(one > 0, "precondition: the modifier lands (" + one + ")");
        Assertions.assertEquals(3 * one, three, one * 1e-6,
                "3 points spent is 3x one point (" + one + " -> " + three + ")");
        Assertions.assertEquals(0, boostAfterSpending(0), 1e-9, "and spending nothing changes nothing");
    }

    // ==================================================================

    private static double boostAfterSpending(int spent) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ALL_DAMAGE_TYPE_BOOST");
        TriggerSpecs.set(effect, "scale", "event_amount");
        TriggerSpecs.set(effect, "percent", 0.06);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "self");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("SKILL_POINT_SPENT",
                List.of("actor == self"), effect))));

        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        double before = owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, owner, owner, 0, spent);
        battle.processRequests();
        return owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - before;
    }
}
