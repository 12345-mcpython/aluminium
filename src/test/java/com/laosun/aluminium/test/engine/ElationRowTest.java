package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * An Elation skill's row leads with a COUNT: "deal #1 instances of damage, each dealing #2% to a random single enemy ... The last deals #3% ...
 * divided evenly among all enemies".
 *
 * <p>THE INSTRUMENT COUNTS INSTANCES, not damage: the sentence is about a NUMBER of hits, and damage would drag in the crit
 * zone and the Elation boost. A test-only rule on the caster adds one counter stack per damage instance it deals.
 */
public class ElationRowTest {
    private static final int AVENTURINE = 8009;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String COUNTER = "命中次数";

    /** Eight hits plus the final split instance, with a single enemy that every random draw must pick. */
    @Test
    public void theRowSettlesEightHitsAndTheSplit() {
        Assertions.assertEquals(9, instancesFromTheElationRow(), 0,
                "8 damage instances + one final split");
    }

    /** Note: And the same reading must NOT be a single 8x instance, which is what the AOE path did before the branch. */
    @Test
    public void itIsNotOneInstanceOfEightTimesTheShare() {
        Assertions.assertNotEquals(1, instancesFromTheElationRow(),
                "the leading column is a count, not a multiplier");
    }

    // ==================================================================

    private static int instancesFromTheElationRow() {
        Character him = CharacterFactory.create(AVENTURINE, LEVEL, false, null, null, 0);
        EffectSpec counter = new EffectSpec();
        TriggerSpecs.set(counter, "op", "ADD_STACK");
        TriggerSpecs.set(counter, "buff", COUNTER);
        TriggerSpecs.set(counter, "amount", 1.0);
        TriggerSpecs.set(counter, "maxStacks", 99);
        TriggerSpecs.set(counter, "permanent", true);
        TriggerSpecs.set(counter, "target", "self");
        him.setTriggerTable(new TriggerTable(AVENTURINE, List.of(
                TriggerSpecs.rule("DEALING_DAMAGE", List.of("actor == self"), counter))));

        Battle battle = new Battle(List.of(him), List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        battle.castImmediate(him.getSkills().get(SkillType.ELATION_SKILL), him,
                List.of(battle.enemies.getFirst()));
        int instances = him.getBuffManager().stacksOf(COUNTER);
        System.out.println("[" + AVENTURINE + "] damage instances settled by the Elation row: " + instances);
        return instances;
    }
}
