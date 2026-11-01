package com.laosun.aluminium.test.content.characters.trailblazer;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The Elation Trailblazer's two resource clauses, on BOTH of its files (8009 and 8010 carry the same kit text):
 * the Skill pays 20 【好活当赏】 and the Talent pays 3 笑点 after an attack.
 */
public class TrailblazerElationTest {

    private static final int LEVEL = 80;

    @Test
    public void bothFilesPayTwentyGiftAndThreeLaughs() {
        for (int cid : new int[]{8009, 8010}) {
            Character trailblazer = CharacterFactory.create(cid, LEVEL);
            Enemy enemy = BattleFixture.monster();
            Battle battle = new Battle(new ArrayList<>(List.of(trailblazer, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            int laughsBefore = battle.partyResourceValue("笑点");
            int giftBefore = trailblazer.getResources().value("好活当赏");

            battle.castImmediate(trailblazer.getSkills().get(SkillType.SKILL), trailblazer, List.of(enemy));
            int laughsAfter = battle.partyResourceValue("笑点");
            int giftAfter = trailblazer.getResources().value("好活当赏");
            System.out.println("[" + cid + "] 笑点 " + laughsBefore + " -> " + laughsAfter
                    + " ; 好活当赏 " + giftBefore + " -> " + giftAfter);
            Assertions.assertEquals(giftBefore + 20, giftAfter, cid + ": 「获得 20 点【好活当赏】」");
            Assertions.assertEquals(laughsBefore + 3, laughsAfter, cid + ": 「获得 3 个笑点」");
        }
    }
}