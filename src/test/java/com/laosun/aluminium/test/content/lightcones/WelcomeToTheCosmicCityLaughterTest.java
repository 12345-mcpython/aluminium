package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillCategory;
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
 * Light cone 2305, its laughter clause: "when casting the Ultimate on oneself, gain 20 [笑点].
 * This effect triggers at most 1 time, and the number of triggers is reset after casting 3 basic attacks".
 *
 * <p>Three rules, and two of them are mutually exclusive on the same event ({@code self_stacks:普攻计数 < 2} and
 * {@code >= 2}), so the third normal attack can only take the RESET branch -- there is no "clear then add" ordering to get
 * wrong.
 */
public class WelcomeToTheCosmicCityLaughterTest {
    private static final int CONE = 23057;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int GRANT = 20;
    private static final int CYCLE = 3;
    private static final String LAUGH = "笑点";
    private static final String FLAG = "笑点已触发";
    private static final String COUNT = "普攻计数";

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

    private void selfUlt(Battle battle) {
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, wearer, 1, 0);
    }

    private void normalAttack(Battle battle) {
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, wearer, enemy, 1, 0, SkillCategory.NORMAL);
    }

    private int laughter() {
        return wearer.getResources().value(LAUGH);
    }

    @Test
    public void aSelfTargetedUltGrantsOncePerCycle() {
        Battle battle = battle(true);
        selfUlt(battle);
        int first = laughter();
        selfUlt(battle);
        int second = laughter();
        for (int i = 0; i < CYCLE; i++) {
            normalAttack(battle);
        }
        selfUlt(battle);
        int afterReset = laughter();
        System.out.println("[23057] first=" + first + " second=" + second + " after " + CYCLE
                + " normals=" + afterReset);
        Assertions.assertEquals(GRANT, first, 1e-9, "a self-targeted Ultimate grants 20");
        Assertions.assertEquals(GRANT, second, 1e-9, "and a second one does NOT -- at most 1 trigger");
        Assertions.assertEquals(2 * GRANT, afterReset, 1e-9,
                "after three normal attacks the count is reset, so the next one grants again");
    }

    @Test
    public void theCounterRunsZeroOneTwoThenZero() {
        Battle battle = battle(true);
        int[] seen = new int[CYCLE + 1];
        seen[0] = wearer.getBuffManager().stacksOf(COUNT);
        for (int i = 1; i <= CYCLE; i++) {
            normalAttack(battle);
            seen[i] = wearer.getBuffManager().stacksOf(COUNT);
            System.out.println("[23057] normal attack " + i + " -> 普攻计数=" + seen[i]);
        }
        Assertions.assertEquals(0, seen[0], "nothing yet");
        Assertions.assertEquals(1, seen[1], "first normal attack counts");
        Assertions.assertEquals(2, seen[2], "second counts");
        Assertions.assertEquals(0, seen[3], "the third RESETS the cycle instead of counting");
    }

    @Test
    public void anEnemyTargetedUltGrantsNothing() {
        Battle battle = battle(true);
        battle.fireTriggers(TriggerEvent.ULT_CAST, wearer, enemy, 1, 0);
        System.out.println("[23057] ult on an enemy: laughter=" + laughter());
        Assertions.assertEquals(0, laughter(), "on oneself is the clause (false case)");
    }

    @Test
    public void aNonNormalAttackDoesNotCount() {
        Battle battle = battle(true);
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, wearer, enemy, 1, 0, SkillCategory.BPSKILL);
        System.out.println("[23057] a Skill used as an attack: 普攻计数="
                + wearer.getBuffManager().stacksOf(COUNT));
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(COUNT), "only basic attacks count");
    }

    @Test
    public void theSpecPinsTheThreeRules() {
        Battle battle = battle(true);
        // matching() EVALUATES conditions (discipline 182): the grant and the count are reachable from a fresh battle,
        // while the RESET branch only holds in a transient state (its gate is "the counter is already full", and the very
        // same event clears it again). That branch is covered BEHAVIOURALLY by theCounterRunsZeroOneTwoThenZero instead of
        // being faked here -- an assertion that has to manufacture an unreachable state would prove nothing about the game.
        for (int i = 0; i < CYCLE; i++) {
            normalAttack(battle);
        }
        int grant = 0;
        int count = 0;
        int reset = 0;
        for (var event : List.of(TriggerEvent.ULT_CAST, TriggerEvent.ALLY_ATTACK)) {
            for (var rule : wearer.getTriggerTable().matching(event,
                    new TriggerTable.TriggerContext(wearer, wearer, wearer, 0, 0, null, battle, SkillCategory.NORMAL))) {
                if (!rule.id().startsWith("cone23057_laughter") && !rule.id().startsWith("cone23057_normal_attack")) {
                    continue;
                }
                System.out.println("[23057] spec rule=" + rule.id() + " on=" + event);
                if (rule.id().endsWith("self_ult")) {
                    grant++;
                    Assertions.assertTrue(rule.effects().stream().anyMatch(e -> "GAIN_RESOURCE".equals(e.getOp())),
                            "the grant is a resource gain");
                } else if (rule.id().endsWith("count")) {
                    count++;
                } else {
                    reset++;
                }
            }
        }
        Assertions.assertEquals(1, grant, "one granting rule");
        Assertions.assertEquals(1, count, "one counting rule");
        Assertions.assertEquals(0, reset,
                "the reset branch is NOT reachable from a settled state -- it is judged by behaviour, not by this half");
    }
}
