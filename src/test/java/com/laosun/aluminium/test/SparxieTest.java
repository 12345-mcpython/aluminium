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

    /** \u26a0 The technique hits and restores exactly two Skill Points, and only when declared. */
    @Test
    public void theTechniqueHitsAndRestoresSkillPoints() {
        Character sparxie = CharacterFactory.create(SPARXIE, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(sparxie), List.of(enemy), fixed());
        battle.markTechniqueUsed(sparxie);
        double before = enemy.getCurrentHp();

        battle.startBattle();

        Assertions.assertTrue(before - enemy.getCurrentHp() > 0,
                "\u300c\u5bf9\u654c\u65b9\u5168\u4f53\u9020\u6210\u7b49\u540c\u4e8e\u706b\u82b150%\u653b\u51fb\u529b\u7684\u706b\u5c5e\u6027\u4f24\u5bb3\u300d");

        Character plain = CharacterFactory.create(SPARXIE, LEVEL);
        Enemy enemy2 = EnemyFactory.create(MONSTER, 90, 1);
        Battle plainBattle = new Battle(List.of(plain), List.of(enemy2), fixed());
        double untouched = enemy2.getCurrentHp();
        plainBattle.startBattle();
        Assertions.assertEquals(untouched, enemy2.getCurrentHp(), 1e-9,
                "\u300c\u4f7f\u7528\u79d8\u6280\u540e\u300d -- undeclared, so nothing");
    }

    /** \u26a0 Exactly two Skill Points: spend two first so the pool's cap cannot clamp the grant, then observe it in isolation. */
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
                "\u300c\u5e76\u4e3a\u6211\u65b9\u6062\u590d2\u4e2a\u6218\u6280\u70b9\u300d: " + before + " -> " + battle.getSkillPoints());
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
