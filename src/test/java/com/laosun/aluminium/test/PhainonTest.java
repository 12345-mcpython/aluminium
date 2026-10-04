package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
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
 * 1408 Phainon, from his own file (2026-09-29, round 222): the 【火种】 resource his transformation kit still lets us declare.
 */
public class PhainonTest {
    private static final int PHAINON = 1408;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** \u26a0 Two Coreflame per Skill, and the document's cap of 12 enforced by exceeding it. */
    @Test
    public void theSkillFeedsCoreflameUpToTwelve() {
        Character phainon = CharacterFactory.create(PHAINON, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(phainon, ally), List.of(enemy), fixed());
        battle.startBattle();

        // ⚠ Updated 2026-10-02: the document DOES state one -- her trace 1408101 「战斗开始时，获得 1 点【火种】」, which is
        // now written, so the pool opens at one. The reading is unchanged in kind: it starts where the sentences say.
        Assertions.assertEquals(1, coreflameOf(phainon),
                "\u300c\u6218\u6597\u5f00\u59cb\u65f6\uff0c\u83b7\u5f97 1 \u70b9\u3010\u706b\u79cd\u3011\u300d");
        battle.fireTriggers(TriggerEvent.SKILL_CAST, phainon, enemy, 0, 0);
        Assertions.assertEquals(3, coreflameOf(phainon),
                "\u300c\u83b7\u5f972\u70b9\u3010\u706b\u79cd\u3011\u300d -- one from the battle start, two from the cast");

        for (int i = 0; i < 9; i++) {
            battle.fireTriggers(TriggerEvent.SKILL_CAST, phainon, enemy, 0, 0);
        }
        // \u26a0 12 -> 15 (2026-10-02, item 39). This expectation was written while the file declared only `max: 12`, and the
        // document's sentence does not stop there: \u300c\u3010\u706b\u79cd\u3011\u8fbe\u5230 12 \u70b9\u65f6\u53ef\u6fc0\u6d3b\u7ec8\u7ed3\u6280\uff0c**\u8fbe\u5230\u4e0a\u9650\u540e\u8fd8\u53ef\u6700\u591a\u6ea2\u51fa 3 \u70b9**\u300d.
        // The test's INTENT is untouched -- the pool is capped, and ten casts cannot run past the ceiling -- but the ceiling now
        // quotes the whole sentence. The allowance itself is pinned from both sides by `CoreflameOverflowTest`.
        Assertions.assertEquals(15, coreflameOf(phainon),
                "\u300c\u8fbe\u5230\u4e0a\u9650\u540e\u8fd8\u53ef\u6700\u591a\u6ea2\u51fa 3 \u70b9\u300d -- ten casts (20 points) stop at 12 + 3");
    }

    /** \u26a0 The technique restores the TEAM's energy (not hers) and grants one Skill Point. */
    @Test
    public void theTechniqueFeedsTheTeamButNotHerself() {
        Character phainon = CharacterFactory.create(PHAINON, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(phainon, ally), List.of(enemy), fixed());
        battle.markTechniqueUsed(phainon);
        battle.startBattle();
        int points = battle.getSkillPoints();

        // A grant cannot be clamped upward, so the claim is an equality -- and it is about the TEAM, not about her.
        double allyBefore = ally.getCurrentEnergy();
        double herBefore = phainon.getCurrentEnergy();

        battle.fireTriggers(TriggerEvent.BATTLE_START, phainon, ally, 0, 0);

        Assertions.assertEquals(25.0, ally.getCurrentEnergy() - allyBefore, 1e-6,
                "\u300c\u4e3a\u6211\u65b9\u961f\u53cb\u6062\u590d25\u70b9\u80fd\u91cf\u300d");
        Assertions.assertEquals(0.0, phainon.getCurrentEnergy() - herBefore, 1e-6,
                "the target is `other_allies`: the energy goes to the TEAM, not to her");
        Assertions.assertEquals(Math.min(points + 1, battle.getSkillPointMax()), battle.getSkillPoints(),
                "\u300c\u83b7\u5f971\u4e2a\u6218\u6280\u70b9\u300d (clamped by the pool's ceiling)");
    }

    /** The declared resource's value, read through the combatant's own manager. */
    private static int coreflameOf(Character unit) {
        return unit.getResources().get("\u706b\u79cd").getValue();
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
