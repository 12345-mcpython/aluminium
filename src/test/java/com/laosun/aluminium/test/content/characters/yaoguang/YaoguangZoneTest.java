package com.laosun.aluminium.test.content.characters.yaoguang;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1502 爻光's Skill: the zone (「结界」, three turns on HER clock) and 「施放普攻、战技后获得 3 个笑点」, which only pays
 * while the zone is up.
 */
public class YaoguangZoneTest {

    private static final int YAO = 1502;
    private static final int LEVEL = 80;
    private static final String ZONE = "结界";

    @Test
    public void theSkillOpensAZoneThatPaysThreeLaughsPerAction() {
        Character yao = CharacterFactory.create(YAO, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = battle(yao, enemy);

        battle.castImmediate(yao.getSkills().get(SkillType.SKILL), yao, List.of(enemy));
        Assertions.assertTrue(yao.getBuffManager().hasState(ZONE), "「展开结界，持续 3 回合」");
        int after = battle.partyResourceValue("笑点");
        System.out.println("[1502] laughs after the skill = " + after);
        Assertions.assertEquals(3, after, "「施放…战技后获得 3 个笑点」");

        battle.fire(TriggerPayload.Simple.of(TriggerEvent.BASIC_ATTACK, yao, enemy, 1, 0));
        Assertions.assertEquals(6, battle.partyResourceValue("笑点"), "「施放普攻…后获得 3 个笑点」");
    }

    @Test
    public void withoutTheZoneNothingIsPaid() {
        Character yao = CharacterFactory.create(YAO, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = battle(yao, enemy);
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.BASIC_ATTACK, yao, enemy, 1, 0));
        Assertions.assertEquals(0, battle.partyResourceValue("笑点"), "no zone, no laughs");
    }

    private static Battle battle(Character yao, Enemy enemy) {
        Battle battle = new Battle(new ArrayList<>(List.of(yao, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }
}