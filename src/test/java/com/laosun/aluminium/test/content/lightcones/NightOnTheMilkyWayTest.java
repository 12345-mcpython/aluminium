package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** Light cone 23000: a weakness break raises the wearer's damage for one turn (its per-enemy clause is registered). */
public class NightOnTheMilkyWayTest {
    private static final int CONE = 23000;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private Character wearer(boolean withCone) {
        return withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
    }

    @Test
    public void aBreakRaisesTheWearersDamageForOneTurn() {
        Character unit = wearer(true);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        double before = unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.fireTriggers(TriggerEvent.BREAK, unit, enemy, 0, 0);
        double after = unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        System.out.println("[23000] break: damage boost " + before + " -> " + after);
        Assertions.assertEquals(before + 0.3, after, 1e-9, "rank 1 states 30 points on a break");
        int pinned = 0;
        for (var rule : unit.getTriggerTable().matching(TriggerEvent.BREAK,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("ALL_DAMAGE_TYPE_BOOST".equals(effect.getAttribute())) {
                    pinned++;
                    System.out.println("[23000] spec percent=" + effect.getPercent() + " turns=" + effect.getTurns());
                    Assertions.assertEquals(1, effect.getTurns(), "for one turn");
                }
            }
        }
        Assertions.assertEquals(1, pinned, "exactly one such rule from this cone");
    }

    @Test
    public void withoutTheConeNothingMoves() {
        Character unit = wearer(false);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        double before = unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.fireTriggers(TriggerEvent.BREAK, unit, enemy, 0, 0);
        System.out.println("[23000] without the cone: " + before + " -> "
                + unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get());
        Assertions.assertEquals(before, unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "no cone, no boost (false case)");
    }
}
