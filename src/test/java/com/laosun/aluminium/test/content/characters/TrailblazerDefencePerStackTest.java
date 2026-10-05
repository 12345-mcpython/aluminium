package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Trailblazer - Destruction (开拓者(毁灭))'s trace Tenacity (坚韧): "each layer of the talent's effect also raises the Trailblazer's DEF by 10%" -- the counter scale again, on a counter the
 * character's own talent marks.
 *
 * <p>Both the counter's name and the tally come from the data: the name is copied out of the JSON by the script that wrote
 * this test, and the stacks are marked by firing the talent's own event with the owner as actor (the rule's `when` says
 * `actor == self`).
 */
public class TrailblazerDefencePerStackTest {
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-9;
    private static final String COUNTER_8001 = "牵制盗垒";
    private static final String COUNTER_8002 = "牵制盗垒";

    /**
     * The added DEFENCE is exactly `percent x base` per layer.
     *
     * <p>Note: Judged as an absolute delta, not as a ratio over the base: the unit already carries about +2.5% DEFENCE from a
     * pre-existing modifier, so `value / baseValue` measured 1.1252 where the clause owns only 0.10 of the base. The delta
     * removes everything this clause does not own.
     */
    @Test
    public void eachLayerAddsTenPercentOfBaseDefence() {
        assertDelta(8001, COUNTER_8001, 1, 0.10);
        assertDelta(8001, COUNTER_8001, 2, 0.20);
        assertDelta(8002, COUNTER_8002, 2, 0.20);
    }

    private static void assertDelta(int cid, String counter, int layers, double shareOfBase) {
        Character trailblazer = CharacterFactory.create(cid, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(trailblazer), List.of(enemy), new Random(0));
        battle.startBattle();
        double base = trailblazer.getAttribute(AttributeType.DEFENCE).baseValue();
        double before = trailblazer.getAttribute(AttributeType.DEFENCE).get();
        for (int i = 0; i < layers; i++) {
            battle.fireTriggers(TriggerEvent.BREAK, trailblazer, enemy, 1, 1000);
        }
        Assertions.assertEquals(layers, trailblazer.getBuffManager().stacksOf(counter),
                "precondition: the talent marked exactly " + layers + " layer(s)");
        double after = trailblazer.getAttribute(AttributeType.DEFENCE).get();
        Assertions.assertEquals(shareOfBase * base, after - before, EPS,
                "\"each layer of the talent's effect also raises the Trailblazer's "
                        + "DEF by 10%\" with " + layers + " layer(s)");
    }
}
