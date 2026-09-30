package com.laosun.aluminium.test;

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
 * Light cone 23064, its last sentence: \u300c\u6bcf\u4e2a\u6ce2\u6b21\u5f00\u59cb\u65f6\u6216\u88c5\u5907\u8005\u6bcf\u65bd\u653e 3 \u6b21\u6b22\u6109\u6280\u540e\uff0c
 * \u6062\u590d 1 \u4e2a\u6218\u6280\u70b9\u300d.
 *
 * <p>\u2b50 Both gates are written "fill first, judge on the full value" (discipline 184): the counting rule stops at the cap and
 * the granting rule fires on the same event once the counter is full, clearing it.
 */
public class Cone23064SkillPointTest {
    private static final int CONE = 23064;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int CYCLE = 3;
    private static final String COUNT = "\u6b22\u6109\u6280\u8ba1\u6570";

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

    private void cast(Battle battle, SkillCategory category) {
        var skill = wearer.getSkills().values().iterator().next();
        var token = battle.beginCast(skill, wearer);
        battle.fireTriggers(TriggerEvent.CAST_SETUP, wearer, enemy, 0, 0, category);
        battle.endCastOutcome();
        battle.endCast(token);
    }

    @Test
    public void aWaveAndThreeElationCastsEachRestoreOne() {
        Battle battle = battle(true);
        Assertions.assertTrue(battle.spendSkillPoint(), "make room first: the pool is capped");
        int before = battle.getSkillPoints();
        battle.fireTriggers(TriggerEvent.WAVE_START);
        int afterWave = battle.getSkillPoints();
        Assertions.assertTrue(battle.spendSkillPoint(), "make room again");
        int beforeCasts = battle.getSkillPoints();
        cast(battle, SkillCategory.ELATION_DAMAGE);
        cast(battle, SkillCategory.ELATION_DAMAGE);
        int afterTwo = battle.getSkillPoints();
        cast(battle, SkillCategory.ELATION_DAMAGE);
        int afterThree = battle.getSkillPoints();
        System.out.println("[23064] wave: " + before + "->" + afterWave + " ; casts: " + beforeCasts + "->"
                + afterTwo + " (two casts) ->" + afterThree + " (three casts)");
        Assertions.assertEquals(before + 1, afterWave, "a wave restores one skill point");
        Assertions.assertEquals(beforeCasts, afterTwo, "two casts are not yet a cycle");
        Assertions.assertEquals(beforeCasts + 1, afterThree, "the third elation cast restores one");
    }

    @Test
    public void aNonElationCastDoesNotCount() {
        Battle battle = battle(true);
        cast(battle, SkillCategory.ULTRA);
        cast(battle, SkillCategory.BPSKILL);
        System.out.println("[23064] after two non-elation casts: \u6b22\u6109\u6280\u8ba1\u6570="
                + wearer.getBuffManager().stacksOf(COUNT));
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(COUNT),
                "the clause counts ELATION casts only (false case)");
    }

    @Test
    public void theCounterRunsOneTwoThenZero() {
        Battle battle = battle(true);
        int[] seen = new int[CYCLE + 1];
        for (int i = 1; i <= CYCLE; i++) {
            cast(battle, SkillCategory.ELATION_DAMAGE);
            seen[i] = wearer.getBuffManager().stacksOf(COUNT);
        }
        System.out.println("[23064] counter after each cast: " + seen[1] + "," + seen[2] + "," + seen[3]);
        Assertions.assertEquals(1, seen[1], "first cast counts");
        Assertions.assertEquals(2, seen[2], "second counts");
        Assertions.assertEquals(0, seen[3], "the third pays out and clears");
    }

    @Test
    public void theSpecPinsTheRulesAndTheCap() {
        Battle battle = battle(true);
        int wave = 0;
        int casts = 0;
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.WAVE_START,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle, null))) {
            if (rule.id().equals("cone23064_skill_point_per_wave")) {
                wave++;
                Assertions.assertTrue(rule.effects().stream().anyMatch(e -> "GAIN_SKILL_POINT".equals(e.getOp())));
            }
        }
        for (var rule : wearer.getTriggerTable().matching(TriggerEvent.CAST_SETUP,
                new TriggerTable.TriggerContext(wearer, wearer, enemy, 0, 0, null, battle,
                        SkillCategory.ELATION_DAMAGE))) {
            if (!rule.id().startsWith("cone23064_")) {
                continue;
            }
            System.out.println("[23064] spec rule=" + rule.id());
            if (rule.id().endsWith("elation_cast_count")) {
                casts++;
                for (var effect : rule.effects()) {
                    if ("ADD_STACK".equals(effect.getOp())) {
                        Assertions.assertEquals(CYCLE, effect.getMaxStacks(), "the counter cycles every 3 casts");
                    }
                }
            }
        }
        Assertions.assertEquals(1, wave, "one wave rule");
        Assertions.assertEquals(1, casts, "one counting rule (the granting one only shows once the counter is full)");
    }
}
