package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
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
 * 1501 Sparxie, from her own file (2026-09-29, round 206): the technique's AoE and the two Skill Points it restores.
 */
public class SparxieTest {
    private static final int SPARXIE = 1501;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: The technique hits and restores exactly two Skill Points, and only when declared. */
    @Test
    public void theTechniqueHitsAndRestoresSkillPoints() {
        Character sparxie = CharacterFactory.create(SPARXIE, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(sparxie), List.of(enemy), fixed());
        battle.markTechniqueUsed(sparxie);
        double before = enemy.getCurrentHp();

        battle.startBattle();

        Assertions.assertTrue(before - enemy.getCurrentHp() > 0,
                "「对敌方全体造成等同于火花50%攻击力的火属性伤害」");

        Character plain = CharacterFactory.create(SPARXIE, LEVEL);
        Enemy enemy2 = EnemyFactory.create(MONSTER, 90, 1);
        Battle plainBattle = new Battle(List.of(plain), List.of(enemy2), fixed());
        double untouched = enemy2.getCurrentHp();
        plainBattle.startBattle();
        Assertions.assertEquals(untouched, enemy2.getCurrentHp(), 1e-9,
                "「使用秘技后」 -- undeclared, so nothing");
    }

    /** Note: Exactly two Skill Points: spend two first so the pool's cap cannot clamp the grant, then observe it in isolation. */
    @Test
    public void theTechniqueGrantsExactlyTwoSkillPoints() {
        Character sparxie = CharacterFactory.create(SPARXIE, LEVEL);
        Character ally = CharacterFactory.create(1002, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(sparxie, ally), List.of(enemy), fixed());
        battle.markTechniqueUsed(sparxie);
        battle.startBattle();

        // Make room: the pool has a cap, so an unspent pool could clamp a +2 into a smaller gain.
        Assertions.assertTrue(battle.spendSkillPoint(), "the fixture must have a point to spend");
        Assertions.assertTrue(battle.spendSkillPoint(), "and a second one");
        int before = battle.getSkillPoints();

        battle.fireTriggers(TriggerEvent.BATTLE_START, sparxie, sparxie, 0, 0);

        Assertions.assertEquals(before + 2, battle.getSkillPoints(),
                "「并为我方恢复2个战技点」: " + before + " -> " + battle.getSkillPoints());
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
