package com.laosun.aluminium.test.content.characters.pela;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1106 佩拉's trace 追歼: 「施放战技解除增益效果时，下一次攻击造成的伤害提高 20%」 - a boost that lasts until the next attack.
 */
public class PelaTraceNextAttackTest {

    private static final int PELA = 1106;
    private static final int LEVEL = 80;

    @Test
    public void theSkillArmsTheNextAttackOnly() {
        Character pela = CharacterFactory.create(PELA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(pela, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double before = pela.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();

        battle.castImmediate(pela.getSkills().get(SkillType.SKILL), pela, List.of(enemy));
        double armed = pela.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        System.out.println("[1106] boost " + before + " -> " + armed);
        Assertions.assertEquals(0.2, armed - before, 1e-9, "「下一次攻击造成的伤害提高 20%」");

        // Spend it: the lifetime is `next_attack`, so one attack later it is gone.
        battle.castImmediate(pela.getSkills().get(SkillType.COMMON), pela, List.of(enemy));
        battle.processRequests();
        Assertions.assertEquals(before, pela.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "the boost was spent on that attack");
    }
}
