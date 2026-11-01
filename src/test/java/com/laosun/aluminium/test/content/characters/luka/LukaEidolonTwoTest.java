package com.laosun.aluminium.test.content.characters.luka;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1111 卢卡's Eidolon 2: 「若战技击中的敌方目标弱点为物理属性，获得 1 层【斗志】」.
 */
public class LukaEidolonTwoTest {

    private static final int LUKA = 1111;
    private static final int LEVEL = 80;
    private static final String WILL = "斗志";

    @Test
    public void theSkillPaysALayerOnlyAgainstAPhysicalWeakness() {
        int plain = layersAgainst(false);
        int weak = layersAgainst(true);
        System.out.println("[1111] plain=" + plain + " weak=" + weak);
        Assertions.assertEquals(plain + 1, weak, "「弱点为物理属性…获得 1 层」 - one MORE than the same Skill without it");
    }

    private static int layersAgainst(boolean physicalWeakness) {
        Character luka = CharacterFactory.create(LUKA, LEVEL, true, null, null, 2);
        Enemy enemy = BattleFixture.monster();
        if (physicalWeakness) {
            enemy.addWeakness(DamageElement.PHYSICAL);
        }
        Battle battle = new Battle(new ArrayList<>(List.of(luka, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        int before = luka.getBuffManager().stacksOf(WILL);
        battle.castImmediate(luka.getSkills().get(SkillType.SKILL), luka, List.of(enemy));
        return luka.getBuffManager().stacksOf(WILL) - before;
    }
}
