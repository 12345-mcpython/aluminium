package com.laosun.aluminium.test.content.relics;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The two relic clauses that were still listed as open: 129's laugh stair and 312's same-element half.
 *
 * <p><b>129 (闪耀功勋的魔法少女) 4-piece, second sentence</b> - 「我方每累计获得 5 点笑点，造成的欢愉伤害额外无视 1% 防御，最多 10 层」.
 * A stair on the 笑点 counter: one layer per five points, and the sentence caps it at ten.
 *
 * <p><b>312 (梦想之地匹诺康尼) 4-piece</b> - 「使与装备者属性相同的我方其他角色的造成的伤害提高 10%」. The condition language already
 * had 「与装备者属性相同」 as {@code is_other_same_element_as_self}; what was missing was a case that shows the per-target filter
 * actually rejecting an ally of another element, which is what this pins.
 */
public class RelicOpenClausesTest {

    private static final int LEVEL = 80;
    private static final int RELIC_LEVEL = 15;

    private static Character wearerWith(int relicSet, int pieces) {
        return CharacterFactory.create(1001, LEVEL, true, null,
                RelicFactory.suit(relicSet, pieces, RELIC_LEVEL));
    }

    private static Battle battleWith(List<Character> party) {
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(party), new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    /**
     * The same-element half reaches the ally whose element matches the wearer's, and skips one that does not.
     */
    @Test
    public void theSameElementClauseSkipsAlliesOfAnotherElement() {
        Character wearer = CharacterFactory.create(1003, LEVEL, true, null, RelicFactory.suit(312, 4, RELIC_LEVEL));
        Character sameElementAlly = CharacterFactory.create(1310, LEVEL);   // 流萤 (Fire), same as 姬子
        Character otherElementAlly = CharacterFactory.create(1001, LEVEL);  // 三月七 (Ice)
        Assertions.assertEquals(wearer.getElement(), sameElementAlly.getElement(), "precondition: same element");
        Assertions.assertNotEquals(wearer.getElement(), otherElementAlly.getElement(), "precondition: a different element");
        Battle battle = battleWith(List.of(wearer, sameElementAlly, otherElementAlly));

        Assertions.assertEquals(0.10, sameElementAlly.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "the ally of the wearer's element gets the 10%");
        Assertions.assertEquals(0.0, otherElementAlly.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "the ally of another element does not");
    }

}
