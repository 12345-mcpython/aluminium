package com.laosun.aluminium.test.content.characters.guinaifen;

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
 * 1210 桂乃芬's Skill clause: 「有 100% 的基础概率使目标与相邻目标陷入灼烧状态…持续 2 回合」.
 */
public class GuinaifenSkillBurnTest {

    private static final int GUINAIFEN = 1210;
    private static final int LEVEL = 80;

    @Test
    public void theSkillBurnsEverythingItHit() {
        Character unit = CharacterFactory.create(GUINAIFEN, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(unit, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Assertions.assertFalse(enemy.getBuffManager().hasState("灼烧"), "precondition: not burning yet");

        battle.castImmediate(unit.getSkills().get(SkillType.SKILL), unit, List.of(enemy));
        System.out.println("[1210] burning=" + enemy.getBuffManager().hasState("灼烧"));
        Assertions.assertTrue(enemy.getBuffManager().hasState("灼烧"), "「陷入灼烧状态」");
    }
}