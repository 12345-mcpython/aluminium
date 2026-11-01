package com.laosun.aluminium.test.content.characters.pela;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1106 佩拉's Eidolon 4:「施放战技时，有 100% 基础概率使敌方目标冰属性抗性降低 12%，持续 2 回合」.
 *
 * <p>⚠ Registered approximation: the engine's {@code RESISTANCE_REDUCTION} is all-type, so this lowers every element's
 * resistance, not only ice.
 */
public class PelaEidolonFourTest {

    @Test
    public void theSkillLowersTheTargetsResistance() {
        double atThree = enemyResistance(3);
        double atFour = enemyResistance(4);
        System.out.println("[1106] enemy RES: E3 " + atThree + " ; E4 " + atFour);
        Assertions.assertEquals(0.12, atFour - atThree, 1e-9, "「冰属性抗性降低 12%」");
    }

    private static double enemyResistance(int eidolon) {
        Character her = CharacterFactory.create(1106, 80, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(her)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        var enemy = battle.enemyUnits().getFirst();
        battle.castImmediate(her.getSkills().get(SkillType.SKILL), her, List.of(enemy));
        battle.processRequests();
        return enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
    }
}