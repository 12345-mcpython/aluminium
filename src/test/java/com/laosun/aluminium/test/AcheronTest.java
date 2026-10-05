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
 * 1308 Acheron, from her own file (2026-09-29, round 229): the 【残梦】 resource the document caps at 9, beside a stack it never caps at all.
 */
public class AcheronTest {
    private static final int ACHERON = 1308;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** ⚠ One Slashed Dream per Skill, stopped at the document's nine. */
    @Test
    public void theSkillFeedsSlashedDreamUpToNine() {
        Character acheron = CharacterFactory.create(ACHERON, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(acheron), List.of(enemy), fixed());
        battle.startBattle();

        Assertions.assertEquals(0, dreamOf(acheron), "the document states no initial value, so it starts at 0");
        battle.fireTriggers(TriggerEvent.SKILL_CAST, acheron, enemy, 0, 0);
        Assertions.assertEquals(1, dreamOf(acheron), "「获得1点【残梦】」");

        for (int i = 0; i < 11; i++) {
            battle.fireTriggers(TriggerEvent.SKILL_CAST, acheron, enemy, 0, 0);
        }
        Assertions.assertEquals(9, dreamOf(acheron),
                "「【残梦】达到9点时可激活终结技」 -- twelve casts must still read nine");
    }


    /** The declared resource's value, read through the combatant's own manager. */
    private static int dreamOf(Character unit) {
        return unit.getResources().get("残梦").getValue();
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
