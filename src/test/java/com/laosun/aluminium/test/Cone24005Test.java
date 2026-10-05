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
 * Light cone 24005: after the wearer casts a Skill, the WHOLE party deals 8% more damage for 3 turns.
 *
 * <p>⭐ The ally is the attributable reading: the wearer is 1205 (whose own kit moves too), and the sentence says 我方全体.
 */
public class Cone24005Test {
    private static final int CONE = 24005;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double BOOST = 0.08;

    private Character wearer;
    private Character ally;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private double partyBoost() {
        return wearer.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get()
                + ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }

    @Test
    public void aSkillLiftsTheWholeParty() {
        Battle battle = battle(true);
        double before = partyBoost();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, wearer, enemy, 0, 0);
        double after = partyBoost();
        System.out.println("[24005] party damage boost before=" + before + " after a Skill=" + after);
        Assertions.assertEquals(2 * BOOST, after - before, 1e-9, "8% each for the wearer and the ally");
    }

    @Test
    public void somebodyElsesSkillDoesNotCount() {
        Battle battle = battle(true);
        double before = partyBoost();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, ally, enemy, 0, 0);
        System.out.println("[24005] after the ALLY's Skill: +" + (partyBoost() - before));
        Assertions.assertEquals(0.0, partyBoost() - before, 1e-9, "the wearer's own cast (false case)");
    }

    @Test
    public void theSpecPinsTheShareTheTurnsAndTheTargets() {
        Battle battle = battle(true);
        int pinned = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.SKILL_CAST,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle, null))) {
            if (!rule.id().startsWith("cone24005_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                pinned++;
                System.out.println("[24005] spec attribute=" + effect.getAttribute() + " percent=" + effect.getPercent()
                        + " turns=" + effect.getTurns() + " target=" + effect.getTarget());
                Assertions.assertEquals("ALL_DAMAGE_TYPE_BOOST", effect.getAttribute(), "damage, not speed");
                Assertions.assertEquals(0.08, effect.getPercent(), 1e-9, "8% at rank 1");
                Assertions.assertEquals(3, effect.getTurns(), "for 3 turns");
                Assertions.assertEquals("all_allies", effect.getTarget(), "the whole party");
            }
        }
        Assertions.assertEquals(1, pinned, "one rule from this cone");
    }

    @Test
    public void withoutTheConeNothingMoves() {
        Battle battle = battle(false);
        double before = partyBoost();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, wearer, enemy, 0, 0);
        System.out.println("[24005] without the cone: +" + (partyBoost() - before));
        Assertions.assertEquals(0.0, partyBoost() - before, 1e-9, "no cone, no boost (false case)");
    }
}
