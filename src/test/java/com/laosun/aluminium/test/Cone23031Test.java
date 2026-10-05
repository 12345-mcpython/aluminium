package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
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
 * Light cone 23031: a follow-up attack grants a stack of 【流光】 (max 2), each layer makes Ultimate damage ignore 27% of the
 * target's defence, and the wearer's turn end removes one layer.
 */
public class Cone23031Test {
    private static final int CONE = 23031;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String GLOW = "流光";

    private Character wearer;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    @Test
    public void followUpsStackItToTheCapOfTwo() {
        Battle battle = battle(true);
        battle.fireTriggers(TriggerEvent.FOLLOW_UP, wearer, enemy, 0, 0);
        int one = wearer.getBuffManager().stacksOf(GLOW);
        battle.fireTriggers(TriggerEvent.FOLLOW_UP, wearer, enemy, 0, 0);
        int two = wearer.getBuffManager().stacksOf(GLOW);
        battle.fireTriggers(TriggerEvent.FOLLOW_UP, wearer, enemy, 0, 0);
        int three = wearer.getBuffManager().stacksOf(GLOW);
        System.out.println("[23031] glow after 1/2/3 follow-ups = " + one + "/" + two + "/" + three);
        Assertions.assertEquals(1, one, "a follow-up grants one layer");
        Assertions.assertEquals(2, two, "two follow-ups grant two");
        Assertions.assertEquals(2, three, "and #3 is capped at 2");
    }

    @Test
    public void turnEndRemovesOneLayer() {
        Battle battle = battle(true);
        battle.fireTriggers(TriggerEvent.FOLLOW_UP, wearer, enemy, 0, 0);
        battle.fireTriggers(TriggerEvent.FOLLOW_UP, wearer, enemy, 0, 0);
        int before = wearer.getBuffManager().stacksOf(GLOW);
        battle.fireTriggers(TriggerEvent.TURN_END, wearer, null, 0, 0);
        int after = wearer.getBuffManager().stacksOf(GLOW);
        System.out.println("[23031] glow before turn end = " + before + " after = " + after);
        Assertions.assertEquals(2, before, "two layers first");
        Assertions.assertEquals(1, after, "the turn end removes exactly one");
    }

    @Test
    public void theSpecPinsTheIgnoreAndThePerStackFactor() {
        Battle battle = battle(true);
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle,
                        com.laosun.aluminium.enums.SkillCategory.ULTRA))) {
            if (!rule.id().startsWith("cone23031_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                pinned++;
                System.out.println("[23031] spec attribute=" + effect.getAttribute() + " percent=" + effect.getPercent()
                        + " perStack=" + effect.getPerStack() + " target=" + effect.getTarget());
                Assertions.assertEquals("DEFENCE_IGNORE", effect.getAttribute(), "it ignores defence, not damage");
                Assertions.assertEquals(0.27, effect.getPercent(), 1e-9, "27% per layer at rank 1");
                Assertions.assertTrue(String.valueOf(effect.getPerStack()).contains(GLOW), "scaled by the layers");
            }
        }
        Assertions.assertEquals(1, pinned, "one ultimate rule");
    }
}
