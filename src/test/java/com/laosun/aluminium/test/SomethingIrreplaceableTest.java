package com.laosun.aluminium.test;

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

/**
 * Light cone 23002's second sentence: after a kill OR after being hit, the wearer gains energy and 24% damage for one turn.
 *
 * <p>Two rules, because the two events are different shapes: a kill makes the wearer the ACTOR, being hit makes it the TARGET.
 * The judge exercises both and pins both shares.
 */
public class SomethingIrreplaceableTest {
    private static final int CONE = 23002;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double BOOST = 0.24;

    private Character wearer;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    @Test
    public void bothTriggersPay() {
        for (TriggerEvent event : List.of(TriggerEvent.KILL, TriggerEvent.TAKING_HIT)) {
            Battle battle = battle(true);
            double energy = wearer.getCurrentEnergy();
            double boost = wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
            if (event == TriggerEvent.KILL) {
                battle.fireTriggers(event, wearer, enemy, 0, 0);
            } else {
                battle.fireTriggers(event, enemy, wearer, 0, 100);
            }
            double energyDelta = wearer.getCurrentEnergy() - energy;
            double boostDelta = wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - boost;
            System.out.println("[23002] " + event + ": energy +" + energyDelta + " damage boost +" + boostDelta);
            Assertions.assertTrue(energyDelta > 0, event + " pays energy");
            Assertions.assertEquals(BOOST, boostDelta, 1e-9, event + " pays 24% for one turn");
        }
    }

    @Test
    public void theSpecPinsBothRules() {
        Battle battle = battle(true);
        int pinned = 0;
        for (TriggerEvent event : List.of(TriggerEvent.KILL, TriggerEvent.TAKING_HIT)) {
            for (var rule : wearer.getTriggerTable().matching(event,
                    new TriggerTable.TriggerContext(wearer, wearer, wearer, 0, 0, null, battle, null))) {
                if (!rule.id().startsWith("cone23002_")) {
                    continue;
                }
                for (var effect : rule.effects()) {
                    if ("MODIFY_ATTR".equals(effect.getOp())) {
                        pinned++;
                        System.out.println("[23002] spec " + event + " attribute=" + effect.getAttribute()
                                + " percent=" + effect.getPercent() + " turns=" + effect.getTurns()
                                + " target=" + effect.getTarget());
                        Assertions.assertEquals(BOOST, effect.getPercent(), 1e-9, "24% at rank 1");
                        Assertions.assertEquals(1, effect.getTurns(), "for one turn");
                    }
                }
            }
        }
        Assertions.assertEquals(2, pinned, "one rule per event");
    }

    @Test
    public void aKillBySomebodyElseIsNotTheWearers() {
        Battle battle = battle(true);
        double boost = wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.fireTriggers(TriggerEvent.KILL, enemy, null, 0, 0);   // the enemy did the killing
        System.out.println("[23002] after someone else's kill: boost +"
                + (wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - boost));
        Assertions.assertEquals(0.0, wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - boost, 1e-9,
                "the clause names the WEARER's kill (false case)");
    }
}
