package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The laugh-point factor of Elation damage: {@code 1 + 笑点×5/(笑点+240)}, applied only to Elation instances.
 *
 * <p>Two-sided by construction: 240 laugh points must multiply an Elation instance by exactly 3.5
 * (1 + 240*5/480), and must leave a NORMAL instance untouched -- the factor belongs to Elation damage alone.
 */
public class ElationLaughMultiplierTest {
    private static final int PEARL = 1503;
    private static final int MONSTER = 1002011;
    private static final int LAUGHS = 240;          // -> factor 1 + 240*5/(240+240) = 3.5

    @Test
    public void laughPointsMultiplyElationDamageAndLeaveNormalDamageAlone() {
        double elationNone = damage(false, 0);
        double elationLaughs = damage(false, LAUGHS);
        double normalNone = damage(true, 0);
        double normalLaughs = damage(true, LAUGHS);
        System.out.println("[laugh] elation: " + elationNone + " -> " + elationLaughs
                + " (ratio " + (elationLaughs / elationNone) + ") ; normal: " + normalNone + " -> " + normalLaughs);

        Assertions.assertEquals(3.5, elationLaughs / elationNone, 1e-6,
                "240 laugh points mean 1 + 240*5/480 = 3.5");
        Assertions.assertEquals(normalNone, normalLaughs, 1e-9,
                "and a NORMAL instance must not see the Elation factor at all");
    }

    /** Settles one rule-driven instance and returns the damage dealt; `normal` picks the damage type. */
    private static double damage(boolean normal, int laughs) {
        Character pearl = CharacterFactory.create(PEARL, 80);
        var enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(pearl, CharacterFactory.create(1204, 80)), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character mine = battle.characters.getFirst();

        EffectSpec instance = TriggerSpecs.damage(null, 0, null, "target");
        TriggerSpecs.set(instance, "scale", "elation_base");
        TriggerSpecs.set(instance, "percent", 1.0);
        TriggerSpecs.set(instance, "element", "Ice");
        if (!normal) {
            TriggerSpecs.set(instance, "damageType", "ELATION");
        }

        List<TriggerSpec> rules = new ArrayList<>();
        if (laughs > 0) {
            rules.add(TriggerSpecs.rule("TURN_START", List.of("actor == self"),
                    TriggerSpecs.gainResource("笑点", laughs)));
        }
        rules.add(TriggerSpecs.rule("ULT_CAST", List.of("actor == self"), instance));
        mine.setTriggerTable(new TriggerTable(PEARL, rules));
        if (laughs > 0) {
            battle.fireTriggers(TriggerEvent.TURN_START, mine, mine, 0, 0);
        }

        double before = enemy.getCurrentHp();
        battle.fireTriggers(TriggerEvent.ULT_CAST, mine, battle.enemies.getFirst(), 0, 0);
        return before - enemy.getCurrentHp();
    }
}
