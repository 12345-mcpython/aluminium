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
 * 8009 开拓者•欢愉: the Skill grants 20 【好活当赏】 and the Talent grants 3 笑点 after an attack.
 */
public class TrailblazerElationTest {

    private static final int TRAILBLAZER = 8009;
    private static final int LEVEL = 80;

    @Test
    public void theSkillPaysTwentyGiftAndTheTalentThreeLaughs() {
        Character trailblazer = CharacterFactory.create(TRAILBLAZER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(trailblazer, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        int laughsBefore = battle.partyResourceValue("笑点");
        int giftBefore = trailblazer.getResources().value("好活当赏");
        System.out.println("[8009] before: 笑点=" + laughsBefore + " 好活当赏=" + giftBefore);

        battle.castImmediate(trailblazer.getSkills().get(SkillType.SKILL), trailblazer, List.of(enemy));
        int laughsAfter = battle.partyResourceValue("笑点");
        int giftAfter = trailblazer.getResources().value("好活当赏");
        System.out.println("[8009] after: 笑点=" + laughsAfter + " 好活当赏=" + giftAfter);
        Assertions.assertEquals(giftBefore + 20, giftAfter, "「获得 20 点【好活当赏】」");
        Assertions.assertEquals(laughsBefore + 3, laughsAfter, "「获得 3 个笑点」");
    }
}


