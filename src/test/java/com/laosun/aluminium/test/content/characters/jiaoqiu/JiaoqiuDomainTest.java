package com.laosun.aluminium.test.content.characters.jiaoqiu;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
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
 * 1218 椒丘's Ultimate, the zone's lifetime: 「结界持续 3 回合，自身每回合开始时持续回合数减 1」 and
 * 「当椒丘陷入无法战斗状态时，结界也会被解除」.
 */
public class JiaoqiuDomainTest {

    private static final int JIAOQIU = 1218;
    private static final int LEVEL = 80;
    private static final String DOMAIN = "结界";

    @Test
    public void theDomainLastsThreeOfHerTurns() {
        Battle battle = battle();
        Character jiaoqiu = battle.characters().getFirst();
        battle.castImmediate(jiaoqiu.getSkills().get(SkillType.ULTRA), jiaoqiu,
                List.of(battle.enemies().getFirst()));
        Assertions.assertTrue(jiaoqiu.getBuffManager().hasState(DOMAIN), "「随后开启结界」");
        Assertions.assertEquals(3, jiaoqiu.getBuffManager().allBuffsOf(StateBuff.class).stream()
                .filter(b -> DOMAIN.equals(b.getState())).findFirst().orElseThrow().remainingTurns(), "「持续 3 回合」");

        for (int i = 0; i < 3; i++) {
            jiaoqiu.getBuffManager().beforeMove();
            jiaoqiu.getBuffManager().afterMove();
        }
        Assertions.assertFalse(jiaoqiu.getBuffManager().hasState(DOMAIN), "spent on HER turn boundaries (ticks_on: self)");
    }

    @Test
    public void recastingRefreshesIt() {
        Battle battle = battle();
        Character jiaoqiu = battle.characters().getFirst();
        for (int i = 0; i < 2; i++) {
            battle.castImmediate(jiaoqiu.getSkills().get(SkillType.ULTRA), jiaoqiu,
                    List.of(battle.enemies().getFirst()));
            Assertions.assertEquals(3, jiaoqiu.getBuffManager().allBuffsOf(StateBuff.class).stream()
                    .filter(b -> DOMAIN.equals(b.getState())).findFirst().orElseThrow().remainingTurns(),
                    "「每次施放终结技时重置」 - a recast puts it back to three");
        }
    }

    private static Battle battle() {
        Character jiaoqiu = CharacterFactory.create(JIAOQIU, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(jiaoqiu, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }
}
