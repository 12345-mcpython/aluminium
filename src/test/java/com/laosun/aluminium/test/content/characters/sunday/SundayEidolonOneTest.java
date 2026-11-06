package com.laosun.aluminium.test.content.characters.sunday;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1313 星期日's 星魂 1:「施放战技时，使目标角色造成伤害时无视目标 16% 的防御力，召唤物造成伤害时无视目标 40% 的防御力，持续 2 回合」.
 *
 * <p>Two DIFFERENT shares on two different units, which is why the engine grew a `target_summon` selector: the ally alone
 * takes 16% and its memosprite alone takes 40%. Measured with a memosprite-carrying ally so both halves exist at once.
 */
public class SundayEidolonOneTest {
    private static final int SUNDAY = 1313;
    private static final int MEMOSPRITE_ALLY = 1402;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;

    @Test
    public void theSkillGivesThePairDifferentDefenceIgnore() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL, false, null, null, 1);
        Character ally = CharacterFactory.create(MEMOSPRITE_ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        // 阿格莱雅 brings 衣匠 out with her Ultimate, so the ally really has a summon to aim at.
        battle.castImmediate(ally.getSkills().get(SkillType.ULTRA), ally, List.of(BattleFixture.dummy()));
        battle.processRequests();
        Summon memosprite = battle.summonOf(ally);
        Assertions.assertNotNull(memosprite, "precondition: the ally has a memosprite");

        battle.castImmediate(sunday.getSkills().get(SkillType.SKILL), sunday, List.of(ally));
        battle.processRequests();

        Assertions.assertEquals(0.16, ally.getAttribute(AttributeType.DEFENCE_IGNORE).get(), EPS,
                "「目标角色…无视 16% 防御力」");
        Assertions.assertEquals(0.4, memosprite.getAttribute(AttributeType.DEFENCE_IGNORE).get(), EPS,
                "「召唤物…无视 40% 防御力」");
    }

    @Test
    public void nothingHappensWithoutTheEidolon() {
        Character sunday = CharacterFactory.create(SUNDAY, LEVEL);
        Character ally = CharacterFactory.create(MEMOSPRITE_ALLY, LEVEL);
        Battle battle = new Battle(List.of(sunday, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.castImmediate(sunday.getSkills().get(SkillType.SKILL), sunday, List.of(ally));
        battle.processRequests();
        Assertions.assertEquals(0.0, ally.getAttribute(AttributeType.DEFENCE_IGNORE).get(), EPS,
                "before 星魂 1 the Skill grants no defence ignore");
    }
}
