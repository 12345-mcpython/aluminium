package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
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
 * "速度大于等于 120 时…之后每超过 1 点速度使自身欢概度提高 1%" (1502:269, 2026-10-02).
 *
 * <p>The reading is the EXCESS over the threshold, not the attribute -- which is exactly what separates this spelling
 * from `self_attr:`. A threshold above the character's own speed must therefore give nothing at all.
 */
public class AboveThresholdTest {
    private static final int OWNER = 1502;
    private static final int MONSTER = 1002011;

    /** The magnitude is a share of the EXCESS, and a threshold above the attribute gives nothing. */
    @Test
    public void theMagnitudeIsTheExcessOverTheThreshold() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        double speed = owner.getAttribute(AttributeType.SPEED).get();
        // Note: Her own speed at Lv80 is 110 (measured), so the threshold here is 100: below 120 on purpose, because the
        // spelling is what is under test, not her ability to reach the document's number without buffs.
        Assertions.assertTrue(speed > 100, "precondition: this character is faster than 100 (" + speed + ")");

        double at100 = boost(100);
        Assertions.assertEquals(0.01 * (speed - 100), at100, 1e-6,
                "1% of the excess over 100 (" + speed + " - 100)");
        Assertions.assertEquals(0, boost(10000), 1e-9, "a threshold above the attribute gives nothing");
        Assertions.assertTrue(boost(105) < at100, "and a higher threshold gives less");
    }

    // ==================================================================

    private static double boost(double threshold) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ALL_DAMAGE_TYPE_BOOST");
        TriggerSpecs.set(effect, "scale", "self_attr_above:SPEED:" + (int) threshold);
        TriggerSpecs.set(effect, "percent", 0.01);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "self");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("BATTLE_START",
                List.of(), effect))));
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return owner.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }
}
